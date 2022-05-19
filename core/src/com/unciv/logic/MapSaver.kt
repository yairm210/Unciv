package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.json.json
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.TileMap
import com.unciv.ui.saves.Gzip

object MapSaver {

    const val mapsFolder = "maps"
    var saveZipped = true

    private fun getMap(mapName:String) = Gdx.files.local("$mapsFolder/$mapName")

    fun mapFromSavedString(mapString: String, checkSizeErrors: Boolean = true): TileMap {
        val unzippedJson = try {
            Gzip.unzip(mapString.trim())
        } catch (ex: Exception) {
            mapString
        }
        return mapFromJson(unzippedJson).apply {
            // old maps (rarely) can come with mapSize fields not matching tile list
            if (checkSizeErrors && mapParameters.getArea() != values.size)
                throw UncivShowableException("Invalid map: Area ([${values.size}]) does not match saved dimensions ([${mapParameters.displayMapDimensions()}]).")
            // compatibility with rare maps saved with old mod names
            if (!checkSizeErrors)
                mapParameters.mods.filter { '-' in it }.forEach {
                    mapParameters.mods.remove(it)
                    mapParameters.mods.add(it.replace('-',' '))
                }
        }
    }
    fun mapToSavedString(tileMap: TileMap): String {
        tileMap.assignContinents(TileMap.AssignContinentsMode.Reassign)
        val mapJson = json().toJson(tileMap)
        return if (saveZipped) Gzip.zip(mapJson) else mapJson
    }

    fun saveMap(mapName: String,tileMap: TileMap) {
        getMap(mapName).writeString(mapToSavedString(tileMap), false)
    }

    fun loadMap(mapFile: FileHandle, checkSizeErrors: Boolean = true): TileMap {
        return mapFromSavedString(mapFile.readString(), checkSizeErrors)
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
        } catch (ex: Exception) {
            mapString
        }
        return json().fromJson(TileMapPreview::class.java, unzippedJson).mapParameters
    }
}
