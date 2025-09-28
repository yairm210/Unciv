@file:Suppress("UNUSED_VARIABLE")  // These are tests and the names serve readability

//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic.map

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.metadata.GameSettings.PathfindingAlgorithm
import com.unciv.models.metadata.GameSettings.PathfindingAlgorithm.ClassicPathfinding
import com.unciv.models.metadata.GameSettings.PathfindingAlgorithm.AStarPathfinding
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.testing.GdxTestRunnerFactory
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.junit.runners.Parameterized.UseParametersRunnerFactory

@RunWith(Parameterized::class)
@UseParametersRunnerFactory(GdxTestRunnerFactory::class)
class UnitMovementTests(
    // parameters come from the Compantion#parameters method
    private val pathfindingAlgorithm: PathfindingAlgorithm,
) {
    private lateinit var tile: Tile
    private lateinit var civInfo: Civilization
    private var testGame = TestGame()

    @Before
    fun initTheWorld() {
        UncivGame.Current.settings.useAStarPathfinding = (pathfindingAlgorithm == AStarPathfinding)
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

            assertTrue(terrain.name, terrain.impassable != unit.movement.canPassThrough(tile))
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
        unit.owner = civInfo.civID
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
            assertTrue(unit.movement.canPassThrough(tile))
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
                assertTrue("%s cannot be at %s".format(type.name, terrain.name),
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

            assertTrue(
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
            assertTrue("$type must not enter Wonder tile",
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

            assertTrue("$type cannot be in Coast",
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

            assertTrue("$type cannot be in Ocean",
                    unit.baseUnit.isLandUnit != unit.movement.canPassThrough(tile))
        }
    }

    @Test
    fun canNOTEnterOceanWithLimitations() {
        tile.baseTerrain = Constants.ocean
        tile.setTransients()

        val unitType = testGame.ruleset.unitTypes.values.first()
        val unit = addFakeUnit(unitType, listOf("Cannot enter ocean tiles"))

        assertFalse(unit.movement.canPassThrough(tile))

        val unitCanEnterAfterAstronomy = addFakeUnit(unitType, listOf("Cannot enter ocean tiles <before discovering [Astronomy]>"))
        assertTrue(unitCanEnterAfterAstronomy.movement.canPassThrough(tile))

        civInfo.tech.techsResearched.remove("Astronomy")
        unitCanEnterAfterAstronomy.updateUniques()
        assertFalse(unitCanEnterAfterAstronomy.movement.canPassThrough(tile))
    }

    @Test
    fun canNOTPassThroughTileWithEnemyUnits() {
        val barbNation = Nation().apply { name = Constants.barbarians } // they are always enemies
        val barbCiv = Civilization(barbNation)
        barbCiv.gameInfo = testGame.gameInfo
        barbCiv.cache.updateState()

        testGame.gameInfo.civilizations.add(barbCiv)

        testGame.addUnit("Warrior", barbCiv, tile)

        for (type in testGame.ruleset.unitTypes.values) {
            val outUnit = addFakeUnit(type)
            assertFalse("$type must not enter occupied tile", outUnit.movement.canPassThrough(tile))
        }
    }

    @Test
    fun canNOTPassForeignTiles() {
        val otherCiv = testGame.addCiv()
        val city = testGame.addCity(otherCiv, testGame.tileMap[1,1])
        tile.setOwningCity(city)

        val unit = testGame.addUnit("Warrior", civInfo, null)

        assertFalse("Unit must not enter other civ tile", unit.movement.canPassThrough(tile))

        city.hasJustBeenConquered = true
        civInfo.diplomacy[otherCiv.civName] = DiplomacyManager(civInfo, otherCiv)
        civInfo.getDiplomacyManager(otherCiv)!!.diplomaticStatus = DiplomaticStatus.War

        assertTrue("Unit can capture other civ city", unit.movement.canPassThrough(tile))
    }

    @Test
    fun canTeleportLandUnit() {
        val unit = testGame.addUnit("Warrior", civInfo, tile)

        val otherCiv = testGame.addCiv()
        val city = testGame.addCity(otherCiv, tile)

        assertTrue("Unit must be teleported to new location", unit.currentTile != tile)
        assertTrue("Unit must be teleported to tile outside of civ's control", unit.currentTile.getOwner() == null)
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
        assertTrue("Unit must be teleported to closest tile outside of civ's control",
            unit.currentTile.position.eq(1, 2))
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
        assertTrue("Unit must not be teleported but destroyed", unit.isDestroyed)
    }

    @Test
    fun `can teleport land unit over civilian and capture it`() {

        testGame.makeHexagonalMap(5)
        val unit = testGame.addUnit("Warrior", civInfo, testGame.tileMap[1,1])
        // Force the unit to teleport to 1,2 specifically, by blocking all other neighboring tiles with mountains
        for (neighbor in unit.currentTile.neighbors) {
            if (neighbor.position.eq(1,2)) continue
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

        assertTrue("Warrior teleported to 1,2", unit.currentTile.position.eq(1,2))
        assertTrue("Worker must be captured", enemyWorkerUnit.civ == civInfo)
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
        assertTrue("Unit must be teleported to closest tile outside of civ's control",
            unit.currentTile.position.eq(1, 2))
        assertTrue("Payload must be teleported to the same tile",
            unit.currentTile == payload.currentTile)
    }
    
    @Test
    fun twoEscortsCanSwap() {
        val settler1 = testGame.addUnit("Settler", civInfo, testGame.tileMap[1,1])
        val settler2 = testGame.addUnit("Settler", civInfo, testGame.tileMap[2,2])
        val warrior1 = testGame.addUnit("Warrior", civInfo, testGame.tileMap[1,1])
        val warrior2 = testGame.addUnit("Warrior", civInfo, testGame.tileMap[2,2])
        warrior1.startEscorting()
        warrior2.startEscorting()
        assertEquals(warrior1, settler1.getOtherEscortUnit())
        assertEquals(warrior2, settler2.getOtherEscortUnit())

        assertTrue(warrior1.movement.canUnitSwapTo(testGame.tileMap[2,2]))
        assertTrue(warrior2.movement.canUnitSwapTo(testGame.tileMap[1,1]))
        
        warrior1.movement.swapMoveToTile(testGame.tileMap[2,2])
        
        assertEquals(testGame.tileMap[2,2], warrior1.currentTile)
        assertEquals(testGame.tileMap[1,1], settler1.currentTile)
        assertEquals(testGame.tileMap[1,1], warrior2.currentTile)
        assertEquals(testGame.tileMap[2,2], settler2.currentTile)
        assertEquals(warrior1, settler2.getOtherEscortUnit())
        assertEquals(warrior2, settler1.getOtherEscortUnit())
    }

    @Test
    fun twoEscortsCanSwapEvenIfSettlerHasNoMovement() {
        val settler1 = testGame.addUnit("Settler", civInfo, testGame.tileMap[1,1])
        val settler2 = testGame.addUnit("Settler", civInfo, testGame.tileMap[2,2])
        val warrior1 = testGame.addUnit("Warrior", civInfo, testGame.tileMap[1,1])
        val warrior2 = testGame.addUnit("Warrior", civInfo, testGame.tileMap[2,2])
        warrior1.startEscorting()
        warrior2.startEscorting()
        assertEquals(warrior1, settler1.getOtherEscortUnit())
        assertEquals(warrior2, settler2.getOtherEscortUnit())
        settler1.currentMovement = 0f

        assertFalse(warrior1.movement.canUnitSwapTo(testGame.tileMap[2,2]))
        assertFalse(warrior2.movement.canUnitSwapTo(testGame.tileMap[1,1]))

        warrior1.movement.swapMoveToTile(testGame.tileMap[2,2])

        assertEquals(testGame.tileMap[2,2], warrior1.currentTile)
        assertEquals(testGame.tileMap[1,1], settler1.currentTile)
        assertEquals(testGame.tileMap[1,1], warrior2.currentTile)
        assertEquals(testGame.tileMap[2,2], settler2.currentTile)
        assertEquals(warrior1, settler2.getOtherEscortUnit())
        assertEquals(warrior2, settler1.getOtherEscortUnit())
    }

    companion object {
        @Suppress("unused")
        @Parameters
        @JvmStatic
        fun parameters(): Collection<Array<Any?>?> {
            return listOf( 
                /* First execute the test with these parametrers */
                arrayOf(ClassicPathfinding),
                /* and then execute the test with these parametrers */
                arrayOf(AStarPathfinding))
        }
    }
}
