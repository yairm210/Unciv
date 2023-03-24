/**
 * TODO: Comment this file
 */

package com.unciv.logic.multiplayer.apiv2

import com.unciv.UncivGame
import com.unciv.logic.UncivShowableException
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentLinkedQueue

internal const val LOBBY_MAX_PLAYERS = 34

/**
 * API wrapper around the newly implemented REST API for multiplayer game handling
 *
 * Note that this does not include the handling of messages via the
 * WebSocket connection, but rather only the pure HTTP-based API.
 * Almost any method may throw certain OS or network errors as well as the
 * [ApiErrorResponse] for invalid requests (4xx) or server failures (5xx).
 *
 * This class should be considered implementation detail, since it just
 * abstracts HTTP endpoint names from other modules in this package.
 */
class ApiV2Wrapper(private val baseUrl: String) {
    private val logger = java.util.logging.Logger.getLogger(this::class.qualifiedName)

    // HTTP client to handle the server connections, logging, content parsing and cookies
    private val client = HttpClient(CIO) {
        // Do not add install(HttpCookies) because it will break Cookie handling
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        install(WebSockets) {
            pingInterval = 90_000
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
        defaultRequest {
            url(baseUrl)
        }
    }

    // Cache the result of the last server API compatibility check
    private var compatibilityCheck: Boolean? = null

    // Helper that replaces library cookie storages to fix cookie serialization problems
    private val authCookieHelper = AuthCookieHelper()

    // Queue to keep references to all opened WebSocket handler jobs
    private var websocketJobs = ConcurrentLinkedQueue<Job>()

    init {
        client.plugin(HttpSend).intercept { request ->
            request.userAgent("Unciv/${UncivGame.VERSION.toNiceString()}-GNU-Terry-Pratchett")
            val clientCall = execute(request)
            Log.debug(
                "'%s %s%s': %s (%d ms)",
                request.method.value,
                if (baseUrl.endsWith("/")) baseUrl.subSequence(0, baseUrl.length - 2) else baseUrl,
                request.url.encodedPath,
                clientCall.response.status,
                clientCall.response.responseTime.timestamp - clientCall.response.requestTime.timestamp
            )
            clientCall
        }
    }

    /**
     * API for account management
     */
    val account = AccountsApi(client, authCookieHelper, logger)

    /**
     * API for authentication management
     */
    val auth = AuthApi(client, authCookieHelper, logger)

    /**
     * API for chat management
     */
    val chat = ChatApi(client, authCookieHelper, logger)

    /**
     * API for friendship management
     */
    val friend = FriendApi(client, authCookieHelper, logger)

    /**
     * API for game management
     */
    val game = GameApi(client, authCookieHelper, logger)

    /**
     * API for invite management
     */
    val invite = InviteApi(client, authCookieHelper, logger)

    /**
     * API for lobby management
     */
    val lobby = LobbyApi(client, authCookieHelper, logger)

    /**
     * Handle existing WebSocket connections
     *
     * This method should be dispatched to a non-daemon thread pool executor.
     */
    private suspend fun handleWebSocketSession(session: ClientWebSocketSession) {
        try {
            val incomingMessage = session.incoming.receive()

            logger.info("Incoming message: $incomingMessage")
            if (incomingMessage.frameType == FrameType.PING) {
                logger.info("Received PING frame")
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
            logger.severe("The channel was closed: $e")
        }
    }

    /**
     * Start a new WebSocket connection
     *
     * The parameter [handler] is a coroutine that will be fed the established
     * [ClientWebSocketSession] on success at a later point. Note that this
     * method does instantly return, detaching the creation of the WebSocket.
     * The [handler] coroutine might not get called, if opening the WS fails.
     */
    suspend fun websocket(handler: suspend (ClientWebSocketSession) -> Unit): Boolean {
        logger.info("Starting a new WebSocket connection ...")

        coroutineScope {
            try {
                val session = client.webSocketSession {
                    method = HttpMethod.Get
                    authCookieHelper.add(this)
                    url {
                        takeFrom(baseUrl)
                        protocol = URLProtocol.WS  // TODO: Verify that secure WebSockets (WSS) work as well
                        path("/api/v2/ws")
                    }
                }
                val job = Concurrency.runOnNonDaemonThreadPool {
                    launch {
                        handler(session)
                    }
                }
                websocketJobs.add(job)
                logger.info("A new WebSocket has been created, running in job $job")
            } catch (e: SerializationException) {
                logger.warning("Failed to create a WebSocket: $e")
                return@coroutineScope false
            }
        }

        return true
    }

    /**
     * Retrieve the currently available API version of the connected server
     */
    suspend fun version(): VersionResponse {
        return client.get("/api/version").body()
    }

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
     */
    suspend fun isServerCompatible(): Boolean {
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
            logger.warning("Unexpected exception calling '$baseUrl': $e")
            false
        }

        if (!versionInfo) {
            compatibilityCheck = false
            return false
        }

        val websocketSupport = try {
            val r = client.get("/api/v2/ws")
            if (r.status.isSuccess()) {
                logger.severe("Websocket endpoint from '$baseUrl' accepted unauthenticated request")
                false
            } else {
                val b: ApiErrorResponse = r.body()
                b.statusCode == ApiStatusCode.Unauthenticated
            }
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: Throwable) {
            logger.warning("Unexpected exception calling '$baseUrl': $e")
            false
        }

        compatibilityCheck = websocketSupport
        return websocketSupport
    }

    /**
     * Getter for [compatibilityCheck]
     */
    fun getCompatibilityCheck(): Boolean? {
        return compatibilityCheck
    }

}

/**
 * APIv2 exception class that is compatible with [UncivShowableException]
 */
class ApiException(val error: ApiErrorResponse) : UncivShowableException(lookupErrorMessage(error.statusCode))

/**
 * Convert an API status code to a string that can be translated and shown to users
 */
private fun lookupErrorMessage(statusCode: ApiStatusCode): String {
    // TODO: Implement translations
    return statusCode.name
}
