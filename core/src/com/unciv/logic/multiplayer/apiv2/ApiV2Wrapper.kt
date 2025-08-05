package com.unciv.logic.multiplayer.apiv2

import com.unciv.UncivGame
import com.unciv.logic.UncivShowableException
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.plugins.websocket.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentLinkedQueue

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
open class ApiV2Wrapper(baseUrl: String) {
    private val baseUrlImpl: String = if (baseUrl.endsWith("/")) baseUrl else ("$baseUrl/")
    private val baseServer = URLBuilder(baseUrl).apply {
        encodedPath = ""
        encodedParameters = ParametersBuilder()
        fragment = ""
    }.toString()

    // HTTP client to handle the server connections, logging, content parsing and cookies
    internal val client = HttpClient(CIO) {
        // Do not add install(HttpCookies) because it will break Cookie handling
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = DEFAULT_REQUEST_TIMEOUT
            connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT
        }
        install(WebSockets) {
            // Pings are configured manually to enable re-connecting automatically, don't use `pingInterval`
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
        defaultRequest {
            url(baseUrlImpl)
        }
    }

    /** Helper that replaces library cookie storages to fix cookie serialization problems and keeps
     * track of user-supplied credentials to be able to refresh expired sessions on the fly */
    private val authHelper = AuthHelper()

    /** Queue to keep references to all opened WebSocket handler jobs */
    protected var websocketJobs = ConcurrentLinkedQueue<Job>()

    init {
        client.plugin(HttpSend).intercept { request ->
            request.userAgent(UncivGame.getUserAgent())
            val clientCall = try {
                execute(request)
            } catch (t: Throwable) {
                Log.error("Failed to query API: %s %s\nURL: %s\nError %s:\n%s", request.method.value, request.url.encodedPath, request.url, t.localizedMessage, t.stackTraceToString())
                throw t
            }
            Log.debug(
                "'%s %s': %s (%d ms%s)",
                request.method.value,
                request.url.toString(),
                clientCall.response.status,
                clientCall.response.responseTime.timestamp - clientCall.response.requestTime.timestamp,
                if (!request.url.protocol.isSecure()) ", insecure!" else ""
            )
            clientCall
        }
    }

    /**
     * Coroutine directly executed after every successful login to the server,
     * which also refreshed the session cookie (i.e., not [AuthApi.loginOnly]).
     * This coroutine should not raise any unhandled exceptions, because otherwise
     * the login function will fail as well. If it requires longer operations,
     * those operations should be detached from the current thread.
     */
    protected open suspend fun afterLogin() {}

    /**
     * Coroutine directly executed after every attempt to logout from the server.
     * The parameter [success] determines whether logging out completed successfully,
     * i.e. this coroutine will also be called in the case of an error.
     * This coroutine should not raise any unhandled exceptions, because otherwise
     * the login function will fail as well. If it requires longer operations,
     * those operations should be detached from the current thread.
     */
    protected open suspend fun afterLogout(success: Boolean) {}

    /**
     * API for account management
     */
    val account = AccountsApi(client, authHelper)

    /**
     * API for authentication management
     */
    val auth = AuthApi(client, authHelper, ::afterLogin, ::afterLogout)

    /**
     * API for chat management
     */
    val chat = ChatApi(client, authHelper)

    /**
     * API for friendship management
     */
    val friend = FriendApi(client, authHelper)

    /**
     * API for game management
     */
    val game = GameApi(client, authHelper)

    /**
     * API for invite management
     */
    val invite = InviteApi(client, authHelper)

    /**
     * API for lobby management
     */
    val lobby = LobbyApi(client, authHelper)

    /**
     * Start a new WebSocket connection
     *
     * The parameter [handler] is a coroutine that will be fed the established
     * [ClientWebSocketSession] on success at a later point. Note that this
     * method does instantly return, detaching the creation of the WebSocket.
     * The [handler] coroutine might not get called, if opening the WS fails.
     * Use [jobCallback] to receive the newly created job handling the WS connection.
     */
    internal suspend fun websocket(handler: suspend (ClientWebSocketSession) -> Unit, jobCallback: ((Job) -> Unit)? = null): Boolean {
        Log.debug("Starting a new WebSocket connection ...")

        coroutineScope {
            try {
                val session = client.webSocketRawSession {
                    authHelper.add(this)
                    url {
                        protocol = if (Url(baseServer).protocol.isSecure()) URLProtocol.WSS else URLProtocol.WS
                        port = Url(baseServer).specifiedPort.takeUnless { it == DEFAULT_PORT } ?: protocol.defaultPort
                        appendPathSegments("api/v2/ws")
                    }
                }
                val job = Concurrency.runOnNonDaemonThreadPool {
                    handler(session)
                }
                websocketJobs.add(job)
                Log.debug("A new WebSocket has been created, running in job $job")
                if (jobCallback != null) {
                    jobCallback(job)
                }
                true
            } catch (e: SerializationException) {
                Log.debug("Failed to create a WebSocket: %s", e.localizedMessage)
                return@coroutineScope false
            } catch (e: Exception) {
                Log.debug("Failed to establish WebSocket connection: %s", e.localizedMessage)
                return@coroutineScope false
            }
        }

        return true
    }

    /**
     * Retrieve the currently available API version of the connected server
     *
     * Unlike other API endpoint implementations, this function does not handle
     * any errors or retries on failure. You must wrap any call in a try-except
     * clause expecting any type of error. The error may not be appropriate to
     * be shown to end users, i.e. it's definitively no [UncivShowableException].
     */
    suspend fun version(): VersionResponse {
        return client.get("api/version").body()
    }

}

/**
 * APIv2 exception class that is compatible with [UncivShowableException]
 */
class ApiException(val error: ApiErrorResponse) : UncivShowableException(error.statusCode.message)
