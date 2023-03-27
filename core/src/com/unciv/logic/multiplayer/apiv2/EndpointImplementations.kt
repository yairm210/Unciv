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
import io.ktor.client.statement.*
import io.ktor.http.*
import java.io.IOException
import java.util.*

/**
 * List of HTTP status codes which are considered to [ApiErrorResponse]s by the specification
 */
internal val ERROR_CODES = listOf(HttpStatusCode.BadRequest, HttpStatusCode.InternalServerError)

/**
 * List of API status codes that should be re-executed after session refresh, if possible
 */
private val RETRY_CODES = listOf(ApiStatusCode.Unauthenticated)

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
    } catch (e: IOException) {
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
            val response = request(HttpMethod.Post, "/api/v2/auth/login", client, authHelper, suppress = true, retry = null, refine = {b ->
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
 * API wrapper for account handling (do not use directly; use the Api class instead)
 */
class AccountsApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper) {

    /**
     * Retrieve information about the currently logged in user
     */
    suspend fun get(): AccountResponse {
        val response = client.get("/api/v2/accounts/me") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

    /**
     * Retrieve details for an account by its UUID (always preferred to using usernames)
     */
    suspend fun lookup(uuid: UUID): AccountResponse {
        val response = client.get("/api/v2/accounts/$uuid") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

    /**
     * Retrieve details for an account by its username
     *
     * Important note: Usernames can be changed, so don't assume they can be
     * cached to do lookups for their display names or UUIDs later. Always convert usernames
     * to UUIDs when handling any user interactions (e.g., inviting, sending messages, ...).
     */
    suspend fun lookup(username: String): AccountResponse {
        return lookup(LookupAccountUsernameRequest(username))
    }

    /**
     * Retrieve details for an account by its username
     *
     * Important note: Usernames can be changed, so don't assume they can be
     * cached to do lookups for their display names or UUIDs later. Always convert usernames
     * to UUIDs when handling any user interactions (e.g., inviting, sending messages, ...).
     */
    suspend fun lookup(r: LookupAccountUsernameRequest): AccountResponse {
        val response = client.post("/api/v2/accounts/lookup") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

    /**
     * Update the currently logged in user information
     *
     * At least one value must be set to a non-null value.
     */
    suspend fun update(username: String?, displayName: String?): Boolean {
        return update(UpdateAccountRequest(username, displayName))
    }

    /**
     * Update the currently logged in user information
     *
     * At least one value must be set to a non-null value.
     */
    suspend fun update(r: UpdateAccountRequest): Boolean {
        val response = client.put("/api/v2/accounts/me") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

    /**
     * Deletes the currently logged-in account
     */
    suspend fun delete(): Boolean {
        val response = client.delete("/api/v2/accounts/me") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            Log.debug("The current user has been deleted")
            authCookieHelper.unset()
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

    /**
     * Set a new password for the currently logged-in account, provided the old password was accepted as valid
     */
    suspend fun setPassword(oldPassword: String, newPassword: String): Boolean {
        return setPassword(SetPasswordRequest(oldPassword, newPassword))
    }

    /**
     * Set a new password for the currently logged-in account, provided the old password was accepted as valid
     */
    suspend fun setPassword(r: SetPasswordRequest): Boolean {
        val response = client.post("/api/v2/accounts/setPassword") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            Log.debug("User's password has been changed successfully")
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

    /**
     * Register a new user account
     */
    suspend fun register(username: String, displayName: String, password: String): Boolean {
        return register(AccountRegistrationRequest(username, displayName, password))
    }

    /**
     * Register a new user account
     */
    suspend fun register(r: AccountRegistrationRequest): Boolean {
        val response = client.post("/api/v2/accounts/register") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            Log.debug("A new account for username ${r.username} has been created")
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

}

/**
 * API wrapper for authentication handling (do not use directly; use the Api class instead)
 */
class AuthApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper) {

    /**
     * Try logging in with username and password for testing purposes, don't set the session cookie
     *
     * This method won't raise *any* exception, just return the boolean value if login worked.
     */
    suspend fun loginOnly(username: String, password: String): Boolean {
        val response = client.post("/api/v2/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username, password))
        }
        return response.status.isSuccess()
    }

    /**
     * Try logging in with username and password
     *
     * This method will also implicitly set a cookie in the in-memory cookie storage to authenticate further API calls
     */
    suspend fun login(username: String, password: String): Boolean {
        return login(LoginRequest(username, password))
    }

    /**
     * Try logging in with username and password
     *
     * This method will also implicitly set a cookie in the in-memory cookie storage to authenticate further API calls
     */
    suspend fun login(r: LoginRequest): Boolean {
        val response = client.post("/api/v2/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(r)
        }
        if (response.status.isSuccess()) {
            val authCookie = response.setCookie()["id"]
            Log.debug("Received new session cookie: $authCookie")
            if (authCookie != null) {
                authCookieHelper.set(authCookie.value)
            }
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

    /**
     * Logs out the currently logged in user
     *
     * This method will also clear the cookie on success only to avoid further authenticated API calls
     */
    suspend fun logout(): Boolean {
        val response = client.post("/api/v2/auth/logout")
        if (response.status.isSuccess()) {
            Log.debug("Logged out successfully (dropping session cookie...)")
            authCookieHelper.unset()
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

}

/**
 * API wrapper for chat room handling (do not use directly; use the Api class instead)
 */
class ChatApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper) {

    /**
     * Retrieve all messages a user has access to
     *
     * In the response, you will find different categories, currently friend rooms and lobby rooms.
     */
    suspend fun list(): GetAllChatsResponse {
        val response = client.get("/api/v2/chats") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

    /**
     * Retrieve the messages of a chatroom
     *
     * [GetChatResponse.members] holds information about all members that are currently in the chat room (including yourself)
     */
    suspend fun get(roomID: Long): GetChatResponse {
        val response = client.get("/api/v2/chats/$roomID") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

}

/**
 * API wrapper for friend handling (do not use directly; use the Api class instead)
 */
class FriendApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper) {

    /**
     * Retrieve a pair of the list of your established friendships and the list of your open friendship requests (incoming and outgoing)
     */
    suspend fun listAll(): Pair<List<FriendResponse>, List<FriendRequestResponse>> {
        val response = client.get("/api/v2/friends") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            val responseBody: GetFriendResponse = response.body()
            return Pair(responseBody.friends, responseBody.friendRequests)
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

    /**
     * Retrieve a list of your established friendships
     */
    suspend fun listFriends(): List<FriendResponse> {
        return listAll().first
    }

    /**
     * Retrieve a list of your open friendship requests (incoming and outgoing)
     *
     * If you have a request with ``from`` equal to your username, it means you
     * have requested a friendship, but the destination hasn't accepted yet.
     * In the other case, if your username is in ``to``, you have received a friend request.
     */
    suspend fun listRequests(): List<FriendRequestResponse> {
        return listAll().second
    }

    /**
     * Request friendship with another user
     */
    suspend fun request(other: UUID): Boolean {
        return request(CreateFriendRequest(other))
    }

    /**
     * Request friendship with another user
     */
    suspend fun request(r: CreateFriendRequest): Boolean {
        val response = client.post("/api/v2/friends") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

    /**
     * Accept a friend request
     */
    suspend fun accept(friendRequestID: Long): Boolean {
        val response = client.delete("/api/v2/friends/$friendRequestID") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            Log.debug("Successfully accepted friendship request ID $friendRequestID")
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

    /**
     * Don't want your friends anymore? Just delete them!
     *
     * This function accepts both friend IDs and friendship request IDs,
     * since they are the same thing in the server's database anyways.
     */
    suspend fun delete(friendID: Long): Boolean {
        val response = client.delete("/api/v2/friends/$friendID") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            Log.debug("Successfully rejected/dropped friendship ID $friendID")
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

}

/**
 * API wrapper for game handling (do not use directly; use the Api class instead)
 */
class GameApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper) {

    /**
     * Retrieves an overview of all open games of a player
     *
     * The response does not contain any full game state, but rather a
     * shortened game state identified by its ID and state identifier.
     * If the state ([GameOverviewResponse.gameDataID]) of a known game
     * differs from the last known identifier, the server has a newer
     * state of the game. The [GameOverviewResponse.lastActivity] field
     * is a convenience attribute and shouldn't be used for update checks.
     */
    suspend fun list(): List<GameOverviewResponse> {
        val response = client.get("/api/v2/games") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            val body: GetGameOverviewResponse = response.body()
            return body.games
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

    /**
     * Retrieves a single game which is currently open (actively played)
     *
     * If the game has been completed or aborted, it will
     * respond with a GameNotFound in [ApiErrorResponse].
     */
    suspend fun get(gameUUID: UUID): GameStateResponse {
        val response = client.get("/api/v2/games/$gameUUID") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            return response.body()
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

    /**
     * Upload a new game state for an existing game
     *
     * If the game can't be updated (maybe it has been already completed
     * or aborted), it will respond with a GameNotFound in [ApiErrorResponse].
     * Use the [gameUUID] retrieved from the server in a previous API call.
     *
     * On success, returns the new game data ID that can be used to verify
     * that the client and server use the same state (prevents re-querying).
     */
    suspend fun upload(gameUUID: UUID, gameData: String): Long {
        return upload(GameUploadRequest(gameData, gameUUID))
    }

    /**
     * Upload a new game state for an existing game
     *
     * If the game can't be updated (maybe it has been already completed
     * or aborted), it will respond with a GameNotFound in [ApiErrorResponse].
     *
     * On success, returns the new game data ID that can be used to verify
     * that the client and server use the same state (prevents re-querying).
     */
    suspend fun upload(r: GameUploadRequest): Long {
        val response = client.put("/api/v2/games") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            val responseBody: GameUploadResponse = response.body()
            Log.debug("The game with ID ${r.gameUUID} has been uploaded, the new data ID is ${responseBody.gameDataID}")
            return responseBody.gameDataID
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

}

/**
 * API wrapper for invite handling (do not use directly; use the Api class instead)
 */
class InviteApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper) {

    /**
     * Retrieve all invites for the executing user
     */
    suspend fun list(): List<GetInvite> {
        val response = client.get("/api/v2/invites") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            val responseBody: GetInvitesResponse = response.body()
            return responseBody.invites
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

    /**
     * Invite a friend to a lobby
     *
     * The executing user must be in the specified open lobby.
     * The invited friend must not be in a friend request state.
     */
    suspend fun new(friend: UUID, lobbyID: Long): Boolean {
        return new(CreateInviteRequest(friend, lobbyID))
    }

    /**
     * Invite a friend to a lobby
     *
     * The executing user must be in the specified open lobby.
     * The invited friend must not be in a friend request state.
     */
    suspend fun new(r: CreateInviteRequest): Boolean {
        val response = client.post("/api/v2/invites") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            Log.debug("The friend ${r.friend} has been invited to lobby ${r.lobbyID}")
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

}

/**
 * API wrapper for lobby handling (do not use directly; use the Api class instead)
 */
class LobbyApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper) {

    /**
     * Retrieves all open lobbies
     *
     * If hasPassword is true, the lobby is secured by a user-set password
     */
    suspend fun list(): List<LobbyResponse> {
        val response = client.get("/api/v2/lobbies") {
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            val responseBody: GetLobbiesResponse = response.body()
            return responseBody.lobbies
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

    /**
     * Create a new lobby and return the new lobby ID
     *
     * If you are already in another lobby, an error is returned.
     * ``max_players`` must be between 2 and 34 (inclusive).
     */
    suspend fun open(name: String, maxPlayers: Int = LOBBY_MAX_PLAYERS): Long {
        return open(CreateLobbyRequest(name, null, maxPlayers))
    }

    /**
     * Create a new lobby and return the new lobby ID
     *
     * If you are already in another lobby, an error is returned.
     * ``max_players`` must be between 2 and 34 (inclusive).
     * If password is an empty string, an error is returned.
     */
    suspend fun open(name: String, password: String?, maxPlayers: Int = LOBBY_MAX_PLAYERS): Long {
        return open(CreateLobbyRequest(name, password, maxPlayers))
    }

    /**
     * Create a new lobby and return the new lobby ID
     *
     * If you are already in another lobby, an error is returned.
     * ``max_players`` must be between 2 and 34 (inclusive).
     * If password is an empty string, an error is returned.
     */
    suspend fun open(r: CreateLobbyRequest): Long {
        val response = client.post("/api/v2/lobbies") {
            contentType(ContentType.Application.Json)
            setBody(r)
            authCookieHelper.add(this)
        }
        if (response.status.isSuccess()) {
            val responseBody: CreateLobbyResponse = response.body()
            return responseBody.lobbyID
        } else {
            val err: ApiErrorResponse = response.body()
            throw err.to()
        }
    }

}
