package com.unciv.logic.automation.unit

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.TurnManager
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.stats.Stat
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
internal class WorkerAutomationTest {
    private lateinit var workerAutomation: WorkerAutomation
    private lateinit var civInfo: Civilization

    val testGame = TestGame()

    @Before
    fun setUp() {
        testGame.ruleset.units["Worker"]!!.uniques.remove("Can transform to [Warrior] <in [Desert] tiles> <with [71] priority>")
        testGame.ruleset.units["Worker"]!!.uniques.remove("Can transform to [Warrior] <in [Desert] tiles> <with [70] priority>")
        testGame.ruleset.units["Worker"]!!.uniques.remove("Can transform to [Warrior] <in [Desert] tiles> <with [51] priority>")
        testGame.ruleset.units["Worker"]!!.uniques.remove("Can transform to [Warrior] <in [Desert] tiles> <with [50] priority>")
        testGame.ruleset.units["Worker"]!!.uniques.remove("Can transform to [Warrior] <in [Desert] tiles> <with [26] priority>")
        testGame.ruleset.units["Worker"]!!.uniques.remove("Can transform to [Warrior] <in [Desert] tiles> <with [25] priority>")
        UncivGame.Current.settings.useAStarPathfinding = true
        testGame.makeHexagonalMap(7)
        civInfo = testGame.addCiv()
        civInfo.tech.addTechnology("The Wheel")
        civInfo.tech.addTechnology("Railroads")
        workerAutomation = WorkerAutomation(civInfo, 3)
    }

    @Test
    fun `should replace already existing improvement to enable resource`() {
        // Add the needed tech to construct the improvements below
        for (improvement in listOf(RoadStatus.Road.name, "Farm", "Mine")) {
            civInfo.tech.techsResearched.add(testGame.ruleset.tileImprovements[improvement]!!.techRequired!!)
        }
        civInfo.tech.techsResearched.add(testGame.ruleset.tileResources["Iron"]!!.revealedBy!!)

        testGame.addCity(civInfo, testGame.tileMap[0,0])

        val currentTile = testGame.tileMap[1,1] // owned by city
        currentTile.setImprovementBasic("Farm") // Set existing improvement
        currentTile.setTileResource("Iron") // This tile also has a resource needs to be enabled by a building a Mine

        val mapUnit = testGame.addUnit("Worker", civInfo, currentTile)

        // Act
        workerAutomation.automateWorkerAction(mapUnit, UniqueActionQueue(mapUnit), hashSetOf())

        // Assert
        assertEquals("Worker should have replaced already existing improvement 'Farm' with 'Mine' to enable 'Iron' resource",
            "Mine", currentTile.improvementInProgress
        )
        assertTrue(currentTile.turnsToImprovement > 0)
    }

    @Test
    fun `should remove terrain feature enable resource`() {
        // Add the needed tech to construct the improvements below
        for (improvement in listOf("Remove Forest", "Plantation")) {
            civInfo.tech.techsResearched.add(testGame.ruleset.tileImprovements[improvement]!!.techRequired!!)
        }

        testGame.addCity(civInfo, testGame.tileMap[0,0])

        val currentTile = testGame.tileMap[1,1]
        currentTile.addTerrainFeature("Hill")
        currentTile.addTerrainFeature("Forest")
        currentTile.setTileResource("Citrus")
        currentTile.resourceAmount = 1

        val mapUnit = testGame.addUnit("Worker", civInfo, currentTile)

        workerAutomation.automateWorkerAction(mapUnit, UniqueActionQueue(mapUnit), hashSetOf())

        assertEquals("Worker should begun removing the forest to clear a luxury resource but didn't",
            "Remove Forest", currentTile.improvementInProgress
        )
        assertTrue(currentTile.turnsToImprovement > 0)
    }

    @Test
    fun `should build improvement on unseen resource`() {
        // Add the needed tech to construct the improvements below
        for (improvement in listOf(RoadStatus.Road.name, "Farm")) {
            civInfo.tech.techsResearched.add(testGame.ruleset.tileImprovements[improvement]!!.techRequired!!)
        }

        val currentTile = testGame.tileMap[1,1]
        val city = testGame.addCity(civInfo, testGame.tileMap[0,0])
        // Currently worked tile is prioritized for worker actions
        city.workedTiles.clear()
        city.workedTiles.add(currentTile.position)

        currentTile.baseTerrain = Constants.grassland
        currentTile.setTileResource("Iron")

        val mapUnit = testGame.addUnit("Worker", civInfo, currentTile)

        // Act
        workerAutomation.automateWorkerAction(mapUnit, UniqueActionQueue(mapUnit),  hashSetOf())

        // Assert
        assertEquals("Worker should be buliding a farm under 'Iron' resource because it can't see it and has nothing else to do",
            "Farm", currentTile.improvementInProgress
        )
        assertTrue(currentTile.turnsToImprovement > 0)
    }
    
    @Test
    fun `should prioritise better tiles`() {
        // Add the needed tech to construct the improvements below
        for (improvement in listOf(RoadStatus.Road.name, "Farm", "Mine")) {
            civInfo.tech.techsResearched.add(testGame.ruleset.tileImprovements[improvement]!!.techRequired!!)
        }

        testGame.addCity(civInfo, testGame.tileMap[0,0])
        for (tile in testGame.tileMap[0,0].getTilesInDistance(3)) {
            tile.baseTerrain = Constants.grassland
        }

        val currentTile = testGame.tileMap[1,1]
        currentTile.baseTerrain = Constants.grassland
        currentTile.addTerrainFeature(Constants.hill)
        currentTile.setTileResource("Gold Ore")

        val mapUnit = testGame.addUnit("Worker", civInfo, currentTile)

        workerAutomation.automateWorkerAction(mapUnit, UniqueActionQueue(mapUnit), hashSetOf())

        assertEquals("Worker should be buliding a mine on the Gold Ore luxury resource",
            "Mine", currentTile.improvementInProgress
        )
        assertTrue(currentTile.turnsToImprovement > 0)
    }

    @Test
    fun `should build improvements in turns`() {
        // Add the needed tech to construct the improvements below
        for (improvement in listOf(RoadStatus.Road.name, "Farm")) {
            civInfo.tech.techsResearched.add(testGame.ruleset.tileImprovements[improvement]!!.techRequired!!)
        }

        val centerTile = testGame.tileMap[0,0] // owned by city
        val city = testGame.addCity(civInfo, centerTile)
        civInfo.addGold(100000000)
        for (tile in centerTile.getTilesInDistanceRange(2..3)) {
            city.expansion.buyTile(tile)
        }
        for (tile in centerTile.getTilesInDistance(3)) {
            tile.baseTerrain = Constants.plains
        }
        city.reassignAllPopulation()
        val worker = testGame.addUnit("Worker", civInfo, centerTile)
        for(i in 0..37) {
            worker.currentMovement = 2f
            for (unit in civInfo.units.getCivUnits()) {
                // Disband any workers that may have been built in this time period
                if (unit != worker && unit.isCivilian()) {
                    unit.disband()
                }
            }
            // Prevent any sort of worker spawning
            civInfo.addGold(-civInfo.gold)
            civInfo.policies.freePolicies = 0
            civInfo.addStat(Stat.Science, - 100000)
            
            NextTurnAutomation.automateCivMoves(civInfo)
            TurnManager(civInfo).endTurn()
            // Invalidate WorkerAutomationCache
            testGame.gameInfo.turns++

            // Set the population back to 1
            if (city.population.population != 1)
                city.population.addPopulation( 1 - city.population.population)
        }

        var finishedCount = 0      
        var inProgressCount = 0
        for (tile in centerTile.getTilesInDistance(3)) {
            if (tile.improvement != null && !tile.isCityCenter()) finishedCount++
            if (tile.turnsToImprovement != 0) inProgressCount++
        }
        
        val maxShouldBeInProgress = 1
        assertTrue("Worker improvements in progress was greater than $maxShouldBeInProgress, actual: $inProgressCount",
            inProgressCount <= maxShouldBeInProgress)
        val minShouldHaveFinished = 5
        assertTrue("Worker should have built at least $minShouldHaveFinished improvements but only built $finishedCount",
            finishedCount >= minShouldHaveFinished)
    }

    @Test
    fun `should build improvements in turns without roads`() {
        // Add the needed tech to construct the improvements below
        for (improvement in listOf("Farm")) {
            civInfo.tech.techsResearched.add(testGame.ruleset.tileImprovements[improvement]!!.techRequired!!)
        }

        val city1 = testGame.addCity(civInfo, testGame.tileMap[-2,1])
        val city2 = testGame.addCity(civInfo, testGame.tileMap[2,-1])
        val cities = listOf(city1, city2)
        civInfo.addGold(100000000)
        for (city in cities) {
            for (tile in city.getCenterTile().getTilesInDistance(3)) {
                if (tile.owningCity == null)
                    city.expansion.buyTile(tile)
                tile.baseTerrain = Constants.grassland
            }
        }
        for (city in cities) {
            // Make sure that the worker know which tiles to work on first
            city.reassignAllPopulation()
        }
        val worker = testGame.addUnit("Worker", civInfo, city1.getCenterTile())
        for(i in 0..37) {
            worker.currentMovement = 2f
            for (unit in civInfo.units.getCivUnits()) {
                // Disband any workers that may have been built in this time period
                if (unit != worker && unit.isCivilian()) {
                    unit.disband()
                }
            }
            // Prevent any sort of worker spawning
            civInfo.addGold(-civInfo.gold)
            civInfo.policies.freePolicies = 0
            civInfo.addStat(Stat.Science, - 100000)
            
            NextTurnAutomation.automateCivMoves(civInfo)
            TurnManager(civInfo).endTurn()
            // Invalidate WorkerAutomationCache
            testGame.gameInfo.turns++
            for (city in cities) {
                // Set the population back to 1
                if (city.population.population != 1)
                    city.population.addPopulation( 1 - city.population.population)
            }
        }

        var finishedCount = 0
        var inProgressCount = 0
        val checkedTiles = HashSet<Tile>()
        for (city in cities) {
            for (tile in city.getCenterTile().getTilesInDistance(3)) {
                if (checkedTiles.contains(tile)) continue
                if (tile.improvement != null && !tile.isCityCenter()) finishedCount++
                if (tile.turnsToImprovement != 0) inProgressCount++
                checkedTiles.add(tile)
            }
        }

        // This could be wrong for a few reasons, here are a few hints:
        // If workers prioritize tiles that have a polulation working on them and the working population 
        // swiches each turn, the worker will try and build multiple improvements at once.
        val maxShouldBeInProgress = 1
        assertTrue("Worker improvements in progress was greater than $maxShouldBeInProgress, actual: $inProgressCount",
            inProgressCount <= maxShouldBeInProgress)
        val minShouldHaveFinished = 5
        assertTrue("Worker should have built at least $minShouldHaveFinished improvements but only built $finishedCount",
            finishedCount >= minShouldHaveFinished)
    }

    @Test
    fun `should build roads in turns`() {
        // Add the needed tech to construct the improvements below
        for (improvement in listOf(RoadStatus.Road.name)) {
            civInfo.tech.techsResearched.add(testGame.ruleset.tileImprovements[improvement]!!.techRequired!!)
        }
        civInfo.tech.techsResearched.remove(testGame.ruleset.tileImprovements["Farm"]!!.techRequired!!)

        val city1 = testGame.addCity(civInfo, testGame.tileMap[3,3])
        val city2 = testGame.addCity(civInfo, testGame.tileMap[-3,-3])
        // preexisting road along half, just to complicate things
        testGame.tileMap[3,3].setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.tileMap[2,2].setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.tileMap[1,1].setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.tileMap[0,0].setRoadStatus(RoadStatus.Railroad, civInfo)
        //testGame.tileMap[1,1].setRoadStatus(RoadStatus.Railroad, civInfo)
        val cities = listOf(city1, city2)
        civInfo.addGold(100000000)
        for (city in cities) {
            for (tile in city.getCenterTile().getTilesInDistance(3)) {
                if (tile.owningCity == null)
                    city.expansion.buyTile(tile)
            }
        }
        for (city in cities) {
            // Make sure that we want to build a road between the cities
            city.population.addPopulation( 10)
            // Need to make sure that we have gold per turn to want to build the roads
            city.cityConstructions.addBuilding("Stock Exchange")
            city.cityConstructions.addBuilding("Stock Exchange")
            city.cityConstructions.addBuilding("Stock Exchange")
        }
        val worker = testGame.addUnit("Worker", civInfo, city1.getCenterTile())
        for(i in 0..24) {
            worker.currentMovement = 2f
            for (unit in civInfo.units.getCivUnits()) {
                // Disband any workers that may have been built in this time period
                if (unit != worker && unit.isCivilian()) {
                    unit.disband()
                }
            }
            // Prevent any sort of worker spawning
            civInfo.addGold(-civInfo.gold)
            civInfo.policies.freePolicies = 0

            NextTurnAutomation.automateCivMoves(civInfo)
            TurnManager(civInfo).endTurn()
            // Invalidate WorkerAutomationCache
            testGame.gameInfo.turns++
            // Because the civ will annoyingly try to research it again
            civInfo.tech.techsResearched.remove(testGame.ruleset.tileImprovements["Farm"]!!.techRequired!!)
        }

        var finishedCount = 0
        var inProgressCount = 0
        for (tile in testGame.tileMap[0,0].getTilesInDistance(5)) {
            if (tile.roadStatus != RoadStatus.None && !tile.isCityCenter()) finishedCount++
            if (tile.turnsToImprovement != 0) inProgressCount++
        }

        val maxShouldBeInProgress = 1
        assertTrue("Worker roads in progress was greater than $maxShouldBeInProgress, actual: $inProgressCount",
            inProgressCount <= maxShouldBeInProgress)
        val minShouldHaveFinished = 5
        assertTrue("Worker should have built $minShouldHaveFinished roads but only built $finishedCount",
            finishedCount >= minShouldHaveFinished)
        civInfo.cache.updateCitiesConnectedToCapital()
        assertTrue("Worker should have built roads to connect the two cities", city2.isConnectedToCapital())
    }

    @Test
    fun `should repair pillaged tile`() {
        // Add the needed tech to construct the improvements below
        for (improvement in listOf("Mine")) {
            civInfo.tech.techsResearched.add(testGame.ruleset.tileImprovements[improvement]!!.techRequired!!)
        }
        civInfo.tech.techsResearched.add(testGame.ruleset.tileResources["Iron"]!!.revealedBy!!)

        testGame.addCity(civInfo, testGame.tileMap[0,0])

        val currentTile = testGame.tileMap[1,1] // owned by city
        currentTile.setTileResource("Iron") // This tile also has a resource needs to be enabled by a building a Mine
        currentTile.setImprovement("Mine")
        currentTile.setPillaged()

        val mapUnit = testGame.addUnit("Worker", civInfo, currentTile)

        // Act
        workerAutomation.automateWorkerAction(mapUnit, UniqueActionQueue(mapUnit), hashSetOf())

        // Assert
        assertEquals("Worker should try to repair the mine",
            "Repair", currentTile.improvementInProgress
        )
        assertTrue(currentTile.turnsToImprovement > 0)
    }
    

    @Test
    fun automateWorkerAction_nearPotentialImprovement_withModdedUnitAction_At71Priority_usesAction() {
        civInfo.tech.techsResearched.addAll(testGame.ruleset.technologies.keys)
        testGame.addCity(civInfo, testGame.tileMap[0,0])
        testGame.setTileTerrain(HexCoord(0,1), "Desert")

        testGame.ruleset.units["Worker"]!!.uniques.add("Can transform to [Warrior] <in [Desert] tiles> <with [71] priority>")
        val unit: MapUnit = testGame.addUnit("Worker", civInfo, testGame.tileMap[0,0])

        workerAutomation.automateWorkerAction(unit, UniqueActionQueue(unit), hashSetOf())
        
        assertTrue(unit.isDestroyed)
        assertEquals("Warrior", testGame.tileMap[0,1].militaryUnit!!.baseUnit.name)
        assertNull(testGame.tileMap[0,0].improvementInProgress)
    }

    @Test
    fun automateWorkerAction_nearPotentialImprovement_withModdedUnitAction_At70Priority_findsTileToWork() {
        civInfo.tech.techsResearched.addAll(testGame.ruleset.technologies.keys)
        testGame.addCity(civInfo, testGame.tileMap[0,0])
        testGame.setTileTerrain(HexCoord(0,1), "Desert")

        testGame.ruleset.units["Worker"]!!.uniques.add("Can transform to [Warrior] <in [Desert] tiles> <with [70] priority>")
        val unit = testGame.addUnit("Worker", civInfo, testGame.tileMap[0,0])

        workerAutomation.automateWorkerAction(unit, UniqueActionQueue(unit), hashSetOf())

        assertFalse(unit.isDestroyed)
        assertEquals("Farm", testGame.tileMap[1,0].improvementInProgress)
        assertEquals("Worker", unit.baseUnit.name)
        assertEquals(HexCoord(1, 0), unit.currentTile.position)
    }

    @Test
    fun automateWorkerAction_nearUndevelopedCity_withModdedUnitAction_At51Priority_usesAction() {
        civInfo.tech.techsResearched.addAll(testGame.ruleset.technologies.keys)
        val city1 = testGame.addCity(civInfo, testGame.tileMap[-6,-6])
        testGame.addCity(civInfo, testGame.tileMap[6,6])
        testGame.setTileTerrain(HexCoord(-5,-4), "Desert")
        for (tile in city1.tilesInRange) tile.setImprovement("Farm", civInfo)

        testGame.ruleset.units["Worker"]!!.uniques.add("Can transform to [Warrior] <in [Desert] tiles> <with [51] priority>")
        val unit = testGame.addUnit("Worker", civInfo, testGame.tileMap[-5,-5])

        workerAutomation.automateWorkerAction(unit, UniqueActionQueue(unit), hashSetOf())

        assertTrue(unit.isDestroyed)
        assertEquals("Warrior", testGame.tileMap[-5,-4].militaryUnit!!.baseUnit.name)
    }

    @Test
    fun automateWorkerAction_nearUndevelopedCity_withModdedUnitAction_At50Priority_seeksCity() {
        civInfo.tech.techsResearched.addAll(testGame.ruleset.technologies.keys)
        val city1 = testGame.addCity(civInfo, testGame.tileMap[-6,-6])
        testGame.addCity(civInfo, testGame.tileMap[6,6])
        testGame.setTileTerrain(HexCoord(-5,-4), "Desert")
        for (tile in city1.tilesInRange) tile.setImprovement("Farm", civInfo)

        testGame.ruleset.units["Worker"]!!.uniques.add("Can transform to [Warrior] <in [Desert] tiles> <with [50] priority>")
        val unit = testGame.addUnit("Worker", civInfo, testGame.tileMap[-5,-5])

        workerAutomation.automateWorkerAction(unit, UniqueActionQueue(unit), hashSetOf())

        assertFalse(unit.isDestroyed)
        assertEquals(HexCoord(-3, -3), unit.currentTile.position)
        assertEquals("Worker", unit.baseUnit.name)
    }

    @Test
    fun automateWorkerAction_nearUnconnectedCity_withModdedUnitAction_At26Priority_usesAction() {
        civInfo.tech.techsResearched.addAll(testGame.ruleset.technologies.keys)
        val city1 = testGame.addCity(civInfo, testGame.tileMap[0,0])
        val city2 = testGame.addCity(civInfo, testGame.tileMap[4,4])
        testGame.setTileTerrain(HexCoord(0,1), "Desert")
        for (tile in city1.tilesInRange) tile.setImprovement("Farm", civInfo)
        for (tile in city2.tilesInRange) tile.setImprovement("Farm", civInfo)

        testGame.ruleset.units["Worker"]!!.uniques.add("Can transform to [Warrior] <in [Desert] tiles> <with [26] priority>")
        val unit = testGame.addUnit("Worker", civInfo, testGame.tileMap[0,0])

        workerAutomation.automateWorkerAction(unit, UniqueActionQueue(unit), hashSetOf())

        assertTrue(unit.isDestroyed)
        assertEquals("Warrior", testGame.tileMap[0,1].militaryUnit!!.baseUnit.name)
        assertNull(testGame.tileMap[-1,-1].improvementInProgress)
    }

    @Test
    fun automateWorkerAction_nearUnconnectedCity_withModdedUnitAction_At25Priority_connectsCities() {
        civInfo.tech.techsResearched.addAll(testGame.ruleset.technologies.keys)
        val city1 = testGame.addCity(civInfo, testGame.tileMap[0,0])
        val city2 = testGame.addCity(civInfo, testGame.tileMap[4,4])
        testGame.setTileTerrain(HexCoord(0,1), "Desert")
        for (tile in city1.tilesInRange) tile.setImprovement("Farm", civInfo)
        for (tile in city2.tilesInRange) tile.setImprovement("Farm", civInfo)
        
        testGame.ruleset.units["Worker"]!!.uniques.add("Can transform to [Warrior] <in [Desert] tiles> <with [25] priority>")
        val unit = testGame.addUnit("Worker", civInfo, testGame.tileMap[0,0])

        workerAutomation.automateWorkerAction(unit, UniqueActionQueue(unit), hashSetOf())
        
        assertFalse(unit.isDestroyed)
        assertEquals(HexCoord(1, 1), unit.currentTile.position)
        assertEquals("Worker", unit.baseUnit.name)
        assertEquals("Railroad", testGame.tileMap[1,1].improvementInProgress)
    }
}
