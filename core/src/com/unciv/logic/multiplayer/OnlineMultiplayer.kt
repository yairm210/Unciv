package com.unciv.logic.multiplayer

import com.badlogic.gdx.files.FileHandle
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.MultiplayerAuthException
import com.unciv.logic.multiplayer.storage.MultiplayerFileNotFoundException
import com.unciv.logic.multiplayer.storage.OnlineMultiplayerServer
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.extensions.isLargerThan
import com.unciv.utils.Concurrency
import com.unciv.utils.Dispatcher
import com.unciv.utils.debug
import com.unciv.utils.launchOnThreadPool
import com.unciv.utils.withGLContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference


/**
 * How often files can be checked for new multiplayer games (could be that the user modified their file system directly). More checks within this time period
 * will do nothing.
 */
private val FILE_UPDATE_THROTTLE_PERIOD = Duration.ofSeconds(60)

/**
 * Provides multiplayer functionality to the rest of the game.
 *
 * See the file of [com.unciv.logic.multiplayer.MultiplayerGameAdded] for all available [EventBus] events.
 */
class OnlineMultiplayer {
    private val files = UncivGame.Current.files
    val multiplayerServer = OnlineMultiplayerServer()

    private val savedGames: MutableMap<FileHandle, OnlineMultiplayerGame> = Collections.synchronizedMap(mutableMapOf())

    private val lastFileUpdate: AtomicReference<Instant?> = AtomicReference()
    private val lastAllGamesRefresh: AtomicReference<Instant?> = AtomicReference()
    private val lastCurGameRefresh: AtomicReference<Instant?> = AtomicReference()

    val games: Set<OnlineMultiplayerGame> get() = savedGames.values.toSet()
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

    private fun getCurrentGame(): OnlineMultiplayerGame? {
        val gameInfo = UncivGame.Current.gameInfo
        return if (gameInfo != null && gameInfo.gameParameters.isOnlineMultiplayer) {
            getGameByGameId(gameInfo.gameId)
        } else null
    }

    /**
     * Requests an update of all multiplayer game state. Does automatic throttling to try to prevent hitting rate limits.
     *
     * Use [forceUpdate] = true to circumvent this throttling.
     *
     * Fires: [MultiplayerGameUpdateStarted], [MultiplayerGameUpdated], [MultiplayerGameUpdateUnchanged], [MultiplayerGameUpdateFailed]
     */
    fun requestUpdate(forceUpdate: Boolean = false, doNotUpdate: List<OnlineMultiplayerGame> = listOf()) {
        Concurrency.run("Update all multiplayer games") {
            val fileThrottleInterval = if (forceUpdate) Duration.ZERO else FILE_UPDATE_THROTTLE_PERIOD
            // An exception only happens here if the files can't be listed, should basically never happen
            throttle(lastFileUpdate, fileThrottleInterval, {}, action = ::updateSavesFromFiles)

            for (game in savedGames.values) {
                if (game in doNotUpdate) continue
                launchOnThreadPool {
                    game.requestUpdate(forceUpdate)
                }
            }
        }
    }

    private suspend fun updateSavesFromFiles() {
        val saves = files.getMultiplayerSaves()

        val removedSaves = savedGames.keys - saves.toSet()
        for (saveFile in removedSaves) {
            deleteGame(saveFile)
        }

        val newSaves = saves - savedGames.keys
        for (saveFile in newSaves) {
            addGame(saveFile)
        }
    }


    /**
     * Fires [MultiplayerGameAdded]
     *
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     */
    suspend fun createGame(newGame: GameInfo) {
        multiplayerServer.tryUploadGame(newGame, withPreview = true)
        addGame(newGame)
    }

    /**
     * Fires [MultiplayerGameAdded]
     *
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
        addGame(gamePreview, saveFileName)
    }

    private suspend fun addGame(newGame: GameInfo) {
        val newGamePreview = newGame.asPreview()
        addGame(newGamePreview, newGamePreview.gameId)
    }

    private suspend fun addGame(preview: GameInfoPreview, saveFileName: String) {
        val fileHandle = files.saveGame(preview, saveFileName)
        return addGame(fileHandle, preview)
    }

    private suspend fun addGame(fileHandle: FileHandle, preview: GameInfoPreview? = null) {
        debug("Adding game %s", fileHandle.name())
        val game = OnlineMultiplayerGame(fileHandle, preview, if (preview != null) Instant.now() else null)
        savedGames[fileHandle] = game
        withGLContext {
            EventBus.send(MultiplayerGameAdded(game.name))
        }
    }

    fun getGameByName(name: String): OnlineMultiplayerGame? {
        return savedGames.values.firstOrNull { it.name == name }
    }

    fun getGameByGameId(gameId: String): OnlineMultiplayerGame? {
        return savedGames.values.firstOrNull { it.preview?.gameId == gameId }
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
    suspend fun resign(game: OnlineMultiplayerGame): Boolean {
        val preview = game.preview ?: throw game.error!!
        // download to work with the latest game state
        val gameInfo = multiplayerServer.tryDownloadGame(preview.gameId)
        val playerCiv = gameInfo.getCurrentPlayerCivilization()

        if (!gameInfo.isUsersTurn()) {
            return false
        }

        //Set own civ info to AI
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
        files.saveGame(newPreview, game.fileHandle)
        multiplayerServer.tryUploadGame(gameInfo, withPreview = true)
        game.doManualUpdate(newPreview)
        return true
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun loadGame(game: OnlineMultiplayerGame) {
        val preview = game.preview ?: throw game.error!!
        loadGame(preview.gameId)
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun loadGame(gameId: String) = coroutineScope {
        val gameInfo = downloadGame(gameId)
        val preview = gameInfo.asPreview()
        val onlineGame = getGameByGameId(gameId)
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
     */
    suspend fun downloadGame(gameId: String): GameInfo {
        val latestGame = multiplayerServer.tryDownloadGame(gameId)
        latestGame.isUpToDate = true
        return latestGame
    }

    /**
     * Deletes the game from disk, does not delete it remotely.
     *
     * Fires [MultiplayerGameDeleted]
     */
    fun deleteGame(multiplayerGame: OnlineMultiplayerGame) {
        deleteGame(multiplayerGame.fileHandle)
    }

    private fun deleteGame(fileHandle: FileHandle) {
        files.deleteSave(fileHandle)

        val game = savedGames[fileHandle] ?: return

        debug("Deleting game %s with id %s", fileHandle.name(), game.preview?.gameId)
        savedGames.remove(game.fileHandle)
        Concurrency.runOnGLThread { EventBus.send(MultiplayerGameDeleted(game.name)) }
    }

    /**
     * Fires [MultiplayerGameNameChanged]
     */
    fun changeGameName(game: OnlineMultiplayerGame, newName: String, onException: (Exception?)->Unit) {
        debug("Changing name of game %s to", game.name, newName)
        val oldPreview = game.preview ?: throw game.error!!
        val oldLastUpdate = game.getLastUpdate()
        val oldName = game.name

        val newFileHandle = files.saveGame(oldPreview, newName, onException)
        val newGame = OnlineMultiplayerGame(newFileHandle, oldPreview, oldLastUpdate)
        savedGames[newFileHandle] = newGame

        savedGames.remove(game.fileHandle)
        files.deleteSave(game.fileHandle)
        EventBus.send(MultiplayerGameNameChanged(oldName, newName))
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     * @throws MultiplayerAuthException if the authentication failed
     */
    suspend fun updateGame(gameInfo: GameInfo) {
        debug("Updating remote game %s", gameInfo.gameId)
        multiplayerServer.tryUploadGame(gameInfo, withPreview = true)
        val game = getGameByGameId(gameInfo.gameId)
        debug("Existing OnlineMultiplayerGame: %s", game)
        if (game == null) {
            addGame(gameInfo)
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
    onFailed: (Exception) -> T = { throw it },
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
    onFailed: (Exception) -> T = { throw it },
    action: suspend () -> T
): T {
    val lastExecution = lastSuccessfulExecution.get()
    val now = Instant.now()
    return if (lastSuccessfulExecution.compareAndSet(lastExecution, now)) {
        try {
            action()
        } catch (e: Exception) {
            lastSuccessfulExecution.compareAndSet(now, lastExecution)
            onFailed(e)
        }
    } else {
        onNoExecution()
    }
}


fun GameInfoPreview.isUsersTurn() = getCivilization(currentPlayer).playerId == UncivGame.Current.settings.multiplayer.userId
fun GameInfo.isUsersTurn() = currentPlayer.isNotEmpty() && getCivilization(currentPlayer).playerId == UncivGame.Current.settings.multiplayer.userId
