package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CityStateType
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapSizeNew
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.Religion
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.*
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.utils.withItem
import kotlin.math.abs
import kotlin.math.max

class TestGame {

    private var objectsCreated = 0
    val ruleset: Ruleset
    val gameInfo = GameInfo()
    /** [tileMap] has a default size of 1 tile, use [makeHexagonalMap] or [makeRectangularMap] to change that */
    val tileMap
        get() = gameInfo.tileMap

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

        // Create a tilemap, needed for city centers
        gameInfo.tileMap = TileMap(1, ruleset, false)
        tileMap.mapParameters.mapSize = MapSizeNew(0, 0)
        tileMap.ruleset = ruleset
    }

    /** Makes a new rectangular tileMap and sets it in gameInfo. Removes all existing tiles. All new tiles have terrain [baseTerrain] */
    fun makeRectangularMap(newHeight: Int, newWidth: Int, baseTerrain: String = Constants.desert) {
        val newTileMap = TileMap(newWidth, newHeight, ruleset, tileMap.mapParameters.worldWrap)
        newTileMap.mapParameters.mapSize = MapSizeNew(newWidth, newHeight)

        for (row in tileMap.tileMatrix)
            for (tile in row)
                if (tile != null)
                    tile.baseTerrain = baseTerrain
        
        gameInfo.tileMap = newTileMap
    }

    /** Makes a new hexagonal tileMap with radius [newRadius] and sets it in gameInfo. 
     * Removes all existing tiles. All new tiles have terrain [baseTerrain] 
     */
    fun makeHexagonalMap(newRadius: Int, baseTerrain: String = Constants.desert) {
        val newTileMap = TileMap(newRadius, ruleset, tileMap.mapParameters.worldWrap)
        newTileMap.mapParameters.mapSize = MapSizeNew(newRadius)
        
        for (row in tileMap.tileMatrix)
            for (tile in row)
                if (tile != null)
                    tile.baseTerrain = baseTerrain

        gameInfo.tileMap = newTileMap
    }
    
    fun getTile(position: Vector2) = tileMap[position]
    
    /** Sets the [terrain] and [features] of the tile at [position], and then returns it */
    fun setTileFeatures(position: Vector2, terrain: String = Constants.desert, features: List<String> = listOf()): TileInfo {
        val tile = tileMap[position]
        tile.baseTerrain = terrain
        for (feature in features) {
            tile.addTerrainFeature(feature)
        }
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
    
    fun addTileToCity(city: CityInfo, tile: TileInfo) {
        city.tiles.add(tile.position)
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

    fun addReligion(foundingCiv: CivilizationInfo): Religion {
        gameInfo.gameParameters.religionEnabled = true
        val religion = Religion("Religion-${objectsCreated++}", gameInfo, foundingCiv.civName)
        foundingCiv.religionManager.religion = religion
        gameInfo.religions[religion.name] = religion
        return religion
    }
    
    fun addBelief(type: BeliefType = BeliefType.Any, vararg uniques: String): Belief {
        val belief = Belief()
        belief.name = "Belief-${objectsCreated++}"
        belief.type = type
        belief.uniques = arrayListOf<String>(*uniques)
        ruleset.beliefs[belief.name] = belief
        return belief
    }
}