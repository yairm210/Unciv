package com.unciv.ui

class GameSettings : LinkedHashMap<String, String>() {

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
