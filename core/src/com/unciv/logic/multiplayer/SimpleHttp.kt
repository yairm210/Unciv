package com.unciv.logic.multiplayer

import com.badlogic.gdx.Net
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.nio.charset.Charset

object SimpleHttp {
    fun sendGetRequest(url: String, action: (success: Boolean, result: String)->Unit) {
        sendRequest(Net.HttpMethods.GET, url, "", action)
    }

    fun sendRequest(method: String, url: String, content: String, action: (success: Boolean, result: String)->Unit) {
        var uri = URI(url)
        if (uri.host == null) uri = URI("http://$url")
        if (uri.port == -1) uri = URI(uri.scheme, uri.userInfo, uri.host, 8080, uri.path, uri.query, uri.fragment)

        with(uri.toURL().openConnection() as HttpURLConnection) {
            requestMethod = method  // default is GET

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
                action(true, text)
            } catch (t: Throwable) {
                println(t.message)
                val errorMessageToReturn =
                    if (errorStream != null) BufferedReader(InputStreamReader(errorStream)).readText()
                    else t.message!!
                println(errorMessageToReturn)
                action(false, errorMessageToReturn)
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
