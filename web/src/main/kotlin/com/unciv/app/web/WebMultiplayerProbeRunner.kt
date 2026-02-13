package com.unciv.app.web

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Base64Coder
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.logic.multiplayer.storage.MultiplayerServer
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.platform.PlatformCapabilities
import com.unciv.utils.Concurrency
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

    private class ProbeChatConnection {
        var socket: Any? = null
        var open = false
        var lastError: String? = null
        val messages = ArrayList<String>()
    }

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

        val roleUser = if (role == Role.HOST) hostUser else guestUser
        val statusServer = serverClient(serverUrl, roleUser, sharedPassword)
        WebMultiplayerProbeInterop.publishState("running:server-check")
        if (!statusServer.checkServerStatus()) {
            error("Multiplayer server /isalive check failed.")
        }

        val startedAt = System.currentTimeMillis()
        val deadline = startedAt + timeoutMs
        val result = when (role) {
            Role.HOST -> runHostFlow(gameId, serverUrl, timeoutMs, deadline)
            Role.GUEST -> runGuestFlow(gameId, serverUrl, timeoutMs, deadline)
        }
        WebMultiplayerProbeInterop.publishResult(buildResultJson(result))
    }

    private suspend fun runHostFlow(
        gameId: String,
        serverUrl: String,
        timeoutMs: Long,
        deadlineMs: Long,
    ): ProbeResult {
        val hostServer = serverClient(serverUrl, hostUser, sharedPassword)

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
        hostServer.uploadGame(hostGame, withPreview = true)

        val hostPingToken = "mp-host-ping:$gameId"
        val guestAckToken = "mp-guest-ack:$gameId"
        val chat = connectProbeChat(serverUrl, hostUser, sharedPassword)
        try {
            awaitChatOpen(chat, deadlineMs)
            sendJoin(chat, gameId, deadlineMs)

            var ownChatEchoObserved = false
            var peerChatObserved = false
            var turnSyncObserved = false
            var localTurnAfter = hostGame.turns
            var localPlayerAfter = hostGame.currentPlayer
            var nextPingAt = 0L

            WebMultiplayerProbeInterop.publishState("running:host:await-guest")
            var nextDownloadPollAt = 0L
            while (System.currentTimeMillis() < deadlineMs) {
                if (chat.lastError != null) {
                    error("Host chat socket error: ${chat.lastError}")
                }

                val now = System.currentTimeMillis()
                if (now >= nextPingAt) {
                    sendChat(chat, hostCivName, hostPingToken, gameId, deadlineMs)
                    nextPingAt = now + 1500
                }

                ownChatEchoObserved = ownChatEchoObserved || chatContains(chat, hostPingToken)
                peerChatObserved = peerChatObserved || chatContains(chat, guestAckToken)

                if (!turnSyncObserved && now >= nextDownloadPollAt) {
                    val remote = runCatching { hostServer.tryDownloadGame(gameId) }.getOrNull()
                    if (remote != null) {
                        val changedTurn = remote.turns != localTurnBefore
                        val changedPlayer = remote.currentPlayer != localPlayerBefore
                        if (changedTurn || changedPlayer) {
                            turnSyncObserved = true
                            localTurnAfter = remote.turns
                            localPlayerAfter = remote.currentPlayer
                        }
                    }
                    nextDownloadPollAt = now + 500
                }

                if (turnSyncObserved && peerChatObserved && ownChatEchoObserved) break
                nextFrame()
            }

            if (!turnSyncObserved || !peerChatObserved || !ownChatEchoObserved) {
                error("Host probe failed (turnSync=$turnSyncObserved, peerChat=$peerChatObserved, ownChatEcho=$ownChatEchoObserved, chatError=${chat.lastError ?: "none"}).")
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
        } finally {
            closeChat(chat)
        }
    }

    private suspend fun runGuestFlow(
        gameId: String,
        serverUrl: String,
        timeoutMs: Long,
        deadlineMs: Long,
    ): ProbeResult {
        val guestServer = serverClient(serverUrl, guestUser, sharedPassword)
        val hostPingToken = "mp-host-ping:$gameId"
        val guestAckToken = "mp-guest-ack:$gameId"

        val chat = connectProbeChat(serverUrl, guestUser, sharedPassword)
        try {
            awaitChatOpen(chat, deadlineMs)
            sendJoin(chat, gameId, deadlineMs)

            WebMultiplayerProbeInterop.publishState("running:guest:await-host")
            var downloaded = runCatching { guestServer.tryDownloadGame(gameId) }.getOrNull()
            var peerChatObserved = chatContains(chat, hostPingToken)
            var nextDownloadPollAt = 0L
            while (System.currentTimeMillis() < deadlineMs && (downloaded == null || !peerChatObserved)) {
                if (chat.lastError != null) {
                    error("Guest chat socket error: ${chat.lastError}")
                }
                val now = System.currentTimeMillis()
                if (downloaded == null && now >= nextDownloadPollAt) {
                    downloaded = runCatching { guestServer.tryDownloadGame(gameId) }.getOrNull()
                    nextDownloadPollAt = now + 500
                }
                peerChatObserved = peerChatObserved || chatContains(chat, hostPingToken)
                nextFrame()
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
            guestServer.uploadGame(gameInfo, withPreview = true)

            var ownChatEchoObserved = false
            var nextAckAt = 0L
            while (System.currentTimeMillis() < deadlineMs) {
                if (chat.lastError != null) {
                    error("Guest chat socket error: ${chat.lastError}")
                }
                val now = System.currentTimeMillis()
                if (now >= nextAckAt) {
                    sendChat(chat, guestCivName, guestAckToken, gameId, deadlineMs)
                    nextAckAt = now + 1000
                }
                ownChatEchoObserved = ownChatEchoObserved || chatContains(chat, guestAckToken)
                if (ownChatEchoObserved) break
                nextFrame()
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
        } finally {
            closeChat(chat)
        }
    }

    private fun serverClient(serverUrl: String, userId: String, password: String): MultiplayerServer {
        val auth = mapOf("Authorization" to authHeaderValue(userId, password))
        return MultiplayerServer(fileStorageIdentifier = serverUrl, authenticationHeader = auth)
    }

    private fun authHeaderValue(userId: String, password: String): String {
        return "Basic ${Base64Coder.encodeString("$userId:$password")}".trim()
    }

    private fun connectProbeChat(serverUrl: String, userId: String, password: String): ProbeChatConnection {
        val chat = ProbeChatConnection()
        val wsBase = toWebSocketBase(serverUrl)
        val auth = WebMultiplayerProbeSocketInterop.encodeURIComponent(authHeaderValue(userId, password))
        val wsUrl = "$wsBase/chat?auth=$auth"

        chat.socket = WebMultiplayerProbeSocketInterop.connect(
            wsUrl,
            { chat.open = true },
            { data -> chat.messages.add(data ?: "") },
            { message -> chat.lastError = message ?: "WebSocket error" },
            { code, reason ->
                if (code != 1000 && chat.lastError == null) {
                    chat.lastError = "Socket closed ($code${if (reason.isNullOrBlank()) "" else ": $reason"})"
                }
            }
        )
        if (chat.socket == null) chat.lastError = "Failed to create websocket to $wsUrl"
        return chat
    }

    private suspend fun awaitChatOpen(chat: ProbeChatConnection, deadlineMs: Long) {
        while (System.currentTimeMillis() < deadlineMs) {
            if (chat.open) return
            if (chat.lastError != null) error("Chat socket failed before open: ${chat.lastError}")
            nextFrame()
        }
        error("Timed out waiting for chat websocket to open.")
    }

    private suspend fun sendJoin(chat: ProbeChatConnection, gameId: String, deadlineMs: Long) {
        val payload = "{\"type\":\"join\",\"gameIds\":[\"${escapeJson(gameId)}\"]}"
        sendWhenReady(chat, payload, deadlineMs)
    }

    private suspend fun sendChat(chat: ProbeChatConnection, civName: String, message: String, gameId: String, deadlineMs: Long) {
        val payload = buildString(196) {
            append('{')
            append("\"type\":\"chat\",")
            append("\"civName\":\"").append(escapeJson(civName)).append("\",")
            append("\"message\":\"").append(escapeJson(message)).append("\",")
            append("\"gameId\":\"").append(escapeJson(gameId)).append("\"")
            append('}')
        }
        sendWhenReady(chat, payload, deadlineMs)
    }

    private suspend fun sendWhenReady(chat: ProbeChatConnection, payload: String, deadlineMs: Long) {
        val socket = chat.socket ?: error("Chat socket unavailable for send.")
        while (System.currentTimeMillis() < deadlineMs) {
            if (chat.lastError != null) error("Chat socket send failed: ${chat.lastError}")
            if (WebMultiplayerProbeSocketInterop.readyState(socket) == 1) {
                WebMultiplayerProbeSocketInterop.send(socket, payload)
                return
            }
            nextFrame()
        }
        error("Timed out waiting for chat socket readyState=OPEN.")
    }

    private suspend fun nextFrame() {
        suspendCoroutine { continuation ->
            val app = Gdx.app
            var resumed = false
            fun resumeOnce() {
                if (resumed) return
                resumed = true
                continuation.resume(Unit)
            }
            if (app == null) {
                WebMultiplayerProbeInterop.schedule({ resumeOnce() }, 16)
            } else {
                app.postRunnable { resumeOnce() }
                WebMultiplayerProbeInterop.schedule({ resumeOnce() }, 16)
            }
        }
    }

    private fun closeChat(chat: ProbeChatConnection) {
        val socket = chat.socket ?: return
        WebMultiplayerProbeSocketInterop.close(socket, 1000, "probe_done")
        chat.socket = null
    }

    private fun chatContains(chat: ProbeChatConnection, token: String): Boolean {
        return chat.messages.any { it.contains(token) }
    }

    private fun toWebSocketBase(serverUrl: String): String {
        val trimmed = serverUrl.trim().trimEnd('/')
        return when {
            trimmed.startsWith("https://") -> "wss://${trimmed.substringAfter("https://")}" 
            trimmed.startsWith("http://") -> "ws://${trimmed.substringAfter("http://")}" 
            trimmed.startsWith("ws://") || trimmed.startsWith("wss://") -> trimmed
            else -> "ws://$trimmed"
        }
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
