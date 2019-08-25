package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.unciv.logic.map.TileMap
import com.unciv.ui.saves.Gzip

class MapSaver(){
    fun json() = GameSaver().json()
    private val mapsFolder = "maps"

    fun getMap(mapName:String) = Gdx.files.local("$mapsFolder/$mapName")
    fun saveMap(mapName: String,tileMap: TileMap){
        getMap(mapName).writeString(Gzip.zip(json().toJson(tileMap)), false)
    }
    fun loadMap(mapName: String): TileMap {
        val gzippedString = getMap(mapName).readString()
        val unzippedJson = Gzip.unzip(gzippedString)
        return json().fromJson(TileMap::class.java, unzippedJson)
    }
    fun deleteMap(mapName: String) = getMap(mapName).delete()

    fun getMaps() = Gdx.files.local(mapsFolder).list().map { it.name() }

    fun mapFromJson(json:String): TileMap = json().fromJson(TileMap::class.java, json)

}