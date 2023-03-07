package com.unciv.logic.multiplayer.api

import io.ktor.client.request.*
import io.ktor.http.*

const val cookieName = "id"

/**
 * Simple authentication cookie helper which doesn't support multiple cookies, but just does the job correctly
 *
 * Do not use [HttpCookies] since the url-encoded cookie values break the authentication flow.
 */
class AuthCookieHelper {
    private var cookieValue: String? = null

    fun set(value: String) {
        cookieValue = value
    }

    fun unset() {
        cookieValue = null
    }

    fun get(): String? {
        return cookieValue
    }

    fun add(request: HttpRequestBuilder) {
        val currentValue = cookieValue
        request.headers
        if (currentValue != null) {
            request.header(HttpHeaders.Cookie, encodeCookieValue(
                "$cookieName=$currentValue", encoding = CookieEncoding.RAW
            ))
        }
    }
}
