/**
 * TODO: Comment this file
 */

package com.unciv.logic.multiplayer.api

import com.unciv.UncivGame
import com.unciv.utils.concurrency.Concurrency
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
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

/**
 * API wrapper around the newly implemented REST API for multiplayer game handling
 *
 * Note that this does not include the handling of messages via the
 * WebSocket connection, but rather only the pure HTTP-based API.
 * Almost any method may throw certain OS or network errors as well as the
 * [ApiErrorResponse] for invalid requests (4xx) or server failures (5xx).
 */
class Api(val baseUrl: String) {
    private val logger = java.util.logging.Logger.getLogger(this::class.qualifiedName)

    // HTTP client to handle the server connections, logging, content parsing and cookies
    private val client = HttpClient(CIO) {
        // Do not add install(HttpCookies) because it will break Cookie handling
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        install(WebSockets) {
            pingInterval = 15_000
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
        defaultRequest {
            url(baseUrl)
        }
    }

    private val authCookieHelper = AuthCookieHelper()

    private var websocketJobs = ConcurrentLinkedQueue<Job>()

    init {
        client.plugin(HttpSend).intercept { request ->
            request.userAgent("Unciv/${UncivGame.VERSION.toNiceString()}-GNU-Terry-Pratchett")
            execute(request)
        }
    }

    /**
     * API for account management
     */
    val accounts = AccountsApi(client, authCookieHelper, logger)

    /**
     * API for authentication management
     */
    val auth = AuthApi(client, authCookieHelper, logger)

    /**
     * API for friendship management
     */
    val friend = FriendApi(client, authCookieHelper, logger)

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
     */
    suspend fun websocket(): Boolean {
        logger.info("Starting a new WebSocket connection ...")

        coroutineScope {
            try {
                val session = client.webSocketSession {
                    method = HttpMethod.Get
                    authCookieHelper.add(this)
                    url {
                        takeFrom(baseUrl)
                        protocol =
                                URLProtocol.WS  // TODO: Verify that secure WebSockets (WSS) work as well
                        path("/api/v2/ws")
                    }
                }
                val job = Concurrency.runOnNonDaemonThreadPool {
                    launch {
                        handleWebSocketSession(session)
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

}
