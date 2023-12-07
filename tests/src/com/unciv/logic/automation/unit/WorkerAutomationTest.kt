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
//            city.cityConstructions.constructionQueue.add(PerpetualConstruction.idle.name)
            TurnManager(civInfo).endTurn()
//            testGame.gameInfo.nextTurn()
        }

        var finishedCount = 0      
        var inProgressCount = 0
        for (tile in centerTile.getTilesInDistance(3)) {
            if (tile.improvement != null) finishedCount++
            if (tile.turnsToImprovement != 0) inProgressCount++
        }

        assertTrue("Worker improvements in progress was greater than 1, actual: $inProgressCount", 
            inProgressCount <= 1)
        assertTrue("Worker should have built over 6 improvements but only built $finishedCount", 
            finishedCount >= 6)
    }

}
