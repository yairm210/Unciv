package com.unciv.ui.musicmanager

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Net
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.net.HttpRequestBuilder
import com.unciv.models.metadata.MusicDownloadTrack

class MusicMgrDownloader {
    var maxConcurrent = 3
    private var tracks = listOf( MusicDownloadTrack )

    companion object {
        fun downloadFile(
                url: String,
                file: FileHandle,
                ID: String = "",
                checkType: String = "",
                completionEvent: ((ID: String, status: Int, message: String) -> Unit)? = null
        ) {
            val requestBuilder = HttpRequestBuilder()
            val request = requestBuilder.newRequest().method("GET").url(url).build()
            Gdx.net.sendHttpRequest (request, object : Net.HttpResponseListener {
                override fun handleHttpResponse (httpResponse: Net.HttpResponse?) {
                    if (httpResponse==null) return
                    //println ("Downloader received a response, status ${httpResponse.status.statusCode}, headers: ${httpResponse.headers}")
                    val contentType = httpResponse.headers["Content-Type"]?.first() ?: ""
                    if (checkType.isEmpty() || contentType.isEmpty() || contentType.startsWith(checkType)) {
                        var status = httpResponse.status.statusCode
                        if (status in 200..299) {
                            var message = "OK"
                            try {
                                file.writeBytes(httpResponse.result, false)
                                println("File downloaded: $ID")
                            } catch (ex: Exception) {
                                println("Saving download failed: $ID")
                                status = -1
                                message = ex.toString()
                            }
                            completionEvent?.invoke(ID, status, message)
                        } else {
                            println ("File download $ID failed - wrong status: $status")
                            completionEvent?.invoke (ID, -1, "Wrong status: $status")
                        }
                    } else {
                        println ("File download $ID failed - wrong Content-Type: $contentType")
                        completionEvent?.invoke (ID, -1, "Wrong content type: $contentType")
                    }
                }
                override fun cancelled() {
                    completionEvent?.invoke (ID, 0, "Cancelled")
                }
                override fun failed(t: Throwable?) {
                    println ("File download $ID failed: ${t.toString()}")
                    completionEvent?.invoke (ID, -1, t?.toString() ?: "Failed")
                }
            })

        }
    }
}