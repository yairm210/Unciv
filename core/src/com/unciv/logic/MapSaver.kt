package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.logic.map.ScenarioMap
import com.unciv.logic.map.TileMap
import com.unciv.ui.saves.Gzip

object MapSaver {

    fun json() = GameSaver.json()

    private const val mapsFolder = "maps"
    private const val scenariosFolder = "scenarios"

    private fun getMap(mapName:String) = Gdx.files.local("$mapsFolder/$mapName")
    private fun getScenario(scenarioName:String) = Gdx.files.local("$scenariosFolder/$scenarioName")
    
    fun saveMap(mapName: String,tileMap: TileMap) {
        getMap(mapName).writeString(Gzip.zip(json().toJson(tileMap)), false)
    }

    fun saveScenario(scenarioName:String, scenarioMap: ScenarioMap) {
        getScenario(scenarioName).writeString(Gzip.zip(json().toJson(scenarioMap)), false)
    }

    fun loadMap(mapName: String) = loadMap(getMap(mapName))

    fun loadMap(mapFile:FileHandle):TileMap{
        val gzippedString = mapFile.readString()
        val unzippedJson = Gzip.unzip(gzippedString)
        return json().fromJson(TileMap::class.java, unzippedJson)
    }

    fun loadScenario(scenarioName: String) = loadScenario(getScenario(scenarioName))

    fun loadScenario(scenarioFile: FileHandle): ScenarioMap {
        val gzippedString = scenarioFile.readString()
        val unzippedJson = Gzip.unzip(gzippedString)
        return json().fromJson(ScenarioMap::class.java, unzippedJson)
    }

    fun deleteMap(mapName: String) = getMap(mapName).delete()

    fun deleteScenario(scenarioName: String) = getScenario(scenarioName).delete()

    fun getMaps() = Gdx.files.local(mapsFolder).list()

    fun getScenarios() = Gdx.files.local(scenariosFolder).list()

    fun mapFromJson(json:String): TileMap = json().fromJson(TileMap::class.java, json)
}