package com.unciv.logic.multiplayer

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.multiplayer.storage.MultiplayerFileNotFoundException
import com.unciv.logic.multiplayer.storage.MultiplayerServer
import com.unciv.ui.components.extensions.isLargerThan
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

private val FILE_UPDATE_THROTTLE_PERIOD = Duration.ofSeconds(60)

class Multiplayer {
    val multiplayerServer = MultiplayerServer()
    val multiplayerFiles = MultiplayerFiles()

    private val lastFileUpdate: AtomicReference<Instant?> = AtomicReference()
    private val lastAllGamesRefresh: AtomicReference<Instant?> = AtomicReference()
    private val lastCurGameRefresh: AtomicReference<Instant?> = AtomicReference()

    val games: Set<MultiplayerGamePreview> get() = multiplayerFiles.savedGames.values.toSet()
    val multiplayerGameUpdater: Job = NonCancellable

    private fun getCurrentGame(): MultiplayerGamePreview? {
        val gameInfo = UncivGame.Current.gameInfo
        return if (gameInfo != null && gameInfo.gameParameters.isOnlineMultiplayer) {
            multiplayerFiles.getGameByGameId(gameInfo.gameId)
        } else null
    }

    suspend fun requestUpdate(forceUpdate: Boolean = false, doNotUpdate: List<MultiplayerGamePreview> = listOf()) {
        val fileThrottleInterval = if (forceUpdate) Duration.ZERO else FILE_UPDATE_THROTTLE_PERIOD
        throttle(lastFileUpdate, fileThrottleInterval, {}, {}, action = { multiplayerFiles.updateSavesFromFiles() })

        val currentGame = getCurrentGame()
        val preview = currentGame?.preview
        if (currentGame != null && (usesCustomServer() || preview == null || !preview.isUsersTurn())) {
            throttle(lastCurGameRefresh, UncivGame.Current.settings.multiplayer.currentGameRefreshDelay, {}, {}) { currentGame.requestUpdate() }
        }

        throttle(lastAllGamesRefresh, UncivGame.Current.settings.multiplayer.allGameRefreshDelay, {}, {}) {
            for (game in multiplayerFiles.savedGames.values.toList()) {
                if (game in doNotUpdate) continue
                if (Duration.between(Instant.ofEpochMilli(game.fileHandle.lastModified()), Instant.now())
                        .isLargerThan(Duration.ofDays(14))) continue
                game.requestUpdate(forceUpdate)
            }
        }
    }

    suspend fun createGame(newGame: GameInfo) {
        multiplayerServer.uploadGame(newGame, withPreview = true)
        multiplayerFiles.addGame(newGame)
    }

    suspend fun addGame(gameId: String, gameName: String? = null) {
        val saveFileName = if (gameName.isNullOrBlank()) gameId else gameName
        val gamePreview: GameInfoPreview = try {
            multiplayerServer.tryDownloadGamePreview(gameId)
        } catch (_: MultiplayerFileNotFoundException) {
            multiplayerServer.tryDownloadGame(gameId).asPreview()
        }
        multiplayerFiles.addGame(gamePreview, saveFileName)
    }

    suspend fun resignPlayer(game: MultiplayerGamePreview, playerCivName: String): String {
        val preview = game.preview ?: throw game.error!!
        val gameInfo = multiplayerServer.tryDownloadGame(preview.gameId)
        if (gameInfo.currentPlayer != preview.currentPlayer) {
            game.updatePreview(gameInfo.asPreview())
            return "Game was out of sync with server - updated"
        }

        val playerCiv = gameInfo.getCivilization(playerCivName)
        playerCiv.playerType = com.unciv.logic.civilization.PlayerType.AI
        playerCiv.playerId = ""
        if (gameInfo.currentPlayer == playerCivName) gameInfo.nextTurn()

        for (civ in gameInfo.civilizations) {
            civ.addNotification(
                "[${playerCiv.civName}] resigned and is now controlled by AI",
                com.unciv.logic.civilization.NotificationCategory.General,
                playerCiv.civName
            )
        }

        multiplayerServer.uploadGame(gameInfo, withPreview = true)
        game.updatePreview(gameInfo.asPreview())
        return ""
    }

    suspend fun skipCurrentPlayerTurn(game: MultiplayerGamePreview, player: String): String? {
        val preview = game.preview ?: return game.error?.message
        val gameInfo = try {
            multiplayerServer.tryDownloadGame(preview.gameId)
        } catch (ex: Exception) {
            return ex.message
        }

        if (gameInfo.currentPlayer != preview.currentPlayer) {
            game.updatePreview(gameInfo.asPreview())
            return "Game was out of sync with server - updated"
        }

        if (gameInfo.currentPlayer != player) {
            return "Could not pass turn - current player is ${gameInfo.currentPlayer}, not $player"
        }

        val playerCiv = gameInfo.getCurrentPlayerCivilization()
        com.unciv.logic.automation.civilization.NextTurnAutomation.automateCivMoves(playerCiv, false)
        gameInfo.nextTurn()

        multiplayerServer.uploadGame(gameInfo, withPreview = true)
        game.updatePreview(gameInfo.asPreview())
        return null
    }

    suspend fun downloadGame(game: MultiplayerGamePreview) {
        val preview = game.preview ?: throw game.error!!
        downloadGame(preview.gameId)
    }

    suspend fun downloadGame(gameId: String) {
        val gameInfo = multiplayerServer.downloadGame(gameId)
        val preview = gameInfo.asPreview()
        val onlineGame = multiplayerFiles.getGameByGameId(gameId)
        val onlinePreview = onlineGame?.preview
        if (onlineGame == null) {
            createGame(gameInfo)
        } else if (onlinePreview != null && hasNewerGameState(preview, onlinePreview)) {
            onlineGame.updatePreview(preview)
        }
        UncivGame.Current.loadGame(gameInfo)
    }

    suspend fun downloadGame(gameInfo: GameInfo) {
        val gameId = gameInfo.gameId
        val preview = multiplayerServer.tryDownloadGamePreview(gameId)
        if (hasLatestGameState(gameInfo, preview)) {
            gameInfo.isUpToDate = true
            UncivGame.Current.loadGame(gameInfo)
        } else {
            downloadGame(gameId)
        }
    }

    suspend fun updateGame(gameInfo: GameInfo) {
        multiplayerServer.uploadGame(gameInfo, withPreview = true)
        val game = multiplayerFiles.getGameByGameId(gameInfo.gameId)
        if (game == null) {
            multiplayerFiles.addGame(gameInfo)
        } else {
            game.updatePreview(gameInfo.asPreview())
        }
    }

    fun hasLatestGameState(gameInfo: GameInfo, preview: GameInfoPreview): Boolean {
        return gameInfo.currentPlayer == preview.currentPlayer && gameInfo.turns == preview.turns
    }

    private fun hasNewerGameState(preview1: GameInfoPreview, preview2: GameInfoPreview): Boolean {
        return preview1.turns > preview2.turns
    }

    companion object {
        fun usesCustomServer() = UncivGame.Current.settings.multiplayer.getServer() != Constants.dropboxMultiplayerServer
        fun usesDropbox() = !usesCustomServer()
    }
}

suspend fun <T> throttle(
    lastSuccessfulExecution: AtomicReference<Instant?>,
    throttleInterval: Duration,
    onNoExecution: () -> T,
    onFailed: (Throwable) -> T,
    action: suspend () -> T,
): T {
    val lastExecution = lastSuccessfulExecution.get()
    val now = Instant.now()
    val shouldRunAction = lastExecution == null || Duration.between(lastExecution, now) > throttleInterval
    return if (shouldRunAction) {
        attemptAction(lastSuccessfulExecution, onNoExecution, onFailed, action)
    } else {
        onNoExecution()
    }
}

suspend fun <T> attemptAction(
    lastSuccessfulExecution: AtomicReference<Instant?>,
    onNoExecution: () -> T,
    onFailed: (Throwable) -> T = { throw it },
    action: suspend () -> T,
): T {
    val lastExecution = lastSuccessfulExecution.get()
    val now = Instant.now()
    return if (lastSuccessfulExecution.compareAndSet(lastExecution, now)) {
        try {
            action()
        } catch (e: Throwable) {
            lastSuccessfulExecution.compareAndSet(now, lastExecution)
            onFailed(e)
        }
    } else {
        onNoExecution()
    }
}

fun GameInfoPreview.isUsersTurn() =
    getCivilization(currentPlayer).playerId == UncivGame.Current.settings.multiplayer.getUserId()

fun GameInfo.isUsersTurn() =
    currentPlayer.isNotEmpty() && getCivilization(currentPlayer).playerId == UncivGame.Current.settings.multiplayer.getUserId()
