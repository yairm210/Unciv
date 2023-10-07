package com.unciv.logic.multiplayer

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Disposable
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.UncivShowableException
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.event.EventBus
import com.unciv.logic.files.IncompatibleGameInfoVersionException
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.multiplayer.apiv2.ApiV2
import com.unciv.logic.multiplayer.apiv2.DEFAULT_REQUEST_TIMEOUT
import com.unciv.logic.multiplayer.apiv2.IncomingChatMessage
import com.unciv.logic.multiplayer.apiv2.UncivNetworkException
import com.unciv.logic.multiplayer.apiv2.UpdateGameData
import com.unciv.logic.multiplayer.storage.ApiV2FileStorage
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.MultiplayerAuthException
import com.unciv.logic.multiplayer.storage.MultiplayerFileNotFoundException
import com.unciv.logic.multiplayer.storage.OnlineMultiplayerServer
import com.unciv.logic.multiplayer.storage.SimpleHttp
import com.unciv.ui.components.extensions.isLargerThan
import com.unciv.utils.Concurrency
import com.unciv.utils.Dispatcher
import com.unciv.utils.Log
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
 * How often files can be checked for new multiplayer games (could be that the user modified
 * their file system directly). More checks within this time period will do nothing.
 */
private val FILE_UPDATE_THROTTLE_PERIOD = Duration.ofSeconds(60)

/**
 * Provides multiplayer functionality to the rest of the game
 *
 * You need to call [initialize] as soon as possible, to bootstrap API detection
 * and first network connectivity. A later version may enforce that no network
 * traffic is generated before [initialize] gets called, but this is not yet the case.
 * You should wait for the completion of this initialization process, otherwise
 * certain more complex functionality may be unavailable, especially event handling
 * features of the [ApiVersion.APIv2]. You may use [awaitInitialized] for that.
 *
 * After initialization, this class can be used to access multiplayer features via
 * methods such as [downloadGame] or [updateGame]. Use the [api] instance to access
 * functionality of the new APIv2 implementations (e.g. lobbies, friends, in-game
 * chats and more). You must ensure that the access to that [api] property is properly
 * guarded: the API must be V2 and the initialization must be completed. Otherwise,
 * accessing that property may yield [UncivShowableException] or trigger race conditions.
 * The recommended way to do this is checking the [apiVersion] of this instance, which
 * is also set after initialization. After usage, you should [dispose] this instance
 * properly to close network connections gracefully. Changing the server URL is only
 * possible by [disposing][dispose] this instance and creating a new one.
 *
 * Certain features (for example the poll checker, see [startPollChecker], or the WebSocket
 * handlers, see [ApiV2.handleWebSocket]) send certain events on the [EventBus]. See the
 * source file of [MultiplayerGameAdded] and [IncomingChatMessage] for an overview of them.
 */
class OnlineMultiplayer: Disposable {
    private val settings
        get() = UncivGame.Current.settings

    // Updating the multiplayer server URL in the Api is out of scope, just drop this class and create a new one
    val baseUrl = UncivGame.Current.settings.multiplayer.server

    /**
     * Access the [ApiV2] instance only after [initialize] has been completed, otherwise
     * it will block until [initialize] has finished (which may take very long for high
     * latency networks). Accessing this property when the [apiVersion] is **not**
     * [ApiVersion.APIv2] will yield an [UncivShowableException] that the it's not supported.
     */
    val api: ApiV2
        get() {
            if (Concurrency.runBlocking { awaitInitialized(); apiImpl.isCompatible() } == true) {
                return apiImpl
            }
            throw UncivShowableException("Unsupported server API: [$baseUrl]")
        }
    private val apiImpl = ApiV2(baseUrl)

    private val files = UncivGame.Current.files
    val multiplayerServer = OnlineMultiplayerServer()
    private lateinit var featureSet: ServerFeatureSet

    private var pollChecker: Job? = null
    private val events = EventBus.EventReceiver()

    private val savedGames: MutableMap<FileHandle, OnlineMultiplayerGame> = Collections.synchronizedMap(mutableMapOf())

    private val lastFileUpdate: AtomicReference<Instant?> = AtomicReference()
    private val lastAllGamesRefresh: AtomicReference<Instant?> = AtomicReference()
    private val lastCurGameRefresh: AtomicReference<Instant?> = AtomicReference()

    val games: Set<OnlineMultiplayerGame>
        get() = savedGames.values.toSet()

    /** Inspect this property to check the API version of the multiplayer server (the server
     * API auto-detection happens in the coroutine [initialize], so you need to wait until
     * this is finished, otherwise this property access will block or ultimatively throw a
     * [UncivNetworkException] to avoid hanging forever when the network is down/very slow) */
    val apiVersion: ApiVersion
        get() {
            if (apiVersionImpl != null) {
                return apiVersionImpl!!
            }

            // Using directly blocking code below is fine, since it's enforced to await [initialize]
            // anyways -- even though it will be cancelled by the second "timeout fallback" job to
            // avoid blocking the calling function forever, so that it will "earlier" crash the game instead
            val waitJob = Concurrency.run {
                // Using an active sleep loop here is not the best way, but the simplest; it could be improved later
                while (apiVersionImpl == null) {
                    delay(20)
                }
            }
            val cancelJob = Concurrency.run {
                delay(2 * DEFAULT_REQUEST_TIMEOUT)
                Log.debug("Cancelling the access to apiVersion, since $baseUrl seems to be too slow to respond")
                waitJob.cancel()
            }
            Concurrency.runBlocking { waitJob.join() }
            if (apiVersionImpl != null) {
                cancelJob.cancel()
                return apiVersionImpl!!
            }
            throw UncivNetworkException("Unable to detect the API version of [$baseUrl]: please check your network connectivity", null)
        }
    private var apiVersionImpl: ApiVersion? = null

    init {
        // The purpose of handling this event here is to avoid de-serializing the GameInfo
        // more often than really necessary. Other actors may just listen for MultiplayerGameCanBeLoaded
        // instead of the "raw" UpdateGameData. Note that the Android turn checker ignores this rule.
        events.receive(UpdateGameData::class, null) {
            Concurrency.runOnNonDaemonThreadPool {
                try {
                    val gameInfo = UncivFiles.gameInfoFromString(it.gameData)
                    gameInfo.setTransients()
                    addGame(gameInfo)
                    val gameDetails = api.game.head(it.gameUUID, suppress = true)
                    Concurrency.runOnGLThread {
                        EventBus.send(MultiplayerGameCanBeLoaded(gameInfo, gameDetails?.name, it.gameDataID))
                    }
                } catch (e: IncompatibleGameInfoVersionException) {
                    Log.debug("Failed to load GameInfo from incoming event: %s", e.localizedMessage)
                }
            }
        }
    }

    /**
     * Initialize this instance and detect the API version of the server automatically
     *
     * This should be called as early as possible to configure other depending attributes.
     * You must await its completion before using the [api] and its related functionality.
     */
    suspend fun initialize() {
        apiVersionImpl = ApiVersion.detect(baseUrl)
        Log.debug("Server at '$baseUrl' detected API version: $apiVersionImpl")
        startPollChecker()
        featureSet = ServerFeatureSet()  // setting this here to fix problems for non-network games
        if (apiVersionImpl == ApiVersion.APIv1) {
            isAliveAPIv1()
        }
        if (apiVersionImpl == ApiVersion.APIv2) {
            if (hasAuthentication()) {
                apiImpl.initialize(Pair(settings.multiplayer.userName, settings.multiplayer.passwords[baseUrl] ?: ""))
            } else {
                apiImpl.initialize()
            }
            ApiV2FileStorage.setApi(apiImpl)
        }
    }

    /**
     * Determine whether the instance has been initialized
     */
    fun isInitialized(): Boolean {
        return (this::featureSet.isInitialized) && (apiVersionImpl != null) && (apiVersionImpl != ApiVersion.APIv2 || apiImpl.isInitialized())
    }

    /**
     * Actively sleeping loop that awaits [isInitialized]
     */
    suspend fun awaitInitialized() {
        while (!isInitialized()) {
            // Using an active sleep loop here is not the best way, but the simplest; it could be improved later
            delay(20)
        }
    }

    /**
     * Determine the server API version of the remote server
     *
     * Check precedence: [ApiVersion.APIv0] > [ApiVersion.APIv2] > [ApiVersion.APIv1]
     */
    private suspend fun determineServerAPI(): ApiVersion {
        return if (usesDropbox()) {
            ApiVersion.APIv0
        } else if (apiImpl.isCompatible()) {
            ApiVersion.APIv2
        } else {
            ApiVersion.APIv1
        }
    }

    private fun getCurrentGame(): OnlineMultiplayerGame? {
        val gameInfo = UncivGame.Current.gameInfo
        return if (gameInfo != null) {
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
        } else if (onlinePreview != null && hasNewerGameState(preview, onlinePreview)){
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
    fun changeGameName(game: OnlineMultiplayerGame, newName: String, onException:(Exception?)->Unit) {
        debug("Changing name of game %s to", game.name, newName)
        val oldPreview = game.preview ?: throw game.error!!
        val oldLastUpdate = game.lastUpdate
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
     * Checks if the server is alive and sets the [featureSet] accordingly.
     * @return true if the server is alive, false otherwise
     */
    suspend fun checkServerStatus(): Boolean {
        if (apiImpl.isCompatible()) {
            try {
                apiImpl.version()
            } catch (e: Throwable) {
                Log.error("Unexpected error during server status query: ${e.localizedMessage}")
                return false
            }
            return true
        }

        return isAliveAPIv1()
    }

    /**
     * Check if the server is reachable by getting the /isalive endpoint
     *
     * This will also update/set the [featureSet] implicitly.
     *
     * Only use this method for APIv1 servers. This method doesn't check the API version, though.
     *
     * This is a blocking method doing network operations.
     */
    private fun isAliveAPIv1(): Boolean {
        var statusOk = false
        try {
            SimpleHttp.sendGetRequest("${UncivGame.Current.settings.multiplayer.server}/isalive") { success, result, _ ->
                statusOk = success
                if (result.isNotEmpty()) {
                    featureSet = try {
                        json().fromJson(ServerFeatureSet::class.java, result)
                    } catch (ex: Exception) {
                        Log.error("${UncivGame.Current.settings.multiplayer.server} does not support server feature set")
                        ServerFeatureSet()
                    }
                }
            }
        } catch (e: Throwable) {
            Log.error("Error while checking server '$baseUrl' isAlive for $apiVersion: $e")
            statusOk = false
        }
        return statusOk
    }

    /**
     * @return true if the authentication was successful or the server does not support authentication.
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerAuthException if the authentication failed
     */
    suspend fun authenticate(password: String?): Boolean {
        if (featureSet.authVersion == 0) {
            return true
        }

        val success = multiplayerServer.fileStorage().authenticate(
            userId=settings.multiplayer.userId,
            password=password ?: settings.multiplayer.passwords[settings.multiplayer.server] ?: ""
        )
        if (password != null && success) {
            settings.multiplayer.passwords[settings.multiplayer.server] = password
        }
        return success
    }

    /**
     * Determine if there are any known credentials for the current server (the credentials might be invalid!)
     */
    fun hasAuthentication(): Boolean {
        val settings = UncivGame.Current.settings.multiplayer
        return settings.passwords.containsKey(settings.server)
    }

    /**
     * Checks if [preview1] has a more recent game state than [preview2]
     */
    private fun hasNewerGameState(preview1: GameInfoPreview, preview2: GameInfoPreview): Boolean {
        return preview1.turns > preview2.turns
    }

    /**
     * Start a background runner that periodically checks for new game updates
     * ([ApiVersion.APIv0] and [ApiVersion.APIv1] only)
     *
     * We have 2 'async processes' that update the multiplayer games:
     * A. This one, which runs as part of *this process* and refreshes for all OS's
     * B. MultiplayerTurnCheckWorker, which runs *as an Android worker* and refreshes
     *   *even when the game is paused*. Only for Android, obviously.
     */
    private fun startPollChecker() {
        if (apiVersion in listOf(ApiVersion.APIv0, ApiVersion.APIv1)) {
            Log.debug("Starting poll service for remote games ...")
            pollChecker = flow<Unit> {
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
            }.launchIn(CoroutineScope(Dispatcher.DAEMON))
        }
    }

    /**
     * Dispose this [OnlineMultiplayer] instance by closing its background jobs and connections
     */
    override fun dispose() {
        ApiV2FileStorage.unsetApi()
        pollChecker?.cancel()
        events.stopReceiving()
        if (isInitialized() && apiVersion == ApiVersion.APIv2) {
            api.dispose()
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


fun GameInfoPreview.isUsersTurn() = getCivilization(currentPlayer).playerId == UncivGame.Current.settings.multiplayer.userId
fun GameInfo.isUsersTurn() = getCivilization(currentPlayer).playerId == UncivGame.Current.settings.multiplayer.userId
