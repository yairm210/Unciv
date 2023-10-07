package com.unciv.logic.multiplayer.apiv2

import com.badlogic.gdx.utils.Disposable
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.event.Event
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.ApiVersion
import com.unciv.logic.multiplayer.OnlineMultiplayer
import com.unciv.logic.multiplayer.storage.MultiplayerFileNotFoundException
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.ktor.websocket.Frame
import io.ktor.websocket.FrameType
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.Random
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Main class to interact with multiplayer servers implementing [ApiVersion.ApiV2]
 *
 * Do not directly initialize this class, but use [OnlineMultiplayer] instead,
 * which will provide access via [OnlineMultiplayer.api] if everything has been set up.
 *
 *
 */
class ApiV2(private val baseUrl: String) : ApiV2Wrapper(baseUrl), Disposable {

    /** Cache the result of the last server API compatibility check */
    private var compatibilityCheck: Boolean? = null

    /** Channel to send frames via WebSocket to the server, may be null
     * for unsupported servers or unauthenticated/uninitialized clients */
    private var sendChannel: SendChannel<Frame>? = null

    /** Info whether this class is fully initialized and ready to use */
    private var initialized = false

    /** Switch to enable auto-reconnect attempts for the WebSocket connection */
    private var reconnectWebSocket = true

    /** Timestamp of the last successful login */
    private var lastSuccessfulAuthentication: AtomicReference<Instant?> = AtomicReference()

    /** Cache for the game details to make certain lookups faster */
    private val gameDetails: MutableMap<UUID, TimedGameDetails> = mutableMapOf()

    /** List of channel that extend the usage of the [EventBus] system, see [getWebSocketEventChannel] */
    private val eventChannelList = mutableListOf<SendChannel<Event>>()

    /** Map of waiting receivers of pongs (answers to pings) via a channel that gets null
     * or any thrown exception; access is synchronized on the [ApiV2] instance */
    private val pongReceivers: MutableMap<String, Channel<Exception?>> = mutableMapOf()

    /**
     * Get a receiver channel for WebSocket [Event]s that is decoupled from the [EventBus] system
     *
     * All WebSocket events are sent to the [EventBus] as well as to all channels
     * returned by this function, so it's possible to receive from any of these to
     * get the event. It's better to cancel the [ReceiveChannel] after usage, but cleanup
     * would also be carried out automatically asynchronously whenever events are sent.
     * Note that only raw WebSocket messages are put here, i.e. no processed [GameInfo]
     * or other large objects will be sent (the exception being [UpdateGameData], which
     * may grow pretty big, as in up to 500 KiB as base64-encoded string data).
     *
     * Use the channel returned by this function if the GL render thread, which is used
     * by the [EventBus] system, may not be available (e.g. in the Android turn checker).
     */
    fun getWebSocketEventChannel(): ReceiveChannel<Event> {
        // We're using CONFLATED channels here to avoid usage of possibly huge amounts of memory
        val c = Channel<Event>(capacity = CONFLATED)
        eventChannelList.add(c as SendChannel<Event>)
        return c
    }

    /**
     * Initialize this class (performing actual networking connectivity)
     *
     * It's recommended to set the credentials correctly in the first run, if possible.
     */
    suspend fun initialize(credentials: Pair<String, String>? = null) {
        if (compatibilityCheck == null) {
            isCompatible()
        }
        if (!isCompatible()) {
            Log.error("Incompatible API detected at '$baseUrl'! Further APIv2 usage will most likely break!")
        }

        if (credentials != null) {
            if (!auth.login(credentials.first, credentials.second, suppress = true)) {
                Log.debug("Login failed using provided credentials (username '${credentials.first}')")
            } else {
                lastSuccessfulAuthentication.set(Instant.now())
                Concurrency.run {
                    refreshGameDetails()
                }
            }
        }
        initialized = true
    }

    // ---------------- LIFECYCLE FUNCTIONALITY ----------------

    /**
     * Determine if the user is authenticated by comparing timestamps
     *
     * This method is not reliable. The server might have configured another session timeout.
     */
    fun isAuthenticated(): Boolean {
        return (lastSuccessfulAuthentication.get() != null && (lastSuccessfulAuthentication.get()!! + DEFAULT_SESSION_TIMEOUT) > Instant.now())
    }

    /**
     * Determine if this class has been fully initialized
     */
    fun isInitialized(): Boolean {
        return initialized
    }

    /**
     * Dispose this class and its children and jobs
     */
    override fun dispose() {
        disableReconnecting()
        sendChannel?.close()
        for (channel in eventChannelList) {
            channel.close()
        }
        for (job in websocketJobs) {
            job.cancel()
        }
        for (job in websocketJobs) {
            Concurrency.runBlocking {
                job.join()
            }
        }
        client.cancel()
    }

    // ---------------- COMPATIBILITY FUNCTIONALITY ----------------

    /**
     * Determine if the remote server is compatible with this API implementation
     *
     * This currently only checks the endpoints `/api/version` and `/api/v2/ws`.
     * If the first returns a valid [VersionResponse] and the second a valid
     * [ApiErrorResponse] for being not authenticated, then the server API
     * is most likely compatible. Otherwise, if 404 errors or other unexpected
     * responses are retrieved in both cases, the API is surely incompatible.
     *
     * This method won't raise any exception other than network-related.
     * It should be used to verify server URLs to determine the further handling.
     *
     * It caches its result once completed; set [update] for actually requesting.
     */
    suspend fun isCompatible(update: Boolean = false): Boolean {
        if (compatibilityCheck != null && !update) {
            return compatibilityCheck!!
        }

        val versionInfo = try {
            val r = client.get("api/version")
            if (!r.status.isSuccess()) {
                false
            } else {
                val b: VersionResponse = r.body()
                b.version == 2
            }
        } catch (_: IllegalArgumentException) {
            false
        } catch (e: Throwable) {
            Log.error("Unexpected exception calling version endpoint for '$baseUrl': $e")
            false
        }

        if (!versionInfo) {
            compatibilityCheck = false
            return false
        }

        val websocketSupport = try {
            val r = client.get("api/v2/ws")
            if (r.status.isSuccess()) {
                Log.error("Websocket endpoint from '$baseUrl' accepted unauthenticated request")
                false
            } else {
                val b: ApiErrorResponse = r.body()
                b.statusCode == ApiStatusCode.Unauthenticated
            }
        } catch (_: IllegalArgumentException) {
            false
        } catch (e: Throwable) {
            Log.error("Unexpected exception calling WebSocket endpoint for '$baseUrl': $e")
            false
        }

        compatibilityCheck = websocketSupport
        return websocketSupport
    }

    // ---------------- GAME-RELATED FUNCTIONALITY ----------------

    /**
     * Fetch server's details about a game based on its game ID
     *
     * @throws MultiplayerFileNotFoundException: if the [gameId] can't be resolved on the server
     */
    suspend fun getGameDetails(gameId: UUID): GameDetails {
        val result = gameDetails[gameId]
        if (result != null && result.refreshed + DEFAULT_CACHE_EXPIRY > Instant.now()) {
            return result.to()
        }
        refreshGameDetails()
        return gameDetails[gameId]?.to() ?: throw MultiplayerFileNotFoundException(null)
    }

    /**
     * Refresh the cache of known multiplayer games, [gameDetails]
     */
    private suspend fun refreshGameDetails() {
        val currentGames = game.list()!!
        for (entry in gameDetails.keys) {
            if (entry !in currentGames.map { it.gameUUID }) {
                gameDetails.remove(entry)
            }
        }
        for (g in currentGames) {
            gameDetails[g.gameUUID] = TimedGameDetails(Instant.now(), g.gameUUID, g.chatRoomUUID, g.gameDataID, g.name)
        }
    }

    // ---------------- WEBSOCKET FUNCTIONALITY ----------------

    /**
     * Send text as a [FrameType.TEXT] frame to the server via WebSocket (fire & forget)
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    @Suppress("Unused")
    internal suspend fun sendText(text: String, suppress: Boolean = false): Boolean {
        val channel = sendChannel
        if (channel == null) {
            Log.debug("No WebSocket connection, can't send text frame to server: '$text'")
            if (suppress) {
                return false
            } else {
                throw UncivNetworkException("WebSocket not connected", null)
            }
        }
        try {
            channel.send(Frame.Text(text))
        } catch (e: Throwable) {
            Log.debug("Sending text via WebSocket failed: %s\n%s", e.localizedMessage, e.stackTraceToString())
            if (!suppress) {
                throw UncivNetworkException(e)
            } else {
                return false
            }
        }
        return true
    }

    /**
     * Send a [FrameType.PING] frame to the server, without awaiting a response
     *
     * This operation might fail with some exception, e.g. network exceptions.
     * Internally, a random byte array of [size] will be used for the ping. It
     * returns true when sending worked as expected, false when there's no
     * send channel available and an exception otherwise.
     */
    private suspend fun sendPing(size: Int = 0): Boolean {
        val body = ByteArray(size)
        Random().nextBytes(body)
        return sendPing(body)
    }

    /**
     * Send a [FrameType.PING] frame with the specified content to the server, without awaiting a response
     *
     * This operation might fail with some exception, e.g. network exceptions.
     * It returns true when sending worked as expected, false when there's no
     * send channel available and an exception otherwise.
     */
    private suspend fun sendPing(content: ByteArray): Boolean {
        val channel = sendChannel
        return if (channel == null) {
            false
        } else {
            channel.send(Frame.Ping(content))
            true
        }
    }

    /**
     * Send a [FrameType.PONG] frame with the specified content to the server
     *
     * This operation might fail with some exception, e.g. network exceptions.
     * It returns true when sending worked as expected, false when there's no
     * send channel available and an exception otherwise.
     */
    private suspend fun sendPong(content: ByteArray): Boolean {
        val channel = sendChannel
        return if (channel == null) {
            false
        } else {
            channel.send(Frame.Pong(content))
            true
        }
    }

    /**
     * Send a [FrameType.PING] and await the response of a [FrameType.PONG]
     *
     * The function returns the delay between Ping and Pong in milliseconds.
     * Note that the function may never return if the Ping or Pong packets are lost on
     * the way, unless [timeout] is set. It will then return `null` if the [timeout]
     * of milliseconds was reached or the sending of the ping failed. Note that ensuring
     * this limit is on a best effort basis and may not be reliable, since it uses
     * [delay] internally to quit waiting for the result of the operation.
     *
     * This function may also throw arbitrary exceptions for network failures,
     * cancelled channels or other unexpected interruptions.
     */
    suspend fun awaitPing(size: Int = 2, timeout: Long? = null): Double? {
        require(size < 2) { "Size too small to identify ping responses uniquely" }
        val body = ByteArray(size)
        Random().nextBytes(body)

        val key = body.toHex()
        val channel = Channel<Exception?>(capacity = Channel.RENDEZVOUS)
        synchronized(this) {
            pongReceivers[key] = channel
        }

        var job: Job? = null
        if (timeout != null) {
            job = Concurrency.runOnNonDaemonThreadPool {
                delay(timeout)
                channel.close()
            }
        }

        try {
            return kotlin.system.measureNanoTime {
                if (!sendPing(body)) {
                    return null
                }
                // Using kotlinx.coroutines.runBlocking is fine here, since the caller should check for any
                // exceptions, as written in the docs -- i.e., no suppressing of exceptions is expected here
                val exception = kotlinx.coroutines.runBlocking { channel.receive() }
                job?.cancel()
                channel.close()
                if (exception != null) {
                    throw exception
                }
            }.toDouble() / 10e6
        } catch (_: ClosedReceiveChannelException) {
            return null
        } finally {
            synchronized(this) {
                pongReceivers.remove(key)
            }
        }
    }

    /**
     * Handler for incoming [FrameType.PONG] frames to make [awaitPing] work properly
     */
    private suspend fun onPong(content: ByteArray) {
        val receiver = synchronized(this) { pongReceivers[content.toHex()] }
        receiver?.send(null)
    }

    /**
     * Handle a newly established WebSocket connection
     */
    private suspend fun handleWebSocket(session: ClientWebSocketSession) {
        sendChannel?.close()
        sendChannel = session.outgoing

        websocketJobs.add(Concurrency.run {
            val currentChannel = session.outgoing
            while (sendChannel != null && currentChannel == sendChannel) {
                try {
                    sendPing()
                } catch (e: Exception) {
                    Log.debug("Failed to send WebSocket ping: %s", e.localizedMessage)
                    Concurrency.run {
                        if (reconnectWebSocket) {
                            websocket(::handleWebSocket)
                        }
                    }
                }
                delay(DEFAULT_WEBSOCKET_PING_FREQUENCY)
            }
            Log.debug("It looks like the WebSocket channel has been replaced")
        })

        try {
            while (true) {
                val incomingFrame = session.incoming.receive()
                when (incomingFrame.frameType) {
                    FrameType.PING -> {
                        sendPong(incomingFrame.data)
                    }
                    FrameType.PONG -> {
                        onPong(incomingFrame.data)
                    }
                    FrameType.CLOSE -> {
                        throw ClosedReceiveChannelException("Received CLOSE frame via WebSocket")
                    }
                    FrameType.BINARY -> {
                        Log.debug("Received binary packet of size %s which can't be parsed at the moment", incomingFrame.data.size)
                    }
                    FrameType.TEXT -> {
                        try {
                            val text = (incomingFrame as Frame.Text).readText()
                            val msg = Json.decodeFromString(WebSocketMessageSerializer(), text)
                            Log.debug("Incoming WebSocket message ${msg::class.java.canonicalName}: $msg")
                            when (msg.type) {
                                WebSocketMessageType.InvalidMessage -> {
                                    Log.debug("Received 'InvalidMessage' from WebSocket connection")
                                }
                                else -> {
                                    // Casting any message but InvalidMessage to WebSocketMessageWithContent should work,
                                    // otherwise the class hierarchy has been messed up somehow; all messages should have content
                                    Concurrency.runOnGLThread {
                                        EventBus.send((msg as WebSocketMessageWithContent).content)
                                    }
                                    for (c in eventChannelList) {
                                        Concurrency.run {
                                            try {
                                                c.send((msg as WebSocketMessageWithContent).content)
                                            } catch (_: ClosedSendChannelException) {
                                                delay(10)
                                                eventChannelList.remove(c)
                                            } catch (t: Throwable) {
                                                Log.debug("Sending event %s to event channel %s failed: %s", (msg as WebSocketMessageWithContent).content, c, t)
                                                delay(10)
                                                eventChannelList.remove(c)
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            Log.error("%s\n%s", e.localizedMessage, e.stackTraceToString())
                        }
                    }
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            Log.debug("The WebSocket channel was closed: $e")
            sendChannel?.close()
            session.close()
            session.flush()
            Concurrency.run {
                if (reconnectWebSocket) {
                    websocket(::handleWebSocket)
                }
            }
        } catch (e: CancellationException) {
            Log.debug("WebSocket coroutine was cancelled, closing connection: $e")
            sendChannel?.close()
            session.close()
            session.flush()
        } catch (e: Throwable) {
            Log.error("Error while handling a WebSocket connection: %s\n%s", e.localizedMessage, e.stackTraceToString())
            sendChannel?.close()
            session.close()
            session.flush()
            Concurrency.run {
                if (reconnectWebSocket) {
                    websocket(::handleWebSocket)
                }
            }
            throw e
        }
    }

    /**
     * Ensure that the WebSocket is connected (send a PING and build a new connection on failure)
     *
     * Use [jobCallback] to receive the newly created job handling the WS connection.
     * Note that this callback might not get called if no new WS connection was created.
     * It returns the measured round trip time in milliseconds if everything was fine.
     */
    suspend fun ensureConnectedWebSocket(timeout: Long = DEFAULT_WEBSOCKET_PING_TIMEOUT, jobCallback: ((Job) -> Unit)? = null): Double? {
        val pingMeasurement = try {
            awaitPing(timeout = timeout)
        } catch (e: Exception) {
            Log.debug("Error %s while ensuring connected WebSocket: %s", e, e.localizedMessage)
            null
        }
        if (pingMeasurement == null) {
            websocket(::handleWebSocket, jobCallback)
        }
        return pingMeasurement
    }

    // ---------------- SESSION FUNCTIONALITY ----------------

    /**
     * Perform post-login hooks and updates
     *
     * 1. Create a new WebSocket connection after logging in (ignoring existing sockets)
     * 2. Update the [UncivGame.Current.settings.multiplayer.userId]
     *    (this makes using APIv0/APIv1 games impossible if the user ID is not preserved!)
     */
    @Suppress("KDocUnresolvedReference")
    override suspend fun afterLogin() {
        enableReconnecting()
        val me = account.get(cache = false, suppress = true)
        if (me != null) {
            Log.error(
                "Updating user ID from %s to %s. This is no error. But you may need the old ID to be able to access your old multiplayer saves.",
                UncivGame.Current.settings.multiplayer.userId,
                me.uuid
            )
            UncivGame.Current.settings.multiplayer.userId = me.uuid.toString()
            UncivGame.Current.settings.save()
            ensureConnectedWebSocket()
        }
        super.afterLogin()
    }

    /**
     * Perform the post-logout hook, cancelling all WebSocket jobs and event channels
     */
    override suspend fun afterLogout(success: Boolean) {
        disableReconnecting()
        sendChannel?.close()
        if (success) {
            for (channel in eventChannelList) {
                channel.close()
            }
            for (job in websocketJobs) {
                job.cancel()
            }
        }
        super.afterLogout(success)
    }

    /**
     * Refresh the currently used session by logging in with username and password stored in the game settings
     *
     * Any errors are suppressed. Differentiating invalid logins from network issues is therefore impossible.
     *
     * Set [ignoreLastCredentials] to refresh the session even if there was no last successful credentials.
     */
    suspend fun refreshSession(ignoreLastCredentials: Boolean = false): Boolean {
        if (!ignoreLastCredentials) {
            return false
        }
        val success = auth.login(
            UncivGame.Current.settings.multiplayer.userName,
            UncivGame.Current.settings.multiplayer.passwords[UncivGame.Current.onlineMultiplayer.multiplayerServer.serverUrl] ?: "",
            suppress = true
        )
        if (success) {
            lastSuccessfulAuthentication.set(Instant.now())
        }
        return success
    }

    /**
     * Enable auto re-connect attempts for the WebSocket connection
     */
    fun enableReconnecting() {
        reconnectWebSocket = true
    }

    /**
     * Disable auto re-connect attempts for the WebSocket connection
     */
    fun disableReconnecting() {
        reconnectWebSocket = false
    }
}

/**
 * Small struct to store the most relevant details about a game, useful for caching
 *
 * Note that those values may become invalid (especially the [dataID]), so use it only for
 * caching for short durations. The [chatRoomUUID] may be valid longer (up to the game's lifetime).
 */
data class GameDetails(val gameUUID: UUID, val chatRoomUUID: UUID, val dataID: Long, val name: String)

/**
 * Holding the same values as [GameDetails], but with an instant determining the last refresh
 */
private data class TimedGameDetails(val refreshed: Instant, val gameUUID: UUID, val chatRoomUUID: UUID, val dataID: Long, val name: String) {
    fun to() = GameDetails(gameUUID, chatRoomUUID, dataID, name)
}

/**
 * Convert a byte array to a hex string
 */
private fun ByteArray.toHex(): String {
    return this.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
}
