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
    private var tracks = mutableListOf<MusicDownloadTrack>()
    private var underway = mutableListOf<String>()
    private val internalMessages = mutableListOf<String>()
    @Volatile var isRunning = false
        private set
    @Volatile private var canPost = false
    private var watchdog: Timer? = null
    var successCount: Int = 0
        private set
    var failureCount: Int = 0
        private set
    val messages
        get() = internalMessages.toList()
    private var currentProgressEvent: ((MusicMgrDownloader) -> Unit)? = null
    private var currentCompletionEvent: ((MusicMgrDownloader) -> Unit)? = null

    fun queueDownloads (music: MusicDownloadInfo, append: Boolean = false) {
        if (!append) tracks.clear()
        tracks.addAll (
            music.groups.filter { it.selected }
                .flatMap { group -> group.tracks.filter { !it.isPresent() } }
        )
    }

    fun startDownload(progressEvent: ((MusicMgrDownloader)->Unit)?, completionEvent: ((MusicMgrDownloader) -> Unit)?) {
        if (isRunning) return
        internalMessages.clear()
        successCount = 0
        failureCount = 0
        canPost = true
        isRunning = true
        currentProgressEvent = progressEvent
        currentCompletionEvent = completionEvent

        // bring maxConcurrent downloads underway, their completion callbacks will queue more
        // Note the request creation and sending the request packet will run on the main thread
        // but the wait for the result will not. So - no subthread for now for the request dispatch.
        for (i in 1..maxConcurrent) {
            nextDownload()
        }
    }

    private fun nextDownload() {
        var wdTimeout = 0L
        if (canPost && tracks.size > 0 && underway.size < maxConcurrent) {
            val track = tracks.removeAt(0)
            underway.add (track.ID)
            wdTimeout = track.getEstimatedDLDuration()          // already conservative, no need to increase
            track.downloadTrack { ID, status, message -> run {
                if (status <= 0) {
                    failureCount++
                    internalMessages.add(message)
                } else {
                    successCount++
                    internalMessages.add("$ID successfully downloaded.")
                }
                underway.remove(ID)
                Gdx.app.postRunnable { currentProgressEvent?.invoke(this) }
                // better have all downloadTrack calls running from the same thread
                Gdx.app.postRunnable(this::nextDownload)
            } }
        }
        if (underway.size == 0) {
            currentCompletionEvent?.invoke(this)
            currentCompletionEvent = null
            stopDownload()
        } else
            restartWatchdog(wdTimeout)
    }

    private fun restartWatchdog(timeoutSuggestion: Long) {
        watchdog?.cancel()
        val timeout = if (timeoutSuggestion==0L) watchdogTimeout else timeoutSuggestion
        watchdog = timer("DownloadWatchdog", true, timeout, 1000000000000L) {
            stopDownload()
            internalMessages.add("Timeout")
        }
    }

    fun stopDownload() {
        watchdog?.cancel()
        canPost = false
        underway.clear()
        //currentProgressEvent = null
        //currentCompletionEvent = null
        watchdog?.purge()
        watchdog = null
        isRunning = false
    }

    companion object {
        private const val maxConcurrent = 3
        private const val httpMethod = "GET"
        private const val mimeHeaderName = "Content-Type"
        private const val followRedirects = true
        private const val requestTimeout = 3000                  // passed to request: Time from request sent to response starts arriving IMHO
        private const val watchdogTimeout = 20000L        // default for our own watchdog: Time without callback. Must include response finishes arriving and file saving
        private const val debugMessages = true

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
                    .timeout(requestTimeout)
                    .build()
            if (debugMessages) println("Sending download request: $ID")
            Gdx.net.sendHttpRequest (request, object : Net.HttpResponseListener {
                override fun handleHttpResponse (httpResponse: Net.HttpResponse?) {
                    if (httpResponse==null) return
                    //if (debugMessages) println ("Downloader received a response, status ${httpResponse.status.statusCode}, headers: ${httpResponse.headers}")
                    val contentType = httpResponse.headers[mimeHeaderName]?.first() ?: ""
                    if (checkType.isEmpty() || contentType.isEmpty() || Regex(checkType).containsMatchIn(contentType)) {
                        var status = httpResponse.status.statusCode
                        if (status in 200..299) {
                            var message = "OK"
                            try {
                                file.writeBytes(httpResponse.result, false)
                                if (debugMessages) println("File downloaded: $ID")
                            } catch (ex: Exception) {
                                if (debugMessages) println("Saving download failed: $ID")
                                status = -1
                                message = ex.toString()
                            }
                            completionEvent?.invoke(ID, status, message)
                        } else {
                            if (debugMessages) println ("File download $ID failed - wrong status: $status")
                            completionEvent?.invoke (ID, -1, "Wrong status: $status")
                        }
                    } else {
                        if (debugMessages) println ("File download $ID failed - wrong Content-Type: $contentType")
                        completionEvent?.invoke (ID, -1, "Wrong content type: $contentType")
                    }
                }
                override fun cancelled() {
                    completionEvent?.invoke (ID, 0, "Cancelled")
                }
                override fun failed(t: Throwable?) {
                    if (debugMessages) println ("File download $ID failed: ${t.toString()}")
                    completionEvent?.invoke (ID, -1, t?.toString() ?: "Failed")
                }
            })
        }
    }
}