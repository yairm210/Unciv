package com.unciv.logic.multiplayer

import com.badlogic.gdx.files.FileHandle
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.GameSaver
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.OnlineMultiplayerGameSaver
import com.unciv.ui.crashhandling.CRASH_HANDLING_DAEMON_SCOPE
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.utils.isLargerThan
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import java.io.FileNotFoundException
import java.time.Duration
import java.time.Instant


/** @see getRefreshInterval */
private const val CUSTOM_SERVER_REFRESH_INTERVAL = 30L

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
class OnlineMultiplayer() {
    private val gameSaver = UncivGame.Current.gameSaver
    private val savedGames: MutableMap<FileHandle, OnlineMultiplayerGame> = Collections.synchronizedMap(mutableMapOf())
    private var lastFileUpdate: AtomicReference<Instant?> = AtomicReference()

    val games: Set<OnlineMultiplayerGame> get() = savedGames.values.toSet()

    init {
        flow<Unit> {
            while (true) {
                delay(getRefreshInterval().toMillis())

                // TODO will be used later
                // requestUpdate()
            }
        }.launchIn(CRASH_HANDLING_DAEMON_SCOPE)
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
        removedSaves.forEach(savedGames::remove)
        val newSaves = saves - savedGames.keys
        for (saveFile in newSaves) {
            val game = OnlineMultiplayerGame(saveFile)
            savedGames[saveFile] = game
            postCrashHandlingRunnable { EventBus.send(MultiplayerGameAdded(game.name)) }
        }
    }

    /**
     * Fires [MultiplayerGameAdded]
     *
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     */
    suspend fun createGame(newGame: GameInfo) {
        OnlineMultiplayerGameSaver().tryUploadGame(newGame, withPreview = true)
        val newGamePreview = newGame.asPreview()
        val file = gameSaver.saveGame(newGamePreview, newGamePreview.gameId)
        val onlineMultiplayerGame = OnlineMultiplayerGame(file, newGamePreview, Instant.now())
        savedGames[file] = onlineMultiplayerGame
        postCrashHandlingRunnable { EventBus.send(MultiplayerGameAdded(onlineMultiplayerGame.name)) }
    }

    /**
     * Fires [MultiplayerGameAdded]
     *
     * @param gameName if this is null or blank, will use the gameId as the game name
     * @return the final name the game was added under
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun addGame(gameId: String, gameName: String? = null): String {
        val saveFileName = if (gameName.isNullOrBlank()) gameId else gameName
        var gamePreview: GameInfoPreview
        var fileHandle: FileHandle
        try {
            gamePreview = OnlineMultiplayerGameSaver().tryDownloadGamePreview(gameId)
            fileHandle = gameSaver.saveGame(gamePreview, saveFileName)
        } catch (ex: FileNotFoundException) {
            // Game is so old that a preview could not be found on dropbox lets try the real gameInfo instead
            gamePreview = OnlineMultiplayerGameSaver().tryDownloadGame(gameId).asPreview()
            fileHandle = gameSaver.saveGame(gamePreview, saveFileName)
        }
        val game = OnlineMultiplayerGame(fileHandle, gamePreview, Instant.now())
        savedGames[fileHandle] = game
        postCrashHandlingRunnable { EventBus.send(MultiplayerGameAdded(game.name)) }
        return saveFileName
    }

    fun getGameByName(name: String): OnlineMultiplayerGame? {
        return savedGames.values.firstOrNull { it.name == name }
    }

    /**
     * Resigns from the given multiplayer [gameId]. Can only resign if it's currently the user's turn,
     * to ensure that no one else can upload the game in the meantime.
     *
     * Fires [MultiplayerGameUpdated]
     *
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     * @return false if it's not the user's turn and thus resigning did not happen
     */
    suspend fun resign(game: OnlineMultiplayerGame): Boolean {
        val preview = game.preview
        if (preview == null) {
            throw game.error!!
        }
        // download to work with the latest game state
        val gameInfo = OnlineMultiplayerGameSaver().tryDownloadGame(preview.gameId)
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
        OnlineMultiplayerGameSaver().tryUploadGame(gameInfo, withPreview = true)
        game.doManualUpdate(newPreview)
        postCrashHandlingRunnable { EventBus.send(MultiplayerGameUpdated(game.name, newPreview)) }
        return true
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun loadGame(game: OnlineMultiplayerGame) {
        val preview = game.preview
        if (preview == null) {
            throw game.error!!
        }
        loadGame(preview.gameId)
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws FileNotFoundException if the file can't be found
     */
    suspend fun loadGame(gameId: String) {
        val gameInfo = OnlineMultiplayerGameSaver().tryDownloadGame(gameId)
        gameInfo.isUpToDate = true
        postCrashHandlingRunnable { UncivGame.Current.loadGame(gameInfo) }
    }

    /**
     * Deletes the game from disk, does not delete it remotely.
     *
     * Fires [MultiplayerGameDeleted]
     */
    fun deleteGame(multiplayerGame: OnlineMultiplayerGame) {
        val name = multiplayerGame.name
        gameSaver.deleteSave(multiplayerGame.fileHandle)
        EventBus.send(MultiplayerGameDeleted(name))
    }

    /**
     * Fires [MultiplayerGameNameChanged]
     */
    fun changeGameName(game: OnlineMultiplayerGame, newName: String) {
        val oldPreview = game.preview
        if (oldPreview == null) {
            throw game.error!!
        }
        val oldLastUpdate = game.lastUpdate
        val oldName = game.name

        savedGames.remove(game.fileHandle)
        gameSaver.deleteSave(game.fileHandle)
        val newFileHandle = gameSaver.saveGame(oldPreview, newName)

        val newGame = OnlineMultiplayerGame(newFileHandle, oldPreview, oldLastUpdate)
        savedGames[newFileHandle] = newGame
        EventBus.send(MultiplayerGameNameChanged(oldName, newName))
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


fun GameInfoPreview.isUsersTurn() = getCivilization(currentPlayer).playerId == UncivGame.Current.settings.userId
fun GameInfo.isUsersTurn() = getCivilization(currentPlayer).playerId == UncivGame.Current.settings.userId

/**
 * How often all multiplayer games are refreshed in the background
 */
private fun getRefreshInterval(): Duration {
    val settings = UncivGame.Current.settings
    val isDropbox = settings.multiplayerServer == Constants.dropboxMultiplayerServer
    return if (isDropbox) {
        Duration.ofMinutes(settings.multiplayerTurnCheckerDelayInMinutes.toLong())
    } else {
        Duration.ofSeconds(CUSTOM_SERVER_REFRESH_INTERVAL)
    }
}
