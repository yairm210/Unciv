package com.unciv.logic

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.android.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*

object UncivKtor {
    val client = HttpClient(chooseEngine()) {
        followRedirects = true
        install(HttpRequestRetry) {
            maxRetries = 3
            retryOnException()
        }
        install(BodyProgress)

        defaultRequest {
            userAgent(UncivGame.getUserAgent())
        }
    }

    private fun chooseEngine(): HttpClientEngineFactory<HttpClientEngineConfig> {
        val name = if (UncivGame.isCurrentInitialized()) UncivGame.Current.settings.ktorEngine else null
        return when(name?.lowercase()) {
            "cio" -> CIO
            "android" -> Android
            "okhttp" -> OkHttp
            else -> if (Gdx.app.type == Application.ApplicationType.Android) OkHttp else CIO
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
