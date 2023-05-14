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
            request.userAgent("Unciv/${UncivGame.VERSION.toNiceString()}-GNU-Terry-Pratchett")
            val clientCall = execute(request)
            Log.debug(
                "'%s %s%s%s': %s (%d ms)",
                request.method.value,
                baseServer,
                if (baseServer.endsWith("/") or request.url.encodedPath.startsWith("/")) "" else "/",
                request.url.encodedPath,
                clientCall.response.status,
                clientCall.response.responseTime.timestamp - clientCall.response.requestTime.timestamp
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
     * API for account management
     */
    val account = AccountsApi(client, authHelper)

    /**
     * API for authentication management
     */
    val auth = AuthApi(client, authHelper, ::afterLogin)

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
     */
    internal suspend fun websocket(handler: suspend (ClientWebSocketSession) -> Unit): Boolean {
        Log.debug("Starting a new WebSocket connection ...")

        coroutineScope {
            try {
                val session = client.webSocketSession {
                    method = HttpMethod.Get
                    authHelper.add(this)
                    url {
                        appendPathSegments("api/v2/ws")
                    }
                }
                val job = Concurrency.run {
                    handler(session)
                }
                websocketJobs.add(job)
                Log.debug("A new WebSocket has been created, running in job $job")
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
class ApiException(val error: ApiErrorResponse) : UncivShowableException(lookupErrorMessage(error.statusCode))

/**
 * Convert an API status code to a string that can be translated and shown to users
 */
private fun lookupErrorMessage(statusCode: ApiStatusCode): String {
    return when (statusCode) {
        ApiStatusCode.Unauthenticated -> "You are not logged in. Please login first."
        ApiStatusCode.NotFound -> "The operation couldn't be completed, since the resource was not found."
        ApiStatusCode.InvalidContentType -> "The media content type was invalid. Please report this as a bug."
        ApiStatusCode.InvalidJson -> "The server didn't understand the sent data. Please report this as a bug."
        ApiStatusCode.PayloadOverflow -> "The amount of data sent to the server was too large. Please report this as a bug."
        ApiStatusCode.LoginFailed -> "The login failed. Is the username and password correct?"
        ApiStatusCode.UsernameAlreadyOccupied -> "The selected username is already taken. Please choose another name."
        ApiStatusCode.InvalidPassword -> "This password is not valid. Please choose another password."
        ApiStatusCode.EmptyJson -> "The server encountered an empty JSON problem. Please report this as a bug."
        ApiStatusCode.InvalidUsername -> "The username is not valid. Please choose another one."
        ApiStatusCode.InvalidDisplayName -> "The display name is not valid. Please choose another one."
        ApiStatusCode.FriendshipAlreadyRequested -> "You have already requested friendship with this player. Please wait until the request is accepted."
        ApiStatusCode.AlreadyFriends -> "You are already friends, you can't request it again."
        ApiStatusCode.MissingPrivileges -> "You don't have the required privileges to perform this operation."
        ApiStatusCode.InvalidMaxPlayersCount -> "The maximum number of players for this lobby is out of the supported range for this server. Please adjust the number. Two players should always work."
        ApiStatusCode.AlreadyInALobby -> "You are already in another lobby. You need to close or leave the other lobby before."
        ApiStatusCode.InvalidUuid -> "The operation could not be completed, since an invalid UUID was given. Please retry later or restart the game. If the problem persists, please report this as a bug."
        ApiStatusCode.InvalidLobbyUuid -> "The lobby was not found. Maybe it has already been closed?"
        ApiStatusCode.InvalidFriendUuid -> "You must be friends with the other player before this action can be completed. Try again later."
        ApiStatusCode.GameNotFound -> "The game was not found on the server. Try again later. If the problem persists, the game was probably already removed from the server, sorry."
        ApiStatusCode.InvalidMessage -> "This message could not be sent, since it was invalid. Remove any invalid characters and try again."
        ApiStatusCode.WsNotConnected -> "The WebSocket is not available. Please restart the game and try again. If the problem persists, please report this as a bug."
        ApiStatusCode.LobbyFull -> "The lobby is currently full. You can't join right now."
        ApiStatusCode.InvalidPlayerUUID -> "The ID of the player was invalid. Does the player exist? Please try again. If the problem persists, please report this as a bug."
        ApiStatusCode.InternalServerError -> "Internal server error. Please report this as a bug."
        ApiStatusCode.DatabaseError -> "Internal server database error. Please report this as a bug."
        ApiStatusCode.SessionError -> "Internal session error. Please report this as a bug."
    }
}
