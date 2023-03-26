package com.unciv.logic.multiplayer.apiv2

import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.storage.ApiV2FileStorageEmulator
import com.unciv.logic.multiplayer.ApiVersion
import com.unciv.logic.multiplayer.MultiplayerGameAdded
import com.unciv.logic.multiplayer.storage.ApiV2FileStorageWrapper
import com.unciv.utils.Log
import com.unciv.utils.concurrency.withGLContext
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** Default session timeout expected from multiplayer servers (unreliable) */
private val DEFAULT_SESSION_TIMEOUT = Duration.ofSeconds(15 * 60)

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
    private var isInitialized = false

    /** Credentials used during the last successful login */
    private var lastSuccessfulCredentials: Pair<String, String>? = null

    /** Timestamp of the last successful login */
    private var lastSuccessfulAuthentication: AtomicReference<Instant?> = AtomicReference()

    /** User identification on the server */
    private var user: AccountResponse? = null

    /**
     * Initialize this class (performing actual networking connectivity)
     *
     * It's recommended to set the credentials correctly
     */
    suspend fun initialize(credentials: Pair<String, String>? = null) {
        if (compatibilityCheck == null) {
            isCompatible()
        }
        if (!isCompatible()) {
            Log.error("Incompatible API detected at '$baseUrl'! Further APIv2 usage will most likely break!")
        }

        if (credentials != null) {
            if (!auth.login(credentials.first, credentials.second)) {
                Log.debug("Login failed using stored credentials")
            } else {
                lastSuccessfulAuthentication.set(Instant.now())
                lastSuccessfulCredentials = credentials
                user = account.get()
                websocket(::handleWebSocket)
            }
        }
        ApiV2FileStorageWrapper.storage = ApiV2FileStorageEmulator(this)
        ApiV2FileStorageWrapper.api = this
        isInitialized = true
    }

    // ---------------- SIMPLE GETTER ----------------

    /**
     * Determine if the user is authenticated by comparing timestamps
     *
     * This method is not reliable. The server might have configured another session timeout.
     */
    fun isAuthenticated(): Boolean {
        return (lastSuccessfulAuthentication.get() != null && (lastSuccessfulAuthentication.get()!! + DEFAULT_SESSION_TIMEOUT) > Instant.now())
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

    // ---------------- WEBSOCKET FUNCTIONALITY ----------------

    /**
     * Handle existing WebSocket connections
     *
     * This method should be dispatched to a daemon thread pool executor.
     */
    private suspend fun handleWebSocketSession(session: ClientWebSocketSession) {
        try {
            val incomingMessage = session.incoming.receive()

            Log.debug("Incoming WebSocket message: $incomingMessage")
            if (incomingMessage.frameType == FrameType.PING) {
                session.send(
                    Frame.byType(
                        false,
                        FrameType.PONG,
                        byteArrayOf(),
                        rsv1 = true,
                        rsv2 = true,
                        rsv3 = true
                    )
                )
            }
        } catch (e: ClosedReceiveChannelException) {
            Log.debug("The WebSocket channel was unexpectedly closed: $e")
        }
    }

    /**
     * Send text as a [FrameType.TEXT] frame to the remote side (fire & forget)
     *
     * Returns [Unit] if no exception is thrown
     */
    internal suspend fun sendText(text: String): Unit {
        if (sendChannel == null) {
            Log.debug("No WebSocket connection, can't send text frame to server: '$text'")
            return
        }
        try {
            sendChannel?.send(Frame.Text(text))
        } catch (e: Throwable) {
            Log.debug("%s\n%s", e.localizedMessage, e.stackTraceToString())
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
            Log.debug("No WebSocket connection, can't send text frame to server: '$text'")
            return false
        }
        try {
            sendChannel!!.send(Frame.Text(text))
        } catch (e: Throwable) {
            Log.debug("%s\n%s", e.localizedMessage, e.stackTraceToString())
        }
        return true
    }

    /**
     * Handle incoming WebSocket messages
     */
    private suspend fun handleIncomingWSMessage(msg: WebSocketMessage) {
        when (msg.type) {
            WebSocketMessageType.InvalidMessage -> {
                Log.debug("Received invalid message from WebSocket connection")
            }
            WebSocketMessageType.FinishedTurn -> {
                // This message type is not meant to be received from the server
                Log.debug("Received FinishedTurn message from WebSocket connection")
            }
            WebSocketMessageType.UpdateGameData -> {
                // TODO: The body of this message contains a whole game state, so we need to unpack and use it here
                withGLContext {
                    EventBus.send(MultiplayerGameAdded(game.name))
                }
            }
            WebSocketMessageType.ClientDisconnected -> {
                Log.debug("Received ClientDisconnected message from WebSocket connection")
                // TODO: Implement client connectivity handling
            }
            WebSocketMessageType.ClientReconnected -> {
                Log.debug("Received ClientReconnected message from WebSocket connection")
                // TODO: Implement client connectivity handling
            }
            WebSocketMessageType.IncomingChatMessage -> {
                Log.debug("Received IncomingChatMessage message from WebSocket connection")
                // TODO: Implement chat message handling
            }
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
                        // This handler won't handle control frames
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
                            handleIncomingWSMessage(msg)
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
            Log.error("%s\n%s", e.localizedMessage, e.stackTraceToString())
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
     */
    suspend fun refreshSession(): Boolean {
        if (lastSuccessfulCredentials == null) {
            return false
        }
        val success = try {
            auth.login(lastSuccessfulCredentials!!.first, lastSuccessfulCredentials!!.second)
        } catch (e: Throwable) {
            Log.error("Suppressed error in 'refreshSession': $e")
            false
        }
        if (success) {
            lastSuccessfulAuthentication.set(Instant.now())
        }
        return success
    }

}
