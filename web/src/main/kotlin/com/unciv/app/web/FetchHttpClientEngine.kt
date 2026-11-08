package com.unciv.app.web

import io.ktor.client.HttpClientEngineContainer
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forEachHeader
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.teavm.jso.JSBody
import org.teavm.jso.JSObject
import org.teavm.jso.JSProperty
import org.teavm.jso.core.JSArray
import org.teavm.jso.core.JSArrayReader
import org.teavm.jso.core.JSPromise
import org.teavm.jso.core.JSString
import org.teavm.jso.streams.ReadableStream
import org.teavm.jso.streams.ReadableStreamDefaultReader
import org.teavm.jso.typedarrays.Int8Array
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Ktor engine provider selected by HttpClient() in the TeaVM browser target. */
class FetchHttpClientEngineContainer : HttpClientEngineContainer {
    override val factory: HttpClientEngineFactory<*> get() = FetchHttpClientEngineFactory
}

object FetchHttpClientEngineFactory : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
        val config = HttpClientEngineConfig().apply(block)
        return FetchHttpClientEngine(config)
    }
}

private class FetchHttpClientEngine(private val engineConfig: HttpClientEngineConfig) : HttpClientEngineBase("Fetch") {
    override val config: HttpClientEngineConfig
        get() = engineConfig

    // Browser fetch and the response reader are asynchronous event-loop operations.
    // Never route these through a blocking IO dispatcher.
    override val dispatcher = engineConfig.dispatcher ?: Dispatchers.Default

    override val supportedCapabilities = setOf(HttpTimeoutCapability)

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val timeout = data.getCapabilityOrNull(HttpTimeoutCapability)
        require(timeout?.connectTimeoutMillis == null && timeout?.socketTimeoutMillis == null) {
            "Browser Fetch does not support separate connect or socket timeouts; use requestTimeoutMillis"
        }
        val requestHeaders = HeadersBuilder()
        data.forEachHeader { name, value -> requestHeaders.append(name, value) }
        val headerNames = requestHeaders.entries().flatMap { entry -> List(entry.value.size) { entry.key } }.toTypedArray()
        val headerValues = requestHeaders.entries().flatMap { it.value }.toTypedArray()
        val requestBody = requestBody(data.body)
        val abortController = FetchJsBridge.newAbortController()
        data.executionContext.invokeOnCompletion { cause ->
            if (cause != null) FetchJsBridge.abort(abortController)
        }

        val response = try {
            FetchJsBridge.fetch(
                data.url.toString(), data.method.value, headerNames, headerValues, requestBody, FetchJsBridge.signal(abortController)
            ).awaitCancellable()
        } catch (t: Throwable) {
            if (data.executionContext.isCancelled) throw t
            throw FetchRequestException("Fetch failed for ${data.method.value} ${data.url}", t)
        }

        val statusCode = FetchJsBridge.responseStatus(response)
        val stream = FetchJsBridge.responseBody(response)
        val responseHasBody = stream != null && data.method.value != "HEAD" &&
            statusCode !in setOf(204, 205, 304)
        val headers = HeadersBuilder()
        val headerPairs = FetchJsBridge.responseHeaders(response, responseHasBody)
        for (index in 0 until headerPairs.length) {
            val pair = headerPairs.get(index)
            headers.append(pair.get(0).stringValue(), pair.get(1).stringValue())
        }

        val bodyChannel = ByteChannel(autoFlush = true)
        val responseBody: ByteReadChannel
        if (stream == null) {
            bodyChannel.close()
            responseBody = bodyChannel
        } else {
            val reader = stream.getReader()
            responseBody = FetchResponseBody(bodyChannel) {
                FetchJsBridge.abort(abortController)
                FetchJsBridge.cancelReader(reader)
            }
            data.executionContext.invokeOnCompletion { cause ->
                if (cause != null) {
                    FetchJsBridge.abort(abortController)
                    FetchJsBridge.cancelReader(reader)
                }
            }
            CoroutineScope(callContext).launch {
                try {
                    copyResponseBody(reader, bodyChannel)
                } catch (t: Throwable) {
                    bodyChannel.cancel(t)
                    FetchJsBridge.abort(abortController)
                    FetchJsBridge.cancelReader(reader)
                } finally {
                    reader.releaseLock()
                }
            }
        }

        return HttpResponseData(
            HttpStatusCode(statusCode, FetchJsBridge.responseStatusText(response)),
            GMTDate(),
            headers.build(),
            HttpProtocolVersion.HTTP_1_1,
            responseBody,
            callContext
        )
    }

    private suspend fun requestBody(content: OutgoingContent): ByteArray = when (content) {
        is OutgoingContent.NoContent -> ByteArray(0)
        is OutgoingContent.ByteArrayContent -> content.bytes()
        is OutgoingContent.ReadChannelContent -> content.readFrom().readAllBytes()
        else -> throw IllegalArgumentException("Fetch engine cannot send ${content::class.java.name} request bodies")
    }

    private suspend fun ByteReadChannel.readAllBytes(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = readAvailable(buffer, 0, buffer.size)
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private suspend fun copyResponseBody(reader: ReadableStreamDefaultReader, output: ByteChannel) {
        while (true) {
            val result = reader.read().awaitCancellable()
            if (result.isDone) break
            val bytes = result.value
            output.writeFully(bytes.toJavaByteArray(), 0, bytes.length)
        }
        output.close()
    }

    private fun Int8Array.toJavaByteArray(): ByteArray = copyToJavaArray()

    /** TeaVM's JSPromise.await uses TeaVM's async transformation, which does not cooperate with Kotlin Job cancellation. */
    private suspend fun <T> JSPromise<T>.awaitCancellable(): T = suspendCancellableCoroutine { continuation ->
        then({ value ->
            if (continuation.isActive) continuation.resume(value)
            null
        }, { reason ->
            if (continuation.isActive) continuation.resumeWithException(reason.asThrowable())
            null
        })
    }

    private fun Any?.asThrowable(): Throwable = when (this) {
        is Throwable -> this
        is JSObject -> RuntimeException(FetchJsBridge.rejectionMessage(this))
        else -> RuntimeException("Browser promise rejected: $this")
    }

}

/** Static TeaVM JSO bridge for browser Fetch and response streams. */
internal object FetchJsBridge {
    @JvmStatic
    @JSBody(params = ["reason"], script = "return reason && reason.message ? reason.message : String(reason);")
    external fun rejectionMessage(reason: JSObject): String

    @JvmStatic
    @JSBody(script = "return new AbortController();")
    external fun newAbortController(): JSObject

    @JvmStatic
    @JSBody(params = ["controller"], script = "return controller.signal;")
    external fun signal(controller: JSObject): JSObject

    @JvmStatic
    @JSBody(params = ["controller"], script = "controller.abort();")
    external fun abort(controller: JSObject)

    @JvmStatic
    @JSBody(params = ["reader"], script = "reader.cancel().catch(() => {});")
    external fun cancelReader(reader: ReadableStreamDefaultReader)

    @JvmStatic
    @JSBody(
        params = ["url", "method", "headerNames", "headerValues", "body", "signal"],
        script = """
            const headers = new Headers();
            for (let i = 0; i < headerNames.length; i++) headers.append(headerNames[i], headerValues[i]);
            const options = { method: method, headers: headers, signal: signal };
            if (body.length > 0) options.body = new Uint8Array(body);
            return fetch(url, options);
        """
    )
    external fun fetch(
        url: String,
        method: String,
        headerNames: Array<String>,
        headerValues: Array<String>,
        body: ByteArray,
        signal: JSObject,
    ): JSPromise<JSObject>

    @JvmStatic
    @JSBody(params = ["response"], script = "return response.status;")
    external fun responseStatus(response: JSObject): Int

    @JvmStatic
    @JSBody(params = ["response"], script = "return response.statusText;")
    external fun responseStatusText(response: JSObject): String

    @JvmStatic
    @JSBody(params = ["response"], script = "return response.body;")
    external fun responseBody(response: JSObject): ReadableStream?

    @JvmStatic
    @JSBody(params = ["response", "hasBody"], script = """
        const headers = Array.from(response.headers.entries());
        if (!hasBody) return headers;

        const contentEncoding = response.headers.get('Content-Encoding');
        const codings = contentEncoding == null
            ? []
            : contentEncoding.split(',').map(coding => coding.trim().toLowerCase()).filter(Boolean);
        // Fetch decodes these codings before exposing Response.body. Unknown codings stay
        // untouched so another Ktor layer can still handle them when supported.
        const fetchDecodedCodings = new Set(['gzip', 'x-gzip', 'deflate', 'br', 'zstd']);
        const nonIdentityCodings = codings.filter(coding => coding !== 'identity');
        const hasNonIdentityCoding = nonIdentityCodings.length > 0;
        const decodedByFetch = hasNonIdentityCoding &&
            nonIdentityCodings.every(coding => fetchDecodedCodings.has(coding));
        const sameOrigin = response.type === 'basic';
        return headers.filter(pair => {
            const lowerName = pair[0].toLowerCase();
            if (lowerName === 'content-length' && (!sameOrigin || hasNonIdentityCoding)) return false;
            if (lowerName === 'content-encoding' && decodedByFetch) return false;
            return true;
        });
    """)
    external fun responseHeaders(response: JSObject, hasBody: Boolean): JSArrayReader<JSArray<JSString>>
}

private class FetchRequestException(message: String, cause: Throwable) : RuntimeException(message, cause)

@OptIn(InternalAPI::class)
private class FetchResponseBody(
    private val channel: ByteChannel,
    private val onCancel: () -> Unit
) : ByteReadChannel by channel {
    override fun cancel(cause: Throwable?) {
        onCancel()
        channel.cancel(cause)
    }
}
