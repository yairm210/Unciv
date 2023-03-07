/**
 * TODO: Comment this file
 */

package com.unciv.logic.multiplayer.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * API wrapper around the newly implemented REST API for multiplayer game handling
 *
 * Note that this does not include the handling of messages via the
 * WebSocket connection, but rather only the pure HTTP-based API.
 * Almost any method may throw certain OS or network errors as well as the
 * [ApiErrorResponse] for invalid requests (4xx) or server failures (5xx).
 */
class Api(private val baseUrl: String) {

    // HTTP client to handle the server connections, logging, content parsing and cookies
    private val client = HttpClient(CIO) {
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
        install(HttpCookies)
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        install(WebSockets) {
            pingInterval = 15_000
        }
        defaultRequest {
            url(baseUrl)
        }
    }

    /**
     * API for account management
     */
    val accounts = AccountsApi(client)

    /**
     * API for authentication management
     */
    val auth = AuthApi(client)

    /**
     * Retrieve the currently available API version of the connected server
     */
    suspend fun version(): VersionResponse {
        return client.get("/api/version").body()
    }

}
