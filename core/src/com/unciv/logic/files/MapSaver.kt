package com.unciv.logic.files

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.json.json
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.TileMap
import com.unciv.ui.screens.savescreens.Gzip

object MapSaver {

    const val mapsFolder = "maps"
    var saveZipped = true

    private fun getMap(mapName: String) = Gdx.files.local("$mapsFolder/$mapName")

    fun mapFromSavedString(mapString: String): TileMap {
        val unzippedJson = try {
            Gzip.unzip(mapString.trim())
        } catch (_: Exception) {
            mapString
        }
        return mapFromJson(unzippedJson)
    }
    fun mapToSavedString(tileMap: TileMap): String {
        tileMap.assignContinents(TileMap.AssignContinentsMode.Reassign)
        val mapJson = json().toJson(tileMap)
        return if (saveZipped) Gzip.zip(mapJson) else mapJson
    }

    fun saveMap(mapName: String, tileMap: TileMap) {
        getMap(mapName).writeString(mapToSavedString(tileMap), false, Charsets.UTF_8.name())
    }

    fun loadMap(mapFile: FileHandle): TileMap {
        return mapFromSavedString(mapFile.readString(Charsets.UTF_8.name()))
    }

    fun getMaps(): Array<FileHandle> = Gdx.files.local(mapsFolder).list()

    private fun mapFromJson(json: String): TileMap = json().fromJson(TileMap::class.java, json)

    /** Class to parse only the parameters out of a map file */
    private class TileMapPreview {
        val mapParameters = MapParameters()
    }

    fun loadMapParameters(mapFile: FileHandle): MapParameters {
        return mapParametersFromSavedString(mapFile.readString())
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun mapParametersFromSavedString(mapString: String): MapParameters {
        val unzippedJson = try {
            Gzip.unzip(mapString.trim())
        } catch (_: Exception) {
            mapString
        }
        return json().fromJson(TileMapPreview::class.java, unzippedJson).mapParameters
    }
}
