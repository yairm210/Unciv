/**
 * Collection of endpoint implementations
 *
 * Those classes are not meant to be used directly. Take a look at the Api class for common usage.
 */

package com.unciv.logic.multiplayer.api

import java.util.logging.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * API wrapper for account handling (do not use directly; use the Api class instead)
 */
class AccountsApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper, private val logger: Logger) {

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
            throw err
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
            throw err
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
            logger.info("The current user has been deleted")
            authCookieHelper.unset()
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
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
            logger.info("Password has been changed successfully")
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
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
            logger.info("A new account for username ${r.username} has been created")
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

}

/**
 * API wrapper for authentication handling (do not use directly; use the Api class instead)
 */
class AuthApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper, private val logger: Logger) {

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
            logger.info("Received new session cookie: $authCookie")
            if (authCookie != null) {
                authCookieHelper.set(authCookie.value)
            }
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
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
            logger.info("Logged out successfully (dropping session cookie...)")
            authCookieHelper.unset()
            return true
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

}

/**
 * API wrapper for lobby handling (do not use directly; use the Api class instead)
 */
class LobbyApi(private val client: HttpClient, private val authCookieHelper: AuthCookieHelper, private val logger: Logger) {

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
            throw err
        }
    }

    /**
     * Create a new lobby and return the new lobby ID
     *
     * If you are already in another lobby, an error is returned.
     * ``max_players`` must be between 2 and 34 (inclusive).
     * If password is an empty string, an error is returned.
     */
    suspend fun open(name: String, maxPlayers: Int): Long {
        return open(CreateLobbyRequest(name, null, maxPlayers))
    }

    /**
     * Create a new lobby and return the new lobby ID
     *
     * If you are already in another lobby, an error is returned.
     * ``max_players`` must be between 2 and 34 (inclusive).
     * If password is an empty string, an error is returned.
     */
    suspend fun open(name: String, password: String?, maxPlayers: Int): Long {
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
            logger.info("A new lobby with ID ${responseBody.lobbyID} has been created")
            return responseBody.lobbyID
        } else {
            val err: ApiErrorResponse = response.body()
            throw err
        }
    }

}
