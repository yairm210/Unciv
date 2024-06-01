package com.unciv.logic.github

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Net
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonWriter
import com.unciv.UncivGame
import com.unciv.ui.screens.savescreens.Gzip
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException

/**
 *  Unciv's use of Github GraphQL API
 *
 *  ## Concepts
 *  -  Switchable backends may be removed for production? The listing test takes averate 1.2s on Gdx, 2.1s on Ktor...
 *  -  Queries and Responses use class hierarchies: [GraphQLQuery], [GraphQLResult]
 *
 *  ## References
 *  -  https://docs.github.com/en/graphql/overview/about-the-graphql-api
 *  -  https://graphql.org/learn/best-practices/
 *  -  https://gist.github.com/magnetikonline/073afe7909ffdd6f10ef06a00bc3bc88
 *
 *  ## Ideas
 *  -  Streaming repo listing result as flow - does a gzipped-json parser exist that will emit before the network stream is fully arrived?
 *
 *  ## Requirements
 *  -  (x) Query mods
 *  -  (x) Selectively query mods for auto-update
 *  -  ( ) Selectively query mods for auto-download missing mods
 *
 *  ## TODO
 *  -  GZipped *query*
 *  +  Translate exception for malformed queries
 *  +  Auth token stored in settings
 *  +  Use for mod manager
 *  -  Use for missing mod autodownload
 *  +  Use for mod auto-update (from mod manager, from main menu?)
 *  -  Option to mark mods as autoupdate candidates
 *  -  RateLimit: Doc how to estimate cost
 *  -  RateLimit: UI
 *
 *  @param backend [Backend.Gdx] (default) or [Backend.Ktor] - engine used to do networking. Switchable during development phase, may disappear in the future.
 *  @param getAuthToken A getter (dynamic so you don't need to discard the instance to change auth) for the auth token. "bearer" is prepended automatically.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate") // This is an API
class GraphQL(
    val backend: Backend = Backend.Gdx,
    private val getAuthToken: (()->String)? = null
)  {
    enum class Backend {
        /** Uses [com.badlogic.gdx.Net] (so far 100% faster) */
        Gdx,
        /** Uses [io.ktor.client.HttpClient] (in the hopes a response can be made into a flow someday) */
        Ktor
    }

    // region Private data

    private object Constants {
        const val endpoint = "https://api.github.com/graphql"
        val method = HttpMethod.Post
        const val acceptHeader = "accept"
        const val acceptValue = "application/graphql-response+json; charset=utf-8, application/json; charset=utf-8"
        const val encodingHeader = "Accept-Encoding"
        const val encodingValue = "gzip"
        const val agentHeader = "User-Agent"
        const val authHeader = "Authorization"
        const val contentTypeHeader = "Content-Type"
        const val contentTypeValue = "application/json"
        const val connectTimeoutMillis = 2500L
        const val requestTimeoutMillis = 5000L
    }

    private val client by lazy { HttpClient {
        // Changing engine.threadsCount (default 4): Slower in both directions
        // Enabling enging.pipelining: No measurable effect
        install(HttpTimeout) {
            connectTimeoutMillis = Constants.connectTimeoutMillis
            requestTimeoutMillis = Constants.requestTimeoutMillis
        }
        install(ContentNegotiation) {
            json()  // DefaultJson is lenient, but not prettyPrint and encodes defaults
        }
        install(ContentEncoding) {
            gzip()
        }
    } }

    private val json = Json(JsonWriter.OutputType.json).apply {
        setTypeName(null) // Never annotate with class (regrettably, it will still wrap the value as `{"value":x}` - which GraphQL won't accept)
        //setUsePrototypes(false) // Do not compare with empty instance and omit if equal
    }

    //endregion

    /** Common to all possible instances/queries/etc as all share the same auth token and github resource (always "graphql") */
    object RateLimit {
        internal var rateLimitData: RateLimitData? = null
        fun getWaitTime(): Long? = synchronized(this) { rateLimitData?.getWaitTime() }
        fun percent(): Int? = synchronized(this) { rateLimitData?.percent() }
        override fun toString() = synchronized(this) { rateLimitData?.toString() ?: "" }
    }

    /** Packages information on the github ratelimit as returned in response headers.
     *  - Units are complicated - number of potential nodes / 100 goes into the formula
     *  TODO: Document how to predict ratelimit cost of a query - most of our needs should be optimizable such that each query only costs 1
     */
    data class RateLimitData(
        val limit: Long,
        val used: Long,
        val remaining: Long,
        /** Comparable to System.currentTimeMillis()/1000 */
        val reset: Long
    ) {
        constructor(response: HttpResponse) : this (
            response.headers[rateLimitLimitHeader]?.toLongOrNull() ?: 0L,
            response.headers[rateLimitUsedHeader]?.toLongOrNull() ?: 0L,
            response.headers[rateLimitRemainingHeader]?.toLongOrNull() ?: 0L,
            response.headers[rateLimitResetHeader]?.toLongOrNull() ?: 0L,
        )

        constructor(response: Net.HttpResponse) : this(
            response.headers[rateLimitLimitHeader]?.get(0)?.toLongOrNull() ?: 0L,
            response.headers[rateLimitUsedHeader]?.get(0)?.toLongOrNull() ?: 0L,
            response.headers[rateLimitRemainingHeader]?.get(0)?.toLongOrNull() ?: 0L,
            response.headers[rateLimitResetHeader]?.get(0)?.toLongOrNull() ?: 0L
        )
        companion object {
            const val rateLimitLimitHeader = "X-RateLimit-Limit"
            const val rateLimitUsedHeader = "X-RateLimit-Used"
            const val rateLimitRemainingHeader = "X-RateLimit-Remaining"
            const val rateLimitResetHeader = "X-RateLimit-Reset"
        }
        fun getWaitTime() = (reset * 1000L - System.currentTimeMillis()).coerceAtLeast(0L)
        fun percent() = (used * 100 / limit).toInt().coerceIn(0, 100)
        override fun toString() = "(limit=$limit, used=$used, remaining=$remaining, reset=$reset)"
    }

    fun synchronousRequest(query: GraphQLQuery): String {
        return runBlocking(Dispatchers.IO) {
            request(query, this)
        }
    }

    suspend fun request(query: GraphQLQuery, scope: CoroutineScope): String {
        val body = json.toJson(query)
        return when(backend) {
            Backend.Ktor -> {
                val response = ktorRequest(body)
                if (!response.status.isSuccess())
                    throw Exception("Request to ${Constants.endpoint} failed: ${response.status.description}")
                RateLimit.rateLimitData = RateLimitData(response)
                response.bodyAsText()
            }
            Backend.Gdx -> {
                val wrapper = gdxRequest(body, scope)
                RateLimit.rateLimitData = wrapper.rateLimit
                wrapper.text
            }
        }
    }

    /** Parse a result as delivered by [synchronousRequest] */
    // Encapsulation: Don't expose our json instance
    fun <T: GraphQLResult> parseResult(clazz: Class<T>, jsonText: String): T {
        return try {
            json.fromJson(clazz, jsonText)
        } catch (noDataException: GraphQLResult.NoDataException) {
            throw try {
                val errors = json.fromJson(GraphQLResult.Errors::class.java, jsonText)
                GraphQLError(errors, noDataException)
            } catch (ex: Throwable) {
                GraphQLParseFailure(noDataException.cause ?: noDataException, ex)
            }
        }
    }

    /** Any Exception in the GraphQL response parsing context */
    open class GraphQLException(override val message: String?) : Exception()

    /** The GraphQL response indicates errors we could parse */
    class GraphQLError(val errors: GraphQLResult.Errors, noDataException: GraphQLResult.NoDataException) : GraphQLException("GraphQL reported an error") {
        override val cause = errors.firstOrNull()?.asException() ?: noDataException.cause
    }

    /** Thrown when the response body is not json at all or has neither a data nor an errors node.
     *  - ***Placeholder until we know better how this can be reached***
     */
    class GraphQLParseFailure(override val cause: Throwable, val secondCause: Throwable) : GraphQLException("GraphQL response could not be parsed")

    fun isTokenInvalid(): Boolean {
        val token = getAuthToken?.invoke() ?: return false
        val regex = Regex("^(gh[oprsu]_[a-zA-Z0-9]{36,}|github_pat_[a-zA-Z0-9]{22}_[a-zA-Z0-9]{59,})\$")
        return !regex.matches(token)
    }

    //region Helpers

    private suspend fun ktorRequest(query: String): HttpResponse {
        val builder = HttpRequestBuilder()
        builder.run {
            method = Constants.method
            url(Constants.endpoint)
            header(Constants.acceptHeader, Constants.acceptValue)
            header(Constants.encodingHeader, Constants.encodingValue)
            header(Constants.agentHeader, "Unciv/" + UncivGame.VERSION.text)
            getAuthToken?.let { header(Constants.authHeader, "bearer ${it()}") }
            contentType(ContentType.Application.Json)
            setBody(query)
        }
        return client.request(builder)
    }

    private suspend fun gdxRequest(query: String, scope: CoroutineScope): GdxResponseWrapper {
        val request = Net.HttpRequest(Net.HttpMethods.POST)
        request.run {
            url = Constants.endpoint
            setHeader(Constants.acceptHeader, Constants.acceptValue)
            setHeader(Constants.encodingHeader, Constants.encodingValue)
            setHeader(Constants.agentHeader, "Unciv/" + UncivGame.VERSION.text)
            getAuthToken?.let { setHeader(Constants.authHeader, "bearer ${it()}") }
            setHeader(Constants.contentTypeHeader, Constants.contentTypeValue)
            content = query
            timeOut = Constants.requestTimeoutMillis.toInt()
        }
        var response: GdxResponseWrapper? = null
        var exception: Throwable? = null
        val listener = object : Net.HttpResponseListener {
            override fun handleHttpResponse(httpResponse: Net.HttpResponse) {
                response = GdxResponseWrapper(httpResponse)
            }
            override fun failed(t: Throwable) { exception = t }
            override fun cancelled() { exception = CancellationException() }
        }
        Gdx.net.sendHttpRequest(request, listener)
        while (scope.isActive && response == null && exception == null) { delay(10L) }
        if (response == null) throw exception!!
        if (response!!.statusCode != 200)
            throw Exception("Request to ${Constants.endpoint} failed: ${response!!.statusMessage}")
        return response!!
    }

    private class GdxResponseWrapper(val text: String, val statusCode: Int, val statusMessage: String?, val rateLimit: RateLimitData?) {
        constructor(response: Net.HttpResponse) : this(
            unzip(response.result),
            response.status.statusCode,
            response.headers[null]?.get(0),
            RateLimitData(response)
        )
        companion object {
            private fun unzip(result: ByteArray?): String {
                if (result == null) return ""
                if (result[0] != '{'.code.toByte()) return Gzip.decompress(result)
                return String(result)
            }
        }
    }

    //endregion
}
