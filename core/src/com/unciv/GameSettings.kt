package com.unciv

import com.unciv.logic.GameSaver

class GameSettings {
    var showWorkedTiles: Boolean = true
    var showResourcesAndImprovements: Boolean = true
    var language: String = "English"
    var resolution: String = "1050x700"
    var tutorialsShown = ArrayList<String>()

    fun save(){
        GameSaver().setGeneralSettings(this)
    }
}


@Deprecated("as of 2.6.9")
class OldGameSettings : LinkedHashMap<String, String>() {
    fun toGameSettings(): GameSettings {
        val newSettings = GameSettings()
        newSettings.showResourcesAndImprovements = showResourcesAndImprovements
        newSettings.showWorkedTiles = showWorkedTiles
        newSettings.language = language
        newSettings.resolution = resolution
        return newSettings
    }

    var showWorkedTiles:Boolean
        get() {
            if(this.containsKey("ShowWorkedTiles")) return get("ShowWorkedTiles")!!.toBoolean()
            else return true
        }
        set(value) {
            this["ShowWorkedTiles"]=value.toString()
        }

    var showResourcesAndImprovements:Boolean
        get() {
            if(this.containsKey("ShowResourcesAndImprovements")) return get("ShowResourcesAndImprovements")!!.toBoolean()
            else return true
        }
        set(value) {
            this["ShowResourcesAndImprovements"]=value.toString()
        }

    var language:String
        get() {
            if(this.containsKey("Language")) return get("Language")!!
            else return "English"
        }
        set(value) {
            this["Language"]=value
        }

    var resolution:String
        get() {
            if(this.containsKey("Resolution")) return get("Resolution")!!
            else return "1050x700"
        }
        set(value) {
            this["Resolution"]=value
        }
}


