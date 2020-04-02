package com.unciv.models.metadata

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.JsonWriter

data class MusicDownloadFile ( var title: String, var localFileName: String, var url: String ) {
    constructor() : this("","","")
}

data class MusicDownloadGroup ( var title: String, var description: String, var credits: String, var license: String, var files: List<MusicDownloadFile> ) {
    constructor() : this ( "", "", "", "", emptyList<MusicDownloadFile>() )
}

data class MusicDownloadInfo ( var groups: List<MusicDownloadGroup> ) {
    constructor() : this(emptyList<MusicDownloadGroup>())

    fun save(fileName: String = "MusicDownloadInfo.json", pretty: Boolean = false) {
        var file = Gdx.files.local("jsons").child(fileName)
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
        fun json() = Json().apply { setIgnoreDeprecated(true); ignoreUnknownFields = true }

        fun load(fileName: String = "MusicDownloadInfo.json"): MusicDownloadInfo {
            var file = Gdx.files.local("jsons").child(fileName)
            if (!file.exists()) return MusicDownloadInfo()
            return json().fromJson(MusicDownloadInfo::class.java, file)
        }
    }
}

/*    fun generateSample(): MusicDownloadInfo {
        var new = MusicDownloadInfo()
        var group = MusicDownloadGroup()
        group.title = "Default track"
        group.description = "This is the default track Unciv adopted long ago."
        group.credits = "The tracks in this group are by Kevin MacLeod (https://incompetech.com)"
        group.license = """Licensed under Creative Commons: By Attribution 4.0 License
http://creativecommons.org/licenses/by/4.0/"""
        group.files = listOf(
                MusicDownloadFile("Thatched Villagers", "Ambient_Thatched-Villagers.mp3", "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Thatched%20Villagers.mp3")
        )
        new.groups = listOf(group)
        return new
    }*/

