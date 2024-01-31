package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.automation.unit.WorkerAutomation
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.UnitActionType
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
internal class UnitFormationTests {
    private lateinit var civInfo: Civilization

    val testGame = TestGame()
    fun setUp(size: Int) {
        testGame.makeHexagonalMap(size)
        civInfo = testGame.addCiv()
    }
    
    @Test
    fun `basic formation functionality civilian`() {
        setUp(1)
        val centerTile = testGame.getTile(Vector2(0f,0f))
        val civilianUnit = testGame.addUnit("Worker", civInfo, centerTile)
        val militaryUnit = testGame.addUnit("Warrior", civInfo, centerTile)
        assertTrue(civilianUnit.getOtherEscortUnit() != null)
        assertFalse(civilianUnit.isEscorting())
        civilianUnit.startEscorting()
        assertTrue(civilianUnit.isEscorting())
        assertTrue(militaryUnit.isEscorting())
        assertTrue(civilianUnit.getOtherEscortUnit() != null)
        civilianUnit.stopEscorting()
        assertFalse(civilianUnit.isEscorting())
        assertFalse(militaryUnit.isEscorting())
        assertTrue(civilianUnit.getOtherEscortUnit() != null)
    }

    @Test
    fun `basic formation functionality military`() {
        setUp(1)
        val centerTile = testGame.getTile(Vector2(0f,0f))
        val civilianUnit = testGame.addUnit("Worker", civInfo, centerTile)
        val militaryUnit = testGame.addUnit("Warrior", civInfo, centerTile)
        assertTrue(militaryUnit.getOtherEscortUnit() != null)
        assertFalse(militaryUnit.isEscorting())
        militaryUnit.startEscorting()
        assertTrue(militaryUnit.isEscorting())
        assertTrue(civilianUnit.isEscorting())
        assertTrue(militaryUnit.getOtherEscortUnit() != null)
        militaryUnit.stopEscorting()
        assertFalse(militaryUnit.isEscorting())
        assertFalse(civilianUnit.isEscorting())
        assertTrue(militaryUnit.getOtherEscortUnit() != null)
    }

    @Test
    fun `basic formation not available functionality`() {
        setUp(1)
        val centerTile = testGame.getTile(Vector2(0f,0f))
        val civilianUnit = testGame.addUnit("Worker", civInfo, centerTile) 
        assertFalse(civilianUnit.getOtherEscortUnit() != null)
        assertFalse(civilianUnit.isEscorting())
        civilianUnit.startEscorting()
        assertFalse(civilianUnit.isEscorting())
        civilianUnit.destroy()
        val militaryUnit = testGame.addUnit("Warrior", civInfo, centerTile)
        assertFalse(militaryUnit.getOtherEscortUnit() != null)
        assertFalse(militaryUnit.isEscorting())
        militaryUnit.startEscorting()
        assertFalse(militaryUnit.isEscorting())
    }

    @Test
    fun `formation idle units`() {
        setUp(1)
        val centerTile = testGame.getTile(Vector2(0f,0f))
        val civilianUnit = testGame.addUnit("Worker", civInfo, centerTile)
        val militaryUnit = testGame.addUnit("Warrior", civInfo, centerTile)
        civilianUnit.startEscorting()
        assertTrue(civilianUnit.isIdle())
        assertTrue(militaryUnit.isIdle())
        civilianUnit.currentMovement = 0f
        assertFalse(civilianUnit.isIdle())
        assertFalse(militaryUnit.isIdle())
        civilianUnit.currentMovement = 2f
        for (improvement in listOf("Farm")) {
            civInfo.tech.techsResearched.add(testGame.ruleset.tileImprovements[improvement]!!.techRequired!!)
        }
        centerTile.startWorkingOnImprovement(testGame.ruleset.tileImprovements["Farm"]!!, civInfo, civilianUnit)
        assertFalse(civilianUnit.isIdle())
        assertFalse(militaryUnit.isIdle())
    }
}
