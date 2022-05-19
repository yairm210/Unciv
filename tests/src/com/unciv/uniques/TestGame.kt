package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CityStateType
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.*
import com.unciv.models.ruleset.unique.UniqueType

class TestGame {

    private var objectsCreated = 0
    val ruleset: Ruleset
    val gameInfo = GameInfo()
    val tileMap = TileMap()

    init {
        // Set UncivGame.Current so that debug variables are initialized
        UncivGame.Current = UncivGame("Test")
        // And the settings can be reached for the locale used in .tr()
        UncivGame.Current.settings = GameSettings()

        // Create a new ruleset we can easily edit, and set the important variables of gameInfo
        RulesetCache.loadRulesets()
        ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
        gameInfo.ruleSet = ruleset
        gameInfo.difficultyObject = ruleset.difficulties["Prince"]!!
        gameInfo.setGameSpeed(ruleset.gameSpeeds[GameSpeed.DEFAULTFORSIMULATION]!!)

        // Create a tilemap, needed for city centers
        gameInfo.tileMap = tileMap
    }

    fun addTile(terrain: String, position: Vector2 = Vector2.Zero, features: List<String> = listOf()): TileInfo {
        val tile = TileInfo()
        tile.ruleset = ruleset
        tile.baseTerrain = terrain
        for (feature in features) {
            tile.addTerrainFeature(feature)
        }
        tile.tileMap = tileMap
        tile.position = position
        tile.setTransients()
        while (tileMap.tileMatrix.size < position.x + 1)
            tileMap.tileMatrix.add(ArrayList())
        while (tileMap.tileMatrix[position.x.toInt()].size < position.y + 1)
            tileMap.tileMatrix[position.x.toInt()].add(null)
        tileMap.tileMatrix[position.x.toInt()][position.y.toInt()] = tile
        return tile
    }

    fun addCiv(uniques: List<String> = emptyList(), isPlayer: Boolean = false, cityState: CityStateType? = null): CivilizationInfo {

        val nationName = "Nation-${objectsCreated++}"
        ruleset.nations[nationName] = Nation().apply {
            name = nationName
            cities = arrayListOf("The Capital")
            if (cityState != null) {
                cityStateType = cityState
            }
            this.uniques = ArrayList(uniques)
        }
        val civInfo = CivilizationInfo()
        civInfo.nation = ruleset.nations[nationName]!!
        civInfo.gameInfo = gameInfo
        civInfo.civName = nationName
        if (isPlayer) civInfo.playerType = PlayerType.Human
        civInfo.setTransients()
        if (cityState != null) {
            civInfo.cityStateFunctions.initCityState(ruleset, "Ancient era", emptyList())
        }
        gameInfo.civilizations.add(civInfo)
        return civInfo
    }

    fun addCity(civInfo: CivilizationInfo, tile: TileInfo, replacePalace: Boolean = false): CityInfo {
        val cityInfo = CityInfo(civInfo, tile.position)
        cityInfo.population.addPopulation(-1) // Remove population

        if (replacePalace && civInfo.cities.size == 1) {
            // Add a capital indicator without any other stats
            val palaceWithoutStats = createBuildingWithUnique(UniqueType.IndicatesCapital.text)
            cityInfo.cityConstructions.removeBuilding("Palace")
            cityInfo.cityConstructions.addBuilding(palaceWithoutStats.name)
        }
        return cityInfo
    }

    fun addUnit(name: String, civInfo: CivilizationInfo, tile: TileInfo): MapUnit {
        val baseUnit = ruleset.units[name]!!
        baseUnit.ruleset = ruleset
        val mapUnit = baseUnit.getMapUnit(civInfo)
        mapUnit.putInTile(tile)
        return mapUnit
    }

    fun addEmptySpecialist(): String {
        val name = "specialist-${objectsCreated++}"
        ruleset.specialists[name] = Specialist()
        return name
    }

    fun createBuildingWithUnique(unique: String): Building {
        return createBuildingWithUniques(arrayListOf(unique))
    }

    fun createBuildingWithUniques(uniques: ArrayList<String> = arrayListOf()): Building {
        val building = Building()
        building.uniques = uniques
        building.name = "Building-${objectsCreated++}"
        ruleset.buildings[building.name] = building
        return building
    }

}