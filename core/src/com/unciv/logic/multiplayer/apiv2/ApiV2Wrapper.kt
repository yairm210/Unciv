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
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentLinkedQueue

internal const val LOBBY_MAX_PLAYERS = 34

/**
 * API wrapper around the newly implemented REST API for multiplayer game handling
 *
 * Note that this class does not include the handling of messages via the
 * WebSocket connection, but rather only the pure HTTP-based API.
 * Almost any method may throw certain OS or network errors as well as the
 * [ApiErrorResponse] for invalid requests (4xx) or server failures (5xx).
 *
 * This class should be considered implementation detail, since it just
 * abstracts HTTP endpoint names from other modules in this package.
 * Use the [ApiV2] class for public methods to interact with the server.
 */
open class ApiV2Wrapper(private val baseUrl: String) {

    // HTTP client to handle the server connections, logging, content parsing and cookies
    internal val client = HttpClient(CIO) {
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
    internal val account = AccountsApi(client, authCookieHelper)

    /**
     * API for authentication management
     */
    internal val auth = AuthApi(client, authCookieHelper)

    /**
     * API for chat management
     */
    internal val chat = ChatApi(client, authCookieHelper)

    /**
     * API for friendship management
     */
    internal val friend = FriendApi(client, authCookieHelper)

    /**
     * API for game management
     */
    internal val game = GameApi(client, authCookieHelper)

    /**
     * API for invite management
     */
    internal val invite = InviteApi(client, authCookieHelper)

    /**
     * API for lobby management
     */
    internal val lobby = LobbyApi(client, authCookieHelper)

    /**
     * Start a new WebSocket connection
     *
     * The parameter [handler] is a coroutine that will be fed the established
     * [ClientWebSocketSession] on success at a later point. Note that this
     * method does instantly return, detaching the creation of the WebSocket.
     * The [handler] coroutine might not get called, if opening the WS fails.
     */
    internal suspend fun websocket(handler: suspend (ClientWebSocketSession) -> Unit): Boolean {
        Log.debug("Starting a new WebSocket connection ...")

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
                    handler(session)
                }
                websocketJobs.add(job)
                Log.debug("A new WebSocket has been created, running in job $job")
            } catch (e: SerializationException) {
                Log.debug("Failed to create a WebSocket: $e")
                return@coroutineScope false
            }
        }

        return true
    }

    /**
     * Retrieve the currently available API version of the connected server
     */
    internal suspend fun version(): VersionResponse {
        return client.get("/api/version").body()
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
