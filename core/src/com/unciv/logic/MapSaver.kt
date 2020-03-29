package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.logic.map.TileMap
import com.unciv.ui.saves.Gzip

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
    fun loadMap(mapName: String): TileMap {
        val loadedString = getMap(mapName).readString()
        var unzippedJson = loadedString
        if (loadedString[0]!='{') {
            unzippedJson = Gzip.unzip(loadedString)
        }
        return json().fromJson(TileMap::class.java, unzippedJson)
    }
    fun deleteMap(mapName: String) = getMap(mapName).delete()

    fun getMaps() = Gdx.files.local(mapsFolder).list().sortedByDescending { it.lastModified() }.map { it.name() }

    fun mapFromJson(json:String): TileMap = json().fromJson(TileMap::class.java, json)

}