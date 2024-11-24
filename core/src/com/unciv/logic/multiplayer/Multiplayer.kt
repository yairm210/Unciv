package com.unciv.logic.multiplayer

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.MultiplayerAuthException
import com.unciv.logic.multiplayer.storage.MultiplayerFileNotFoundException
import com.unciv.logic.multiplayer.storage.MultiplayerServer
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.extensions.isLargerThan
import com.unciv.utils.Dispatcher
import com.unciv.utils.debug
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference


/**
 * How often files can be checked for new multiplayer games (could be that the user modified their file system directly). More checks within this time period
 * will do nothing.
 */
private val FILE_UPDATE_THROTTLE_PERIOD = Duration.ofSeconds(60)

/**
 * Provides *online* multiplayer functionality to the rest of the game.
 * Multiplayer data is a mix of local files ([multiplayerFiles]) and server data ([multiplayerServer]).
 * This class handles functions that require a mix of both.
 *
 * See the file of [com.unciv.logic.multiplayer.HasMultiplayerGameName] for all available [EventBus] events.
 */
class Multiplayer {
    /** Handles SERVER DATA only */
    val multiplayerServer = MultiplayerServer()
    /** Handles LOCAL FILES only */
    val multiplayerFiles = MultiplayerFiles()


    private val lastFileUpdate: AtomicReference<Instant?> = AtomicReference()
    private val lastAllGamesRefresh: AtomicReference<Instant?> = AtomicReference()
    private val lastCurGameRefresh: AtomicReference<Instant?> = AtomicReference()

    val games: Set<MultiplayerGame> get() = multiplayerFiles.savedGames.values.toSet()
    val multiplayerGameUpdater: Job

    init {
        /** We have 2 'async processes' that update the multiplayer games:
         * A. This one, which as part of *this process* runs refreshes for all OS's
         * B. MultiplayerTurnCheckWorker, which *as an Android worker* runs refreshes *even when the game is closed*.
         *    Only for Android, obviously
         */
        multiplayerGameUpdater = flow<Unit> {
            while (true) {
                delay(500)
                if (!currentCoroutineContext().isActive) return@flow
                val multiplayerSettings: GameSettings.GameSettingsMultiplayer
                try { // Fails in unknown cases - cannot debug :/ This is just so it doesn't appear in GP analytics
                    multiplayerSettings = UncivGame.Current.settings.multiplayer
                } catch (ex:Exception){ continue }

                val currentGame = getCurrentGame()
                val preview = currentGame?.preview
                if (currentGame != null && (usesCustomServer() || preview == null || !preview.isUsersTurn())) {
                    throttle(lastCurGameRefresh, multiplayerSettings.currentGameRefreshDelay, {}) { currentGame.requestUpdate() }
                }

                val doNotUpdate = if (currentGame == null) listOf() else listOf(currentGame)
                throttle(lastAllGamesRefresh, multiplayerSettings.allGameRefreshDelay, {}) { requestUpdate(doNotUpdate = doNotUpdate) }
            }
        }.launchIn(CoroutineScope(Dispatcher.DAEMON))
    }

    private fun getCurrentGame(): MultiplayerGame? {
        val gameInfo = UncivGame.Current.gameInfo
        return if (gameInfo != null && gameInfo.gameParameters.isOnlineMultiplayer) {
            multiplayerFiles.getGameByGameId(gameInfo.gameId)
        } else null
    }

    /**
     * Requests an update of all multiplayer game state. Does automatic throttling to try to prevent hitting rate limits.
     *
     * Use [forceUpdate] = true to circumvent this throttling.
     *
     * Fires: [MultiplayerGameUpdateStarted], [MultiplayerGameUpdated], [MultiplayerGameUpdateUnchanged], [MultiplayerGameUpdateFailed]
     */
    suspend fun requestUpdate(forceUpdate: Boolean = false, doNotUpdate: List<MultiplayerGame> = listOf()) {
        val fileThrottleInterval = if (forceUpdate) Duration.ZERO else FILE_UPDATE_THROTTLE_PERIOD
        // An exception only happens here if the files can't be listed, should basically never happen
        throttle(lastFileUpdate, fileThrottleInterval, {}, action = {multiplayerFiles.updateSavesFromFiles()})

        for (game in multiplayerFiles.savedGames.values.toList()) { // since updates are long, .toList for immutability
            if (game in doNotUpdate) continue
            // Any games that haven't been updated in 2 weeks (!) are inactive, don't waste your time
            if (Duration.between(Instant.ofEpochMilli(game.fileHandle.lastModified()), Instant.now())
                .isLargerThan(Duration.ofDays(14))) continue
            game.requestUpdate(forceUpdate) // DO NOT spawn in thread, since that leads to OOMs when many games try at once
        }
    }


    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     */
    suspend fun createGame(newGame: GameInfo) {
        multiplayerServer.tryUploadGame(newGame, withPreview = true)
        multiplayerFiles.addGame(newGame)
    }

    /**
     * @param gameName if this is null or blank, will use the gameId as the game name
     * @return the final name the game was added under
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun addGame(gameId: String, gameName: String? = null) {
        val saveFileName = if (gameName.isNullOrBlank()) gameId else gameName
        val gamePreview: GameInfoPreview = try {
            multiplayerServer.tryDownloadGamePreview(gameId)
        } catch (_: MultiplayerFileNotFoundException) {
            // Game is so old that a preview could not be found on dropbox lets try the real gameInfo instead
            multiplayerServer.tryDownloadGame(gameId).asPreview()
        }
        multiplayerFiles.addGame(gamePreview, saveFileName)
    }


    /**
     * Resigns from the given multiplayer [game]. Can only resign if it's currently the user's turn,
     * to ensure that no one else can upload the game in the meantime.
     *
     * Fires [MultiplayerGameUpdated]
     *
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     * @throws MultiplayerAuthException if the authentication failed
     * @return false if it's not the user's turn and thus resigning did not happen
     */
    suspend fun resignCurrentPlayer(game: MultiplayerGame): Boolean {
        val preview = game.preview ?: throw game.error!!
        // download to work with the latest game state
        val gameInfo = multiplayerServer.tryDownloadGame(preview.gameId)


        if (gameInfo.currentPlayer != preview.currentPlayer) {
            game.doManualUpdate(gameInfo.asPreview())
            return false
        }

        val playerCiv = gameInfo.getCurrentPlayerCivilization()

        //Set civ info to AI
        playerCiv.playerType = PlayerType.AI
        playerCiv.playerId = ""

        //call next turn so turn gets simulated by AI
        gameInfo.nextTurn()

        //Add notification so everyone knows what happened
        //call for every civ cause AI players are skipped anyway
        for (civ in gameInfo.civilizations) {
            civ.addNotification("[${playerCiv.civName}] resigned and is now controlled by AI",
                NotificationCategory.General, playerCiv.civName)
        }

        val newPreview = gameInfo.asPreview()
        multiplayerFiles.files.saveGame(newPreview, game.fileHandle)
        multiplayerServer.tryUploadGame(gameInfo, withPreview = true)
        game.doManualUpdate(newPreview)
        return true
    }

    /** Returns false if game was not up to date
     * Returned value indicates an error string - will be null if successful  */
    suspend fun skipCurrentPlayerTurn(game: MultiplayerGame): String? {
        val preview = game.preview ?: return game.error!!.message
        // download to work with the latest game state
        val gameInfo: GameInfo
        try {
            gameInfo = multiplayerServer.tryDownloadGame(preview.gameId)
        }
        catch (ex: Exception){
            return ex.message
        }
        
        if (gameInfo.currentPlayer != preview.currentPlayer) {
            game.doManualUpdate(gameInfo.asPreview())
            return "Could not pass turn - current player has been updated!"
        }

        val playerCiv = gameInfo.getCurrentPlayerCivilization()
        NextTurnAutomation.automateCivMoves(playerCiv, false)
        gameInfo.nextTurn()

        val newPreview = gameInfo.asPreview()
        multiplayerFiles.files.saveGame(newPreview, game.fileHandle)
        multiplayerServer.tryUploadGame(gameInfo, withPreview = true)
        game.doManualUpdate(newPreview)
        return null
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun loadGame(game: MultiplayerGame) {
        val preview = game.preview ?: throw game.error!!
        loadGame(preview.gameId)
    }

    /** Downloads game, and updates it locally
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun loadGame(gameId: String) = coroutineScope {
        val gameInfo = multiplayerServer.downloadGame(gameId)
        val preview = gameInfo.asPreview()
        val onlineGame = multiplayerFiles.getGameByGameId(gameId)
        val onlinePreview = onlineGame?.preview
        if (onlineGame == null) {
            createGame(gameInfo)
        } else if (onlinePreview != null && hasNewerGameState(preview, onlinePreview)) {
            onlineGame.doManualUpdate(preview)
        }
        UncivGame.Current.loadGame(gameInfo)
    }

    /**
     * Checks if the given game is current and loads it, otherwise loads the game from the server
     */
    suspend fun loadGame(gameInfo: GameInfo) = coroutineScope {
        val gameId = gameInfo.gameId
        val preview = multiplayerServer.tryDownloadGamePreview(gameId)
        if (hasLatestGameState(gameInfo, preview)) {
            gameInfo.isUpToDate = true
            UncivGame.Current.loadGame(gameInfo)
        } else {
            loadGame(gameId)
        }
    }




    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     * @throws MultiplayerAuthException if the authentication failed
     */
    suspend fun updateGame(gameInfo: GameInfo) {
        debug("Updating remote game %s", gameInfo.gameId)
        multiplayerServer.tryUploadGame(gameInfo, withPreview = true)
        val game = multiplayerFiles.getGameByGameId(gameInfo.gameId)
        debug("Existing OnlineMultiplayerGame: %s", game)
        if (game == null) {
            multiplayerFiles.addGame(gameInfo)
        } else {
            game.doManualUpdate(gameInfo.asPreview())
        }
    }

    /**
     * Checks if [gameInfo] and [preview] are up-to-date with each other.
     */
    fun hasLatestGameState(gameInfo: GameInfo, preview: GameInfoPreview): Boolean {
        // TODO look into how to maybe extract interfaces to not make this take two different methods
        return gameInfo.currentPlayer == preview.currentPlayer
                && gameInfo.turns == preview.turns
    }


    /**
     * Checks if [preview1] has a more recent game state than [preview2]
     */
    private fun hasNewerGameState(preview1: GameInfoPreview, preview2: GameInfoPreview): Boolean {
        return preview1.turns > preview2.turns
    }

    companion object {
        fun usesCustomServer() = UncivGame.Current.settings.multiplayer.server != Constants.dropboxMultiplayerServer
        fun usesDropbox() = !usesCustomServer()
    }
}

/**
 * Calls the given [action] when [lastSuccessfulExecution] lies further in the past than [throttleInterval].
 *
 * Also updates [lastSuccessfulExecution] to [Instant.now], but only when [action] did not result in an exception.
 *
 * Any exception thrown by [action] is propagated.
 *
 * @return true if the update happened
 */
suspend fun <T> throttle(
    lastSuccessfulExecution: AtomicReference<Instant?>,
    throttleInterval: Duration,
    onNoExecution: () -> T,
    onFailed: (Throwable) -> T = { throw it },
    action: suspend () -> T
): T {
    val lastExecution = lastSuccessfulExecution.get()
    val now = Instant.now()
    val shouldRunAction = lastExecution == null || Duration.between(lastExecution, now).isLargerThan(throttleInterval)
    return if (shouldRunAction) {
        attemptAction(lastSuccessfulExecution, onNoExecution, onFailed, action)
    } else {
        onNoExecution()
    }
}

/**
 * Attempts to run the [action], changing [lastSuccessfulExecution], but only if no other thread changed [lastSuccessfulExecution] in the meantime
 * and [action] did not throw an exception.
 */
suspend fun <T> attemptAction(
    lastSuccessfulExecution: AtomicReference<Instant?>,
    onNoExecution: () -> T,
    onFailed: (Throwable) -> T = { throw it },
    action: suspend () -> T
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


fun GameInfoPreview.isUsersTurn() = getCivilization(currentPlayer).playerId == UncivGame.Current.settings.multiplayer.userId
fun GameInfo.isUsersTurn() = currentPlayer.isNotEmpty() && getCivilization(currentPlayer).playerId == UncivGame.Current.settings.multiplayer.userId
