package com.unciv.logic.multiplayer.apiv2

import com.unciv.utils.Log
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.encodeCookieValue
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * Authentication helper which doesn't support multiple cookies, but just does the job correctly
 *
 * It also stores the username and password as well as the timestamp of the last successful login.
 * Do not use HttpCookies since the url-encoded cookie values break the authentication flow.
 */
class AuthHelper {

    /** Value of the last received session cookie (pair of cookie value and max age) */
    private var cookie: AtomicReference<Pair<String, Int?>?> = AtomicReference()

    /** Credentials used during the last successful login */
    internal var lastSuccessfulCredentials: AtomicReference<Pair<String, String>?> = AtomicReference()

    /** Timestamp of the last successful login */
    private var lastSuccessfulAuthentication: AtomicReference<Instant?> = AtomicReference()

    /**
     * Set the session cookie, update the last refresh timestamp and the last successful credentials
     */
    internal fun setCookie(value: String, maxAge: Int? = null, credentials: Pair<String, String>? = null) {
        cookie.set(Pair(value, maxAge))
        lastSuccessfulAuthentication.set(Instant.now())
        lastSuccessfulCredentials.set(credentials)
    }

    /**
     * Drop the session cookie and credentials, so that authenticating won't be possible until re-login
     */
    internal fun unset() {
        cookie.set(null)
        lastSuccessfulCredentials.set(null)
    }

    /**
     * Add authentication to the request builder by adding the Cookie header
     */
    fun add(request: HttpRequestBuilder) {
        val value = cookie.get()
        if (value != null) {
            if ((lastSuccessfulAuthentication.get()?.plusSeconds((value.second ?: 0).toLong()) ?: Instant.MIN) < Instant.now()) {
                Log.debug("Session cookie might have already expired")
            }
            // Using the raw cookie encoding ensures that valid base64 characters are not re-url-encoded
            request.header(HttpHeaders.Cookie, encodeCookieValue(
                "$SESSION_COOKIE_NAME=${value.first}", encoding = CookieEncoding.RAW
            ))
        } else {
            Log.debug("Session cookie is not available")
        }
    }

}
