package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.unciv.logic.map.Scenario
import com.unciv.logic.map.TileMap
import com.unciv.ui.saves.Gzip

object MapSaver {

    fun json() = GameSaver.json()

    private const val mapsFolder = "maps"
    private const val scenariosFolder = "scenarios"

    private fun getMap(mapName:String) = Gdx.files.local("$mapsFolder/$mapName")
    private fun getScenario(scenarioName:String) = Gdx.files.local("$scenariosFolder/$scenarioName")
    
    fun saveMap(mapName: String,tileMap: TileMap) {
        getMap(mapName).writeString(Gzip.zip(json().toJson(tileMap.getStrippedMap())), false)
    }

    fun saveScenario(scenarioName:String, scenario: Scenario) {
        getScenario(scenarioName).writeString(json().toJson(scenario), false)
    }

    fun loadMap(mapName: String): TileMap {
        val gzippedString = getMap(mapName).readString()
        val unzippedJson = Gzip.unzip(gzippedString)
        return json().fromJson(TileMap::class.java, unzippedJson).getStrippedMap()
    }

    fun loadScenario(scenarioName: String): Scenario {
        val scenarioJson = getScenario(scenarioName).readString()
        return json().fromJson(Scenario::class.java, scenarioJson)
    }

    fun deleteMap(mapName: String) = getMap(mapName).delete()

    fun deleteScenario(scenarioName: String) = getScenario(scenarioName).delete()

    fun getMaps() = Gdx.files.local(mapsFolder).list().map { it.name() }

    fun getScenarios() = Gdx.files.local(scenariosFolder).list().map { it.name() }

    fun mapFromJson(json:String): TileMap = json().fromJson(TileMap::class.java, json)
}