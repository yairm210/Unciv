package com.unciv.logic.automation.unit

import com.unciv.Constants
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.TurnManager
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
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
        testGame.makeHexagonalMap(7)
        civInfo = testGame.addCiv()
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
        currentTile.improvement = "Farm" // Set existing improvement
        currentTile.resource = "Iron" // This tile also has a resource needs to be enabled by a building a Mine

        val mapUnit = testGame.addUnit("Worker", civInfo, currentTile)

        // Act
        workerAutomation.automateWorkerAction(mapUnit, hashSetOf())

        // Assert
        assertEquals("Worker should have replaced already existing improvement 'Farm' with 'Mine' to enable 'Iron' resource",
            "Mine", currentTile.improvementInProgress)
        assertTrue(currentTile.turnsToImprovement > 0)
    }

    @Test
    fun `should build improvements in turns`() {
        // Add the needed tech to construct the improvements below
        for (improvement in listOf(RoadStatus.Road.name, "Farm")) {
            civInfo.tech.techsResearched.add(testGame.ruleset.tileImprovements[improvement]!!.techRequired!!)
        }
        civInfo.tech.techsResearched.add(testGame.ruleset.tileResources["Iron"]!!.revealedBy!!)

        val centerTile = testGame.tileMap[0,0] // owned by city
        val city = testGame.addCity(civInfo, centerTile)
        civInfo.addGold(100000000)
        for (tile in centerTile.getTilesInDistanceRange(2..3)) {
            city.expansion.buyTile(tile)
        }
        for (tile in centerTile.getTilesInDistance(3)) {
            tile.baseTerrain = Constants.plains
        }
        val worker = testGame.addUnit("Worker", civInfo, centerTile)
        for(i in 0..37) {
            worker.currentMovement = 2f
            NextTurnAutomation.automateCivMoves(civInfo)
            city.cityConstructions.constructionQueue.clear()
            TurnManager(civInfo).endTurn()
            // Invalidate WorkerAutomationCache
            testGame.gameInfo.turns++
        }

        var finishedCount = 0      
        var inProgressCount = 0
        for (tile in centerTile.getTilesInDistance(3)) {
            if (tile.improvement != null) finishedCount++
            if (tile.turnsToImprovement != 0) inProgressCount++
        }
        
        val maxShouldBeInProgress = 1
        assertTrue("Worker improvements in progress was greater than $maxShouldBeInProgress, actual: $inProgressCount",
            inProgressCount <= maxShouldBeInProgress)
        val minShouldHaveFinished = 6
        assertTrue("Worker should have built over $minShouldHaveFinished improvements but only built $finishedCount",
            finishedCount >= minShouldHaveFinished)
    }

    @Test
    fun `should build improvements in turns instead without roads`() {
        // Add the needed tech to construct the improvements below
        for (improvement in listOf("Farm")) {
            civInfo.tech.techsResearched.add(testGame.ruleset.tileImprovements[improvement]!!.techRequired!!)
        }
        civInfo.tech.techsResearched.add(testGame.ruleset.tileResources["Iron"]!!.revealedBy!!)

        val city1 = testGame.addCity(civInfo, testGame.tileMap[-2,1])
        val city2 = testGame.addCity(civInfo, testGame.tileMap[2,-1])
        civInfo.addGold(100000000)
        for (city in listOf(city1, city2)) {
            for (tile in city.getCenterTile().getTilesInDistance(3)) {
                if (tile.owningCity == null)
                    city.expansion.buyTile(tile)
                tile.baseTerrain = Constants.grassland
            }
        }
        val worker = testGame.addUnit("Worker", civInfo, city1.getCenterTile())
        for(i in 0..37) {
            worker.currentMovement = 2f
            NextTurnAutomation.automateCivMoves(civInfo)
            city1.cityConstructions.constructionQueue.clear()
            TurnManager(civInfo).endTurn()
            // Invalidate WorkerAutomationCache
            testGame.gameInfo.turns++
        }

        var finishedCount = 0
        var inProgressCount = 0
        for (city in listOf(city1, city2)) {
            for (tile in city.getCenterTile().getTilesInDistance(3)) {
                if (tile.improvement != null) finishedCount++
                if (tile.turnsToImprovement != 0) inProgressCount++
            }
        }

        val maxShouldBeInProgress = 1
        // This could be wrong for a few reasons, here are a few hints:
        // If workers prioritize tiles that have a polulation working on them and the working population 
        // swiches each turn, the worker will try and build multiple improvements at once.
        assertTrue("Worker improvements in progress was greater than $maxShouldBeInProgress, actual: $inProgressCount",
            inProgressCount <= maxShouldBeInProgress)
        val minShouldHaveFinished = 6
        assertTrue("Worker should have built over $minShouldHaveFinished improvements but only built $finishedCount",
            finishedCount >= minShouldHaveFinished)
    }


}
