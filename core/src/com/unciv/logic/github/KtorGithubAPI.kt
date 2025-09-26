package com.unciv.logic.github

import com.unciv.UncivGame
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object KtorGithubAPI {
    const val baseUrl = "https://api.github.com"

    // add bearer token here if needed
    const val bearerToken = ""

    private val client = HttpClient(CIO) {
        followRedirects = true
        install(HttpRequestRetry) {
            maxRetries = 3
            retryOnException()
        }
        defaultRequest {
            header("X-GitHub-Api-Version", "2022-11-28")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            userAgent(UncivGame.getUserAgent("Github"))
            if (bearerToken.isNotBlank()) bearerAuth(bearerToken)
        }
    }

    /**
     * wait for rate limit to end if any and returns true if there was any rate limit
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun consumeRateLimit(resp: HttpResponse): Boolean {
        if (resp.status != HttpStatusCode.Forbidden && resp.status != HttpStatusCode.TooManyRequests) return false

        val remainingRequests = resp.headers["x-ratelimit-remaining"]?.toIntOrNull() ?: 0
        if (remainingRequests < 1) return false

        val resetEpoch = resp.headers["x-ratelimit-reset"]?.toLongOrNull() ?: 0
        delay(Instant.fromEpochSeconds(resetEpoch) - Clock.System.now())

        return true
    }

    /**
     * Make a ktor request. Stuff like rate limits, retries and redirects is handled automatically
     */
    private suspend fun request(
        maxRateLimitedRetries: Int = 3,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        val resp = client.request(block)
        val rateLimited = consumeRateLimit(resp)

        return if (rateLimited) {
            if (maxRateLimitedRetries <= 0) return resp
            return request(maxRateLimitedRetries - 1, block)
        } else resp
    }

    private suspend fun paginatedRequest(
        page: Int, amountPerPage: Int, block: HttpRequestBuilder.() -> Unit
    ) = request {
        parameter("page", page)
        parameter("per_page", amountPerPage)
        block()
    }

    suspend fun fetchGithubReposWithTopic(search: String, page: Int, amountPerPage: Int) =
        paginatedRequest(page, amountPerPage) {
            url("$baseUrl/search/repositories")
            parameter("sort", "stars")
            parameter("q", "$search${if (search.isEmpty()) "" else "+"} topic:unciv-mod fork:true")
        }

    suspend fun fetchGithubTopics() = request {
        url("$baseUrl/search/topics")
        parameter("sort", "name")
        parameter("order", "asc")

        // `repositories:>1` means ignore unused or practically unused topics
        parameter("q", "unciv-mod repositories:>1")
    }
}
