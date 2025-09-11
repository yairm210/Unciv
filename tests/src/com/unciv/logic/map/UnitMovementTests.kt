@file:Suppress("UNUSED_VARIABLE")  // These are tests and the names serve readability

//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class UnitMovementTests {

    private lateinit var tile: Tile
    private lateinit var civInfo: Civilization
    private var testGame = TestGame()

    @Before
    fun initTheWorld() {
        testGame.makeHexagonalMap(2)
        tile = testGame.tileMap[0,0]
        civInfo = testGame.addCiv()
        civInfo.tech.techsResearched.addAll(testGame.ruleset.technologies.keys)
        civInfo.tech.embarkedUnitsCanEnterOcean = true
        civInfo.tech.unitsCanEmbark = true
    }

    @Test
    fun canPassThroughPassableTerrains() {
        val unit = testGame.addUnit("Warrior", civInfo, null)
        for (terrain in testGame.ruleset.terrains.values) {
            tile.baseTerrain = terrain.name
            tile.setTerrainFeatures(listOf())
            tile.setTransients()

            Assert.assertTrue(terrain.name, terrain.impassable != unit.movement.canPassThrough(tile))
        }
    }

    fun addFakeUnit(unitType: UnitType, uniques: List<String> = listOf()): MapUnit {
        val baseUnit = BaseUnit()
        baseUnit.unitType = unitType.name
        baseUnit.uniques.addAll(uniques)
        baseUnit.setRuleset(testGame.ruleset)

        val unit = MapUnit()
        unit.name = baseUnit.name
        unit.civ = civInfo
        unit.owner = civInfo.civName
        unit.baseUnit = baseUnit
        unit.updateUniques()
        return unit
    }

    @Test
    fun allUnitTypesCanEnterCity() {

        testGame.addCity(civInfo, tile)

        for (type in testGame.ruleset.unitTypes.values)
        {
            val unit = addFakeUnit(type)
            Assert.assertTrue(unit.movement.canPassThrough(tile))
        }
    }

    @Test
    fun waterUnitCanNOTEnterLand() {
        for (terrain in testGame.ruleset.terrains.values) {
            if (terrain.impassable) continue
            tile.baseTerrain = terrain.name
            tile.setTransients()

            for (type in testGame.ruleset.unitTypes.values) {
                val unit = addFakeUnit(type)
                Assert.assertTrue("%s cannot be at %s".format(type.name, terrain.name),
                        (unit.baseUnit.isWaterUnit && tile.isLand) != unit.movement.canPassThrough(tile))
            }
        }
    }

    @Test
    fun canNOTEnterIce() {
        tile.baseTerrain = Constants.ocean
        tile.setTerrainFeatures(listOf(Constants.ice))
        tile.setTransients()

        for (type in testGame.ruleset.unitTypes.values) {
            val unit = addFakeUnit(type)
            unit.updateUniques()

            Assert.assertTrue(
                "$type cannot be in Ice",
                unit.movement.canPassThrough(tile) == (
                    type.uniques.contains("Can enter ice tiles")
                    || type.uniques.contains("Can pass through impassable tiles")
                )
            )
        }
    }

    @Test
    fun canNOTEnterNaturalWonder() {
        tile.baseTerrain = Constants.plains
        tile.naturalWonder = "Mount Fuji"
        tile.setTransients()

        for (type in testGame.ruleset.unitTypes.values) {
            val unit = addFakeUnit(type)
            Assert.assertTrue("$type must not enter Wonder tile",
                unit.movement.canPassThrough(tile) == type.hasUnique(UniqueType.CanPassImpassable))
        }
    }

    @Test
    fun canNOTEnterCoastUntilProperTechIsResearched() {
        civInfo.tech.unitsCanEmbark = false
        tile.baseTerrain = Constants.coast
        tile.setTransients()

        for (type in testGame.ruleset.unitTypes.values) {
            val unit = addFakeUnit(type)

            Assert.assertTrue("$type cannot be in Coast",
                    unit.baseUnit.isLandUnit != unit.movement.canPassThrough(tile))
        }
    }

    @Test
    fun canNOTEnterOceanUntilProperTechIsResearched() {
        civInfo.tech.embarkedUnitsCanEnterOcean = false

        tile.baseTerrain = Constants.ocean
        tile.setTransients()

        for (type in testGame.ruleset.unitTypes.values) {
            val unit = addFakeUnit(type)

            Assert.assertTrue("$type cannot be in Ocean",
                    unit.baseUnit.isLandUnit != unit.movement.canPassThrough(tile))
        }
    }

    @Test
    fun canNOTEnterOceanWithLimitations() {
        tile.baseTerrain = Constants.ocean
        tile.setTransients()

        val unitType = testGame.ruleset.unitTypes.values.first()
        val unit = addFakeUnit(unitType, listOf("Cannot enter ocean tiles"))

        Assert.assertFalse(unit.movement.canPassThrough(tile))

        val unitCanEnterAfterAstronomy = addFakeUnit(unitType, listOf("Cannot enter ocean tiles <before discovering [Astronomy]>"))
        Assert.assertTrue(unitCanEnterAfterAstronomy.movement.canPassThrough(tile))

        civInfo.tech.techsResearched.remove("Astronomy")
        unitCanEnterAfterAstronomy.updateUniques()
        Assert.assertFalse(unitCanEnterAfterAstronomy.movement.canPassThrough(tile))
    }

    @Test
    fun canNOTPassThroughTileWithEnemyUnits() {
        val barbCiv = Civilization()
        barbCiv.gameInfo = testGame.gameInfo
        barbCiv.setNameForUnitTests(Constants.barbarians) // they are always enemies
        barbCiv.nation = Nation().apply { name = Constants.barbarians }
        barbCiv.cache.updateState()

        testGame.gameInfo.civilizations.add(barbCiv)

        testGame.addUnit("Warrior", barbCiv, tile)

        for (type in testGame.ruleset.unitTypes.values) {
            val outUnit = addFakeUnit(type)
            Assert.assertFalse("$type must not enter occupied tile", outUnit.movement.canPassThrough(tile))
        }
    }

    @Test
    fun canNOTPassForeignTiles() {
        val otherCiv = testGame.addCiv()
        val city = testGame.addCity(otherCiv, testGame.tileMap[1,1])
        tile.setOwningCity(city)

        val unit = testGame.addUnit("Warrior", civInfo, null)

        Assert.assertFalse("Unit must not enter other civ tile", unit.movement.canPassThrough(tile))

        city.hasJustBeenConquered = true
        civInfo.diplomacy[otherCiv.civName] = DiplomacyManager(otherCiv, otherCiv.civName)
        civInfo.getDiplomacyManager(otherCiv)!!.diplomaticStatus = DiplomaticStatus.War

        Assert.assertTrue("Unit can capture other civ city", unit.movement.canPassThrough(tile))
    }

    @Test
    fun canTeleportLandUnit() {
        val unit = testGame.addUnit("Warrior", civInfo, tile)

        val otherCiv = testGame.addCiv()
        val city = testGame.addCity(otherCiv, tile)

        Assert.assertTrue("Unit must be teleported to new location", unit.currentTile != tile)
        Assert.assertTrue("Unit must be teleported to tile outside of civ's control", unit.currentTile.getOwner() == null)
    }

    @Test
    fun canTeleportWaterUnit() {
        testGame.makeHexagonalMap(5)
        for (i in 1..3) {
            val waterTile = testGame.tileMap[1,i]
            waterTile.baseTerrain = Constants.ocean
            waterTile.setTransients()
        }

        // 1,1 is within the radius of the new city, so it will be teleported away
        val unit = testGame.addUnit("Frigate", civInfo, testGame.tileMap[1,1])

        val otherCiv = testGame.addCiv()
        val city = testGame.addCity(otherCiv, tile)

        // Don't move him all the way to 1,3 - since there's a closer tile at 1,2
        Assert.assertTrue("Unit must be teleported to closest tile outside of civ's control",
            unit.currentTile.position == Vector2(1f, 2f))
    }

    @Test
    fun `can NOT teleport water unit over the land`() {
        testGame.makeHexagonalMap(5)
        for (i in listOf(1,3)) { // only water tiles are 1,1 and 1,3, which are non-contiguous
            val waterTile = testGame.tileMap[1,i]
            waterTile.baseTerrain = Constants.ocean
            waterTile.setTransients()
        }

        // 1,1 is within the radius of the new city, so it will be teleported away
        val unit = testGame.addUnit("Frigate", civInfo, testGame.tileMap[1,1])

        val otherCiv = testGame.addCiv()
        val city = testGame.addCity(otherCiv, tile)

        // Don't move him all the way to 1,3 - since there's a closer tile at 1,2
        Assert.assertTrue("Unit must not be teleported but destroyed", unit.isDestroyed)
    }

    @Test
    fun `can teleport land unit over civilian and capture it`() {

        testGame.makeHexagonalMap(5)
        val unit = testGame.addUnit("Warrior", civInfo, testGame.tileMap[1,1])
        // Force the unit to teleport to 1,2 specifically, by blocking all other neighboring tiles with mountains
        for (neighbor in unit.currentTile.neighbors) {
            if (neighbor.position == Vector2(1f,2f)) continue
            neighbor.baseTerrain = Constants.mountain
            neighbor.setTransients()
        }

        // Place an enemy civilian unit on that tile
        val atWarCiv = testGame.addCiv()
        atWarCiv.diplomacyFunctions.makeCivilizationsMeet(civInfo)
        atWarCiv.getDiplomacyManager(civInfo)!!.declareWar()
        val enemyWorkerUnit = testGame.addUnit("Worker", atWarCiv, testGame.tileMap[1,2])

        val otherCiv = testGame.addCiv()
        val city = testGame.addCity(otherCiv, tile)

        Assert.assertTrue("Warrior teleported to 1,2", unit.currentTile.position == Vector2(1f,2f))
        Assert.assertTrue("Worker must be captured", enemyWorkerUnit.civ == civInfo)
    }


    @Test
    fun canTeleportTransportWithPayload() {
        testGame.makeHexagonalMap(5)
        for (i in 1..3) {
            val waterTile = testGame.tileMap[1,i]
            waterTile.baseTerrain = Constants.ocean
            waterTile.setTransients()
        }

        val unit = testGame.addUnit("Carrier", civInfo, testGame.tileMap[1,1])
        val payload = testGame.addUnit("Fighter", civInfo, testGame.tileMap[1,1])

        val otherCiv = testGame.addCiv()
        val city = testGame.addCity(otherCiv, tile)

        // Don't move him all the way to 1,3 - since there's a closer tile at 1,2
        Assert.assertTrue("Unit must be teleported to closest tile outside of civ's control",
            unit.currentTile.position == Vector2(1f, 2f))
        Assert.assertTrue("Payload must be teleported to the same tile",
            unit.currentTile == payload.currentTile)
    }
}
