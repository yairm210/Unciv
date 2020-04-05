package com.unciv.models.metadata

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Net
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.net.HttpRequestBuilder
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter
import com.unciv.ui.musicmanager.MusicMgrDownloader
import java.io.File

data class MusicDownloadTrack (
        var title: String,              // track name as chosen by the author
        var info: String,               // optional info (like track length in brackets)
        var localFileName: String,      // relative filename (no path, include extension)
        var url: String                 // url to download from
) {
    constructor() : this("","","", "")          // for the json parser

    @Transient internal var path = ""
    val ID
        get() = path + File.separator + localFileName

    fun isPresent (): Boolean {
        return file() != null
    }
    fun file(): FileHandle? {
        val pathFile = Gdx.files.local(path)
        val nameNoExt = pathFile.child(localFileName).nameWithoutExtension()
        return allowedExtensions
                .map { pathFile.child("$nameNoExt.$it") }
                .firstOrNull { it.exists() && !it.isDirectory }
    }
    fun downloadTrack(completionEvent: ((ID: String, status: Int, message: String) -> Unit)?) {
        MusicMgrDownloader.downloadFile(url,file()!!,ID,"audio/") { ID, status, message -> completionEvent?.invoke(ID, status, message) }
    }
    override fun toString(): String {
        return super.toString() + " ($ID)"
    }
    companion object {
        val allowedExtensions = listOf("mp3","ogg","m4a")
    }
}

data class MusicDownloadGroup (
        var title: String,              // Playlist caption
        var cover: String,              // url to download cover image from
        var coverLocal: String,         // local filename (no path), also serves as unique key
        var description: String,        // lengthy blurb about this collection
        var credits: String,            // author (one link recognized, makes widget touchable)
        var attribution: String,        // license (one link recognized, makes widget touchable)
        var tracks: List<MusicDownloadTrack>
) {
    constructor() : this ( "", "", "", "", "", "", emptyList<MusicDownloadTrack>() )     // for the json parser

    @Transient var selected = false
    @Transient var coverDownloadHasFailed = false
        private set
    @Transient internal var path = ""
        set(value) {
            field = value
            tracks.forEach { it.path = value }
        }

    fun isPresent(): Boolean {
        return tracks.all { it.isPresent() }
    }

    override fun toString(): String {
        return super.toString() + " ($title: ${tracks.size} tracks)"
    }

    private fun getCachedCoverFile(): FileHandle? {
        if (coverLocal.isEmpty()) return null
        val cacheDir = Gdx.files.local(coverCacheDir)
        if (!cacheDir.exists()) cacheDir.mkdirs()
        return cacheDir.child(coverLocal)
    }
    fun hasCover(): Boolean = !(cover.isEmpty() || coverLocal.isEmpty())
    fun isCoverCached(): Boolean = getCachedCoverFile()?.exists() ?: false
    fun shouldDownloadCover(): Boolean = hasCover() && !coverDownloadHasFailed && !isCoverCached()
    fun getCachedCover(): Texture? {
        val cachedFile = getCachedCoverFile()
        return if (cachedFile!=null) Texture(cachedFile) else null
    }
    fun downloadCover (completionEvent: ((ID: String, status: Int, message: String) -> Unit)?) {
        // asynchronously fetch a cover image to local cache, then notify the caller
        // Note that completionEvent receiver will not run on the main thread
        // ID is the coverLocal name of this instance, but any immutable unique string
        // recoverable from the instance would do: when the client is working with this instance,
        // posts a downloadCover request, then receives the notification a while later, the user
        // may already have modified the context so the finished download might no longer be relevant.
        // It is the responsibility of the client to check this, and for this purpose it gets the ID back.
        if (!hasCover()) return
        MusicMgrDownloader.downloadFile(cover,getCachedCoverFile()!!,coverLocal,"image/") { ID, status, message ->
            run {
                coverDownloadHasFailed = true
                completionEvent?.invoke(ID, status, message)
            }
        }
    }

    companion object {
        private val coverCacheDir = "cover-cache"
    }
}

data class MusicDownloadInfo ( var groups: ArrayList<MusicDownloadGroup> ) {
    constructor() : this(ArrayList<MusicDownloadGroup>())                   // for the json parser

    private fun setPath (path: String) {
        groups.forEach { it.path = path }
    }

    fun anySelected(): Boolean {
        return groups.any { it.selected }
    }

    val size: Int
        get() = this.groups.size

    operator fun plusAssign (other: MusicDownloadInfo) {
        groups.addAll (other.groups)
    }
    override fun toString(): String {
        return super.toString() + groups.joinToString(prefix = " (", postfix = ")") { it.title }
    }

    fun save (fileName: String = defaultFileName, pretty: Boolean = false) {
        var file = Gdx.files.local(jsonsFolder).child(fileName)
        var jsonStr = json().toJson(this)
        if (pretty) {
            val settings = JsonValue.PrettyPrintSettings()
            settings.outputType = JsonWriter.OutputType.json
            settings.singleLineColumns = 4
            settings.wrapNumericArrays = false
            jsonStr = json().prettyPrint(jsonStr, settings)
        }
        file.writeString(jsonStr, false)
    }

    companion object {
        private val defaultFileName = "MusicDownloadInfo.json"
        private val jsonsFolder = "jsons"
        private val musicFolder = "music"
        private val modsFolder = "mods"

        fun json() = Json().apply { setIgnoreDeprecated(true); ignoreUnknownFields = true }

        fun load (path: String = jsonsFolder, fileName: String = defaultFileName): MusicDownloadInfo {
            var file = Gdx.files.local(path).child(fileName)
            if (!file.exists()) return MusicDownloadInfo()
            val info = json().fromJson(MusicDownloadInfo::class.java, file)
            val musicPath = file.parent().parent().child(musicFolder).path()
            info.setPath (musicPath)
            return info
        }
        fun load (paths: List<String>, fileName: String = defaultFileName): MusicDownloadInfo {
            var result = MusicDownloadInfo()
            paths.forEach {
                result += load (it, fileName)
            }
            return result
        }
        fun load (allMods: Boolean): MusicDownloadInfo {
            val paths = mutableListOf(jsonsFolder)
            if (allMods) {
                paths += Gdx.files.local(modsFolder)
                        .list { name: File? -> name?.isDirectory ?: false }
                        .map { it.child(jsonsFolder) }
                        .filter { it.exists() }
                        .map { it.path() }
            }
            return load(paths)
        }
    }
}
