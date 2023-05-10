package com.unciv.logic.multiplayer.storage

import com.badlogic.gdx.Net
import com.unciv.UncivGame
import com.unciv.utils.Log
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

private typealias SendRequestCallback = (success: Boolean, result: String, code: Int?)->Unit

object SimpleHttp {
    fun sendGetRequest(url: String, timeout: Int = 5000, header: Map<String, String>? = null, action: SendRequestCallback) {
        sendRequest(Net.HttpMethods.GET, url, "", timeout, header, action)
    }

    fun sendRequest(method: String, url: String, content: String, timeout: Int = 5000, header: Map<String, String>? = null, action: SendRequestCallback) {
        var uri = URI(url)
        if (uri.host == null) uri = URI("http://$url")

        val urlObj: URL
        try {
            urlObj = uri.toURL()
        } catch (t: Throwable) {
            Log.debug("Bad URL", t)
            action(false, "Bad URL", null)
            return
        }

        with(urlObj.openConnection() as HttpURLConnection) {
            requestMethod = method  // default is GET
            connectTimeout = timeout
            instanceFollowRedirects = true
            if (UncivGame.isCurrentInitialized())
                setRequestProperty("User-Agent", "Unciv/${UncivGame.VERSION.toNiceString()}-GNU-Terry-Pratchett")
            else
                setRequestProperty("User-Agent", "Unciv/Turn-Checker-GNU-Terry-Pratchett")
            setRequestProperty("Content-Type", "text/plain")

            for ((key, value) in header.orEmpty()) {
                setRequestProperty(key, value)
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
                action(true, text, responseCode)
            } catch (t: Throwable) {
                debug("Error during HTTP request", t)
                val errorMessageToReturn =
                    if (errorStream != null) BufferedReader(InputStreamReader(errorStream)).readText()
                    else t.message!!
                debug("Returning error message [%s]", errorMessageToReturn)
                action(false, errorMessageToReturn, responseCode)
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
