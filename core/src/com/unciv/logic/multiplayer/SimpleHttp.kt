package com.unciv.logic.multiplayer

import com.badlogic.gdx.Net
import com.unciv.UncivGame
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.*
import java.nio.charset.Charset

object SimpleHttp {
    fun sendGetRequest(url: String, action: (success: Boolean, result: String)->Unit) {
        sendRequest(Net.HttpMethods.GET, url, "", action)
    }

    fun sendRequest(method: String, url: String, content: String, action: (success: Boolean, result: String)->Unit) {
        var uri = URI(url)
        if (uri.host == null) uri = URI("http://$url")

        val urlObj: URL
        try {
            urlObj = uri.toURL()
        } catch (t:Throwable){
            action(false, "Bad URL")
            return
        }
        
        with(urlObj.openConnection() as HttpURLConnection) {
            requestMethod = method  // default is GET
            if (UncivGame.isCurrentInitialized())
                setRequestProperty("User-Agent", "Unciv/${UncivGame.Current.version}-GNU-Terry-Pratchett")
            else
                setRequestProperty("User-Agent", "Unciv/Turn-Checker-GNU-Terry-Pratchett")

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
