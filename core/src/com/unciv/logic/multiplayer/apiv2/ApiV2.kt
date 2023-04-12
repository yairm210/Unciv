package com.unciv.logic.multiplayer.apiv2

import com.unciv.UncivGame
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.storage.ApiV2FileStorageEmulator
import com.unciv.logic.multiplayer.ApiVersion
import com.unciv.logic.multiplayer.storage.ApiV2FileStorageWrapper
import com.unciv.logic.multiplayer.storage.MultiplayerFileNotFoundException
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/** Default session timeout expected from multiplayer servers (unreliable) */
private val DEFAULT_SESSION_TIMEOUT = Duration.ofSeconds(15 * 60)

/** Default cache expiry timeout to indicate that certain data needs to be re-fetched */
private val DEFAULT_CACHE_EXPIRY = Duration.ofSeconds(30 * 60)

/**
 * Main class to interact with multiplayer servers implementing [ApiVersion.ApiV2]
 */
class ApiV2(private val baseUrl: String) : ApiV2Wrapper(baseUrl) {

    /** Cache the result of the last server API compatibility check */
    private var compatibilityCheck: Boolean? = null

    /** Channel to send frames via WebSocket to the server, may be null
     * for unsupported servers or unauthenticated/uninitialized clients */
    private var sendChannel: SendChannel<Frame>? = null

    /** Info whether this class is fully initialized and ready to use */
    private var initialized = false

    /** Timestamp of the last successful login */
    private var lastSuccessfulAuthentication: AtomicReference<Instant?> = AtomicReference()

    /** Cache for the game details to make certain lookups faster */
    private val gameDetails: MutableMap<UUID, TimedGameDetails> = mutableMapOf()

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
        ApiV2FileStorageWrapper.storage = ApiV2FileStorageEmulator(this)
        ApiV2FileStorageWrapper.api = this
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
    fun dispose() {
        sendChannel?.close()
        for (job in websocketJobs) {
            job.cancel()
        }
        client.cancel()
    }

    // ---------------- COMPATIBILITY FUNCTIONALITY ----------------

    /**
     * Determine if the remote server is compatible with this API implementation
     *
     * This currently only checks the endpoints /api/version and /api/v2/ws.
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
            val r = client.get("/api/version")
            if (!r.status.isSuccess()) {
                false
            } else {
                val b: VersionResponse = r.body()
                b.version == 2
            }
        } catch (e: IllegalArgumentException) {
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
            val r = client.get("/api/v2/ws")
            if (r.status.isSuccess()) {
                Log.error("Websocket endpoint from '$baseUrl' accepted unauthenticated request")
                false
            } else {
                val b: ApiErrorResponse = r.body()
                b.statusCode == ApiStatusCode.Unauthenticated
            }
        } catch (e: IllegalArgumentException) {
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
     * Internally, a random 8-byte array will be used for the ping. It returns
     * true when sending worked as expected, false when there's no send channel
     * available and an any exception otherwise.
     */
    internal suspend fun sendPing(): Boolean {
        val body = ByteArray(8)
        Random().nextBytes(body)
        val channel = sendChannel
        return if (channel == null) {
            false
        } else {
            channel.send(Frame.Ping(body))
            true
        }
    }

    /**
     * Create a new WebSocket connection after logging in and if there's no current connection available
     */
    override suspend fun afterLogin() {
        val pingSuccess = try {
            sendPing()
        } catch (e: Exception) {
            Log.debug("Exception while sending WebSocket PING: %s", e.localizedMessage)
            false
        }
        if (!pingSuccess) {
            websocket(::handleWebSocket)
        }
    }

    /**
     * Handle a newly established WebSocket connection
     */
    private suspend fun handleWebSocket(session: ClientWebSocketSession) {
        sendChannel?.close()
        sendChannel = session.outgoing

        try {
            while (true) {
                val incomingFrame = session.incoming.receive()
                when (incomingFrame.frameType) {
                    FrameType.CLOSE, FrameType.PING, FrameType.PONG -> {
                        // This WebSocket handler won't handle control frames
                        Log.debug("Received CLOSE, PING or PONG as message")
                    }
                    FrameType.BINARY -> {
                        Log.debug("Received binary packet which can't be parsed at the moment")
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
                                    Log.debug("Sending event %s with content %s", msg, (msg as WebSocketMessageWithContent).content)
                                    Concurrency.runOnGLThread {
                                        EventBus.send(msg.content)
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
        } catch (e: Throwable) {
            Log.error("Error while handling a WebSocket connection: %s\n%s", e.localizedMessage, e.stackTraceToString())
            sendChannel?.close()
            session.close()
            throw e
        }
    }

    // ---------------- SESSION FUNCTIONALITY ----------------

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
            UncivGame.Current.settings.multiplayer.passwords[UncivGame.Current.onlineMultiplayer.baseUrl] ?: "",
            suppress = true
        )
        if (success) {
            lastSuccessfulAuthentication.set(Instant.now())
        }
        return success
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
