package com.unciv.testing

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.city.City
import com.unciv.logic.city.managers.CityFounder
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Religion
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IRulesetObject
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.Specialist
import com.unciv.models.ruleset.Speed
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
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
        UncivGame.Current.gameInfo = gameInfo

        // Create a new ruleset we can easily edit, and set the important variables of gameInfo
        RulesetCache.loadRulesets(noMods = true)
        ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
        gameInfo.ruleset = ruleset
        gameInfo.difficulty = "Prince"
        gameInfo.difficultyObject = ruleset.difficulties["Prince"]!!
        gameInfo.speed = ruleset.speeds[Speed.DEFAULTFORSIMULATION]!!
        gameInfo.currentPlayerCiv = Civilization()  // Will be uninitialized, do not build on for tests

        // Create a tilemap, needed for city centers
        gameInfo.tileMap = TileMap(0, ruleset, false)
        tileMap.mapParameters.mapSize = MapSize(0)
        tileMap.ruleset = ruleset
        tileMap.gameInfo = gameInfo

        for (baseUnit in ruleset.units.values)
            baseUnit.setRuleset(ruleset)
    }

    /** Makes a new rectangular tileMap and sets it in gameInfo. Removes all existing tiles. All new tiles have terrain [baseTerrain] */
    fun makeRectangularMap(newHeight: Int, newWidth: Int, baseTerrain: String = Constants.desert) {
        val newTileMap = TileMap(newWidth, newHeight, ruleset, tileMap.mapParameters.worldWrap)
        newTileMap.mapParameters.mapSize = MapSize(newWidth, newHeight)

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
        val newTileMap = TileMap(newRadius, ruleset, false)
        newTileMap.mapParameters.mapSize = MapSize(newRadius)

        for (row in tileMap.tileMatrix)
            for (tile in row)
                if (tile != null)
                    tile.baseTerrain = baseTerrain

        gameInfo.tileMap = newTileMap
        tileMap.gameInfo = gameInfo
    }

    fun getTile(position: Vector2) = tileMap[position]
    fun getTile(x: Int, y: Int) = tileMap[x, y]

    /** Sets the [terrain] and [features] of the tile at [position], and then returns it */
    fun setTileTerrainAndFeatures(position: Vector2, terrain: String, vararg features: String): Tile {
        setTileTerrain(position, terrain)
        return setTileFeatures(position, *features)
    }

    fun setTileTerrain(position: Vector2, terrain: String): Tile {
        val tile = tileMap[position]
        tile.baseTerrain = terrain
        tile.setTerrainTransients()
        return tile
    }

    fun setTileFeatures(position: Vector2, vararg features: String): Tile {
        val tile = tileMap[position]
        tile.setTerrainFeatures(listOf())
        for (feature in features) {
            tile.addTerrainFeature(feature)
        }
        tile.setTerrainTransients()
        return tile
    }

    fun addCiv(vararg uniques: String, isPlayer: Boolean = false, cityStateType: String? = null): Civilization {
        fun nationFactory() = Nation().apply {
            cities = arrayListOf("The Capital")
            this.cityStateType = cityStateType
        }
        val nation = createRulesetObject(ruleset.nations, *uniques) {
            nationFactory()
        }
        val civInfo = Civilization()
        civInfo.nation = nation
        civInfo.gameInfo = gameInfo
        civInfo.setNameForUnitTests(nation.name)
        if (isPlayer) civInfo.playerType = PlayerType.Human
        civInfo.cache.updateState()
        gameInfo.civilizations.add(civInfo)
        civInfo.setTransients()

        // Add 1 tech to the player so the era is computed correctly
        civInfo.tech.addTechnology(ruleset.technologies.values.minBy { it.era() }.name)
        if (cityStateType != null) {
            civInfo.cityStateFunctions.initCityState(ruleset, "Ancient era", emptySequence())
        }
        return civInfo
    }

    fun addBarbarianCiv() : Civilization {
        val barbarianCivilization = Civilization(Constants.barbarians)
        val nation = Nation()
        nation.name = Constants.barbarians
        barbarianCivilization.nation = nation
        barbarianCivilization.gameInfo = gameInfo
        barbarianCivilization.cache.updateState()
        gameInfo.civilizations.add(barbarianCivilization)
        return barbarianCivilization
    }

    fun addCity(
        civInfo: Civilization,
        tile: Tile,
        replacePalace: Boolean = false,
        initialPopulation: Int = 1
    ): City {
        val city = CityFounder().foundCity(civInfo, tile.position)
        city.population.addPopulation(initialPopulation - 1)

        if (replacePalace && civInfo.cities.size == 1) {
            // Add a capital indicator without any other stats
            val palaceWithoutStats = createBuilding(UniqueType.IndicatesCapital.text)
            city.cityConstructions.removeBuilding("Palace")
            city.cityConstructions.addBuilding(palaceWithoutStats)
        }
        return city
    }

    fun addTileToCity(city: City, tile: Tile) {
        city.tiles.add(tile.position)
    }

    fun addUnit(name: String, civInfo: Civilization, tile: Tile?): MapUnit {
        val baseUnit = ruleset.units[name]!!
        baseUnit.setRuleset(ruleset)
        val mapUnit = baseUnit.getMapUnit(civInfo)
        civInfo.units.addUnit(mapUnit)
        if (tile!=null) {
            mapUnit.putInTile(tile)
            mapUnit.currentMovement = mapUnit.getMaxMovement().toFloat()
        }
        return mapUnit
    }

    fun createSpecialist(): String {
        val name = "specialist-${objectsCreated++}"
        ruleset.specialists[name] = Specialist()
        return name
    }

    fun addReligion(foundingCiv: Civilization): Religion {
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

    fun addDefaultMeleeUnitWithUniques(civInfo: Civilization, tile: Tile, vararg uniques: String) : MapUnit {
        val createBaseUnit = createBaseUnit("Sword", *uniques)
        createBaseUnit.movement = 2
        createBaseUnit.strength = 8
        return this.addUnit(createBaseUnit.name, civInfo, tile)
    }

    fun addDefaultRangedUnitWithUniques(civInfo: Civilization, tile: Tile, vararg uniques: String) : MapUnit {
        val createBaseUnit = createBaseUnit("Archery", *uniques)
        createBaseUnit.movement = 2
        createBaseUnit.strength = 5
        createBaseUnit.rangedStrength = 7
        createBaseUnit.range = 2
        return this.addUnit(createBaseUnit.name, civInfo, tile)
    }

    fun createBaseUnit(unitType: String = createUnitType().name, vararg uniques: String) =
        createRulesetObject(ruleset.units, *uniques) {
            val baseUnit = BaseUnit()
            baseUnit.unitType = unitType
            baseUnit.setRuleset(gameInfo.ruleset)
            baseUnit
        }
    fun createBelief(type: BeliefType = BeliefType.Any, vararg uniques: String) =
        createRulesetObject(ruleset.beliefs, *uniques) { Belief(type) }
    fun createBuilding(vararg uniques: String) =
        createRulesetObject(ruleset.buildings, *uniques) { Building() }
    fun createResource(vararg uniques: String) =
        createRulesetObject(ruleset.tileResources, *uniques) { TileResource() }

    fun createWonder(vararg uniques: String): Building {
        val createdBuilding = createBuilding(*uniques)
        createdBuilding.isWonder = true
        return createdBuilding
    }
    fun createPolicy(vararg uniques: String) =
        createRulesetObject(ruleset.policies, *uniques) { Policy() }
    fun createTileImprovement(vararg uniques: String) =
        createRulesetObject(ruleset.tileImprovements, *uniques) { TileImprovement() }
    fun createUnitType(vararg uniques: String) =
        createRulesetObject(ruleset.unitTypes, *uniques) { UnitType() }
    fun createUnitPromotion(vararg uniques: String) =
        createRulesetObject(ruleset.unitPromotions, *uniques) { Promotion() }
}
