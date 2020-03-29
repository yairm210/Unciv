package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.TileMap
import com.unciv.ui.saves.Gzip

class MapBasicInfo {
    var mapParameters = MapParameters()
    var createdWithMods = HashSet<String>()
    var requiredMods = HashSet<String>()
}

class MapSaver {
    fun json() = GameSaver().json()
    private val mapsFolder = "maps"

    fun getMap(mapName:String) = Gdx.files.local("$mapsFolder/$mapName")
    fun saveMap(mapName: String,tileMap: TileMap){
        if (UncivGame.Current.settings.saveMapsUncompressed)
            getMap(mapName).writeString(json().toJson(tileMap), false)
        else
            getMap(mapName).writeString(Gzip.zip(json().toJson(tileMap)), false)
    }
    private fun loadMapString(mapName: String): String {
        val loadedString = getMap(mapName).readString()
        if (loadedString[0]=='{') return loadedString
        return Gzip.unzip(loadedString)
    }
    fun loadMap(mapName: String): TileMap {
        return json().fromJson(TileMap::class.java, loadMapString(mapName))
    }
    fun loadMapInfo(mapName: String): MapBasicInfo {
        // light(er)weight load mods and parameters only
        return json().fromJson(MapBasicInfo::class.java, loadMapString(mapName))
    }

    fun deleteMap(mapName: String) = getMap(mapName).delete()

    fun getMaps() = Gdx.files.local(mapsFolder).list().sortedByDescending { it.lastModified() }.map { it.name() }

    fun mapFromJson(json:String): TileMap = json().fromJson(TileMap::class.java, json)

}