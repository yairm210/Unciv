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
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.UnitType

class TestGame {

    private var objectsCreated = 0
    val ruleset: Ruleset
    val gameInfo = GameInfo()
    /** [tileMap] has a default size of 1 tile, use [makeHexagonalMap] or [makeRectangularMap] to change that */
    val tileMap
        get() = gameInfo.tileMap

    init {
        // Set UncivGame.Current so that debug variables are initialized
        UncivGame.Current = UncivGame()
        // And the settings can be reached for the locale used in .tr()
        UncivGame.Current.settings = GameSettings()

        // Create a new ruleset we can easily edit, and set the important variables of gameInfo
        RulesetCache.loadRulesets()
        ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
        gameInfo.ruleSet = ruleset
        gameInfo.difficultyObject = ruleset.difficulties["Prince"]!!
        gameInfo.speed = ruleset.speeds[Speed.DEFAULTFORSIMULATION]!!
        gameInfo.currentPlayerCiv = CivilizationInfo()

        // Create a tilemap, needed for city centers
        gameInfo.tileMap = TileMap(1, ruleset, false)
        tileMap.mapParameters.mapSize = MapSizeNew(0, 0)
        tileMap.ruleset = ruleset
        tileMap.gameInfo = gameInfo
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
        tileMap.gameInfo = gameInfo
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
        tileMap.gameInfo = gameInfo
    }

    fun getTile(position: Vector2) = tileMap[position]

    /** Sets the [terrain] and [features] of the tile at [position], and then returns it */
    fun setTileFeatures(position: Vector2, terrain: String = Constants.desert, features: List<String> = listOf()): TileInfo {
        val tile = tileMap[position]
        tile.baseTerrain = terrain
        tile.setTerrainFeatures(listOf())
        for (feature in features) {
            tile.addTerrainFeature(feature)
        }
        tile.setTerrainTransients()
        return tile
    }

    fun addCiv(vararg uniques: String, isPlayer: Boolean = false, cityState: CityStateType? = null): CivilizationInfo {
        fun nationFactory() = Nation().apply {
            cities = arrayListOf("The Capital")
            cityStateType = cityState
        }
        val nation = createRulesetObject(ruleset.nations, *uniques) {
            nationFactory()
        }
        val civInfo = CivilizationInfo()
        civInfo.nation = nation
        civInfo.gameInfo = gameInfo
        civInfo.civName = nation.name
        if (isPlayer) civInfo.playerType = PlayerType.Human
        civInfo.setTransients()
        if (cityState != null) {
            civInfo.cityStateFunctions.initCityState(ruleset, "Ancient era", emptyList())
        }
        gameInfo.civilizations.add(civInfo)
        return civInfo
    }

    fun addCity(
        civInfo: CivilizationInfo,
        tile: TileInfo,
        replacePalace: Boolean = false,
        initialPopulation: Int = 0
    ): CityInfo {
        val cityInfo = CityInfo(civInfo, tile.position)
        if (initialPopulation != 1)
            cityInfo.population.addPopulation(initialPopulation - 1) // With defaults this will remove population

        if (replacePalace && civInfo.cities.size == 1) {
            // Add a capital indicator without any other stats
            val palaceWithoutStats = createBuilding(UniqueType.IndicatesCapital.text)
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

    fun createSpecialist(): String {
        val name = "specialist-${objectsCreated++}"
        ruleset.specialists[name] = Specialist()
        return name
    }

    fun addReligion(foundingCiv: CivilizationInfo): Religion {
        val religion = Religion("Religion-${objectsCreated++}", gameInfo, foundingCiv.civName)
        foundingCiv.religionManager.religion = religion
        gameInfo.religions[religion.name] = religion
        return religion
    }

    private fun <T: IRulesetObject> createRulesetObject(
        rulesetCollection: LinkedHashMap<String, T>,
        vararg uniques: String,
        factory: () -> T
    ): T {
        val obj = factory()
        val name = "${obj::class.simpleName}-${objectsCreated++}"
        obj.name = name
        uniques.toCollection(obj.uniques)
        rulesetCollection[name] = obj
        return obj
    }

    fun createBaseUnit(unitType: String = createUnitType().name, vararg uniques: String) =
        createRulesetObject(ruleset.units, *uniques) {
            val baseUnit = BaseUnit()
            baseUnit.ruleset = gameInfo.ruleSet
            baseUnit.unitType = unitType
            baseUnit
        }
    fun createBelief(type: BeliefType = BeliefType.Any, vararg uniques: String) =
        createRulesetObject(ruleset.beliefs, *uniques) { Belief(type) }
    fun createBuilding(vararg uniques: String) =
        createRulesetObject(ruleset.buildings, *uniques) { Building() }
    fun createPolicy(vararg uniques: String) =
        createRulesetObject(ruleset.policies, *uniques) { Policy() }
    fun createTileImprovement(vararg uniques: String) =
        createRulesetObject(ruleset.tileImprovements, *uniques) { TileImprovement() }
    fun createUnitType(vararg uniques: String) =
        createRulesetObject(ruleset.unitTypes, *uniques) { UnitType() }
}
