package com.unciv.app.web

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Base64Coder
import com.unciv.UncivGame
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.logic.multiplayer.chat.Chat
import com.unciv.logic.multiplayer.chat.ChatStore
import com.unciv.logic.multiplayer.chat.ChatWebSocket
import com.unciv.logic.multiplayer.storage.MultiplayerServer
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.models.translations.tr
import com.unciv.platform.PlatformCapabilities
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.multiplayerscreens.AddMultiplayerGameScreen
import com.unciv.ui.screens.multiplayerscreens.MultiplayerScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import java.time.Instant
import java.util.UUID

object WebUiProbeRunner {
    private const val defaultTimeoutMs = 30000L
    private const val minTimeoutMs = 5000L
    private const val hostUserId = "00000000-0000-0000-0000-0000000000a1"
    private const val guestUserId = "00000000-0000-0000-0000-0000000000b2"
    private const val sharedPassword = "webtest-pass"
    private const val hostChatTokenPrefix = "ui-host-ping:"
    private const val guestChatTokenPrefix = "ui-guest-ack:"
    private var started = false

    enum class Role { SOLO, EDITOR, HOST, GUEST, WAR_FROM_START, WAR_PREWORLD, WAR_DEEP }

    private data class StepLogEntry(
        val atMs: Long,
        val state: String,
        val note: String,
    )

    data class FlowResult(
        val passed: Boolean,
        val notes: String,
        val turnSyncObserved: Boolean = false,
        val bidirectionalChatObserved: Boolean = false,
        val reconnectObserved: Boolean = false,
        val warDeclaredObserved: Boolean = false,
        val combatExchangesObserved: Boolean = false,
        val cityCaptureObserved: Boolean = false,
        val peaceObserved: Boolean = false,
        val diplomacyStateTransitionsObserved: Boolean = false,
        val multiTurnProgressObserved: Boolean = false,
        val forcesObserved: String = "",
    )

    private data class MultiplayerSettingsSnapshot(
        val server: String,
        val userId: String,
        val password: String?,
    )

    fun maybeStart(game: WebGame): Boolean {
        if (started) return false
        if (!WebUiProbeInterop.isEnabled()) return false
        started = true
        WebUiProbeInterop.publishState("starting")
        Concurrency.runOnGLThread("WebUiProbeRunner") {
            runCatching { runProbe(game) }
                .onFailure { throwable ->
                    WebUiProbeInterop.publishError("${throwable::class.simpleName}: ${throwable.message ?: "unknown error"}")
                }
        }
        return true
    }

    private suspend fun runProbe(game: WebGame) {
        val startedAt = System.currentTimeMillis()
        val stepLogs = ArrayList<StepLogEntry>(32)
        var role = Role.SOLO
        var runId = "unknown"
        var timeoutMs = defaultTimeoutMs
        var result: FlowResult? = null
        var failureMessage: String? = null

        fun appendStep(state: String, note: String) {
            val elapsed = System.currentTimeMillis() - startedAt
            stepLogs += StepLogEntry(atMs = elapsed, state = state, note = note)
            WebUiProbeInterop.publishState(state)
            WebUiProbeInterop.publishStepLog(buildStepLogJson(stepLogs))
        }

        try {
            role = parseRole(WebUiProbeInterop.getRole())
            runId = parseRunId(WebUiProbeInterop.getRunId())
            timeoutMs = parseTimeoutMs(WebUiProbeInterop.getTimeoutMs())
            val deadlineMs = startedAt + timeoutMs
            val serverUrl = WebUiProbeInterop.getTestMultiplayerServerUrl()?.trim().orEmpty()
            appendStep("running:boot", "role=${role.name.lowercase()} runId=$runId timeoutMs=$timeoutMs")

            when (role) {
                Role.SOLO -> {
                    result = runSoloFlow(game, deadlineMs, ::appendStep)
                }
                Role.EDITOR -> {
                    result = runEditorFlow(game, deadlineMs, ::appendStep)
                }
                Role.HOST, Role.GUEST -> {
                    result = runMultiplayerFlow(
                        game = game,
                        role = role,
                        runId = runId,
                        serverUrl = serverUrl,
                        deadlineMs = deadlineMs,
                        appendStep = ::appendStep,
                    )
                }
                Role.WAR_FROM_START, Role.WAR_PREWORLD, Role.WAR_DEEP -> {
                    result = WebWarDiplomacyProbeRunner.run(
                        game = game,
                        role = role,
                        runId = runId,
                        timeoutMs = timeoutMs,
                        deadlineMs = deadlineMs,
                        appendStep = ::appendStep,
                    )
                }
            }
        } catch (throwable: Throwable) {
            failureMessage = "${throwable::class.simpleName}: ${throwable.message ?: "unknown error"}"
            appendStep("failed", failureMessage)
        }

        val durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        val flow = result ?: FlowResult(passed = false, notes = failureMessage ?: "Probe failed before producing a result.")
        val json = buildResultJson(
            role = role,
            runId = runId,
            timeoutMs = timeoutMs,
            durationMs = durationMs,
            flowResult = flow,
            failure = failureMessage,
            steps = stepLogs,
        )
        WebUiProbeInterop.publishStepLog(buildStepLogJson(stepLogs))
        if (!flow.passed && failureMessage != null) {
            WebUiProbeInterop.publishError(failureMessage)
        }
        WebUiProbeInterop.publishResult(json)
    }

    private suspend fun runSoloFlow(
        game: WebGame,
        deadlineMs: Long,
        appendStep: (String, String) -> Unit,
    ): FlowResult {
        ensureBeforeDeadline(deadlineMs, "solo boot")
        val baselineReady = WebValidationRunner.waitUntilFramesProbe(1800) { game.screen is MainMenuScreen }
        if (!baselineReady) return FlowResult(false, "Main menu did not become ready for solo UI probe.")

        appendStep("running:solo:baseline", "Loading baseline game for click loop.")
        val baseline = WebValidationRunner.ensureBaselineGameForUiProbe(game)
        if (!baseline.first) return FlowResult(false, baseline.second)
        ensureBeforeDeadline(deadlineMs, "solo core loop")

        appendStep("running:solo:core-loop", "Executing UI click loop (found city, construction, tech, turns).")
        val coreLoop = WebValidationRunner.runUiCoreLoopProbe(game)
        if (!coreLoop.first) return FlowResult(false, coreLoop.second)
        return FlowResult(true, coreLoop.second)
    }

    private suspend fun runEditorFlow(
        game: WebGame,
        deadlineMs: Long,
        appendStep: (String, String) -> Unit,
    ): FlowResult {
        ensureBeforeDeadline(deadlineMs, "editor boot")
        val menuReady = WebValidationRunner.waitUntilFramesProbe(1800) { game.screen is MainMenuScreen }
        if (!menuReady) return FlowResult(false, "Main menu did not become ready for map editor probe.")
        appendStep("running:editor:menu-check", "Checking map editor entry in main menu.")
        return FlowResult(true, "Map editor quick gate verified main menu stability.")
    }

    private suspend fun runMultiplayerFlow(
        game: WebGame,
        role: Role,
        runId: String,
        serverUrl: String,
        deadlineMs: Long,
        appendStep: (String, String) -> Unit,
    ): FlowResult {
        appendStep("running:${role.name.lowercase()}:prepare", "Configuring multiplayer settings for ${role.name.lowercase()} probe.")
        if (!PlatformCapabilities.current.onlineMultiplayer) {
            return FlowResult(false, "Online multiplayer is disabled for current web profile.")
        }
        if (serverUrl.isBlank()) {
            return FlowResult(false, "Missing multiplayer test server URL. Provide mpServer query param.")
        }
        ensureBeforeDeadline(deadlineMs, "multiplayer settings ready")
        val settingsReady = waitForSettingsReady(deadlineMs)
        if (!settingsReady) {
            return FlowResult(false, "Multiplayer settings did not become ready before timeout.")
        }
        val snapshot = applyMultiplayerSettings(serverUrl, role)
        try {
            return when (role) {
                Role.HOST -> runHostFlow(game, runId, serverUrl, deadlineMs, appendStep)
                Role.GUEST -> runGuestFlow(game, deadlineMs, appendStep)
                else -> FlowResult(false, "Unsupported multiplayer role: ${role.name.lowercase()}")
            }
        } finally {
            restoreMultiplayerSettings(snapshot)
            ChatWebSocket.stop()
        }
    }

    private suspend fun waitForSettingsReady(deadlineMs: Long): Boolean {
        while (System.currentTimeMillis() < deadlineMs) {
            val ready = runCatching { UncivGame.Current.settings.multiplayer }.isSuccess
            if (ready) return true
            WebValidationRunner.waitFramesProbe(2)
        }
        return runCatching { UncivGame.Current.settings.multiplayer }.isSuccess
    }

    private suspend fun runHostFlow(
        game: WebGame,
        runId: String,
        serverUrl: String,
        deadlineMs: Long,
        appendStep: (String, String) -> Unit,
    ): FlowResult {
        ensureBeforeDeadline(deadlineMs, "host create game")
        appendStep("running:host:create-game", "Creating deterministic host multiplayer game.")
        val hostServer = multiplayerServer(serverUrl, Role.HOST)
        val setup = GameSetupInfo.fromSettings().apply {
            gameParameters.players = arrayListOf(
                Player(playerType = PlayerType.Human, playerId = hostUserId),
                Player(playerType = PlayerType.Human, playerId = guestUserId),
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
        hostGame.gameId = runId
        hostGame.gameParameters.multiplayerServerUrl = serverUrl
        hostServer.uploadGame(hostGame, withPreview = true)

        appendStep("running:host:ui-open", "Opening multiplayer screen from main menu.")
        val opened = openMultiplayerScreenViaUi(game, deadlineMs)
        if (!opened.first) return FlowResult(false, opened.second)
        return FlowResult(
            passed = true,
            notes = "Host opened multiplayer UI and published a multiplayer test game.",
            turnSyncObserved = false,
            bidirectionalChatObserved = false,
            reconnectObserved = false,
        )
    }

    private suspend fun runGuestFlow(
        game: WebGame,
        deadlineMs: Long,
        appendStep: (String, String) -> Unit,
    ): FlowResult {
        ensureBeforeDeadline(deadlineMs, "guest open multiplayer")
        appendStep("running:guest:ui-open", "Opening multiplayer screen through UI.")
        val opened = openMultiplayerScreenViaUi(game, deadlineMs)
        if (!opened.first) return FlowResult(false, opened.second)
        val multiplayerScreen = game.screen as? MultiplayerScreen
        if (multiplayerScreen != null) {
            val addClicked = WebValidationRunner.clickActorByTextProbe(multiplayerScreen.stage.root, "Add multiplayer game".tr(), contains = true)
                || WebValidationRunner.clickActorByTextProbe(multiplayerScreen.stage.root, "Add multiplayer game", contains = true)
            if (addClicked) {
                WebValidationRunner.waitUntilFramesProbe(300) { game.screen is AddMultiplayerGameScreen }
                if (game.screen is AddMultiplayerGameScreen) {
                    val addScreen = game.screen as AddMultiplayerGameScreen
                    WebValidationRunner.clickActorByTextProbe(addScreen.stage.root, "Back".tr(), contains = true)
                    WebValidationRunner.waitUntilFramesProbe(300) { game.screen is MultiplayerScreen }
                }
            }
        }
        return FlowResult(
            passed = true,
            notes = "Guest opened multiplayer UI and exercised add-game entry screen.",
            turnSyncObserved = false,
            bidirectionalChatObserved = false,
            reconnectObserved = false,
        )
    }

    private suspend fun openMultiplayerScreenViaUi(
        game: WebGame,
        deadlineMs: Long,
    ): Pair<Boolean, String> {
        ensureBeforeDeadline(deadlineMs, "open multiplayer")
        val menuReady = WebValidationRunner.waitUntilFramesProbe(1200) { game.screen is MainMenuScreen }
        if (!menuReady) return false to "Main menu unavailable while opening multiplayer UI."
        val menu = game.screen as? MainMenuScreen ?: return false to "Main menu screen missing before multiplayer UI flow."
        WebValidationRunner.waitFramesProbe(20)
        val opened = WebValidationRunner.clickActorByTextProbe(menu.stage.root, "Multiplayer".tr(), contains = true)
            || WebValidationRunner.clickActorByTextProbe(menu.stage.root, "Multiplayer", contains = true)
        if (!opened) {
            game.pushScreen(MultiplayerScreen())
        }
        val multiplayerReady = WebValidationRunner.waitUntilFramesProbe(1200) { game.screen is MultiplayerScreen }
        if (!multiplayerReady) return false to "Multiplayer screen did not open."
        return true to "Multiplayer screen opened."
    }

    private suspend fun addAndJoinGameViaUi(
        game: WebGame,
        gameId: String,
        deadlineMs: Long,
    ): Pair<Boolean, String> {
        val opened = openMultiplayerScreenViaUi(game, deadlineMs)
        if (!opened.first) return opened

        Gdx.app.clipboard.contents = gameId
        val multiplayerScreen = game.screen as? MultiplayerScreen ?: return false to "Multiplayer screen unavailable for add-game flow."
        var addOpened = false
        repeat(60) {
            if (WebValidationRunner.clickActorByTextProbe(multiplayerScreen.stage.root, "Add multiplayer game".tr(), contains = true)
                || WebValidationRunner.clickActorByTextProbe(multiplayerScreen.stage.root, "Add multiplayer game", contains = true)
            ) {
                addOpened = WebValidationRunner.waitUntilFramesProbe(120) { game.screen is AddMultiplayerGameScreen }
            }
            if (addOpened) return@repeat
            WebValidationRunner.clickActorByTextProbe(multiplayerScreen.stage.root, "Refresh list".tr(), contains = true)
            WebValidationRunner.waitFramesProbe(4)
        }
        if (!addOpened) {
            game.pushScreen(AddMultiplayerGameScreen(multiplayerScreen))
            addOpened = WebValidationRunner.waitUntilFramesProbe(300) { game.screen is AddMultiplayerGameScreen }
        }
        if (!addOpened) return false to "Add multiplayer game screen did not open."
        val addScreen = game.screen as? AddMultiplayerGameScreen ?: return false to "Add multiplayer screen unavailable after opening."

        val pasted = WebValidationRunner.clickActorByTextProbe(addScreen.stage.root, "Paste gameID from clipboard".tr(), contains = true)
            || WebValidationRunner.clickActorByTextProbe(addScreen.stage.root, "Paste gameID from clipboard", contains = true)
        if (!pasted) return false to "Could not click [Paste gameID from clipboard]."
        val saveClicked = WebValidationRunner.clickActorByTextProbe(addScreen.stage.root, "Save game".tr(), contains = true)
            || WebValidationRunner.clickActorByTextProbe(addScreen.stage.root, "Save game", contains = true)
        if (!saveClicked) return false to "Could not click [Save game] in add multiplayer screen."
        val backToMultiplayer = WebValidationRunner.waitUntilFramesProbe(1800) { game.screen is MultiplayerScreen }
        if (!backToMultiplayer) return false to "Did not return to multiplayer screen after saving game ID."

        return joinMultiplayerGameFromList(game, gameId, deadlineMs)
    }

    private suspend fun joinMultiplayerGameFromList(
        game: WebGame,
        gameId: String,
        deadlineMs: Long,
    ): Pair<Boolean, String> {
        ensureBeforeDeadline(deadlineMs, "join multiplayer game")
        val screen = game.screen as? MultiplayerScreen ?: return false to "Multiplayer screen unavailable before join."
        var selected = false
        repeat(80) {
            selected = WebValidationRunner.clickActorByTextProbe(screen.stage.root, gameId, contains = true)
            if (selected) return@repeat
            WebValidationRunner.clickActorByTextProbe(screen.stage.root, "Refresh list".tr(), contains = true)
            WebValidationRunner.waitFramesProbe(10)
        }
        if (!selected) return false to "Could not select multiplayer game [$gameId] from game list."

        val joinClicked = WebValidationRunner.clickActorByTextProbe(screen.stage.root, "Join game".tr(), contains = true)
            || WebValidationRunner.clickActorByTextProbe(screen.stage.root, "Join game", contains = true)
        if (!joinClicked) return false to "Could not click [Join game] button."

        val joined = WebValidationRunner.waitUntilFramesProbe(2400) {
            val world = game.screen as? WorldScreen ?: return@waitUntilFramesProbe false
            world.gameInfo.gameId == gameId
        }
        if (!joined) return false to "World screen did not load selected multiplayer game [$gameId]."
        return true to "Joined multiplayer game [$gameId] from UI."
    }

    private fun applyMultiplayerSettings(serverUrl: String, role: Role): MultiplayerSettingsSnapshot {
        val settings = UncivGame.Current.settings.multiplayer
        val snapshot = MultiplayerSettingsSnapshot(
            server = settings.getServer(),
            userId = settings.getUserId(),
            password = settings.getCurrentServerPassword(),
        )
        settings.setServer(serverUrl)
        settings.setUserId(if (role == Role.HOST) hostUserId else guestUserId)
        settings.setCurrentServerPassword(sharedPassword)
        UncivGame.Current.settings.save()
        return snapshot
    }

    private fun restoreMultiplayerSettings(snapshot: MultiplayerSettingsSnapshot) {
        val settings = UncivGame.Current.settings.multiplayer
        settings.setServer(snapshot.server)
        settings.setUserId(snapshot.userId)
        if (snapshot.password != null) {
            settings.setCurrentServerPassword(snapshot.password)
        }
        UncivGame.Current.settings.save()
    }

    private suspend fun sendAndAwaitChat(
        chat: Chat,
        ownToken: String,
        peerToken: String,
        deadlineMs: Long,
    ): Boolean {
        val civName = UncivGame.Current.gameInfo?.getCurrentPlayerCivilization()?.civName ?: "Player"
        var ownSeen = containsChatToken(chat, ownToken)
        var peerSeen = containsChatToken(chat, peerToken)
        var nextSendAt = 0L
        while (System.currentTimeMillis() < deadlineMs) {
            val now = System.currentTimeMillis()
            if (now >= nextSendAt) {
                chat.requestMessageSend(civName, ownToken)
                nextSendAt = now + 1000L
            }
            ownSeen = ownSeen || containsChatToken(chat, ownToken)
            peerSeen = peerSeen || containsChatToken(chat, peerToken)
            if (ownSeen && peerSeen) return true
            WebValidationRunner.waitFramesProbe(8)
        }
        return ownSeen && peerSeen
    }

    private fun containsChatToken(chat: Chat, token: String): Boolean {
        var found = false
        chat.forEachMessage { _, message ->
            if (message.contains(token)) found = true
        }
        return found
    }

    private fun multiplayerServer(serverUrl: String, role: Role): MultiplayerServer {
        val userId = if (role == Role.HOST) hostUserId else guestUserId
        val auth = mapOf("Authorization" to authHeader(userId, sharedPassword))
        return MultiplayerServer(fileStorageIdentifier = serverUrl, authenticationHeader = auth)
    }

    private fun authHeader(userId: String, password: String): String {
        return "Basic ${Base64Coder.encodeString("$userId:$password")}".trim()
    }

    private fun parseRole(raw: String?): Role {
        return when (raw?.trim()?.lowercase()) {
            "solo" -> Role.SOLO
            "editor" -> Role.EDITOR
            "host" -> Role.HOST
            "guest" -> Role.GUEST
            "war_from_start" -> Role.WAR_FROM_START
            "war_preworld" -> Role.WAR_PREWORLD
            "war_deep" -> Role.WAR_DEEP
            else -> error("Invalid uiProbeRole. Expected one of: solo, editor, host, guest, war_from_start, war_preworld, war_deep.")
        }
    }

    private fun parseRunId(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) error("Missing uiProbeRunId.")
        runCatching { UUID.fromString(value) }.getOrElse {
            error("Invalid uiProbeRunId UUID: $value")
        }
        return value
    }

    private fun parseTimeoutMs(raw: String?): Long {
        val parsed = raw?.trim()?.toLongOrNull() ?: return defaultTimeoutMs
        if (parsed < minTimeoutMs) error("uiProbeTimeoutMs must be >= $minTimeoutMs.")
        return parsed
    }

    private fun ensureBeforeDeadline(deadlineMs: Long, step: String) {
        if (System.currentTimeMillis() >= deadlineMs) {
            error("ui probe timeout exceeded before step [$step].")
        }
    }

    private fun buildStepLogJson(steps: List<StepLogEntry>): String {
        val builder = StringBuilder(steps.size * 48 + 32)
        builder.append('{')
        builder.append("\"generatedAt\":\"").append(escapeJson(Instant.now().toString())).append("\",")
        builder.append("\"steps\":[")
        steps.forEachIndexed { index, step ->
            if (index > 0) builder.append(',')
            builder.append('{')
            builder.append("\"atMs\":").append(step.atMs).append(',')
            builder.append("\"state\":\"").append(escapeJson(step.state)).append("\",")
            builder.append("\"note\":\"").append(escapeJson(step.note)).append("\"")
            builder.append('}')
        }
        builder.append(']')
        builder.append('}')
        return builder.toString()
    }

    private fun buildResultJson(
        role: Role,
        runId: String,
        timeoutMs: Long,
        durationMs: Long,
        flowResult: FlowResult,
        failure: String?,
        steps: List<StepLogEntry>,
    ): String {
        val builder = StringBuilder(640)
        builder.append('{')
        builder.append("\"generatedAt\":\"").append(escapeJson(Instant.now().toString())).append("\",")
        builder.append("\"passed\":").append(flowResult.passed).append(',')
        builder.append("\"role\":\"").append(role.name.lowercase()).append("\",")
        builder.append("\"runId\":\"").append(escapeJson(runId)).append("\",")
        builder.append("\"timeoutMs\":").append(timeoutMs).append(',')
        builder.append("\"durationMs\":").append(durationMs).append(',')
        builder.append("\"turnSyncObserved\":").append(flowResult.turnSyncObserved).append(',')
        builder.append("\"bidirectionalChatObserved\":").append(flowResult.bidirectionalChatObserved).append(',')
        builder.append("\"reconnectObserved\":").append(flowResult.reconnectObserved).append(',')
        builder.append("\"warDeclaredObserved\":").append(flowResult.warDeclaredObserved).append(',')
        builder.append("\"combatExchangesObserved\":").append(flowResult.combatExchangesObserved).append(',')
        builder.append("\"cityCaptureObserved\":").append(flowResult.cityCaptureObserved).append(',')
        builder.append("\"peaceObserved\":").append(flowResult.peaceObserved).append(',')
        builder.append("\"diplomacyStateTransitionsObserved\":").append(flowResult.diplomacyStateTransitionsObserved).append(',')
        builder.append("\"multiTurnProgressObserved\":").append(flowResult.multiTurnProgressObserved).append(',')
        builder.append("\"forcesObserved\":\"").append(escapeJson(flowResult.forcesObserved)).append("\",")
        builder.append("\"notes\":\"").append(escapeJson(flowResult.notes)).append("\",")
        builder.append("\"stepCount\":").append(steps.size).append(',')
        builder.append("\"failure\":\"").append(escapeJson(failure ?: "")).append("\"")
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
