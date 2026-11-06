@file:Suppress("UNUSED_VARIABLE")  // These are tests and the names serve readability

package com.unciv.logic.map

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.movement.UnitMovement
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.models.metadata.GameSettings.PathfindingAlgorithm
import com.unciv.models.metadata.GameSettings.PathfindingAlgorithm.ClassicPathfinding
import com.unciv.models.metadata.GameSettings.PathfindingAlgorithm.AStarPathfinding
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.testing.TestRunnerFactory
import com.unciv.testing.TestGame
import com.unciv.ui.components.UnitMovementMemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.junit.runners.Parameterized.UseParametersRunnerFactory

@RunWith(Parameterized::class)
@UseParametersRunnerFactory(TestRunnerFactory::class)
class UnitMovementTests(private val pathfindingAlgorithm: PathfindingAlgorithm) {
    companion object {
        @Suppress("unused")
        @Parameters
        @JvmStatic
        fun parameters() = TestRunnerFactory.Parameters.pathfinding
    }

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
    fun germanEncampmentRecruitmentUsesFullHealth() {
        val germany = testGame.addCiv(
            "When conquering an encampment, earn [25] Gold and recruit a Barbarian unit <with [100]% chance>"
        )
        testGame.addBarbarianCiv()
        testGame.gameInfo.barbarians.setTransients(testGame.gameInfo)
        val campTile = testGame.getTile(0, 0)
        testGame.gameInfo.barbarians.createNewCamp(campTile)
        val unit = testGame.addUnit("Warrior", germany, campTile.neighbors.first())

        unit.movement.moveToTile(campTile)

        val recruitedUnit = germany.units.getCivUnits().single { it != unit }
        assertEquals(100, recruitedUnit.health)
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
    fun `forced displacement leaves an enemy civilian uncaptured`() {

        testGame.makeHexagonalMap(5)
        val unit = testGame.addUnit("Warrior", civInfo, testGame.tileMap[1,1])
        // The nearest otherwise-valid tile is 1,2. Every other neighbor is a mountain.
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
        val workerTile = enemyWorkerUnit.currentTile

        val otherCiv = testGame.addCiv()
        val city = testGame.addCity(otherCiv, tile)

        assertSame("Worker stays with its owner", atWarCiv, enemyWorkerUnit.civ)
        assertSame("Worker stays on its tile", workerTile, enemyWorkerUnit.currentTile)
        assertFalse("Displaced unit takes another tile", unit.isDestroyed)
        assertFalse(unit.currentTile.position.eq(1, 2))
        assertNotEquals(testGame.tileMap[1, 1], unit.currentTile)
    }

    @Test
    fun `barbarian displacement does not capture an enemy civilian`() {
        testGame.makeHexagonalMap(5)
        val barbarians = testGame.addBarbarianCiv()
        val unit = testGame.addUnit("Warrior", barbarians, testGame.tileMap[1,1])
        for (neighbor in unit.currentTile.neighbors) {
            if (neighbor.position.eq(1,2)) continue
            neighbor.baseTerrain = Constants.mountain
            neighbor.setTransients()
        }

        val worker = testGame.addUnit("Worker", civInfo, testGame.tileMap[1,2])
        val workerTile = worker.currentTile
        val otherCiv = testGame.addCiv()
        testGame.addCity(otherCiv, tile)

        assertSame(civInfo, worker.civ)
        assertSame(workerTile, worker.currentTile)
        assertFalse(unit.isDestroyed)
        assertFalse(unit.currentTile.position.eq(1, 2))
        assertSame(barbarians, unit.civ)
    }

    @Test
    fun `forced displacement stacks with an own civilian`() {
        testGame.makeHexagonalMap(5)
        val unit = testGame.addUnit("Warrior", civInfo, testGame.tileMap[1,1])
        for (neighbor in unit.currentTile.neighbors) {
            if (neighbor.position.eq(1,2)) continue
            neighbor.baseTerrain = Constants.mountain
            neighbor.setTransients()
        }
        val worker = testGame.addUnit("Worker", civInfo, testGame.tileMap[1,2])

        val otherCiv = testGame.addCiv()
        testGame.addCity(otherCiv, tile)

        assertSame(civInfo, worker.civ)
        assertSame(testGame.tileMap[1, 2], worker.currentTile)
        assertSame(worker.currentTile, unit.currentTile)
        assertSame(unit, unit.currentTile.militaryUnit)
        assertSame(worker, unit.currentTile.civilianUnit)
    }

    @Test
    fun `forced displacement does not stop on another civ's civilian`() {
        testGame.makeHexagonalMap(5)
        val unit = testGame.addUnit("Warrior", civInfo, testGame.tileMap[1,1])
        for (neighbor in unit.currentTile.neighbors) {
            if (neighbor.position.eq(1,2)) continue
            neighbor.baseTerrain = Constants.mountain
            neighbor.setTransients()
        }

        val friend = testGame.addCiv()
        friend.diplomacyFunctions.makeCivilizationsMeet(civInfo)
        civInfo.getDiplomacyManager(friend)!!.hasOpenBorders = true
        friend.getDiplomacyManager(civInfo)!!.hasOpenBorders = true
        val worker = testGame.addUnit("Worker", friend, testGame.tileMap[1,2])
        val workerTile = worker.currentTile

        val otherCiv = testGame.addCiv()
        testGame.addCity(otherCiv, tile)

        assertSame("Worker stays with its owner", friend, worker.civ)
        assertSame("Worker stays on its tile", workerTile, worker.currentTile)
        assertFalse("Displaced unit takes another tile", unit.isDestroyed)
        assertNotEquals(worker.currentTile, unit.currentTile)
        assertFalse(unit.currentTile.position.eq(1, 2))
    }

    @Test
    fun `military movement still captures an enemy civilian`() {
        val warrior = testGame.addUnit("Warrior", civInfo, testGame.tileMap[0, 0])
        val enemy = testGame.addCiv()
        enemy.diplomacyFunctions.makeCivilizationsMeet(civInfo)
        enemy.getDiplomacyManager(civInfo)!!.declareWar()
        val worker = testGame.addUnit("Worker", enemy, testGame.tileMap[1, 0])

        warrior.movement.moveToTile(worker.currentTile)

        assertSame(civInfo, worker.civ)
        assertSame(warrior.currentTile, worker.currentTile)
        assertSame(worker, warrior.currentTile.civilianUnit)
    }

    @Test
    fun `own city fallback skips an enemy civilian`() {
        testGame.makeHexagonalMap(7)
        val ourCity = testGame.addCity(civInfo, testGame.getTile(0, 0))
        val origin = testGame.getTile(6, 6)
        // Nothing within 4 tiles is enterable, so placement has to use our city.
        origin.forEachTileInDistance(4) { nearTile ->
            if (nearTile == origin) return@forEachTileInDistance
            nearTile.baseTerrain = Constants.mountain
            nearTile.setTransients()
        }

        val enemy = testGame.addCiv()
        enemy.diplomacyFunctions.makeCivilizationsMeet(civInfo)
        enemy.getDiplomacyManager(civInfo)!!.declareWar()
        val infiltrator = testGame.createBaseUnit("Civilian", "May enter foreign tiles without open borders")
        val closestCityTile = ourCity.getTiles().minBy { it.aerialDistanceTo(origin) }
        val enemyCivilian = testGame.addUnit(infiltrator.name, enemy, closestCityTile)
        assertSame(closestCityTile, enemyCivilian.currentTile)

        val unit = testGame.addUnit("Warrior", civInfo, origin)
        val otherCiv = testGame.addCiv()
        testGame.addCity(otherCiv, origin)

        assertSame(enemy, enemyCivilian.civ)
        assertSame(closestCityTile, enemyCivilian.currentTile)
        assertFalse(unit.isDestroyed)
        assertNotEquals(closestCityTile, unit.currentTile)
        assertSame(civInfo, unit.currentTile.getOwner())
    }

    @Test
    fun `forced displacement destroys the unit when the only candidate holds an enemy civilian`() {
        testGame.makeHexagonalMap(4)
        val origin = testGame.getTile(0, 0)
        val onlyCandidate = origin.neighbors.first()
        origin.forEachTileInDistance(4) { nearTile ->
            if (nearTile == origin || nearTile == onlyCandidate) return@forEachTileInDistance
            nearTile.baseTerrain = Constants.mountain
            nearTile.setTransients()
        }
        val enemy = testGame.addCiv()
        enemy.diplomacyFunctions.makeCivilizationsMeet(civInfo)
        enemy.getDiplomacyManager(civInfo)!!.declareWar()
        val worker = testGame.addUnit("Worker", enemy, onlyCandidate)

        val unit = testGame.addUnit("Warrior", civInfo, origin)
        origin.baseTerrain = Constants.mountain
        origin.setTransients()
        unit.movement.teleportToClosestMoveableTile()

        assertTrue(unit.isDestroyed)
        assertSame(enemy, worker.civ)
        assertSame(onlyCandidate, worker.currentTile)
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
    fun paradroppingTransportKeepsPayload() {
        val origin = testGame.tileMap[0,0]
        val destination = testGame.tileMap[1,0]
        origin.baseTerrain = Constants.coast
        origin.setTransients()
        destination.baseTerrain = Constants.coast
        destination.setTransients()

        val transportBaseUnit = testGame.createBaseUnit(
            "Aircraft Carrier",
            "Can carry [2] [Aircraft] units",
            "May Paradrop to [Water] tiles up to [2] tiles away"
        ).apply {
            movement = 2
            strength = 1
        }
        val transport = testGame.addUnit(transportBaseUnit.name, civInfo, origin)
        // Freed from the carrier first, so the two real payloads can fill it to capacity below -
        // a full carrier is exactly the case that used to lose its payloads.
        val untransportedAirUnit = testGame.addUnit("Fighter", civInfo, origin)
        untransportedAirUnit.isTransported = false
        val payload = testGame.addUnit("Fighter", civInfo, origin)
        val secondPayload = testGame.addUnit("Fighter", civInfo, origin)

        transport.action = UnitActionType.Paradrop.value
        transport.movement.moveToTile(destination)

        assertEquals(destination, transport.currentTile)
        assertEquals(destination, payload.currentTile)
        assertEquals(destination, secondPayload.currentTile)
        assertTrue(payload.isTransported)
        assertTrue(secondPayload.isTransported)
        assertEquals(UnitMovementMemoryType.UnitTeleported, transport.mostRecentMoveType)
        assertEquals(UnitMovementMemoryType.UnitTeleported, payload.mostRecentMoveType)
        assertEquals(UnitMovementMemoryType.UnitTeleported, secondPayload.mostRecentMoveType)
        assertEquals(origin, untransportedAirUnit.currentTile)
        assertFalse(untransportedAirUnit.isTransported)
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

        assertFalse(warrior1.movement.canReachInCurrentTurn(testGame.tileMap[2,2]))
        assertTrue(warrior2.movement.canReachInCurrentTurn(testGame.tileMap[1,1]))
        assertFalse(settler1.movement.canReachInCurrentTurn(testGame.tileMap[2,2]))
        assertTrue(settler2.movement.canReachInCurrentTurn(testGame.tileMap[1,1]))
        assertTrue(warrior1.movement.canMoveTo(testGame.tileMap[2,2], allowSwap = true))
        assertTrue(warrior2.movement.canMoveTo(testGame.tileMap[1,1], allowSwap = true))
        assertTrue(settler1.movement.canMoveTo(testGame.tileMap[2,2], allowSwap = true))
        assertTrue(settler2.movement.canMoveTo(testGame.tileMap[1,1], allowSwap = true))
        assertFalse(warrior1.movement.canUnitSwapTo(testGame.tileMap[2,2]))
        assertFalse(warrior2.movement.canUnitSwapTo(testGame.tileMap[1,1]))
        assertFalse(settler1.movement.canUnitSwapTo(testGame.tileMap[2,2]))
        assertFalse(settler2.movement.canUnitSwapTo(testGame.tileMap[1,1]))

        warrior1.movement.swapMoveToTile(testGame.tileMap[2,2])

        assertEquals(testGame.tileMap[2,2], warrior1.currentTile)
        assertEquals(testGame.tileMap[1,1], settler1.currentTile)
        assertEquals(testGame.tileMap[1,1], warrior2.currentTile)
        assertEquals(testGame.tileMap[2,2], settler2.currentTile)
        assertEquals(warrior1, settler2.getOtherEscortUnit())
        assertEquals(warrior2, settler1.getOtherEscortUnit())
    }

    @Test
    fun loadedCarrierDoesNotEnterCityThatCannotHoldItsPayload() {
        val cityTile = testGame.tileMap[0, 0]
        for (neighbor in cityTile.neighbors) {
            neighbor.baseTerrain = Constants.coast
            neighbor.setTransients()
        }
        val city = testGame.addCity(civInfo, cityTile)
        val water = cityTile.neighbors.first()
        // One valid fighter, then the stale flag, then a full city garrison that stays city-based.
        val stale = testGame.addUnit("Fighter", civInfo, cityTile)
        stale.isTransported = true
        val cityAircraft = ArrayList<MapUnit>()
        repeat(city.getMaxAirUnits()) {
            cityAircraft += testGame.addUnit("Fighter", civInfo, cityTile)
        }
        val carrier = testGame.addUnit("Carrier", civInfo, water)
        carrier.health = 30
        val payload = listOf(
            testGame.addUnit("Fighter", civInfo, water),
            testGame.addUnit("Fighter", civInfo, water)
        )
        val idsBefore = civInfo.units.getCivUnits().map { it.id }.sorted().toList()

        assertEquals(UnitMovement.CannotMoveToReason.NoAirUnitTransport, carrier.movement.getCannotMoveToReason(cityTile))
        assertFalse(carrier.movement.canMoveTo(cityTile))

        carrier.movement.moveToTile(cityTile)

        assertEquals(water, carrier.currentTile)
        for (passenger in payload) {
            assertEquals(water, passenger.currentTile)
            assertTrue(passenger.isTransported)
            assertTrue(water.airUnits.contains(passenger))
            assertFalse(cityTile.airUnits.contains(passenger))
        }

        UnitAutomation.automateUnitMoves(carrier)

        assertEquals(idsBefore, civInfo.units.getCivUnits().map { it.id }.sorted().toList())
        for (unit in civInfo.units.getCivUnits()) {
            var seen = 0
            for (mapTile in testGame.tileMap.tileList)
                seen += mapTile.getUnits().count { it == unit }
            assertEquals(1, seen)
        }
        assertEquals(water, carrier.currentTile)
        for (passenger in payload) {
            assertEquals(water, passenger.currentTile)
            assertTrue(passenger.isTransported)
        }
        assertEquals(cityTile, stale.currentTile)
        assertTrue(stale.isTransported)
        for (aircraft in cityAircraft) {
            assertEquals(cityTile, aircraft.currentTile)
            assertFalse(aircraft.isTransported)
        }
    }

    @Test
    fun loadedCarrierEntersFullCityWhenAircraftAreCityBased() {
        val cityTile = testGame.tileMap[0, 0]
        val water = cityTile.neighbors.first()
        water.baseTerrain = Constants.coast
        water.setTransients()
        val city = testGame.addCity(civInfo, cityTile)
        val cityAircraft = ArrayList<MapUnit>()
        repeat(city.getMaxAirUnits()) {
            cityAircraft += testGame.addUnit("Fighter", civInfo, cityTile)
        }
        val carrier = testGame.addUnit("Carrier", civInfo, water)
        val payload = listOf(
            testGame.addUnit("Fighter", civInfo, water),
            testGame.addUnit("Fighter", civInfo, water)
        )
        for (passenger in payload)
            passenger.mostRecentMoveType = UnitMovementMemoryType.UnitTeleported

        assertTrue(carrier.movement.canMoveTo(cityTile))
        carrier.movement.moveToTile(cityTile)

        assertEquals(cityTile, carrier.currentTile)
        for (passenger in payload) {
            assertEquals(cityTile, passenger.currentTile)
            assertTrue(passenger.isTransported)
            assertEquals(UnitMovementMemoryType.UnitMoved, passenger.mostRecentMoveType)
        }
        for (aircraft in cityAircraft) {
            assertEquals(cityTile, aircraft.currentTile)
            assertFalse(aircraft.isTransported)
        }
        assertEquals(city.getMaxAirUnits(), cityTile.airUnits.count { !it.isTransported })
        assertEquals(payload.size, cityTile.airUnits.count { it.isTransported })
        assertEquals(0, carrier.checkCarryCapacity(cityAircraft[0]))
    }

    @Test
    fun loadedCarrierEntersWhenDestinationCarrierSlotsFitPayload() {
        val cityTile = testGame.tileMap[0, 0]
        val water = cityTile.neighbors.first()
        water.baseTerrain = Constants.coast
        water.setTransients()
        testGame.addCity(civInfo, cityTile)
        val carrierBase = testGame.createBaseUnit(
            "Aircraft Carrier",
            "Can carry [1] [Aircraft] units",
            "Can carry [1] extra [Fighter] units"
        ).apply {
            movement = 4
            strength = 40
        }
        val carrier = testGame.addUnit(carrierBase.name, civInfo, water)
        val resident = testGame.addUnit("Bomber", civInfo, cityTile)
        resident.isTransported = true
        val payload = testGame.addUnit("Fighter", civInfo, water)

        assertTrue(carrier.checkCarryCapacity(payload, sequenceOf(resident)) > 0)
        assertTrue(carrier.movement.canMoveTo(cityTile))
        carrier.movement.moveToTile(cityTile)

        assertEquals(cityTile, carrier.currentTile)
        assertEquals(cityTile, payload.currentTile)
        assertEquals(cityTile, resident.currentTile)
        assertTrue(payload.isTransported)
        assertTrue(resident.isTransported)
    }

    @Test
    fun loadedCarrierRejectsDestinationOneSlotOverCapacity() {
        val cityTile = testGame.tileMap[0, 0]
        val water = cityTile.neighbors.first()
        water.baseTerrain = Constants.coast
        water.setTransients()
        testGame.addCity(civInfo, cityTile)
        val carrierBase = testGame.createBaseUnit(
            "Aircraft Carrier",
            "Can carry [1] [Aircraft] units",
            "Can carry [1] extra [Fighter] units"
        ).apply {
            movement = 4
            strength = 40
        }
        val carrier = testGame.addUnit(carrierBase.name, civInfo, water)
        val resident = testGame.addUnit("Bomber", civInfo, cityTile)
        resident.isTransported = true
        val payload = listOf(
            testGame.addUnit("Fighter", civInfo, water),
            testGame.addUnit("Fighter", civInfo, water)
        )

        assertTrue(carrier.checkCarryCapacity(payload[0], sequenceOf(resident)) > 0)
        assertTrue(carrier.checkCarryCapacity(payload[1], sequenceOf(resident, payload[0])) <= 0)
        assertEquals(UnitMovement.CannotMoveToReason.NoAirUnitTransport, carrier.movement.getCannotMoveToReason(cityTile))
        carrier.movement.moveToTile(cityTile)

        assertEquals(water, carrier.currentTile)
        assertEquals(cityTile, resident.currentTile)
        assertTrue(resident.isTransported)
        for (passenger in payload) {
            assertEquals(water, passenger.currentTile)
            assertTrue(passenger.isTransported)
            assertFalse(cityTile.airUnits.contains(passenger))
        }
    }

    @Test
    fun fullCarrierKeepsPassengersOnWaterAndCityDeparture() {
        val cityTile = testGame.tileMap[0, 0]
        val water = cityTile.neighbors.first()
        water.baseTerrain = Constants.coast
        water.setTransients()
        val nextWater = water.neighbors.first { it != cityTile }
        nextWater.baseTerrain = Constants.coast
        nextWater.setTransients()
        val foreignCoast = cityTile.getTilesAtDistance(2).first { it != nextWater }
        foreignCoast.baseTerrain = Constants.coast
        foreignCoast.setTransients()
        val city = testGame.addCity(civInfo, cityTile)

        val carrier = testGame.addUnit("Carrier", civInfo, cityTile)
        val cityAircraft = ArrayList<MapUnit>()
        repeat(city.getMaxAirUnits()) {
            cityAircraft += testGame.addUnit("Fighter", civInfo, cityTile)
        }
        val passengers = listOf(
            testGame.addUnit("Fighter", civInfo, cityTile),
            testGame.addUnit("Fighter", civInfo, cityTile)
        )
        val foreignCiv = testGame.addCiv()
        testGame.addUnit("Carrier", foreignCiv, foreignCoast)
        val foreignFighter = testGame.addUnit("Fighter", foreignCiv, foreignCoast)
        foreignFighter.removeFromTile()
        cityTile.airUnits.add(foreignFighter)
        foreignFighter.currentTile = cityTile
        foreignFighter.isTransported = true

        for (passenger in passengers) {
            assertTrue(passenger.isTransported)
            passenger.mostRecentMoveType = UnitMovementMemoryType.UnitTeleported
        }
        val memorySizes = passengers.map { it.movementMemories.size }

        carrier.movement.moveToTile(water)
        carrier.movement.moveToTile(nextWater)

        assertEquals(nextWater, carrier.currentTile)
        for ((passenger, memorySize) in passengers.zip(memorySizes)) {
            assertEquals(nextWater, passenger.currentTile)
            assertTrue(passenger.isTransported)
            assertTrue(nextWater.airUnits.contains(passenger))
            assertEquals(UnitMovementMemoryType.UnitMoved, passenger.mostRecentMoveType)
            assertEquals(memorySize, passenger.movementMemories.size)
        }
        for (aircraft in cityAircraft) {
            assertEquals(cityTile, aircraft.currentTile)
            assertFalse(aircraft.isTransported)
            assertFalse(nextWater.airUnits.contains(aircraft))
        }
        assertSame(foreignCiv, foreignFighter.civ)
        assertEquals(cityTile, foreignFighter.currentTile)
        assertTrue(foreignFighter.isTransported)
        assertTrue(cityTile.airUnits.contains(foreignFighter))
        assertFalse(nextWater.airUnits.contains(foreignFighter))
    }

    @Test
    fun overCapacityCarrierCanReturnToItsOwnTile() {
        val origin = testGame.tileMap[0, 0]
        val other = testGame.tileMap[1, 0]
        origin.baseTerrain = Constants.coast
        origin.setTransients()
        other.baseTerrain = Constants.coast
        other.setTransients()
        val carrierBase = testGame.createBaseUnit(
            "Aircraft Carrier",
            "Can carry [1] [Aircraft] units"
        ).apply {
            movement = 4
            strength = 40
        }
        val carrier = testGame.addUnit(carrierBase.name, civInfo, origin)
        val fighter = testGame.addUnit("Fighter", civInfo, origin)
        val extra = testGame.addUnit("Fighter", civInfo, null)
        origin.airUnits.add(extra)
        extra.currentTile = origin
        extra.isTransported = true

        assertEquals(UnitMovement.CannotMoveToReason.TileIsNotEmpty, carrier.movement.getCannotMoveToReason(origin))
        assertEquals(UnitMovement.CannotMoveToReason.NoAirUnitTransport, carrier.movement.getCannotMoveToReason(other))

        carrier.removeFromTile()
        carrier.putInTile(origin)

        assertEquals(origin, carrier.currentTile)
        assertTrue(origin.airUnits.contains(fighter))
        assertTrue(origin.airUnits.contains(extra))
        assertTrue(fighter.isTransported)
        assertTrue(extra.isTransported)
        assertEquals(UnitMovement.CannotMoveToReason.NoAirUnitTransport, carrier.movement.getCannotMoveToReason(other))
    }

    @Test
    fun ordinaryAircraftBoardingRespectsCarrierSlots() {
        val cityTile = testGame.tileMap[0, 0]
        val water = cityTile.neighbors.first()
        water.baseTerrain = Constants.coast
        water.setTransients()
        testGame.addCity(civInfo, cityTile)
        val carrierBase = testGame.createBaseUnit(
            "Aircraft Carrier",
            "Can carry [1] [Fighter] units"
        ).apply {
            movement = 4
            strength = 40
        }
        val carrier = testGame.addUnit(carrierBase.name, civInfo, water)
        val fighter = testGame.addUnit("Fighter", civInfo, cityTile)
        val secondFighter = testGame.addUnit("Fighter", civInfo, cityTile)
        val bomber = testGame.addUnit("Bomber", civInfo, cityTile)

        assertTrue(carrier.canTransport(fighter))
        assertTrue(fighter.movement.canMoveTo(water))
        fighter.movement.moveToTile(water)

        assertEquals(water, fighter.currentTile)
        assertTrue(fighter.isTransported)
        assertFalse(carrier.canTransport(secondFighter))
        assertFalse(secondFighter.movement.canMoveTo(water))
        assertEquals(cityTile, secondFighter.currentTile)
        assertFalse(secondFighter.isTransported)
        assertFalse(carrier.canTransport(bomber))
        assertFalse(bomber.movement.canMoveTo(water))
        assertEquals(cityTile, bomber.currentTile)
        assertFalse(bomber.isTransported)
    }
}
