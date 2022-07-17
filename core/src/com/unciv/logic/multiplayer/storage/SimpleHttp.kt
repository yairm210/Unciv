package com.unciv.logic.multiplayer.storage

import com.badlogic.gdx.Net
import com.unciv.UncivGame
import com.unciv.ui.utils.extensions.toNiceString
import com.unciv.utils.concurrency.withThreadPoolContext
import com.unciv.utils.debug
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.nio.charset.Charset


object SimpleHttp {
    data class RequestResult(
        val success: Boolean,
        /** Contains the result body if the request was successful, or the error message if not */
        val body: String,
        val code: Int? = null
    )

    suspend fun sendGetRequest(url: String, timeout: Int = 5000): RequestResult {
        return sendRequest(Net.HttpMethods.GET, url, "", timeout)
    }

    suspend fun sendRequest(method: String, url: String, content: String, timeout: Int = 500) = withThreadPoolContext toplevel@{
        var uri = URI(url)
        if (uri.host == null) uri = URI("http://$url")

        val urlObj: URL
        try {
            urlObj = uri.toURL()
        } catch (t: Throwable) {
            return@toplevel RequestResult(false, "Bad URL")
        }

        with(urlObj.openConnection() as HttpURLConnection) {
            requestMethod = method  // default is GET
            connectTimeout = timeout
            instanceFollowRedirects = true
            if (UncivGame.isCurrentInitialized()) {
                setRequestProperty("User-Agent", "Unciv/${UncivGame.VERSION.toNiceString()}-GNU-Terry-Pratchett")
            } else {
                setRequestProperty("User-Agent", "Unciv/Turn-Checker-GNU-Terry-Pratchett")
            }

            try {
                if (content.isNotEmpty()) {
                    doOutput = true
                    // StandardCharsets.UTF_8 requires API 19
                    val postData: ByteArray = content.toByteArray(Charset.forName("UTF-8"))
                    val outputStream = DataOutputStream(outputStream)
                    outputStream.write(postData)
                    outputStream.flush()
                }

                val text = BufferedReader(InputStreamReader(inputStream)).readText()
                return@toplevel RequestResult(true, text, responseCode)
            } catch (t: Throwable) {
                debug("Error during HTTP request", t)
                val errorMessageToReturn = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).readText()
                } else {
                    t.message!!
                }
                debug("Returning error message [%s]", errorMessageToReturn)
                return@toplevel RequestResult(false, errorMessageToReturn, if (errorStream != null) responseCode else null)
            }
        }
    }

    fun getIpAddress(): String? {
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002)
            return socket.localAddress.hostAddress
        }
    }

    /** Builds an URL out of the provided components, automatically joining them with a `/`. The components are only allowed to have no slashes or one slash at the end. */
    fun buildURL(vararg components: String): String = components.reduce { acc, cur ->
        acc.removeSuffix("/") + "/" + cur.removeSuffix("/")
    }
}
