package com.unciv.logic.civilization.managers

import com.badlogic.gdx.math.Vector2
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import com.unciv.utils.DebugUtils
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class ThreatManangerTests {

    val testGame = TestGame()
    val civ = testGame.addCiv()
    val neutralCiv = testGame.addCiv()
    val enemyCiv = testGame.addCiv()
    val threatManager = civ.threatManager


    @Before
    fun setUp() {
        DebugUtils.VISIBLE_MAP = true
        testGame.makeHexagonalMap(10)
        civ.diplomacyFunctions.makeCivilizationsMeet(enemyCiv)
        civ.diplomacyFunctions.makeCivilizationsMeet(neutralCiv)
        civ.getDiplomacyManager(enemyCiv).declareWar()
    }
    
    @Test
    fun `Distance to closest enemy with no enemies`() {
        val centerTile = testGame.getTile(Vector2(0f, 0f))
        assertEquals(5, threatManager.getDistanceToClosestEnemyUnit(centerTile,5, false))
    }

    @Test
    fun `Find tiles with enemies with no enemies`() {
        val centerTile = testGame.getTile(Vector2(0f, 0f))
        assertEquals(0, threatManager.getTilesWithEnemyUnitsInDistance(centerTile, 5).count())
    }

    @Test
    fun `Find enemies on tiles with no enemies`() {
        val centerTile = testGame.getTile(Vector2(0f, 0f))
        assertEquals(0, threatManager.getEnemyUnitsOnTiles(threatManager.getTilesWithEnemyUnitsInDistance(centerTile, 5)).count())
    }
    
    @Test
    fun `Find distance to enemy`() {
        val centerTile = testGame.getTile(Vector2(0f, 0f))
        testGame.addUnit("Warrior", enemyCiv, testGame.getTile(Vector2(3f, 0f)))
        testGame.addUnit("Warrior", neutralCiv, testGame.getTile(Vector2(1f, 1f)))
        assertEquals(3, threatManager.getDistanceToClosestEnemyUnit(centerTile, 5))
    }

    @Test
    fun `Find distance to closer enemy`() {
        val centerTile = testGame.getTile(Vector2(0f, 0f))
        testGame.addUnit("Warrior", enemyCiv, testGame.getTile(Vector2(3f, 0f)))
        testGame.addUnit("Warrior", enemyCiv, testGame.getTile(Vector2(4f, 0f)))
        assertEquals(3, threatManager.getDistanceToClosestEnemyUnit(centerTile, 5))
    }

    @Test
    fun `Find distance to enemy wrong cache`() {
        val centerTile = testGame.getTile(Vector2(0f, 0f))
        testGame.addUnit("Warrior", enemyCiv, testGame.getTile(Vector2(3f, 0f)))
        assertEquals(3, threatManager.getDistanceToClosestEnemyUnit(centerTile, 5))
        testGame.getTile(Vector2(3f, 0f)).militaryUnit!!.removeFromTile()
        testGame.addUnit("Warrior", enemyCiv, testGame.getTile(Vector2(4f, 0f)))
        assertEquals(4, threatManager.getDistanceToClosestEnemyUnit(centerTile, 5))
        testGame.getTile(Vector2(4f, 0f)).militaryUnit!!.removeFromTile()
        assertEquals(5, threatManager.getDistanceToClosestEnemyUnit(centerTile, 5, false))
    }

    @Test
    fun `Find distance to enemy cache`() {
        val centerTile = testGame.getTile(Vector2(0f, 0f))
        testGame.addUnit("Warrior", enemyCiv, testGame.getTile(Vector2(3f, 0f)))
        assertEquals(3, threatManager.getDistanceToClosestEnemyUnit(centerTile, 5))
        // An enemy unit should never be spawned closer than we previously searched
        // Therefore our cache results should return 3 instead of the closer unit at a distance of 2
        testGame.addUnit("Warrior", enemyCiv, testGame.getTile(Vector2(2f, 0f)))
        assertEquals(3, threatManager.getDistanceToClosestEnemyUnit(centerTile, 5))
    }


}