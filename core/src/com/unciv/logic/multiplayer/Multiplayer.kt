package com.unciv.logic.multiplayer

import com.badlogic.gdx.utils.Disposable
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.HasGameTurnData
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.Multiplayer.ServerType.CUSTOM
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.MultiplayerFileNotFoundException
import com.unciv.logic.multiplayer.storage.MultiplayerFiles
import com.unciv.logic.multiplayer.storage.SimpleHttp
import com.unciv.ui.utils.extensions.isLargerThan
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
import com.unciv.utils.concurrency.launchOnThreadPool
import com.unciv.utils.concurrency.withGLContext
import com.unciv.utils.debug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference


/**
 * How often the multiplayer games JSON can be checked for new multiplayer games. It could be that either the user modified their file system directly,
 * or the turn checker updated the file. More checks within this time period will do nothing.
 */
private val FILE_UPDATE_THROTTLE_PERIOD = Duration.ofSeconds(60)

/**
 * Provides multiplayer functionality to the rest of the game.
 *
 * See the file of [com.unciv.logic.multiplayer.MultiplayerGameAdded] for all available [EventBus] events.
 */
class Multiplayer : Disposable {
    private val files = UncivGame.Current.files
    private val multiplayerFiles = MultiplayerFiles()

    private val savedGames = mutableMapOf<String, MultiplayerGame>()

    private val lastFileUpdate: AtomicReference<Instant?> = AtomicReference(Instant.now())
    private val lastAllGamesRefresh: AtomicReference<Instant?> = AtomicReference()
    private val lastCurGameRefresh: AtomicReference<Instant?> = AtomicReference()

    private var gameUpdateJob: Job? = null
    private var writeFileDebounce: Job? = null

    private val events = EventBus.EventReceiver()

    val games: Set<MultiplayerGame> get() = savedGames.values.toSet()

    init {
        Concurrency.runBlocking {
            loadGamesFromFile()
        }
        startGameUpdateJob()
        events.receive(MultiplayerGameChanged::class) {
            Concurrency.run {
                writeGamesToFile()
            }
        }
    }

    private suspend fun loadGamesFromFile() {
        val gamesFromFile = files.loadMultiplayerGames()
        savedGames.putAll(gamesFromFile.associateBy(MultiplayerGame::gameId)) // initial game addition does not get events sent
    }

    private suspend fun writeGamesToFile() {
        files.saveMultiplayerGames(games)
    }

    private fun startGameUpdateJob() {
        gameUpdateJob = Concurrency.run {
            while (true) {
                try {
                    delay(500)
                } catch (ex: CancellationException) {
                    break;
                }

                val currentGame = getCurrentGame()
                val multiplayerSettings = UncivGame.Current.settings.multiplayer
                val status = currentGame?.status
                // We update during our current turn with custom servers since they can handle the load and it allows the user to play on multiple devices.
                // Dropbox can't handle the load so that only updates when it's not the user's turn.
                if (currentGame != null && (status == null || currentGame.serverData.type == CUSTOM || !status.isUsersTurn())) {
                    throttle(lastCurGameRefresh, multiplayerSettings.currentGameRefreshDelay, {}) { currentGame.requestUpdate() }
                }

                val doNotUpdate = if (currentGame == null) listOf() else listOf(currentGame)
                throttle(lastAllGamesRefresh, multiplayerSettings.allGameRefreshDelay, {}) { requestUpdate(doNotUpdate = doNotUpdate) }
            }
        }
    }

    private fun getCurrentGame(): MultiplayerGame? {
        val gameInfo = UncivGame.Current.gameInfo ?: return null
        return getGameById(gameInfo.gameId)
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
            // An exception only happens here if there's some file IO exception, should basically never happen
            throttle(lastFileUpdate, fileThrottleInterval, {}, action = ::updateSavesFromFiles)

            for (game in savedGames.values) {
                if (game in doNotUpdate) continue
                launchOnThreadPool {
                    game.requestUpdate(forceUpdate)
                }
            }
        }
    }

    private suspend fun updateSavesFromFiles() = coroutineScope {
        val gamesFromFile = files.loadMultiplayerGames()

        val removedGames = games - gamesFromFile
        for (game in removedGames) {
            deleteGame(game)
        }

        val newGames = gamesFromFile - games
        for (game in newGames) {
            addGame(game)
        }

        for (gameFromFile in gamesFromFile) {
            val currentGame = savedGames[gameFromFile.gameId]!!
            if (currentGame.lastUpdate.get() != gameFromFile.lastUpdate.get()) {
                val fileStatus = gameFromFile.status
                val fileError = gameFromFile.error
                val event = if (fileError != null) {
                    MultiplayerGameUpdateFailed(gameFromFile, fileError)
                } else if (fileStatus != null && fileStatus == currentGame.status) {
                    MultiplayerGameUpdateUnchanged(gameFromFile, fileStatus)
                } else {
                    MultiplayerGameUpdated(gameFromFile, fileStatus!!)
                }
                launchOnGLThread {
                    EventBus.send(event)
                }
            }
        }
    }


    /**
     * Fires [MultiplayerGameAdded]
     *
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     */
    suspend fun createGame(serverData: ServerData, gameInfo: GameInfo, gameName: String? = null) {
        multiplayerFiles.tryUploadGame(serverData, gameInfo)
        gameInfo.isUpToDate = true
        addGame(serverData, gameInfo.gameId, gameName, gameInfo)
    }

    /**
     * Fires [MultiplayerGameAdded]
     *
     * @param gameName if this is null, will use the gameId as the game name
     * @return the final name the game was added under
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun addGame(
        serverData: ServerData,
        gameId: String,
        gameName: String? = null,
        status: HasGameTurnData? = null
    ): MultiplayerGame {
        debug("Adding game %s", gameId)
        val loadedStatus = if (status != null) {
            status
        } else {
            try {
                multiplayerFiles.tryDownloadGameStatus(serverData, gameId)
            } catch (ex: MultiplayerFileNotFoundException) {
                // Maybe something went wrong with uploading the multiplayer status, let's try the full game info
                GameStatus(multiplayerFiles.tryDownloadGame(serverData, gameId))
            }
        }
        val game = MultiplayerGame(gameId, serverData, gameName, GameStatus(loadedStatus))
        addGame(game)
        return game
    }

    private suspend fun addGame(game: MultiplayerGame) {
        savedGames[game.gameId] = game
        withGLContext {
            EventBus.send(MultiplayerGameAdded(game))
        }
    }

    fun getGameByName(name: String): MultiplayerGame? {
        return savedGames.values.firstOrNull { it.name == name }
    }

    fun getGameById(gameId: String): MultiplayerGame? {
        return savedGames[gameId]
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
        // download to work with the latest game state
        val gameInfo = multiplayerFiles.tryDownloadGame(game.serverData, game.gameId)
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
        multiplayerFiles.tryUploadGame(game.serverData, gameInfo)
        game.doManualUpdate(newStatus)
        return true
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun loadGame(game: MultiplayerGame) {
        loadGame(game.serverData, game.gameId)
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun loadGame(serverData: ServerData, gameId: String) = coroutineScope {
        val newGameInfo = downloadGame(serverData, gameId)
        val multiplayerGame = getGameById(gameId)
        val oldStatus = multiplayerGame?.status
        if (multiplayerGame == null) {
            createGame(serverData, newGameInfo)
        } else if (oldStatus != null && !oldStatus.hasLatestGameState(newGameInfo)) {
            multiplayerGame.doManualUpdate(newGameInfo)
        }
        UncivGame.Current.loadGame(newGameInfo)
    }

    /**
     * Checks if the given game is current and loads it, otherwise loads the game from the server
     */
    suspend fun loadGame(serverData: ServerData, gameInfo: GameInfo) = coroutineScope {
        val gameId = gameInfo.gameId
        val status = multiplayerFiles.tryDownloadGameStatus(serverData, gameId)
        if (gameInfo.hasLatestGameState(status)) {
            gameInfo.isUpToDate = true
            UncivGame.Current.loadGame(gameInfo)
        } else {
            loadGame(serverData, gameId)
        }
    }

    /**
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun downloadGame(serverData: ServerData, gameId: String): GameInfo {
        val latestGame = multiplayerFiles.tryDownloadGame(serverData, gameId)
        latestGame.isUpToDate = true
        return latestGame
    }

    /**
     * Deletes the game from disk, does not delete it remotely.
     *
     * Fires [MultiplayerGameDeleted]
     */
    fun deleteGame(toDelete: MultiplayerGame) {
        debug("Deleting game %s with id %s", toDelete.name, toDelete.gameId)
        if (savedGames.remove(toDelete.gameId) != null) {
            Concurrency.runOnGLThread {
                EventBus.send(MultiplayerGameDeleted(toDelete))
            }
        }
    }

    /**
     * Adds the game if it doesn't exist yet.
     *
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerFileNotFoundException if the file can't be found
     */
    suspend fun updateGame(serverData: ServerData, gameInfo: GameInfo) {
        debug("Updating remote game %s", gameInfo.gameId)
        multiplayerFiles.tryUploadGame(serverData, gameInfo)
        val game = getGameById(gameInfo.gameId)
        debug("Existing OnlineMultiplayerGame: %s", game)
        if (game == null) {
            addGame(serverData, gameInfo.gameId, status = gameInfo)
        } else {
            game.doManualUpdate(gameInfo)
        }
    }

    /** Checks if it's possible to connect to the given multiplayer server URL. If null, will check connection to Dropbox. */
    suspend fun checkConnection(url: String?): Boolean {
        return checkConnection(ServerData(url))
    }

    /** Checks if it's possible to connect to the given multiplayer server. */
    suspend fun checkConnection(serverData: ServerData): Boolean {
        val url = if (serverData.url != null) SimpleHttp.buildURL(serverData.url, "isalive") else "https://content.dropboxapi.com"
        val result = SimpleHttp.sendGetRequest(url)

        // Dropbox will return a 404, which is not a "success". But we only care about that a connection was established,
        // and checking if the responseCode is null tells us that.
        return result.code != null
    }

    override fun dispose() {
        val delayedSaveToFileJob = writeFileDebounce
        if (delayedSaveToFileJob?.isActive == true) {
            delayedSaveToFileJob.cancel() // save immediately instead
            Concurrency.runBlocking {
                writeGamesToFile()
            }
        }
        gameUpdateJob?.cancel()
    }

    enum class ServerType {
        DROPBOX, CUSTOM;

        companion object {
            fun fromUrl(url: String?) = if (url == null) DROPBOX else CUSTOM
        }
    }

    /**
     * Reduced variant of [GameInfo] used for multiplayer saves.
     */
    data class GameStatus(
        override val turns: Int,
        override val currentTurnStartTime: Long,
        override val currentCivName: String,
        override val currentPlayerId: String,
    ) : HasGameTurnData {
        constructor(gd: HasGameTurnData) : this(gd.turns, gd.currentTurnStartTime, gd.currentCivName, gd.currentPlayerId)
    }

    /** A multiplayer server definition. If [url] is null, the server will be the default Dropbox server. */
    data class ServerData(
        val url: String?
    ) {
        val type: ServerType get() = ServerType.fromUrl(url)

        @Suppress("unused") // used by json serialization
        private constructor() : this(null)

        companion object {
            val default get() = UncivGame.Current.settings.multiplayer.defaultServerData
        }
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

