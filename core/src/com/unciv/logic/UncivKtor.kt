package com.unciv.logic

import com.unciv.UncivGame
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

object UncivKtor {
    val client = HttpClient(CIO) {
        followRedirects = true
        install(HttpRequestRetry) {
            maxRetries = 3
            retryOnException()
        }
        defaultRequest {
            userAgent(UncivGame.getUserAgent())
        }
    }

    /**
     * Wrapper for [client.get][HttpClient.get] that returns `null` on failure
     *
     * @return [HttpResponse] on success and `null` on failure
     */
    suspend fun getOrNull(url: String, block: HttpRequestBuilder.() -> Unit = {}) = try {
        val resp = client.get(url, block)
        if (resp.status.isSuccess()) resp else null
    } catch (_: Throwable) {
        null
    }
}
