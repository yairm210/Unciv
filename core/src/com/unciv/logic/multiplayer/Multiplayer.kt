package com.unciv.logic.multiplayer

import com.badlogic.gdx.files.FileHandle
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.HasGameId
import com.unciv.logic.HasGameTurnData
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.MultiplayerFileNotFoundException
import com.unciv.logic.multiplayer.storage.MultiplayerFiles
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
class Multiplayer {
    private val files = UncivGame.Current.files
    private val multiplayerFiles = MultiplayerFiles()

    private val savedGames: MutableMap<FileHandle, MultiplayerGame> = Collections.synchronizedMap(mutableMapOf())

    private val lastFileUpdate: AtomicReference<Instant?> = AtomicReference()
    private val lastAllGamesRefresh: AtomicReference<Instant?> = AtomicReference()
    private val lastCurGameRefresh: AtomicReference<Instant?> = AtomicReference()

    val games: Set<MultiplayerGame> get() = savedGames.values.toSet()

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

    private fun getCurrentGame(): MultiplayerGame? {
        val gameInfo = UncivGame.Current.gameInfo
        if (gameInfo != null) {
            return getGameById(gameInfo.gameId)
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
    fun requestUpdate(forceUpdate: Boolean = false, doNotUpdate: List<MultiplayerGame> = listOf()) {
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
        var status: GameStatus
        try {
            status = multiplayerFiles.tryDownloadGameStatus(gameId)
        } catch (ex: MultiplayerFileNotFoundException) {
            // Maybe something went wrong with uploading the multiplayer game info, let's try the full game info
            status = GameStatus(multiplayerFiles.tryDownloadGame(gameId))
        }
        addGame(status, saveFileName)
    }

    private suspend fun addGame(gameInfo: GameInfo) {
        val status = GameStatus(gameInfo)
        addGame(status, status.gameId)
    }

    private suspend fun addGame(status: GameStatus, saveFileName: String) {
        val fileHandle = files.saveMultiplayerGameStatus(status, saveFileName)
        return addGame(fileHandle, status)
    }

    private suspend fun addGame(fileHandle: FileHandle, status: GameStatus? = null) {
        debug("Adding game %s", fileHandle.name())
        val game = MultiplayerGame(fileHandle, status, if (status == null) Instant.now() else null)
        savedGames[fileHandle] = game
        withGLContext {
            EventBus.send(MultiplayerGameAdded(game.name))
        }
    }

    fun getGameByName(name: String): MultiplayerGame? {
        return savedGames.values.firstOrNull { it.name == name }
    }

    fun getGameById(gameId: String): MultiplayerGame? {
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
    suspend fun resign(game: MultiplayerGame): Boolean {
        val oldStatus = game.status ?: throw game.error!!
        // download to work with the latest game state
        val gameInfo = multiplayerFiles.tryDownloadGame(oldStatus.gameId)
        val playerCiv = gameInfo.currentCiv

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

        val newStatus = GameStatus(gameInfo)
        files.saveMultiplayerGameStatus(newStatus, game.fileHandle)
        multiplayerFiles.tryUploadGame(gameInfo, withGameStatus = true)
        game.doManualUpdate(newStatus)
        return true
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun loadGame(game: MultiplayerGame) {
        val status = game.status ?: throw game.error!!
        loadGame(status.gameId)
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun loadGame(gameId: String) = coroutineScope {
        val newGameInfo = downloadGame(gameId)
        val multiplayerGame = getGameById(gameId)
        val oldStatus = multiplayerGame?.status
        if (multiplayerGame == null) {
            createGame(newGameInfo)
        } else if (oldStatus != null && !oldStatus.hasLatestGameState(newGameInfo)) {
            multiplayerGame.doManualUpdate(newGameInfo)
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
    fun deleteGame(multiplayerGame: MultiplayerGame) {
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
    fun changeGameName(game: MultiplayerGame, newName: String) {
        debug("Changing name of game %s to", game.name, newName)
        val oldStatus = game.status ?: throw game.error!!
        val oldLastUpdate = game.lastUpdate
        val oldName = game.name

        savedGames.remove(game.fileHandle)
        files.deleteFile(game.fileHandle)
        val newFileHandle = files.saveMultiplayerGameStatus(oldStatus, newName)

        val newGame = MultiplayerGame(newFileHandle, oldStatus, oldLastUpdate)
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
        val game = getGameById(gameInfo.gameId)
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

    /**
     * Reduced variant of GameInfo used for multiplayer saves.
     * Contains additional data for multiplayer settings.
     */
    data class GameStatus(
        override val gameId: String,
        override val turns: Int,
        override val currentTurnStartTime: Long,
        override val currentCivName: String,
        override val currentPlayerId: String,
    ) : HasGameId, HasGameTurnData {

        /**
         * Converts a GameInfo object (can be uninitialized).
         * Sets all multiplayer settings to default.
         */
        constructor (gameInfo: GameInfo) : this(gameInfo.gameId, gameInfo.turns, gameInfo.currentTurnStartTime, gameInfo.currentCivName, gameInfo.currentPlayerId)
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

fun HasGameTurnData.isUsersTurn() = currentPlayerId == UncivGame.Current.settings.multiplayer.userId

