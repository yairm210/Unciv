package com.unciv.logic.web

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.unciv.logic.UncivShowableException
import org.teavm.jso.typedarrays.ArrayBuffer
import org.teavm.jso.typedarrays.Int8Array
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class WebHttpResponse(
    val status: Int,
    val statusText: String,
    val url: String,
    val headers: Map<String, String>,
    val text: String? = null,
    val bytes: ByteArray? = null,
) {
    val ok: Boolean get() = status in 200..299
}

object WebHttp {
    private const val defaultTimeoutMs = 30000
    private val jsonReader = JsonReader()

    suspend fun requestText(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = defaultTimeoutMs,
    ): WebHttpResponse = suspendCoroutine { continuation ->
        val headerNames = headers.keys.toTypedArray()
        val headerValues = headers.values.toTypedArray()
        val onSuccess = object : WebFetch.TextSuccess {
            override fun handle(status: Int, statusText: String, responseUrl: String, headersJson: String, text: String) {
                continuation.resume(
                    WebHttpResponse(
                        status = status,
                        statusText = statusText,
                        url = responseUrl,
                        headers = decodeHeaders(headersJson),
                        text = text,
                    )
                )
            }
        }
        val onError = object : WebFetch.ErrorCallback {
            override fun handle(message: String) {
                continuation.resumeWithException(UncivShowableException(message))
            }
        }
        WebFetch.fetchText(
            method,
            url,
            headerNames,
            headerValues,
            body,
            timeoutMs,
            onSuccess,
            onError
        )
    }

    suspend fun requestBytes(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeoutMs: Int = defaultTimeoutMs,
    ): WebHttpResponse = suspendCoroutine { continuation ->
        val headerNames = headers.keys.toTypedArray()
        val headerValues = headers.values.toTypedArray()
        val onSuccess = object : WebFetch.BinarySuccess {
            override fun handle(status: Int, statusText: String, responseUrl: String, headersJson: String, buffer: ArrayBuffer) {
                continuation.resume(
                    WebHttpResponse(
                        status = status,
                        statusText = statusText,
                        url = responseUrl,
                        headers = decodeHeaders(headersJson),
                        bytes = bufferToBytes(buffer),
                    )
                )
            }
        }
        val onError = object : WebFetch.ErrorCallback {
            override fun handle(message: String) {
                continuation.resumeWithException(UncivShowableException(message))
            }
        }
        WebFetch.fetchBytes(
            method,
            url,
            headerNames,
            headerValues,
            body,
            timeoutMs,
            onSuccess,
            onError
        )
    }

    private fun decodeHeaders(headersJson: String?): Map<String, String> {
        if (headersJson.isNullOrBlank()) return emptyMap()
        val parsed = try {
            jsonReader.parse(headersJson)
        } catch (_: Throwable) {
            return emptyMap()
        }
        if (parsed == null || !parsed.isObject) return emptyMap()
        val result = LinkedHashMap<String, String>()
        var child = parsed.child
        while (child != null) {
            result[child.name] = child.asString()
            child = child.next
        }
        return result
    }

    private fun bufferToBytes(buffer: ArrayBuffer): ByteArray {
        val view = Int8Array(buffer)
        val length = view.length
        val out = ByteArray(length)
        for (i in 0 until length) {
            out[i] = view[i].toByte()
        }
        return out
    }
}
