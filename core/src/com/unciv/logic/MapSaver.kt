package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.logic.map.TileMap
import com.unciv.ui.saves.Gzip

object MapSaver {

    fun json() = GameSaver.json()

    private const val mapsFolder = "maps"
    private const val saveZipped = false

    private fun getMap(mapName:String) = Gdx.files.local("$mapsFolder/$mapName")

    fun mapFromSavedString(mapString: String): TileMap {
        val unzippedJson = try {
            Gzip.unzip(mapString)
        } catch (ex: Exception) {
            mapString
        }
        return mapFromJson(unzippedJson)
    }
    fun mapToSavedString(tileMap: TileMap): String {
        val mapJson = json().toJson(tileMap)
        return if (saveZipped) Gzip.zip(mapJson) else mapJson
    }

    fun saveMap(mapName: String,tileMap: TileMap) {
        getMap(mapName).writeString(mapToSavedString(tileMap), false)
    }

    fun loadMap(mapFile:FileHandle):TileMap {
        return mapFromSavedString(mapFile.readString())
    }

    fun getMaps(): Array<FileHandle> = Gdx.files.local(mapsFolder).list()

    private fun mapFromJson(json:String): TileMap = json().fromJson(TileMap::class.java, json)
}