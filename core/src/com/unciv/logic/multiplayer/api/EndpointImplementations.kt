/**
 * Collection of endpoint implementations
 *
 * Those classes are not meant to be used directly. Take a look at the Api class for common usage.
 */

package com.unciv.logic.multiplayer.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * API wrapper for account handling (do not use directly; use the Api class instead)
 */
class AccountsApi constructor(private val client: HttpClient) {

    /**
     * Retrieve information about the currently logged in user
     */
    suspend fun get(): AccountResponse {
        val response = client.get("/api/v2/accounts/me")
        if (response.status.equals(200)) {
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
        }
        if (response.status.equals(200)) {
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
        val response = client.delete("/api/v2/accounts/me")
        if (response.status.equals(200)) {
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
        }
        if (response.status.equals(200)) {
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
        }
        if (response.status.equals(200)) {
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
class AuthApi constructor(private val client: HttpClient) {

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
        return if (response.status.equals(200)) {
            true
        } else {
            response.body()
        }
    }

    /**
     * Logs out the currently logged in user
     *
     * This method will also clear the cookie on success only to avoid further authenticated API calls
     */
    suspend fun logout(): Boolean {
        val response = client.post("/api/v2/auth/logout")
        return if (response.status.equals(200)) {
            // TODO: Maybe clear cookie here
            true
        } else {
            response.body()
        }
    }

}
