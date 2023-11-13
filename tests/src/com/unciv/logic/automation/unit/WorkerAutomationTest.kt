package com.unciv.logic.automation.unit

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.RoadStatus
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
}
