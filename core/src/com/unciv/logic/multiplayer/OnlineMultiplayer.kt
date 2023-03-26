package com.unciv.logic.multiplayer

import com.badlogic.gdx.files.FileHandle
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.apiv2.AccountResponse
import com.unciv.logic.multiplayer.apiv2.ApiV2Wrapper
import com.unciv.logic.multiplayer.apiv2.ApiErrorResponse
import com.unciv.logic.multiplayer.apiv2.ApiException
import com.unciv.logic.multiplayer.apiv2.ApiStatusCode
import com.unciv.logic.multiplayer.apiv2.OnlineAccountResponse
import com.unciv.logic.multiplayer.apiv2.WebSocketMessage
import com.unciv.logic.multiplayer.apiv2.WebSocketMessageSerializer
import com.unciv.logic.multiplayer.apiv2.WebSocketMessageType
import com.unciv.logic.multiplayer.storage.ApiV2FileStorageEmulator
import com.unciv.logic.multiplayer.storage.ApiV2FileStorageWrapper
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.MultiplayerAuthException
import com.unciv.logic.multiplayer.storage.MultiplayerFileNotFoundException
import com.unciv.logic.multiplayer.storage.OnlineMultiplayerFiles
import com.unciv.logic.multiplayer.storage.SimpleHttp
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.Dispatcher
import com.unciv.utils.concurrency.launchOnThreadPool
import com.unciv.utils.concurrency.withGLContext
import com.unciv.utils.debug
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * How often files can be checked for new multiplayer games (could be that the user modified their file system directly). More checks within this time period
 * will do nothing.
 */
private val FILE_UPDATE_THROTTLE_PERIOD = Duration.ofSeconds(60)

private val SESSION_TIMEOUT = Duration.ofSeconds(15 * 60)

/**
 * Provides multiplayer functionality to the rest of the game
 *
 * The other parts of the game should not use any other classes from the multiplayer package.
 *
 * See the file of [com.unciv.logic.multiplayer.MultiplayerGameAdded] for all available [EventBus] events.
 */
class OnlineMultiplayer {
    private val settings = UncivGame.Current.settings
    private val logger = java.util.logging.Logger.getLogger(this::class.qualifiedName)

    // Updating the multiplayer server URL in the Api is out of scope, just drop this class and create a new one
    val baseUrl = UncivGame.Current.settings.multiplayer.server
    val api = ApiV2Wrapper(baseUrl)

    private val files = UncivGame.Current.files
    val multiplayerFiles = OnlineMultiplayerFiles()
    private lateinit var featureSet: ServerFeatureSet

    private val savedGames: MutableMap<FileHandle, OnlineMultiplayerGame> = Collections.synchronizedMap(mutableMapOf())

    private val lastFileUpdate: AtomicReference<Instant?> = AtomicReference()
    private val lastAllGamesRefresh: AtomicReference<Instant?> = AtomicReference()
    private val lastCurGameRefresh: AtomicReference<Instant?> = AtomicReference()

    val games: Set<OnlineMultiplayerGame> get() = savedGames.values.toSet()
    val serverFeatureSet: ServerFeatureSet get() = featureSet

    private var lastSuccessfulAuthentication: AtomicReference<Instant?> = AtomicReference()

    // Channel to send frames via WebSocket to the server, may be null
    // for unsupported servers or unauthenticated/uninitialized clients
    private var sendChannel: SendChannel<Frame>? = null

    // Server API auto-detection happens in a coroutine triggered in the constructor
    lateinit var apiVersion: ApiVersion

    lateinit var user: AccountResponse

    init {
        // Run the server auto-detection in a coroutine, only afterwards this class can be considered initialized
        Concurrency.run {
            apiVersion = determineServerAPI()
            checkServerStatus()
            startPollChecker()
            isAliveAPIv1()  // this is called for any API version since it sets the featureSet implicitly
            if (apiVersion == ApiVersion.APIv2) {
                // Trying to log in with the stored credentials at this step decreases latency later
                if (hasAuthentication()) {
                    if (!api.auth.login(UncivGame.Current.settings.multiplayer.userName, UncivGame.Current.settings.multiplayer.passwords[UncivGame.Current.settings.multiplayer.server]!!)) {
                        logger.warning("Login failed using stored credentials")
                    } else {
                        lastSuccessfulAuthentication.set(Instant.now())
                        api.websocket(::handleWS)
                        user = api.account.get()
                    }
                }
                ApiV2FileStorageWrapper.storage = ApiV2FileStorageEmulator(api)
                ApiV2FileStorageWrapper.api = api
            }
        }

        runBlocking {
            coroutineScope {
                Concurrency.runOnNonDaemonThreadPool {
                    if (!api.isServerCompatible()) {
                        logger.warning("Server API at ${UncivGame.Current.settings.multiplayer.server} is not APIv2-compatible")
                        return@runOnNonDaemonThreadPool
                    }
                    ApiV2FileStorageWrapper.storage = ApiV2FileStorageEmulator(api)
                }
            }
        }
    }

    /**
     * Determine whether the object has been initialized.
     */
    fun isInitialized(): Boolean {
        return (this::featureSet.isInitialized) && (this::apiVersion.isInitialized)
    }

    /**
     * Actively sleeping loop that awaits [isInitialized].
     */
    suspend fun awaitInitialized() {
        while (!isInitialized()) {
            delay(1)
        }
    }

    /**
     * Determine if the user is authenticated by comparing timestamps (APIv2 only)
     *
     * This method is not reliable. The server might have configured another session timeout.
     */
    fun isAuthenticated(): Boolean {
        return (lastSuccessfulAuthentication.get() != null && (lastSuccessfulAuthentication.get()!! + SESSION_TIMEOUT) > Instant.now())
    }

    /**
     * Determine the server API version of the remote server
     *
     * Check precedence: [ApiVersion.APIv0] > [ApiVersion.APIv2] > [ApiVersion.APIv1]
     */
    private suspend fun determineServerAPI(): ApiVersion {
        return if (usesDropbox()) {
            ApiVersion.APIv0
        } else if (api.isServerCompatible()) {
            ApiVersion.APIv2
        } else {
            ApiVersion.APIv1
        }
    }

    /**
     * Send text as a [FrameType.TEXT] frame to the remote side (fire & forget)
     *
     * Returns [Unit] if no exception is thrown
     */
    internal suspend fun sendText(text: String): Unit {
        if (sendChannel == null) {
            return
        }
        try {
            sendChannel!!.send(Frame.Text(text))
        } catch (e: Throwable) {
            logger.warning(e.localizedMessage)
            logger.warning(e.stackTraceToString())
            throw e
        }
    }

    /**
     * Send text as a [FrameType.TEXT] frame to the remote side (fire & forget)
     *
     * Returns true on success, false otherwise. Any error is suppressed!
     */
    internal suspend fun sendTextSuppressed(text: String): Boolean {
        if (sendChannel == null) {
            return false
        }
        try {
            sendChannel!!.send(Frame.Text(text))
        } catch (e: Throwable) {
            logger.severe(e.localizedMessage)
            logger.severe(e.stackTraceToString())
        }
        return true
    }

    /**
     * Handle incoming WebSocket messages
     */
    private fun handleIncomingWSMessage(msg: WebSocketMessage) {
        when (msg.type) {
            WebSocketMessageType.InvalidMessage -> {
                logger.warning("Received invalid message from WebSocket connection")
            }
            WebSocketMessageType.FinishedTurn -> {
                // This message type is not meant to be received from the server
                logger.warning("Received FinishedTurn message from WebSocket connection")
            }
            WebSocketMessageType.UpdateGameData -> {
                // TODO: The body of this message contains a whole game state, so we need to unpack and use it here
            }
            WebSocketMessageType.ClientDisconnected -> {
                logger.info("Received ClientDisconnected message from WebSocket connection")
                // TODO: Implement client connectivity handling
            }
            WebSocketMessageType.ClientReconnected -> {
                logger.info("Received ClientReconnected message from WebSocket connection")
                // TODO: Implement client connectivity handling
            }
            WebSocketMessageType.IncomingChatMessage -> {
                logger.info("Received IncomingChatMessage message from WebSocket connection")
                // TODO: Implement chat message handling
            }
        }
    }

    /**
     * Allocate a new game ID on the server and return it
     *
     * IMPORTANT: This is a temporary solution until proper handling of lobbies is implemented.
     * When this is done, this function should be changed to something like `startLobby`.
     */
    fun allocateGameId(): String? {
        // TODO: Make backward-compatible by ignoring remote backends which can't create game IDs
        runBlocking {
            // TODO: Implement the server endpoint for the function api.lobby.create()
            return@runBlocking UUID.randomUUID().toString()
        }
        return null
    }

    /**
     * Handle a newly established WebSocket connection
     */
    private suspend fun handleWS(session: ClientWebSocketSession) {
        sendChannel?.close()
        sendChannel = session.outgoing

        try {
            while (true) {
                val incomingFrame = session.incoming.receive()
                when (incomingFrame.frameType) {
                    FrameType.CLOSE, FrameType.PING, FrameType.PONG -> {
                        // This handler won't handle control frames
                        logger.info("Received CLOSE, PING or PONG as message")
                    }
                    FrameType.BINARY -> {
                        logger.warning("Received binary packet which can't be parsed at the moment")
                    }
                    FrameType.TEXT -> {
                        try {
                            logger.fine("Incoming text message: $incomingFrame")
                            val text = (incomingFrame as Frame.Text).readText()
                            logger.fine("Message text: $text")
                            val msg = Json.decodeFromString(WebSocketMessageSerializer(), text)
                            logger.fine("Message type: ${msg::class.java.canonicalName}")
                            logger.fine("Deserialized: $msg")
                            handleIncomingWSMessage(msg)
                        } catch (e: Throwable) {
                            logger.severe(e.localizedMessage)
                            logger.severe(e.stackTraceToString())
                        }
                    }
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            logger.warning("The WebSocket channel was closed: $e")
        } catch (e: Throwable) {
            logger.severe(e.localizedMessage)
            logger.severe(e.stackTraceToString())
            throw e
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
        logger.info("Creating game")
        multiplayerFiles.tryUploadGame(newGame, withPreview = true)
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
        var gamePreview: GameInfoPreview
        try {
            gamePreview = multiplayerFiles.tryDownloadGamePreview(gameId)
        } catch (ex: MultiplayerFileNotFoundException) {
            // Game is so old that a preview could not be found on dropbox lets try the real gameInfo instead
            gamePreview = multiplayerFiles.tryDownloadGame(gameId).asPreview()
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
        val gameInfo = multiplayerFiles.tryDownloadGame(preview.gameId)
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
        multiplayerFiles.tryUploadGame(gameInfo, withPreview = true)
        game.doManualUpdate(newPreview)
        return true
    }

    /**
     * Load all friends and friend requests (split by incoming and outgoing) of the currently logged-in user
     */
    suspend fun getFriends(): Triple<List<OnlineAccountResponse>, List<AccountResponse>, List<AccountResponse>> {
        val (friends, requests) = api.friend.listAll()
        // TODO: The user's UUID should be cached, when this class is extended to a game manager class
        val myUUID = api.account.get().uuid
        return Triple(
            friends.map { it.to },
            requests.filter { it.to.uuid == myUUID }.map{ it.from },
            requests.filter { it.from.uuid == myUUID }.map { it.to }
        )
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
        val preview = multiplayerFiles.tryDownloadGamePreview(gameId)
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
        multiplayerFiles.tryUploadGame(gameInfo, withPreview = true)
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
     * Checks if the server is alive and sets the [serverFeatureSet] accordingly.
     * @return true if the server is alive, false otherwise
     */
    suspend fun checkServerStatus(): Boolean {
        if (api.getCompatibilityCheck() == null) {
            api.isServerCompatible()
            if (api.getCompatibilityCheck()!!) {
                return true  // if the compatibility check succeeded, the server is obviously running
            }
        } else if (api.getCompatibilityCheck()!!) {
            try {
                api.version()
            } catch (e: Throwable) {  // the only expected exception type is network-related (e.g. timeout or unreachable)
                Log.error("Suppressed error in 'checkServerStatus': $e")
                return false
            }
            return true  // no exception means the server responded with the excepted answer type
        }

        return isAliveAPIv1()
    }

    /**
     * Check if the server is reachable by getting the /isalive endpoint
     *
     * This will also update/set the [featureSet] implicitly.
     *
     * Only use this method for APIv1 servers. This method doesn't check the API version, though.
     */
    private fun isAliveAPIv1(): Boolean {
        var statusOk = false
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

        val settings = UncivGame.Current.settings.multiplayer

        val success = multiplayerFiles.fileStorage().authenticate(
            userId=settings.userId,
            password=password ?: settings.passwords[settings.server] ?: ""
        )
        if (password != null && success) {
            settings.passwords[settings.server] = password
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
     * Refresh the currently used session by logging in with username and password stored in the game settings
     *
     * Any errors are suppressed. Differentiating invalid logins from network issues is therefore impossible.
     */
    suspend fun refreshSession(): Boolean {
        if (!hasAuthentication()) {
            return false
        }
        val success = try {
            api.auth.login(
                UncivGame.Current.settings.multiplayer.userName,
                UncivGame.Current.settings.multiplayer.passwords[UncivGame.Current.settings.multiplayer.server]!!
            )
        } catch (e: Throwable) {
            Log.error("Suppressed error in 'refreshSession': $e")
            false
        }
        if (success) {
            lastSuccessfulAuthentication.set(Instant.now())
        }
        return success
    }

    /**
     * @return true if setting the password was successful, false otherwise.
     * @throws FileStorageRateLimitReached if the file storage backend can't handle any additional actions for a time
     * @throws MultiplayerAuthException if the authentication failed
     */
    suspend fun setPassword(password: String): Boolean {
        if (
                featureSet.authVersion > 0 &&
                multiplayerFiles.fileStorage().setPassword(newPassword = password)
        ) {
            val settings = UncivGame.Current.settings.multiplayer
            settings.passwords[settings.server] = password
            return true
        }

        return false
    }

    /**
     * Checks if [preview1] has a more recent game state than [preview2]
     */
    private fun hasNewerGameState(preview1: GameInfoPreview, preview2: GameInfoPreview): Boolean {
        return preview1.turns > preview2.turns
    }

    /**
     * Start a background runner that periodically checks for new game updates ([ApiVersion.APIv0] and [ApiVersion.APIv1] only)
     */
    private fun startPollChecker() {
        if (apiVersion in listOf(ApiVersion.APIv0, ApiVersion.APIv1)) {
            logger.info("Starting poll service for remote games ...")
            flow<Unit> {
                while (true) {
                    delay(500)

                    val currentGame = getCurrentGame()
                    val multiplayerSettings = UncivGame.Current.settings.multiplayer
                    val preview = currentGame?.preview
                    if (currentGame != null && (OldOnlineMultiplayer.usesCustomServer() || preview == null || !preview.isUsersTurn())) {
                        throttle(lastCurGameRefresh, multiplayerSettings.currentGameRefreshDelay, {}) { currentGame.requestUpdate() }
                    }

                    val doNotUpdate = if (currentGame == null) listOf() else listOf(currentGame)
                    throttle(lastAllGamesRefresh, multiplayerSettings.allGameRefreshDelay, {}) { requestUpdate(doNotUpdate = doNotUpdate) }
                }
            }.launchIn(CoroutineScope(Dispatcher.DAEMON))
        }
    }

    companion object {
        fun usesCustomServer() = UncivGame.Current.settings.multiplayer.server != Constants.dropboxMultiplayerServer
        fun usesDropbox() = !usesCustomServer()
    }

}
