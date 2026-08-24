package com.unciv.logic.files

import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.TileMap
import com.unciv.ui.screens.savescreens.Gzip

object MapSaver {

    const val mapsFolder = "maps"
    var saveZipped = true

    private fun getMap(mapName: String) = UncivGame.Current.files.getLocalFile("$mapsFolder/$mapName")

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
        tileMap.assignContinents(TileMap.AssignContinentsMode.Reassign)
        val file = getMap(mapName)
        if (saveZipped) {
            Gzip.zippedOutputStream(file.write(false)).writer(Charsets.UTF_8).use {
                json().toJson(tileMap, it)
            }
        } else {
            json().toJson(tileMap, file)
        }
    }

    fun loadMap(mapFile: FileHandle): TileMap {
        return openJsonReader(mapFile).use { json().fromJson(TileMap::class.java, it) }
    }

    fun getMaps(): Array<FileHandle> = UncivGame.Current.files.getLocalFile(mapsFolder).list()

    private fun mapFromJson(json: String): TileMap = json().fromJson(TileMap::class.java, json)

    fun loadMapParameters(mapFile: FileHandle): MapParameters {
        return loadMapPreview(mapFile).mapParameters
    }

    fun loadMapPreview(mapFile: FileHandle): TileMap.Preview {
        return openJsonReader(mapFile).use { json().fromJson(TileMap.Preview::class.java, it) }
    }

    /** Opens [file] for reading its JSON content, transparently gunzipping+base64-decoding if it's in that format,
     *  falling back to reading it as plain text otherwise (e.g. if [saveZipped] was off when it was saved). */
    private fun openJsonReader(file: FileHandle) = try {
        Gzip.unzippedInputStream(file.read()).reader(Charsets.UTF_8)
    } catch (ex: Exception) {
        file.reader(Charsets.UTF_8.name())
    }
}
