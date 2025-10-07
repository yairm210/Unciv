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
    /**
     * @see <a href="https://ktor.io/docs/client-default-request.html#url">
     *          Ktor Client > Developing applications > Requests > Default request > Base URL
     *      </a>
     */
    const val baseUrl = "https://api.github.com"

    /**
     * Add a bearer token here if needed
     *
     * @see <a href="https://github.com/yairm210/Unciv/issues/13951#issuecomment-3326406877">#13951 (comment)</a>
     */
    const val bearerToken = ""

    private val client = HttpClient(CIO) {
        followRedirects = true
        install(HttpRequestRetry) {
            maxRetries = 3
            retryOnException()
        }
        defaultRequest {
            url(baseUrl)
            header("X-GitHub-Api-Version", "2022-11-28")
            header(HttpHeaders.Accept, "application/vnd.github+json")
            userAgent(UncivGame.getUserAgent("Github"))
            if (bearerToken.isNotBlank()) bearerAuth(bearerToken)
        }
    }

    /**
     * Wait for rate limit to end if any and returns true if there was any rate limit
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

    /**
     * Make a ktor request handling rate limits automatically
     */
    suspend fun request(
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
            url("/search/repositories")
            parameter("sort", "stars")
            parameter("q", "$search${if (search.isEmpty()) "" else "+"} topic:unciv-mod fork:true")
        }

    suspend fun fetchGithubTopics() = request {
        url("/search/topics")
        parameter("sort", "name")
        parameter("order", "asc")

        /**
         * `repositories:>1` means ignore unused or practically unused topics
         */
        parameter("q", "unciv-mod repositories:>1")
    }

    suspend fun fetchSingleRepo(owner: String, repoName: String) =
        request { url("/repos/$owner/$repoName") }

    suspend fun fetchPreviewImageOrNull(modUrl: String, branch: String, ext: String) =
        getOrNull("$modUrl/$branch/preview.${ext}") { host = "raw.githubusercontent.com" }
}
