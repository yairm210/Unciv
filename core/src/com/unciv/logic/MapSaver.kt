package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.logic.map.TileMap
import com.unciv.ui.saves.Gzip

object MapSaver {

    fun json() = GameSaver.json()

    private const val mapsFolder = "maps"

    private fun getMap(mapName:String) = Gdx.files.local("$mapsFolder/$mapName")

    fun saveMap(mapName: String,tileMap: TileMap) {
        getMap(mapName).writeString(Gzip.zip(json().toJson(tileMap)), false)
    }

    fun loadMap(mapFile:FileHandle):TileMap {
        val gzippedString = mapFile.readString()
        val unzippedJson = Gzip.unzip(gzippedString)
        return json().fromJson(TileMap::class.java, unzippedJson)
    }

    fun getMaps() = Gdx.files.local(mapsFolder).list()

    fun mapFromJson(json:String): TileMap = json().fromJson(TileMap::class.java, json)
}