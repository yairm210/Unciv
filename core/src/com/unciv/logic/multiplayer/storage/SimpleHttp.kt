package com.unciv.logic.multiplayer.storage

import com.badlogic.gdx.Net
import com.unciv.UncivGame
import com.unciv.utils.Log
import com.unciv.utils.debug
import io.ktor.http.*
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL

private typealias SendRequestCallback = (success: Boolean, result: String, code: Int?)->Unit

object SimpleHttp {
    fun sendGetRequest(url: String, timeout: Int = 5000, header: Map<String, String>? = null, action: SendRequestCallback) {
        sendRequest(Net.HttpMethods.GET, url, "", timeout, header, action)
    }

    fun sendRequest(method: String, url: String, content: String, timeout: Int = 5000, header: Map<String, String>? = null, action: SendRequestCallback) {
        val response = try {
            openStream(method, url, content, timeout, header)
        } catch (ex: HttpStatusException) {
            debug("Returning error message [%s]", ex.message)
            action(false, ex.message, ex.code)
            return
        }

        val text = try {
            response.body.use { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }
        } catch (ex: IOException) {
            debug("Error while reading HTTP response", ex)
            action(false, ex.message ?: "Unknown error", response.code)
            return
        }
        action(true, text, response.code)
    }

    /** A successful response: [body] must be closed by the caller, which also disconnects. */
    class HttpResponse(val code: Int, val body: InputStream)

    /** Thrown by [openStream] on a bad URL or error response. [code] is null if there was no HTTP status. */
    class HttpStatusException(val code: Int?, override val message: String) : Exception(message)

    /**
     * Sends the request and returns as soon as the response headers arrive, so the body can be processed as it arrives.
     * @throws HttpStatusException on a bad URL or error response (with the error body, if any, as message)
     */
    fun openStream(method: String, url: String, content: String, timeout: Int = 5000, header: Map<String, String>? = null): HttpResponse {
        var uri = URI(url)
        if (uri.host == null) uri = URI("http://$url")

        val urlObj: URL
        try {
            urlObj = uri.toURL()
        } catch (t: Throwable) {
            Log.debug("Bad URL", t)
            throw HttpStatusException(null, "Bad URL")
        }

        with(urlObj.openConnection() as HttpURLConnection) {
            requestMethod = method  // default is GET
            connectTimeout = timeout
            instanceFollowRedirects = true
            setRequestProperty(HttpHeaders.UserAgent, UncivGame.getUserAgent("Turn-Checker"))
            setRequestProperty(HttpHeaders.ContentType, "text/plain")

            for ((key, value) in header.orEmpty()) {
                setRequestProperty(key, value)
            }

            try {
                if (content.isNotEmpty()) {
                    doOutput = true
                    val postData: ByteArray = content.toByteArray(Charsets.UTF_8)
                    val outputStream = DataOutputStream(outputStream)
                    outputStream.write(postData)
                    outputStream.flush()
                }

                val body = object : FilterInputStream(inputStream) {
                    override fun close() {
                        try { super.close() } finally { disconnect() }
                    }
                }
                return HttpResponse(responseCode, body)
            } catch (t: Throwable) {
                debug("Error during HTTP request", t)
                val errorMessage = errorStream?.use { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }
                    ?: t.message ?: "Unknown error"
                val code = try { responseCode } catch (_: IOException) { null }
                disconnect()
                throw HttpStatusException(code, errorMessage)
            }
        }
    }

    fun getIpAddress(): String? {
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002)
            return socket.localAddress.hostAddress
        }
    }
}
