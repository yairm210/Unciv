package com.unciv.models.metadata

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter
import java.io.File

data class MusicDownloadFile ( var title: String, var localFileName: String, var url: String ) {
    constructor() : this("","","")          // for the json parser

    fun isPresent(path: String): Boolean {
        val pathFile = Gdx.files.local(path)
        val nameNoExt = pathFile.child(localFileName).nameWithoutExtension()
        if (listOf("mp3","ogg","m4a").any { pathFile.child("$nameNoExt.$it").takeIf { it.exists() && !it.isDirectory } != null })
            return true
        else
            return false
    }
}

data class MusicDownloadGroup (var title: String, var description: String, var credits: String, var attribution: String, var files: List<MusicDownloadFile> ) {
    constructor() : this ( "", "", "", "", emptyList<MusicDownloadFile>() )     // for the json parser

    @Transient var selected = false
    @Transient internal var path = ""

    fun isPresent(): Boolean {
        return files.all { it.isPresent(path) }
    }
}

data class MusicDownloadInfo ( var groups: ArrayList<MusicDownloadGroup> ) {
    constructor() : this(ArrayList<MusicDownloadGroup>())                   // for the json parser

    private fun setPath (path: String) {
        groups.forEach { it.path = path }
    }

    val size: Int
        get() = this.groups.size

    operator fun plusAssign (other: MusicDownloadInfo) {
        groups.addAll (other.groups)
    }
    override fun toString(): String {
        return super.toString() + groups.joinToString(prefix = " (", postfix = ")") { it.title }
    }

    fun save(fileName: String = defaultFileName, pretty: Boolean = false) {
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

        fun load(path: String = jsonsFolder, fileName: String = defaultFileName): MusicDownloadInfo {
            var file = Gdx.files.local(path).child(fileName)
            if (!file.exists()) return MusicDownloadInfo()
            val info = json().fromJson(MusicDownloadInfo::class.java, file)
            val musicPath = file.parent().parent().child(musicFolder).path()
            info.setPath (musicPath)
            return info
        }
        fun load(paths: List<String>, fileName: String = defaultFileName): MusicDownloadInfo {
            var result = MusicDownloadInfo()
            paths.forEach {
                result += load (it, fileName)
            }
            return result
        }
        fun load(allMods: Boolean): MusicDownloadInfo {
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
