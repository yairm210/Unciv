package com.unciv.logic.multiplayer

import com.badlogic.gdx.files.FileHandle
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.OnlineMultiplayerGameSaver
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.crashhandling.CRASH_HANDLING_DAEMON_SCOPE
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.utils.isLargerThan
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
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
    private val gameSaver = UncivGame.Current.gameSaver
    private val onlineGameSaver = OnlineMultiplayerGameSaver()

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
                val preview = currentGame?.preview
                if (currentGame != null && (usesCustomServer() || preview == null || !preview.isUsersTurn())) {
                    throttle(lastCurGameRefresh, multiplayerSettings.currentGameRefreshDelay, {}) { currentGame.requestUpdate() }
                }

                val doNotUpdate = if (currentGame == null) listOf() else listOf(currentGame)
                throttle(lastAllGamesRefresh, multiplayerSettings.allGameRefreshDelay, {}) { requestUpdate(doNotUpdate = doNotUpdate) }
            }
        }.launchIn(CRASH_HANDLING_DAEMON_SCOPE)
    }

    private fun getCurrentGame(): OnlineMultiplayerGame? {
        if (UncivGame.isCurrentInitialized() && UncivGame.Current.isGameInfoInitialized()) {
            return getGameByGameId(UncivGame.Current.gameInfo.gameId)
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
        launchCrashHandling("Update all multiplayer games") {
            val fileThrottleInterval = if (forceUpdate) Duration.ZERO else FILE_UPDATE_THROTTLE_PERIOD
            // An exception only happens here if the files can't be listed, should basically never happen
            throttle(lastFileUpdate, fileThrottleInterval, {}, action = ::updateSavesFromFiles)

            for (game in savedGames.values) {
                if (game in doNotUpdate) continue
                launch {
                    game.requestUpdate(forceUpdate)
                }
            }
        }
    }

    private fun updateSavesFromFiles() {
        val saves = gameSaver.getMultiplayerSaves()

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
        onlineGameSaver.tryUploadGame(newGame, withPreview = true)
        addGame(newGame)
    }

    /**
     * Fires [MultiplayerGameAdded]
     *
     * @param gameName if this is null or blank, will use the gameId as the game name
     * @return the final name the game was added under
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun addGame(gameId: String, gameName: String? = null) {
        val saveFileName = if (gameName.isNullOrBlank()) gameId else gameName
        var gamePreview: GameInfoPreview
        try {
            gamePreview = onlineGameSaver.tryDownloadGamePreview(gameId)
        } catch (ex: FileNotFoundException) {
            // Game is so old that a preview could not be found on dropbox lets try the real gameInfo instead
            gamePreview = onlineGameSaver.tryDownloadGame(gameId).asPreview()
        }
        addGame(gamePreview, saveFileName)
    }

    private fun addGame(newGame: GameInfo) {
        val newGamePreview = newGame.asPreview()
        addGame(newGamePreview, newGamePreview.gameId)
    }

    private fun addGame(preview: GameInfoPreview, saveFileName: String) {
        val fileHandle = gameSaver.saveGame(preview, saveFileName)
        return addGame(fileHandle, preview)
    }

    private fun addGame(fileHandle: FileHandle, preview: GameInfoPreview = gameSaver.loadGamePreviewFromFile(fileHandle)) {
        val game = OnlineMultiplayerGame(fileHandle, preview, Instant.now())
        savedGames[fileHandle] = game
        postCrashHandlingRunnable { EventBus.send(MultiplayerGameAdded(game.name)) }
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
     * @throws FileNotFoundException if the file can't be found
     * @return false if it's not the user's turn and thus resigning did not happen
     */
    suspend fun resign(game: OnlineMultiplayerGame): Boolean {
        val preview = game.preview ?: throw game.error!!
        // download to work with the latest game state
        val gameInfo = onlineGameSaver.tryDownloadGame(preview.gameId)
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

        val newPreview = gameInfo.asPreview()
        gameSaver.saveGame(newPreview, game.fileHandle)
        onlineGameSaver.tryUploadGame(gameInfo, withPreview = true)
        game.doManualUpdate(newPreview)
        return true
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun loadGame(game: OnlineMultiplayerGame) {
        val preview = game.preview ?: throw game.error!!
        loadGame(preview.gameId)
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun loadGame(gameId: String) {
        val gameInfo = downloadGame(gameId)
        val preview = gameInfo.asPreview()
        val onlineGame = getGameByGameId(gameId)
        val onlinePreview = onlineGame?.preview
        if (onlineGame == null) {
            createGame(gameInfo)
        } else if (onlinePreview != null && hasNewerGameState(preview, onlinePreview)){
            onlineGame.doManualUpdate(preview)
        }
        postCrashHandlingRunnable { UncivGame.Current.loadGame(gameInfo) }
    }

    /**
     * Checks if the given game is current and loads it, otherwise loads the game from the server
     */
    suspend fun loadGame(gameInfo: GameInfo) {
        val gameId = gameInfo.gameId
        val preview = onlineGameSaver.tryDownloadGamePreview(gameId)
        if (hasLatestGameState(gameInfo, preview)) {
            gameInfo.isUpToDate = true
            postCrashHandlingRunnable { UncivGame.Current.loadGame(gameInfo) }
        } else {
            loadGame(gameId)
        }
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun downloadGame(gameId: String): GameInfo {
        val latestGame = onlineGameSaver.tryDownloadGame(gameId)
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
        gameSaver.deleteSave(fileHandle)

        val game = savedGames[fileHandle]
        if (game == null) return

        savedGames.remove(game.fileHandle)
        postCrashHandlingRunnable { EventBus.send(MultiplayerGameDeleted(game.name)) }
    }

    /**
     * Fires [MultiplayerGameNameChanged]
     */
    fun changeGameName(game: OnlineMultiplayerGame, newName: String) {
        val oldPreview = game.preview ?: throw game.error!!
        val oldLastUpdate = game.lastUpdate
        val oldName = game.name

        savedGames.remove(game.fileHandle)
        gameSaver.deleteSave(game.fileHandle)
        val newFileHandle = gameSaver.saveGame(oldPreview, newName)

        val newGame = OnlineMultiplayerGame(newFileHandle, oldPreview, oldLastUpdate)
        savedGames[newFileHandle] = newGame
        EventBus.send(MultiplayerGameNameChanged(oldName, newName))
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun updateGame(gameInfo: GameInfo) {
        onlineGameSaver.tryUploadGame(gameInfo, withPreview = true)
        val game = getGameByGameId(gameInfo.gameId)
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
fun GameInfo.isUsersTurn() = getCivilization(currentPlayer).playerId == UncivGame.Current.settings.multiplayer.userId

