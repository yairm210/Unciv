package com.unciv.ui.musicmanager

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Net
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.net.HttpRequestBuilder
import com.unciv.models.metadata.MusicDownloadInfo
import com.unciv.models.metadata.MusicDownloadTrack
import java.util.*
import kotlin.concurrent.timer


class MusicMgrDownloader {
    var maxConcurrent = 3
    var interval = 100L
    private var tracks = mutableListOf<MusicDownloadTrack>()
    private var underway = mutableListOf<String>()
    private var downloadTimer: Timer? = null
    private val errorMessages = mutableListOf<String>()
    @Volatile private var canPost = false
    val isRunning
        get() = downloadTimer != null && (underway.size > 0 || tracks.size > 0)
    val messages
        get() = errorMessages.toList()

    fun queueDownloads (music: MusicDownloadInfo) {
        tracks.addAll (
            music.groups.filter { it.selected }
                .flatMap { group -> group.tracks.filter { !it.isPresent() } }
        )
    }

    fun startDownload(completionEvent: (() -> Unit)?) {
        if (isRunning) return
        errorMessages.clear()
        canPost = true
        downloadTimer = timer("Downloader", true, period = interval) {
            while (canPost && tracks.size + underway.size > 0) {
                if (underway.size < maxConcurrent && tracks.size > 0) {
                    val track = tracks.removeAt(0)
                    underway.add(track.ID)
                    track.downloadTrack { ID, status, message -> run { if (status <= 0) errorMessages.add(message); underway.remove(ID) } }
                }
            }
            Gdx.app.postRunnable {
                downloadTimer = null
                completionEvent?.invoke()
            }
        }
    }

    fun stopDownload() {
        canPost = false
        if ( downloadTimer != null ) downloadTimer!!.cancel()
    }

    companion object {
        val httpMethod = "GET"
        val mimeHeaderName = ""
        val followRedirects = true
        val timeout = 3000

        fun downloadFile(
                url: String,
                file: FileHandle,
                ID: String = "",
                checkType: String = "",
                completionEvent: ((ID: String, status: Int, message: String) -> Unit)? = null
        ) {
            // Asynchronously fetch a file to local cache, then optionally notify the caller.
            // Note that completionEvent receiver will not run on the main thread.
            // ID should be any string that can be used by the caller to uniquely recognize the
            // requested file within the callback, such as the local path. Can be used e.g. to
            // make sure a visual representation of the requested file arriving just now actually
            // matches the current context - the user may have moved on, the finished download
            // might no longer be immediately relevant.
            // It is the responsibility of the client to check this, and for this purpose it gets the ID back.
            // Note that the HttpRequest doesn't have a cancel or similar method, thus it is not returned.
            val requestBuilder = HttpRequestBuilder()
            val request = requestBuilder
                    .newRequest().method(httpMethod).url(url)
                    .followRedirects(followRedirects)
                    .timeout(timeout)
                    .build()
            Gdx.net.sendHttpRequest (request, object : Net.HttpResponseListener {
                override fun handleHttpResponse (httpResponse: Net.HttpResponse?) {
                    if (httpResponse==null) return
                    //println ("Downloader received a response, status ${httpResponse.status.statusCode}, headers: ${httpResponse.headers}")
                    val contentType = httpResponse.headers[mimeHeaderName]?.first() ?: ""
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