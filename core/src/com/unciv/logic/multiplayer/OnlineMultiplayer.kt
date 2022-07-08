package com.unciv.logic.multiplayer

import com.badlogic.gdx.files.FileHandle
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.MultiplayerFileNotFoundException
import com.unciv.logic.multiplayer.storage.OnlineMultiplayerFiles
import com.unciv.ui.utils.extensions.isLargerThan
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.Dispatcher
import com.unciv.utils.concurrency.launchOnThreadPool
import com.unciv.utils.concurrency.withGLContext
import com.unciv.utils.debug
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import java.io.FileNotFoundException
import java.time.Duration
import java.time.Instant
import java.util.*
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
    private val multiplayerFiles = OnlineMultiplayerFiles()

    private val savedGames: MutableMap<FileHandle, OnlineMultiplayerGame> = Collections.synchronizedMap(mutableMapOf())

    private val lastFileUpdate: AtomicReference<Instant?> = AtomicReference()
    private val lastAllGamesRefresh: AtomicReference<Instant?> = AtomicReference()
    private val lastCurGameRefresh: AtomicReference<Instant?> = AtomicReference()

    val games: Set<OnlineMultiplayerGame> get() = savedGames.values.toSet()

    init {
        flow<Unit> {
            while (true) {
                delay(500)

                val currentGame = getCurrentGame()
                val multiplayerSettings = UncivGame.Current.settings.multiplayer
                val status = currentGame?.status
                if (currentGame != null && (usesCustomServer() || status == null || !status.isUsersTurn())) {
                    throttle(lastCurGameRefresh, multiplayerSettings.currentGameRefreshDelay, {}) { currentGame.requestUpdate() }
                }

                val doNotUpdate = if (currentGame == null) listOf() else listOf(currentGame)
                throttle(lastAllGamesRefresh, multiplayerSettings.allGameRefreshDelay, {}) { requestUpdate(doNotUpdate = doNotUpdate) }
            }
        }.launchIn(CoroutineScope(Dispatcher.DAEMON))
    }

    private fun getCurrentGame(): OnlineMultiplayerGame? {
        val gameInfo = UncivGame.Current.gameInfo
        if (gameInfo != null) {
            return getGameByGameId(gameInfo.gameId)
        } else {
            return null
        }
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
        val saves = files.getMultiplayerGameStatuses()

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
        multiplayerFiles.tryUploadGame(newGame, withGameStatus = true)
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
        var status: MultiplayerGameStatus
        try {
            status = multiplayerFiles.tryDownloadGameStatus(gameId)
        } catch (ex: MultiplayerFileNotFoundException) {
            // Maybe something went wrong with uploading the multiplayer game info, let's try the full game info
            status = MultiplayerGameStatus(multiplayerFiles.tryDownloadGame(gameId))
        }
        addGame(status, saveFileName)
    }

    private suspend fun addGame(gameInfo: GameInfo) {
        val status = MultiplayerGameStatus(gameInfo)
        addGame(status, status.gameId)
    }

    private suspend fun addGame(status: MultiplayerGameStatus, saveFileName: String) {
        val fileHandle = files.saveMultiplayerGameStatus(status, saveFileName)
        return addGame(fileHandle, status)
    }

    private suspend fun addGame(fileHandle: FileHandle, status: MultiplayerGameStatus = files.loadMultiplayerGameStatusFromFile(fileHandle)) {
        debug("Adding game %s", status.gameId)
        val game = OnlineMultiplayerGame(fileHandle, status, Instant.now())
        savedGames[fileHandle] = game
        withGLContext {
            EventBus.send(MultiplayerGameAdded(game.name))
        }
    }

    fun getGameByName(name: String): OnlineMultiplayerGame? {
        return savedGames.values.firstOrNull { it.name == name }
    }

    fun getGameByGameId(gameId: String): OnlineMultiplayerGame? {
        return savedGames.values.firstOrNull { it.status?.gameId == gameId }
    }

    /**
     * Resigns from the given multiplayer [game]. Can only resign if it's currently the user's turn,
     * to ensure that no one else can upload the game in the meantime.
     *
     * Fires [MultiplayerGameUpdated]
     *
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     * @return false if it's not the user's turn and thus resigning did not happen
     */
    suspend fun resign(game: OnlineMultiplayerGame): Boolean {
        val oldStatus = game.status ?: throw game.error!!
        // download to work with the latest game state
        val gameInfo = multiplayerFiles.tryDownloadGame(oldStatus.gameId)
        val playerCiv = gameInfo.currentPlayerCiv

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
            civ.addNotification("[${playerCiv.civName}] resigned and is now controlled by AI", playerCiv.civName)
        }

        val newStatus = MultiplayerGameStatus(gameInfo)
        files.saveMultiplayerGameStatus(newStatus, game.fileHandle)
        multiplayerFiles.tryUploadGame(gameInfo, withGameStatus = true)
        game.doManualUpdate(newStatus)
        return true
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun loadGame(game: OnlineMultiplayerGame) {
        val status = game.status ?: throw game.error!!
        loadGame(status.gameId)
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun loadGame(gameId: String) = coroutineScope {
        val newGameInfo = downloadGame(gameId)
        val newStatus = MultiplayerGameStatus(newGameInfo)
        val onlineGame = getGameByGameId(gameId)
        val oldStatus = onlineGame?.status
        if (onlineGame == null) {
            createGame(newGameInfo)
        } else if (oldStatus != null && !oldStatus.hasLatestGameState(newStatus)){
            onlineGame.doManualUpdate(newStatus)
        }
        UncivGame.Current.loadGame(newGameInfo)
    }

    /**
     * Checks if the given game is current and loads it, otherwise loads the game from the server
     */
    suspend fun loadGame(gameInfo: GameInfo) = coroutineScope {
        val gameId = gameInfo.gameId
        val status = multiplayerFiles.tryDownloadGameStatus(gameId)
        if (gameInfo.hasLatestGameState(status)) {
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
        val latestGame = multiplayerFiles.tryDownloadGame(gameId)
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
        files.deleteFile(fileHandle)

        val game = savedGames[fileHandle]
        if (game == null) return

        debug("Deleting game %s with id %s", fileHandle.name(), game.status?.gameId)
        savedGames.remove(game.fileHandle)
        Concurrency.runOnGLThread { EventBus.send(MultiplayerGameDeleted(game.name)) }
    }

    /**
     * Fires [MultiplayerGameNameChanged]
     */
    fun changeGameName(game: OnlineMultiplayerGame, newName: String) {
        debug("Changing name of game %s to", game.name, newName)
        val oldStatus = game.status ?: throw game.error!!
        val oldLastUpdate = game.lastUpdate
        val oldName = game.name

        savedGames.remove(game.fileHandle)
        files.deleteFile(game.fileHandle)
        val newFileHandle = files.saveMultiplayerGameStatus(oldStatus, newName)

        val newGame = OnlineMultiplayerGame(newFileHandle, oldStatus, oldLastUpdate)
        savedGames[newFileHandle] = newGame
        EventBus.send(MultiplayerGameNameChanged(oldName, newName))
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun updateGame(gameInfo: GameInfo) {
        debug("Updating remote game %s", gameInfo.gameId)
        multiplayerFiles.tryUploadGame(gameInfo, withGameStatus = true)
        val game = getGameByGameId(gameInfo.gameId)
        debug("Existing OnlineMultiplayerGame: %s", game)
        if (game == null) {
            addGame(gameInfo)
        } else {
            game.doManualUpdate(gameInfo)
        }
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


fun MultiplayerGameStatus.isUsersTurn() = getCivilization(currentPlayer).playerId == UncivGame.Current.settings.multiplayer.userId
fun GameInfo.isUsersTurn() = getCivilization(currentPlayer).playerId == UncivGame.Current.settings.multiplayer.userId

