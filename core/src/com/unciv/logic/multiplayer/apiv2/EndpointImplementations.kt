/**
 * Collection of endpoint implementations
 *
 * Those classes are not meant to be used directly. Take a look at the Api class for common usage.
 */

package com.unciv.logic.multiplayer.apiv2

import com.unciv.utils.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.request.request
import io.ktor.client.statement.*
import io.ktor.client.statement.request
import io.ktor.http.*
import io.ktor.util.network.*
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * List of HTTP status codes which are considered to [ApiErrorResponse]s by the specification
 */
internal val ERROR_CODES = listOf(HttpStatusCode.BadRequest, HttpStatusCode.InternalServerError)

/**
 * List of API status codes that should be re-executed after session refresh, if possible
 */
private val RETRY_CODES = listOf(ApiStatusCode.Unauthenticated)

/**
 * Default value for randomly generated passwords
 */
private const val DEFAULT_RANDOM_PASSWORD_LENGTH = 32

/**
 * Max age of a cached entry before it will be re-queried
 */
private val MAX_CACHE_AGE = Duration.ofSeconds(60)

/**
 * Perform a HTTP request via [method] to [endpoint]
 *
 * Use [refine] to change the [HttpRequestBuilder] after it has been prepared with the method
 * and path. Do not edit the cookie header or the request URL, since they might be overwritten.
 * If [suppress] is set, it will return null instead of throwing any exceptions.
 * This function retries failed requests after executing coroutine [retry] which will be passed
 * the same arguments as the [request] coroutine, if it is set and the request failed due to
 * network or defined API errors, see [RETRY_CODES]. It should return a [Boolean] which determines
 * if the original request should be retried after finishing [retry]. For example, to silently
 * repeat a request on such failure, use such coroutine: suspend { true }
 *
 * @throws ApiException: thrown for defined and recognized API problems
 * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
 */
private suspend fun request(
    method: HttpMethod,
    endpoint: String,
    client: HttpClient,
    authHelper: AuthHelper,
    refine: ((HttpRequestBuilder) -> Unit)? = null,
    suppress: Boolean = false,
    retry: (suspend () -> Boolean)? = null
): HttpResponse? {
    val builder = HttpRequestBuilder()
    builder.method = method
    if (refine != null) {
        refine(builder)
    }
    builder.url { path(endpoint) }
    authHelper.add(builder)

    // Perform the request, but handle network issues gracefully according to the specified exceptions
    val response = try {
        client.request(builder)
    } catch (e: Throwable) {
        when (e) {
            // This workaround allows to catch multiple exception types at the same time
            // See https://youtrack.jetbrains.com/issue/KT-7128 if you want this feature in Kotlin :)
            is IOException, is UnresolvedAddressException -> {
                val shouldRetry = if (retry != null) {
                    Log.debug("Calling retry coroutine %s for network error %s in '%s %s'", retry, e, method, endpoint)
                    retry()
                } else {
                    false
                }
                return if (shouldRetry) {
                    Log.debug("Retrying after network error %s: %s (cause: %s)", e, e.message, e.cause)
                    request(method, endpoint, client, authHelper,
                        refine = refine,
                        suppress = suppress,
                        retry = null
                    )
                } else if (suppress) {
                    Log.debug("Suppressed network error %s: %s (cause: %s)", e, e.message, e.cause)
                    null
                } else {
                    Log.debug("Throwing network error %s: %s (cause: %s)", e, e.message, e.cause)
                    throw UncivNetworkException(e)
                }
            }
            else -> throw e
        }
    }

    // For HTTP errors defined in the API, throwing an ApiException would be the correct handling.
    // Therefore, try to de-serialize the response as ApiErrorResponse first. If it happens to be
    // an authentication failure, the request could be retried as well. Otherwise, throw the error.
    if (response.status in ERROR_CODES) {
        try {
            val error: ApiErrorResponse = response.body()
            // Now the API response can be checked for retry-able failures
            if (error.statusCode in RETRY_CODES && retry != null) {
                Log.debug("Calling retry coroutine %s for API response error %s in '%s %s'", retry, error, method, endpoint)
                if (retry()) {
                    return request(method, endpoint, client, authHelper,
                        refine = refine,
                        suppress = suppress,
                        retry = null
                    )
                }
            }
            if (suppress) {
                Log.debug("Suppressing %s for call to '%s'", error, response.request.url)
                return null
            }
            throw error.to()
        } catch (e: IllegalArgumentException) {  // de-serialization failed
            Log.error("Invalid body for '%s %s' -> %s: %s: '%s'", method, response.request.url, response.status, e.message, response.bodyAsText())
            val shouldRetry = if (retry != null) {
                Log.debug("Calling retry coroutine %s for serialization error %s in '%s %s'", retry, e, method, endpoint)
                retry()
            } else {
                false
            }
            return if (shouldRetry) {
                request(method, endpoint, client, authHelper,
                    refine = refine,
                    suppress = suppress,
                    retry = null
                )
            } else if (suppress) {
                Log.debug("Suppressed invalid API error response %s: %s (cause: %s)", e, e.message, e.cause)
                null
            } else {
                Log.debug("Throwing network error instead of API error due to serialization failure %s: %s (cause: %s)", e, e.message, e.cause)
                throw UncivNetworkException(e)
            }
        }
    } else if (response.status.isSuccess()) {
        return response
    } else {
        // Here, the server returned a non-success code which is not recognized,
        // therefore it is considered a network error (even if was something like 404)
        if (suppress) {
            Log.debug("Suppressed unknown HTTP status code %s for '%s %s'", response.status, method, response.request.url)
            return null
        }
        // If the server does not conform to the API, re-trying requests is useless
        throw UncivNetworkException(IllegalArgumentException(response.status.toString()))
    }
}

/**
 * Get the default retry mechanism which tries to refresh the current session, if credentials are available
 */
private fun getDefaultRetry(client: HttpClient, authHelper: AuthHelper): (suspend () -> Boolean) {
    val lastCredentials = authHelper.lastSuccessfulCredentials.get()
    if (lastCredentials != null) {
        return suspend {
            val response = request(HttpMethod.Post, "api/v2/auth/login", client, authHelper, suppress = true, retry = null, refine = {b ->
                b.contentType(ContentType.Application.Json)
                b.setBody(LoginRequest(lastCredentials.first, lastCredentials.second))
            })
            if (response != null && response.status.isSuccess()) {
                val authCookie = response.setCookie()[SESSION_COOKIE_NAME]
                Log.debug("Received new session cookie in retry handler: $authCookie")
                if (authCookie != null) {
                    authHelper.setCookie(
                        authCookie.value,
                        authCookie.maxAge,
                        Pair(lastCredentials.first, lastCredentials.second)
                    )
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    } else {
        return suspend { false }
    }
}

/**
 * Simple cache for GET queries to the API
 */
private object Cache {
    private var responseCache: MutableMap<String, Pair<Instant, HttpResponse>> = mutableMapOf()

    /**
     * Clear the response cache
     */
    fun clear() {
        responseCache.clear()
    }

    /**
     * Wrapper around [request] to cache responses to GET queries up to [MAX_CACHE_AGE]
     */
    suspend fun get(
        endpoint: String,
        client: HttpClient,
        authHelper: AuthHelper,
        refine: ((HttpRequestBuilder) -> Unit)? = null,
        suppress: Boolean = false,
        cache: Boolean = true,
        retry: (suspend () -> Boolean)? = null
    ): HttpResponse? {
        val result = responseCache[endpoint]
        if (cache && result != null && (result.first + MAX_CACHE_AGE).isAfter(Instant.now())) {
            return result.second
        }
        val response = request(HttpMethod.Get, endpoint, client, authHelper, refine, suppress, retry)
        if (cache && response != null) {
            responseCache[endpoint] = Pair(Instant.now(), response)
        }
        return response
    }
}

/**
 * API wrapper for account handling (do not use directly; use the Api class instead)
 */
class AccountsApi(private val client: HttpClient, private val authHelper: AuthHelper) {

    /**
     * Retrieve information about the currently logged in user
     *
     * Unset [cache] to avoid using the cache and update the data from the server.
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise [AccountResponse] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun get(cache: Boolean = true, suppress: Boolean = false): AccountResponse? {
        return Cache.get(
            "api/v2/accounts/me",
            client, authHelper,
            suppress = suppress,
            cache = cache,
            retry = getDefaultRetry(client, authHelper)
        )?.body()
    }

    /**
     * Retrieve details for an account by its [uuid] (always preferred to using usernames)
     *
     * Unset [cache] to avoid using the cache and update the data from the server.
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise [AccountResponse] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun lookup(uuid: UUID, cache: Boolean = true, suppress: Boolean = false): AccountResponse? {
        return Cache.get(
            "api/v2/accounts/$uuid",
            client, authHelper,
            suppress = suppress,
            cache = cache,
            retry = getDefaultRetry(client, authHelper)
        )?.body()
    }

    /**
     * Retrieve details for an account by its [username]
     *
     * Important note: Usernames can be changed, so don't assume they can be
     * cached to do lookups for their display names or UUIDs later. Always convert usernames
     * to UUIDs when handling any user interactions (e.g., inviting, sending messages, ...).
     *
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise [AccountResponse] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun lookup(username: String, suppress: Boolean = false): AccountResponse? {
        return request(
            HttpMethod.Post, "api/v2/accounts/lookup",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper),
            refine = { b ->
                b.contentType(ContentType.Application.Json)
                b.setBody(LookupAccountUsernameRequest(username))
            }
        )?.body()
    }

    /**
     * Set the [username] of the currently logged-in user
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun setUsername(username: String, suppress: Boolean = false): Boolean {
        return update(UpdateAccountRequest(username, null), suppress)
    }

    /**
     * Set the [displayName] of the currently logged-in user
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun setDisplayName(displayName: String, suppress: Boolean = false): Boolean {
        return update(UpdateAccountRequest(null, displayName), suppress)
    }

    /**
     * Update the currently logged in user information
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    private suspend fun update(r: UpdateAccountRequest, suppress: Boolean): Boolean {
        val response = request(
            HttpMethod.Put, "api/v2/accounts/me",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper),
            refine = { b ->
                b.contentType(ContentType.Application.Json)
                b.setBody(r)
            }
        )
        return response?.status?.isSuccess() == true
    }

    /**
     * Deletes the currently logged-in account (irreversible operation!)
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun delete(suppress: Boolean = false): Boolean {
        val response = request(
            HttpMethod.Delete, "api/v2/accounts/me",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper)
        )
        return response?.status?.isSuccess() == true
    }

    /**
     * Set [newPassword] for the currently logged-in account, provided the [oldPassword] was accepted as valid
     *
     * If not given, the [oldPassword] will be used from the login session cache, if available.
     * However, if the [oldPassword] can't be determined, it will likely yield in a [ApiStatusCode.InvalidPassword].
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun setPassword(newPassword: String, oldPassword: String? = null, suppress: Boolean = false): Boolean {
        var oldLocalPassword = oldPassword
        val lastKnownPassword = authHelper.lastSuccessfulCredentials.get()?.second
        if (oldLocalPassword == null && lastKnownPassword != null) {
            oldLocalPassword = lastKnownPassword
        }
        if (oldLocalPassword == null) {
            oldLocalPassword = "" // empty passwords will yield InvalidPassword, so this is fine here
        }
        val response = request(
            HttpMethod.Post, "api/v2/accounts/setPassword",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper),
            refine = { b ->
                b.contentType(ContentType.Application.Json)
                b.setBody(SetPasswordRequest(oldLocalPassword, newPassword))
            }
        )
        return if (response?.status?.isSuccess() == true) {
            Log.debug("User's password has been changed successfully")
            true
        } else {
            false
        }
    }

    /**
     * Register a new user account
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun register(username: String, displayName: String, password: String, suppress: Boolean = false): Boolean {
        val response = request(
            HttpMethod.Post, "api/v2/accounts/register",
            client, authHelper,
            suppress = suppress,
            refine = { b ->
                b.contentType(ContentType.Application.Json)
                b.setBody(AccountRegistrationRequest(username, displayName, password))
            }
        )
        return if (response?.status?.isSuccess() == true) {
            Log.debug("A new account for username '%s' has been created", username)
            true
        } else {
            false
        }
    }

}

/**
 * API wrapper for authentication handling (do not use directly; use the Api class instead)
 */
class AuthApi(private val client: HttpClient, private val authHelper: AuthHelper, private val afterLogin: suspend () -> Unit, private val afterLogout: suspend (Boolean) -> Unit) {

    /**
     * Try logging in with [username] and [password] for testing purposes, don't set the session cookie
     *
     * This method won't raise *any* exception, just return the boolean value if login worked.
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun loginOnly(username: String, password: String): Boolean {
        val response = request(
            HttpMethod.Post, "api/v2/auth/login",
            client, authHelper,
            suppress = true,
            refine = { b ->
                b.contentType(ContentType.Application.Json)
                b.setBody(LoginRequest(username, password))
            }
        )
        return response?.status?.isSuccess() == true
    }

    /**
     * Try logging in with [username] and [password] to get a new session
     *
     * This method will also implicitly set a cookie in the in-memory cookie storage to authenticate
     * further API calls and cache the username and password to refresh expired sessions.
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun login(username: String, password: String, suppress: Boolean = false): Boolean {
        val response = request(
            HttpMethod.Post, "api/v2/auth/login",
            client, authHelper,
            suppress = suppress,
            refine = { b ->
                b.contentType(ContentType.Application.Json)
                b.setBody(LoginRequest(username, password))
            },
            retry = { Log.error("Failed to login. See previous debug logs for details."); false }
        )
        return if (response?.status?.isSuccess() == true) {
            val authCookie = response.setCookie()[SESSION_COOKIE_NAME]
            Log.debug("Received new session cookie: $authCookie")
            if (authCookie != null) {
                authHelper.setCookie(
                    authCookie.value,
                    authCookie.maxAge,
                    Pair(username, password)
                )
                afterLogin()
                true
            } else {
                Log.error("No recognized, valid session cookie found in login response!")
                false
            }
        } else {
            false
        }
    }

    /**
     * Logs out the currently logged in user
     *
     * This method will also clear the cookie and credentials to avoid further authenticated API calls.
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun logout(suppress: Boolean = true): Boolean {
        val response = try {
            request(
                HttpMethod.Get, "api/v2/auth/logout",
                client, authHelper,
                suppress = suppress,
                retry = getDefaultRetry(client, authHelper)
            )
        } catch (e: Throwable) {
            authHelper.unset()
            Cache.clear()
            Log.debug("Logout failed due to %s (%s), dropped session anyways", e, e.message)
            afterLogout(false)
            return false
        }
        Cache.clear()
        return if (response?.status?.isSuccess() == true) {
            authHelper.unset()
            Log.debug("Logged out successfully, dropped session")
            afterLogout(true)
            true
        } else {
            authHelper.unset()
            Log.debug("Logout failed for some reason, dropped session anyways")
            afterLogout(false)
            false
        }
    }

}

/**
 * API wrapper for chat room handling (do not use directly; use the Api class instead)
 */
class ChatApi(private val client: HttpClient, private val authHelper: AuthHelper) {

    /**
     * Retrieve all chats a user has access to
     *
     * In the response, you will find different room types / room categories.
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise [GetAllChatsResponse] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun list(suppress: Boolean = false): GetAllChatsResponse? {
        return request(
            HttpMethod.Get, "api/v2/chats",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper)
        )?.body()
    }

    /**
     * Retrieve the messages of a chatroom identified by [roomUUID]
     *
     * The [ChatMessage]s should be sorted by their timestamps, [ChatMessage.createdAt].
     * The [ChatMessage.uuid] should be used to uniquely identify chat messages. This is
     * needed as new messages may be delivered via WebSocket as well. [GetChatResponse.members]
     * holds information about all members that are currently in the chat room (including yourself).
     *
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise [GetChatResponse] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun get(roomUUID: UUID, suppress: Boolean = false): GetChatResponse? {
        return request(
            HttpMethod.Get, "api/v2/chats/$roomUUID",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper)
        )?.body()
    }

    /**
     * Send a message to a chat room
     *
     * The executing user must be a member of the chatroom and the message must not be empty.
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun send(message: String, chatRoomUUID: UUID, suppress: Boolean = false): ChatMessage? {
        val response = request(
            HttpMethod.Post, "api/v2/chats/$chatRoomUUID",
            client, authHelper,
            suppress = suppress,
            refine = { b ->
                b.contentType(ContentType.Application.Json)
                b.setBody(SendMessageRequest(message))
            },
            retry = getDefaultRetry(client, authHelper)
        )
        return response?.body()
    }

}

/**
 * API wrapper for friend handling (do not use directly; use the Api class instead)
 */
class FriendApi(private val client: HttpClient, private val authHelper: AuthHelper) {

    /**
     * Retrieve a pair of the list of your established friendships and the list of your open friendship requests (incoming and outgoing)
     *
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise a pair of lists or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun list(suppress: Boolean = false): Pair<List<FriendResponse>, List<FriendRequestResponse>>? {
        val body: GetFriendResponse? = request(
            HttpMethod.Get, "api/v2/friends",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper)
        )?.body()
        return if (body != null) Pair(body.friends, body.friendRequests) else null
    }

    /**
     * Retrieve a list of your established friendships
     *
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise a list of [FriendResponse] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun listFriends(suppress: Boolean = false): List<FriendResponse>? {
        return list(suppress = suppress)?.first
    }

    /**
     * Retrieve a list of your open friendship requests (incoming and outgoing)
     *
     * If you have a request with [FriendRequestResponse.from] equal to your username, it means
     * you have requested a friendship, but the destination hasn't accepted yet. In the other
     * case, if your username is in [FriendRequestResponse.to], you have received a friend request.
     *
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise a list of [FriendRequestResponse] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun listRequests(suppress: Boolean = false): List<FriendRequestResponse>? {
        return list(suppress = suppress)?.second
    }

    /**
     * Request friendship with another user
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun request(other: UUID, suppress: Boolean = false): Boolean {
        val response = request(
            HttpMethod.Post, "api/v2/friends",
            client, authHelper,
            suppress = suppress,
            refine = { b ->
                b.contentType(ContentType.Application.Json)
                b.setBody(CreateFriendRequest(other))
            },
            retry = getDefaultRetry(client, authHelper)
        )
        return response?.status?.isSuccess() == true
    }

    /**
     * Accept a friend request identified by [friendRequestUUID]
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun accept(friendRequestUUID: UUID, suppress: Boolean = false): Boolean {
        val response = request(
            HttpMethod.Put, "api/v2/friends/$friendRequestUUID",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper)
        )
        return response?.status?.isSuccess() == true
    }

    /**
     * Don't want your friends anymore? Just delete them!
     *
     * This function accepts both friend UUIDs and friendship request UUIDs.
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun delete(friendUUID: UUID, suppress: Boolean = false): Boolean {
        val response = request(
            HttpMethod.Delete, "api/v2/friends/$friendUUID",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper)
        )
        return response?.status?.isSuccess() == true
    }

}

/**
 * API wrapper for game handling (do not use directly; use the Api class instead)
 */
class GameApi(private val client: HttpClient, private val authHelper: AuthHelper) {

    /**
     * Retrieves an overview of all open games of a player
     *
     * The response does not contain any full game state, but rather a
     * shortened game state identified by its ID and state identifier.
     * If the state ([GameOverviewResponse.gameDataID]) of a known game
     * differs from the last known identifier, the server has a newer
     * state of the game. The [GameOverviewResponse.lastActivity] field
     * is a convenience attribute and shouldn't be used for update checks.
     *
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise list of [GameOverviewResponse] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun list(suppress: Boolean = false): List<GameOverviewResponse>? {
        val body: GetGameOverviewResponse? = request(
            HttpMethod.Get, "api/v2/games",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper)
        )?.body()
        return body?.games
    }

    /**
     * Retrieves a single game identified by [gameUUID] which is currently open (actively played)
     *
     * Other than [list], this method's return value contains a full game state (on success).
     * Set [cache] to false to avoid getting a cached result by this function. This
     * is especially useful for receiving a new game on purpose / on request.
     *
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise [GameStateResponse] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun get(gameUUID: UUID, cache: Boolean = true, suppress: Boolean = false): GameStateResponse? {
        return Cache.get(
            "api/v2/games/$gameUUID",
            client, authHelper,
            suppress = suppress,
            cache = cache,
            retry = getDefaultRetry(client, authHelper)
        )?.body()
    }

    /**
     * Retrieves an overview of a single game of a player (or null if no such game is available)
     *
     * The response does not contain any full game state, but rather a
     * shortened game state identified by its ID and state identifier.
     * If the state ([GameOverviewResponse.gameDataID]) of a known game
     * differs from the last known identifier, the server has a newer
     * state of the game. The [GameOverviewResponse.lastActivity] field
     * is a convenience attribute and shouldn't be used for update checks.
     *
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise [GameOverviewResponse] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun head(gameUUID: UUID, suppress: Boolean = false): GameOverviewResponse? {
        val result = list(suppress = suppress)
        return result?.filter { it.gameUUID == gameUUID }?.getOrNull(0)
    }

    /**
     * Upload a new game state for an existing game identified by [gameUUID]
     *
     * If the game can't be updated (maybe it has been already completed
     * or aborted), it will respond with a GameNotFound in [ApiErrorResponse].
     * Use the [gameUUID] retrieved from the server in a previous API call.
     *
     * On success, returns the new game data ID that can be used to verify
     * that the client and server use the same state (prevents re-querying).
     *
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise [Long] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun upload(gameUUID: UUID, gameData: String, suppress: Boolean = false): Long? {
        val body: GameUploadResponse? = request(
            HttpMethod.Put, "api/v2/games/$gameUUID",
            client, authHelper,
            suppress = suppress,
            refine = { b ->
                b.contentType(ContentType.Application.Json)
                b.setBody(GameUploadRequest(gameData))
            },
            retry = getDefaultRetry(client, authHelper)
        )?.body()
        if (body != null) {
            Log.debug("The game with UUID $gameUUID has been uploaded, the new data ID is ${body.gameDataID}")
        }
        return body?.gameDataID
    }

}

/**
 * API wrapper for invite handling (do not use directly; use the Api class instead)
 */
class InviteApi(private val client: HttpClient, private val authHelper: AuthHelper) {

    /**
     * Retrieve all invites for the executing user
     *
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise list of [GetInvite] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun list(suppress: Boolean = false): List<GetInvite>? {
        val body: GetInvitesResponse? = request(
            HttpMethod.Get, "api/v2/invites",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper)
        )?.body()
        return body?.invites
    }

    /**
     * Invite a friend to a lobby
     *
     * The executing user must be in the specified open lobby. The invited
     * player (identified by its [friendUUID]) must not be in a friend request state.
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun new(friendUUID: UUID, lobbyUUID: UUID, suppress: Boolean = false): Boolean {
        val response = request(
            HttpMethod.Post, "api/v2/invites",
            client, authHelper,
            suppress = suppress,
            refine = { b ->
                b.contentType(ContentType.Application.Json)
                b.setBody(CreateInviteRequest(friendUUID, lobbyUUID))
            },
            retry = getDefaultRetry(client, authHelper)
        )
        return response?.status?.isSuccess() == true
    }

    /**
     * Reject or retract an invite to a lobby
     *
     * This endpoint can be used either by the sender of the invite
     * to retract the invite or by the receiver to reject the invite.
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun reject(inviteUUID: UUID, suppress: Boolean = false): Boolean {
        val response = request(
            HttpMethod.Delete, "api/v2/invites/$inviteUUID",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper)
        )
        return response?.status?.isSuccess() == true
    }

}

/**
 * API wrapper for lobby handling (do not use directly; use the Api class instead)
 */
class LobbyApi(private val client: HttpClient, private val authHelper: AuthHelper) {

    /**
     * Retrieves all open lobbies
     *
     * If [LobbyResponse.hasPassword] is true, the lobby is secured by a user-set password.
     *
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise list of [LobbyResponse] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun list(suppress: Boolean = false): List<LobbyResponse>? {
        val body: GetLobbiesResponse? = request(
            HttpMethod.Get, "api/v2/lobbies",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper)
        )?.body()
        return body?.lobbies
    }

    /**
     * Fetch a single open lobby
     *
     * If [LobbyResponse.hasPassword] is true, the lobby is secured by a user-set password.
     *
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise [GetLobbyResponse] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun get(lobbyUUID: UUID, suppress: Boolean = false): GetLobbyResponse? {
        return request(
            HttpMethod.Get, "api/v2/lobbies/$lobbyUUID",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper)
        )?.body()
    }

    /**
     * Create a new lobby and return the new lobby with some extra info as [CreateLobbyResponse]
     *
     * You can't be in more than one lobby at the same time. If [password] is set, the lobby
     * will be considered closed. Users need the specified [password] to be able to join the
     * lobby on their own behalf. Invites to the lobby are always possible as lobby creator.
     *
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise [CreateLobbyResponse] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun open(name: String, password: String? = null, maxPlayers: Int = DEFAULT_LOBBY_MAX_PLAYERS, suppress: Boolean = false): CreateLobbyResponse? {
        return open(CreateLobbyRequest(name, password, maxPlayers), suppress)
    }

    /**
     * Create a new private lobby and return the new lobby with some extra info as [CreateLobbyResponse]
     *
     * You can't be in more than one lobby at the same time. *Important*:
     * This lobby will be created with a random password which will *not* be stored.
     * Other users can't join without invitation to this lobby, afterwards.
     *
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise [CreateLobbyResponse] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun openPrivate(name: String, maxPlayers: Int = DEFAULT_LOBBY_MAX_PLAYERS, suppress: Boolean = false): CreateLobbyResponse? {
        val charset = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val password = (1..DEFAULT_RANDOM_PASSWORD_LENGTH)
            .map { charset.random() }
            .joinToString("")
        return open(CreateLobbyRequest(name, password, maxPlayers), suppress)
    }

    /**
     * Endpoint implementation to create a new lobby
     */
    private suspend fun open(req: CreateLobbyRequest, suppress: Boolean): CreateLobbyResponse? {
        return request(
            HttpMethod.Post, "api/v2/lobbies",
            client, authHelper,
            suppress = suppress,
            refine = { b ->
                b.contentType(ContentType.Application.Json)
                b.setBody(req)
            },
            retry = getDefaultRetry(client, authHelper)
        )?.body()
    }

    /**
     * Kick a player from an open lobby (as the lobby owner)
     *
     * All players in the lobby as well as the kicked player will receive a [LobbyKickMessage] on success.
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun kick(lobbyUUID: UUID, playerUUID: UUID, suppress: Boolean = false): Boolean {
        val response = request(
            HttpMethod.Delete, "api/v2/lobbies/$lobbyUUID/$playerUUID",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper)
        )
        return response?.status?.isSuccess() == true
    }

    /**
     * Close an open lobby (as the lobby owner)
     *
     * On success, all joined players will receive a [LobbyClosedMessage] via WebSocket.
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun close(lobbyUUID: UUID, suppress: Boolean = false): Boolean {
        val response = request(
            HttpMethod.Delete, "api/v2/lobbies/$lobbyUUID",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper)
        )
        return response?.status?.isSuccess() == true
    }

    /**
     * Join an existing lobby
     *
     * The executing user must not be the owner of a lobby or member of a lobby.
     * To be placed in a lobby, an active WebSocket connection is required.
     * As a lobby might be protected by password, the optional parameter password
     * may be specified. On success, all players that were in the lobby before,
     * are notified about the new player with a [LobbyJoinMessage].
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun join(lobbyUUID: UUID, password: String? = null, suppress: Boolean = false): Boolean {
        return request(
            HttpMethod.Post, "api/v2/lobbies/$lobbyUUID/join",
            client, authHelper,
            suppress = suppress,
            refine = { b ->
                b.contentType(ContentType.Application.Json)
                b.setBody(JoinLobbyRequest(password = password))
            },
            retry = getDefaultRetry(client, authHelper)
        )?.status?.isSuccess() == true
    }

    /**
     * Leave an open lobby
     *
     * This endpoint can only be used by joined users.
     * All players in the lobby will receive a [LobbyLeaveMessage] on success.
     *
     * Use [suppress] to forbid throwing *any* errors (returns false, otherwise true or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun leave(lobbyUUID: UUID, suppress: Boolean = false): Boolean {
        return request(
            HttpMethod.Post, "api/v2/lobbies/$lobbyUUID/leave",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper)
        )?.status?.isSuccess() == true
    }

    /**
     * Start a game from an existing lobby
     *
     * The executing user must be the owner of the lobby. The lobby is deleted in the
     * process, a new chatroom is created and all messages from the lobby chatroom are
     * attached to the game chatroom. This will invoke a [GameStartedMessage] that is sent
     * to all members of the lobby to inform them which lobby was started. It also contains
     * the the new and old chatroom [UUID]s to make mapping for the clients easier. Afterwards,
     * the lobby owner must use the [GameApi.upload] to upload the initial game state.
     *
     * Note: This behaviour is subject to change. The server should be set the order in
     * which players are allowed to make their turns. This allows the server to detect
     * malicious players trying to update the game state before its their turn.
     *
     * Use [suppress] to forbid throwing *any* errors (returns null, otherwise [StartGameResponse] or an error).
     *
     * @throws ApiException: thrown for defined and recognized API problems
     * @throws UncivNetworkException: thrown for any kind of network error or de-serialization problems
     */
    suspend fun startGame(lobbyUUID: UUID, suppress: Boolean = false): StartGameResponse? {
        return request(
            HttpMethod.Post, "api/v2/lobbies/$lobbyUUID/start",
            client, authHelper,
            suppress = suppress,
            retry = getDefaultRetry(client, authHelper)
        )?.body()
    }

}
