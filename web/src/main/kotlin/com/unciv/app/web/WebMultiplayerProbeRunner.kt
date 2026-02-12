package com.unciv.app.web

import com.unciv.UncivGame
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.logic.multiplayer.chat.Chat
import com.unciv.logic.multiplayer.chat.ChatStore
import com.unciv.logic.multiplayer.chat.ChatWebSocket
import com.unciv.logic.multiplayer.chat.Message
import com.unciv.logic.multiplayer.storage.MultiplayerServer
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.platform.PlatformCapabilities
import com.unciv.utils.Concurrency
import com.unciv.utils.delayMillis
import java.time.Instant
import java.util.UUID

object WebMultiplayerProbeRunner {
    private const val defaultTimeoutMs = 240000L
    private const val hostUser = "00000000-0000-0000-0000-0000000000a1"
    private const val guestUser = "00000000-0000-0000-0000-0000000000b2"
    private const val sharedPassword = "webtest-pass"
    private const val hostCivName = "HostProbe"
    private const val guestCivName = "GuestProbe"
    private var started = false

    private enum class Role { HOST, GUEST }

    private data class ProbeResult(
        val role: Role,
        val gameId: String,
        val serverUrl: String,
        val timeoutMs: Long,
        val turnSyncObserved: Boolean,
        val peerChatObserved: Boolean,
        val ownChatEchoObserved: Boolean,
        val localTurnBefore: Int,
        val localTurnAfter: Int,
        val localPlayerBefore: String,
        val localPlayerAfter: String,
        val notes: String,
        val durationMs: Long,
    )

    fun maybeStart(game: WebGame): Boolean {
        if (started) return false
        if (!WebMultiplayerProbeInterop.isEnabled()) return false
        started = true
        WebMultiplayerProbeInterop.publishState("starting")
        Concurrency.runOnGLThread("WebMultiplayerProbeRunner") {
            runCatching { runProbe(game) }
                .onFailure { throwable ->
                    WebMultiplayerProbeInterop.publishError("${throwable::class.simpleName}: ${throwable.message ?: "unknown error"}")
                }
        }
        return true
    }

    private suspend fun runProbe(@Suppress("UNUSED_PARAMETER") game: WebGame) {
        if (!PlatformCapabilities.current.onlineMultiplayer) {
            error("Online multiplayer is disabled for current web profile.")
        }

        val role = parseRole(WebMultiplayerProbeInterop.getRole())
        val gameId = parseGameId(WebMultiplayerProbeInterop.getGameId())
        val timeoutMs = parseTimeoutMs(WebMultiplayerProbeInterop.getTimeoutMs())
        val serverUrl = WebMultiplayerProbeInterop.getTestMultiplayerServerUrl()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: error("Missing multiplayer test server URL. Provide mpServer query param.")

        val settings = UncivGame.Current.settings.multiplayer
        val previousServer = settings.getServer()
        val previousUserId = settings.getUserId()
        val previousPassword = settings.getCurrentServerPassword()

        ChatWebSocket.stop()
        ChatStore.clear()

        val startedAt = System.currentTimeMillis()
        val deadline = startedAt + timeoutMs
        try {
            settings.setServer(serverUrl)
            settings.setCurrentServerPassword(sharedPassword)
            settings.setUserId(if (role == Role.HOST) hostUser else guestUser)

            WebMultiplayerProbeInterop.publishState("running:server-check")
            if (!MultiplayerServer().checkServerStatus()) {
                error("Multiplayer server /isalive check failed.")
            }

            val result = when (role) {
                Role.HOST -> runHostFlow(gameId, serverUrl, timeoutMs, deadline)
                Role.GUEST -> runGuestFlow(gameId, serverUrl, timeoutMs, deadline)
            }

            WebMultiplayerProbeInterop.publishResult(buildResultJson(result))
        } finally {
            settings.setServer(previousServer)
            settings.setUserId(previousUserId)
            if (previousPassword != null) settings.setCurrentServerPassword(previousPassword)
            ChatWebSocket.stop()
            ChatStore.clear()
        }
    }

    private suspend fun runHostFlow(
        gameId: String,
        serverUrl: String,
        timeoutMs: Long,
        deadlineMs: Long,
    ): ProbeResult {
        WebMultiplayerProbeInterop.publishState("running:host:create")
        val setup = GameSetupInfo.fromSettings().apply {
            gameParameters.players = arrayListOf(
                Player(playerType = PlayerType.Human, playerId = hostUser),
                Player(playerType = PlayerType.Human, playerId = guestUser),
            )
            gameParameters.isOnlineMultiplayer = true
            gameParameters.randomNumberOfCityStates = false
            gameParameters.numberOfCityStates = 0
            gameParameters.minNumberOfCityStates = 0
            gameParameters.maxNumberOfCityStates = 0
            gameParameters.noBarbarians = true
            mapParameters.shape = MapShape.rectangular
            mapParameters.worldWrap = true
            mapParameters.mapSize = MapSize.Tiny
            mapParameters.type = MapType.pangaea
        }
        val hostGame = GameStarter.startNewGame(setup)
        hostGame.gameId = gameId
        hostGame.gameParameters.multiplayerServerUrl = serverUrl

        val localTurnBefore = hostGame.turns
        val localPlayerBefore = hostGame.currentPlayer

        WebMultiplayerProbeInterop.publishState("running:host:upload")
        MultiplayerServer().uploadGame(hostGame, withPreview = true)

        val hostPingToken = "mp-host-ping:$gameId"
        val guestAckToken = "mp-guest-ack:$gameId"
        val chat = ChatStore.getChatByGameId(gameId)
        ChatWebSocket.restart(force = true)
        ChatWebSocket.requestMessageSend(Message.Join(listOf(gameId)))

        var ownChatEchoObserved = false
        var peerChatObserved = false
        var turnSyncObserved = false
        var localTurnAfter = hostGame.turns
        var localPlayerAfter = hostGame.currentPlayer
        var nextPingAt = 0L

        WebMultiplayerProbeInterop.publishState("running:host:await-guest")
        while (System.currentTimeMillis() < deadlineMs) {
            val now = System.currentTimeMillis()
            if (now >= nextPingAt) {
                ChatWebSocket.requestMessageSend(Message.Chat(hostCivName, hostPingToken, gameId))
                nextPingAt = now + 1500
            }

            ownChatEchoObserved = ownChatEchoObserved || chatContains(chat, hostCivName, hostPingToken)
            peerChatObserved = peerChatObserved || chatContains(chat, guestCivName, guestAckToken)

            if (!turnSyncObserved) {
                val remote = runCatching { MultiplayerServer().tryDownloadGame(gameId) }.getOrNull()
                if (remote != null) {
                    val changedTurn = remote.turns != localTurnBefore
                    val changedPlayer = remote.currentPlayer != localPlayerBefore
                    if (changedTurn || changedPlayer) {
                        turnSyncObserved = true
                        localTurnAfter = remote.turns
                        localPlayerAfter = remote.currentPlayer
                    }
                }
            }

            if (turnSyncObserved && peerChatObserved && ownChatEchoObserved) break
            delayMillis(250)
        }

        if (!turnSyncObserved || !peerChatObserved || !ownChatEchoObserved) {
            error("Host probe failed (turnSync=$turnSyncObserved, peerChat=$peerChatObserved, ownChatEcho=$ownChatEchoObserved).")
        }

        return ProbeResult(
            role = Role.HOST,
            gameId = gameId,
            serverUrl = serverUrl,
            timeoutMs = timeoutMs,
            turnSyncObserved = true,
            peerChatObserved = true,
            ownChatEchoObserved = true,
            localTurnBefore = localTurnBefore,
            localTurnAfter = localTurnAfter,
            localPlayerBefore = localPlayerBefore,
            localPlayerAfter = localPlayerAfter,
            notes = "Host observed guest turn update and guest chat ack.",
            durationMs = timeoutMs - (deadlineMs - System.currentTimeMillis()).coerceAtLeast(0L),
        )
    }

    private suspend fun runGuestFlow(
        gameId: String,
        serverUrl: String,
        timeoutMs: Long,
        deadlineMs: Long,
    ): ProbeResult {
        val hostPingToken = "mp-host-ping:$gameId"
        val guestAckToken = "mp-guest-ack:$gameId"
        val chat = ChatStore.getChatByGameId(gameId)
        ChatWebSocket.restart(force = true)
        ChatWebSocket.requestMessageSend(Message.Join(listOf(gameId)))

        WebMultiplayerProbeInterop.publishState("running:guest:await-host")
        var downloaded = runCatching { MultiplayerServer().tryDownloadGame(gameId) }.getOrNull()
        var peerChatObserved = chatContains(chat, hostCivName, hostPingToken)
        while (System.currentTimeMillis() < deadlineMs && (downloaded == null || !peerChatObserved)) {
            if (downloaded == null) {
                downloaded = runCatching { MultiplayerServer().tryDownloadGame(gameId) }.getOrNull()
            }
            peerChatObserved = peerChatObserved || chatContains(chat, hostCivName, hostPingToken)
            delayMillis(250)
        }
        if (downloaded == null) error("Guest probe failed to download host game before timeout.")
        if (!peerChatObserved) error("Guest probe did not receive host chat ping before timeout.")

        val gameInfo = downloaded ?: error("Guest probe missing downloaded game payload.")
        val localTurnBefore = gameInfo.turns
        val localPlayerBefore = gameInfo.currentPlayer

        WebMultiplayerProbeInterop.publishState("running:guest:advance-and-upload")
        gameInfo.nextTurn()
        gameInfo.gameParameters.multiplayerServerUrl = serverUrl
        val localTurnAfter = gameInfo.turns
        val localPlayerAfter = gameInfo.currentPlayer
        val turnSyncObserved = localTurnAfter != localTurnBefore || localPlayerAfter != localPlayerBefore
        if (!turnSyncObserved) {
            error("Guest probe nextTurn produced no state change (turn=$localTurnBefore, player=$localPlayerBefore).")
        }
        MultiplayerServer().uploadGame(gameInfo, withPreview = true)

        var ownChatEchoObserved = false
        var nextAckAt = 0L
        while (System.currentTimeMillis() < deadlineMs) {
            val now = System.currentTimeMillis()
            if (now >= nextAckAt) {
                ChatWebSocket.requestMessageSend(Message.Chat(guestCivName, guestAckToken, gameId))
                nextAckAt = now + 1000
            }
            ownChatEchoObserved = ownChatEchoObserved || chatContains(chat, guestCivName, guestAckToken)
            if (ownChatEchoObserved) break
            delayMillis(200)
        }
        if (!ownChatEchoObserved) error("Guest probe did not observe its own chat ack echo.")

        return ProbeResult(
            role = Role.GUEST,
            gameId = gameId,
            serverUrl = serverUrl,
            timeoutMs = timeoutMs,
            turnSyncObserved = true,
            peerChatObserved = true,
            ownChatEchoObserved = true,
            localTurnBefore = localTurnBefore,
            localTurnAfter = localTurnAfter,
            localPlayerBefore = localPlayerBefore,
            localPlayerAfter = localPlayerAfter,
            notes = "Guest downloaded host game, advanced one turn, uploaded and sent chat ack.",
            durationMs = timeoutMs - (deadlineMs - System.currentTimeMillis()).coerceAtLeast(0L),
        )
    }

    private fun chatContains(chat: Chat, civName: String, token: String): Boolean {
        var found = false
        chat.forEachMessage { from, message ->
            if (from == civName && message.contains(token)) found = true
        }
        return found
    }

    private fun parseRole(raw: String?): Role {
        return when (raw?.trim()?.lowercase()) {
            "host" -> Role.HOST
            "guest" -> Role.GUEST
            else -> error("Invalid mpRole. Expected 'host' or 'guest'.")
        }
    }

    private fun parseGameId(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) error("Missing mpGameId.")
        runCatching { UUID.fromString(value) }.getOrElse {
            error("Invalid mpGameId UUID: $value")
        }
        return value
    }

    private fun parseTimeoutMs(raw: String?): Long {
        val parsed = raw?.trim()?.toLongOrNull() ?: return defaultTimeoutMs
        if (parsed < 10000L) error("mpTimeoutMs must be >= 10000.")
        return parsed
    }

    private fun buildResultJson(result: ProbeResult): String {
        val builder = StringBuilder(512)
        builder.append('{')
        builder.append("\"generatedAt\":\"").append(escapeJson(Instant.now().toString())).append("\",")
        builder.append("\"passed\":true,")
        builder.append("\"role\":\"").append(result.role.name.lowercase()).append("\",")
        builder.append("\"gameId\":\"").append(escapeJson(result.gameId)).append("\",")
        builder.append("\"serverUrl\":\"").append(escapeJson(result.serverUrl)).append("\",")
        builder.append("\"timeoutMs\":").append(result.timeoutMs).append(',')
        builder.append("\"turnSyncObserved\":").append(result.turnSyncObserved).append(',')
        builder.append("\"peerChatObserved\":").append(result.peerChatObserved).append(',')
        builder.append("\"ownChatEchoObserved\":").append(result.ownChatEchoObserved).append(',')
        builder.append("\"localTurnBefore\":").append(result.localTurnBefore).append(',')
        builder.append("\"localTurnAfter\":").append(result.localTurnAfter).append(',')
        builder.append("\"localPlayerBefore\":\"").append(escapeJson(result.localPlayerBefore)).append("\",")
        builder.append("\"localPlayerAfter\":\"").append(escapeJson(result.localPlayerAfter)).append("\",")
        builder.append("\"durationMs\":").append(result.durationMs).append(',')
        builder.append("\"notes\":\"").append(escapeJson(result.notes)).append('"')
        builder.append('}')
        return builder.toString()
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
