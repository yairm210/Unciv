@file:Suppress("UNUSED_VARIABLE")  // These are tests and the names serve readability

package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.civilization.Civilization
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import com.unciv.utils.DebugUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
internal class UnitFormationTests {
    private lateinit var civInfo: Civilization

    val testGame = TestGame()
    fun setUp(size: Int, baseTerrain: String = Constants.desert) {
        testGame.makeHexagonalMap(size, baseTerrain)
        civInfo = testGame.addCiv()
    }

    @After
    fun wrapUp() {
        DebugUtils.VISIBLE_MAP = false
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
        civInfo.tech.techsResearched.add(testGame.ruleset.tileImprovements["Farm"]!!.techRequired!!)
        centerTile.startWorkingOnImprovement(testGame.ruleset.tileImprovements["Farm"]!!, civInfo, civilianUnit)
        assertFalse(civilianUnit.isIdle())
        assertFalse(militaryUnit.isIdle())
    }

    @Test
    fun `formation movement` () {
        setUp(3)
        val centerTile = testGame.getTile(Vector2(0f,0f))
        val civilianUnit = testGame.addUnit("Worker", civInfo, centerTile)
        val militaryUnit = testGame.addUnit("Warrior", civInfo, centerTile)
        civilianUnit.startEscorting()
        val targetTile = testGame.getTile(Vector2(0f,2f))
        civilianUnit.movement.moveToTile(targetTile)
        assert(civilianUnit.getTile() == targetTile)
        assert(militaryUnit.getTile() == targetTile)
        assertTrue(civilianUnit.isEscorting())
        assertTrue(militaryUnit.isEscorting())
    }

    @Test
    fun `stop formation movement` () {
        setUp(3)
        val centerTile = testGame.getTile(Vector2(0f,0f))
        val civilianUnit = testGame.addUnit("Worker", civInfo, centerTile)
        val militaryUnit = testGame.addUnit("Warrior", civInfo, centerTile)
        civilianUnit.startEscorting()
        civilianUnit.stopEscorting()
        val targetTile = testGame.getTile(Vector2(0f,2f))
        civilianUnit.movement.moveToTile(targetTile)
        assert(civilianUnit.getTile() == targetTile)
        assert(militaryUnit.getTile() == centerTile)
        assertFalse(civilianUnit.isEscorting())
        assertFalse(militaryUnit.isEscorting())
    }

    @Test
    fun `formation canMoveTo` () {
        setUp(3)
        val centerTile = testGame.getTile(Vector2(0f,0f))
        val civilianUnit = testGame.addUnit("Worker", civInfo, centerTile)
        val militaryUnit = testGame.addUnit("Warrior", civInfo, centerTile)
        val targetTile = testGame.getTile(Vector2(0f,2f))
        val blockingCivilianUnit = testGame.addUnit("Worker", civInfo, targetTile)
        assertFalse(civilianUnit.movement.canMoveTo(targetTile))
        assertTrue(militaryUnit.movement.canMoveTo(targetTile))
        civilianUnit.startEscorting()
        assertFalse(militaryUnit.movement.canMoveTo(targetTile))
    }

    @Test
    fun `formation canMoveTo water` () {
        setUp(3, "Ocean")
        val centerTile = testGame.getTile(Vector2(0f,0f))
        centerTile.baseTerrain = "Coast"
        centerTile.isWater = true
        centerTile.isLand = false
        civInfo.tech.embarkedUnitsCanEnterOcean = true
        civInfo.tech.addTechnology("Astronomy")
        val civilianUnit = testGame.addUnit("Work Boats", civInfo, centerTile) // Can enter ocean
        val militaryUnit = testGame.addUnit("Trireme", civInfo, centerTile) // Can't enter ocean
        val targetTile = testGame.getTile(Vector2(0f,1f))
        targetTile.isWater = true
        targetTile.isLand = false
        targetTile.isOcean = true
        assertFalse(militaryUnit.movement.canMoveTo(targetTile))
        assertTrue(civilianUnit.movement.canMoveTo(targetTile))
        civilianUnit.startEscorting()
        assertFalse(civilianUnit.movement.canMoveTo(targetTile))
    }


    @Test
    fun `formation head towards with faster units` () {
        setUp(5)
        val centerTile = testGame.getTile(Vector2(0f,0f))
        val civilianUnit = testGame.addUnit("Worker", civInfo, centerTile)
        val militaryUnit = testGame.addUnit("Horseman", civInfo, centerTile) // 4 movement
        civilianUnit.startEscorting()
        val targetTile = testGame.getTile(Vector2(0f,4f))
        val excpectedTile = testGame.getTile(Vector2(0f,2f))
        militaryUnit.movement.headTowards(targetTile)
        assert(civilianUnit.getTile() == excpectedTile)
        assert(militaryUnit.getTile() == excpectedTile)
        assertTrue(civilianUnit.isEscorting())
        assertTrue(militaryUnit.isEscorting())
        assertTrue(militaryUnit.currentMovement == 2f)
        assertFalse("The unit should not be idle if it's escort has no movement points",militaryUnit.isIdle())
    }

    @Test
    fun `getDistanceToTiles when in formation`() {
        setUp(5)
        val centerTile = testGame.getTile(Vector2(0f,0f))
        val civilianUnit = testGame.addUnit("Worker", civInfo, centerTile)
        val militaryUnit = testGame.addUnit("Horseman", civInfo, centerTile) // 4 movement
        civilianUnit.startEscorting()
        var civilianDistanceToTiles = civilianUnit.movement.getDistanceToTiles()
        assertFalse(militaryUnit.movement.getDistanceToTiles().any { !civilianDistanceToTiles.contains(it.key) })

        // Test again with caching
        civilianUnit.stopEscorting()
        militaryUnit.movement.getDistanceToTiles()
        civilianUnit.movement.getDistanceToTiles()
        civilianUnit.startEscorting()
        civilianDistanceToTiles = civilianUnit.movement.getDistanceToTiles()
        assertFalse(militaryUnit.movement.getDistanceToTiles().any { !civilianDistanceToTiles.contains(it.key) })
    }

    @Test
    fun `test escort attack and move civilian unit`() {
        setUp(3)
        val enemyCiv = testGame.addCiv()
        civInfo.diplomacyFunctions.makeCivilizationsMeet(enemyCiv)
        civInfo.getDiplomacyManager(enemyCiv)!!.declareWar()
        val centerTile = testGame.getTile(Vector2(0f,0f))
        val enemyTile = testGame.getTile(Vector2(2f,2f))
        val scout = testGame.addUnit("Warrior", civInfo, centerTile)
        val civilianUnit = testGame.addUnit("Worker", civInfo, centerTile)
        val enemyUnit = testGame.addUnit("Warrior", enemyCiv , enemyTile)
        enemyUnit.health = 1 // Needs to be killable by the scout
        scout.startEscorting()
        assertTrue(scout.isEscorting())
        assertTrue(civilianUnit.isEscorting())

        assertEquals(1, TargetHelper.getAttackableEnemies(scout, scout.movement.getDistanceToTiles()).count())
        Battle.attack(MapUnitCombatant(scout), MapUnitCombatant(enemyUnit))
        assertEquals(0, enemyUnit.health)
        assertEquals(enemyTile, scout.getTile())
        assertEquals(enemyTile, civilianUnit.getTile())
        assertTrue(scout.isEscorting())
        assertTrue(civilianUnit.isEscorting())
    }

    @Test
    fun `test escort attack with ranged unit`() {
        setUp(3)
        val enemyCiv = testGame.addCiv()
        DebugUtils.VISIBLE_MAP = true
        civInfo.diplomacyFunctions.makeCivilizationsMeet(enemyCiv)
        civInfo.getDiplomacyManager(enemyCiv)!!.declareWar()
        val centerTile = testGame.getTile(Vector2(0f,0f))
        val enemyTile = testGame.getTile(Vector2(3f,3f))
        val archer = testGame.addUnit("Archer", civInfo, centerTile)
        val civilianUnit = testGame.addUnit("Worker", civInfo, centerTile)
        val enemyUnit = testGame.addUnit("Warrior", enemyCiv , enemyTile)
        enemyUnit.health = 1 // Needs to be killable by the scout
        archer.startEscorting()
        assertTrue(archer.isEscorting())
        assertTrue(civilianUnit.isEscorting())

        assertEquals(1, TargetHelper.getAttackableEnemies(archer, archer.movement.getDistanceToTiles()).count())
        Battle.attack(MapUnitCombatant(archer), MapUnitCombatant(enemyUnit))
        assertEquals(0, enemyUnit.health)
        assertTrue(archer.isEscorting())
        assertTrue(civilianUnit.isEscorting())
        assertEquals(archer.getTile(), civilianUnit.getTile())
    }

    @Test
    fun `test escort attack with military unit having ignoreTerrainCost`() {
        setUp(3)
        val enemyCiv = testGame.addCiv()
        civInfo.diplomacyFunctions.makeCivilizationsMeet(enemyCiv)
        civInfo.getDiplomacyManager(enemyCiv)!!.declareWar()
        val centerTile = testGame.getTile(Vector2(0f,0f))
        val forestTile = testGame.getTile(Vector2(1f,1f))
        val enemyTile = testGame.getTile(Vector2(2f,2f))
        val scout = testGame.addUnit("Warrior", civInfo, centerTile)
        val civilianUnit = testGame.addUnit("Worker", civInfo, centerTile)
        val enemyUnit = testGame.addUnit("Warrior", enemyCiv , enemyTile)
        enemyUnit.health = 1 // Needs to be killable by the scout
        forestTile.addTerrainFeature("Forest")
        scout.startEscorting()
        assertTrue(scout.isEscorting())
        assertTrue(civilianUnit.isEscorting())
        assertTrue(TargetHelper.getAttackableEnemies(scout, scout.movement.getDistanceToTiles()).isEmpty())
    }

    @Test
    fun `test escort path with hills one turn civilian`() {
        setUp(3)
        val centerTile = testGame.getTile(Vector2(0f,0f))
        val hillTile = testGame.getTile(Vector2(1f,1f))
        val destinationTile = testGame.getTile(Vector2(1f,2f))
        val militaryUnit = testGame.addUnit("Mechanized Infantry", civInfo, centerTile)
        val civilianUnit = testGame.addUnit("Worker", civInfo, centerTile)
        hillTile.addTerrainFeature("Hill")
        destinationTile.addTerrainFeature("Hill")
        civilianUnit.startEscorting()
        civilianUnit.movement.moveToTile(destinationTile)
        assertEquals(civilianUnit.getTile(), destinationTile)
        assertEquals(militaryUnit.getTile(), destinationTile)
    }

    @Test
    fun `test escort path with hills one turn military`() {
        setUp(3)
        val centerTile = testGame.getTile(Vector2(0f,0f))
        val hillTile = testGame.getTile(Vector2(1f,1f))
        val destinationTile = testGame.getTile(Vector2(1f,2f))
        val militaryUnit = testGame.addUnit("Mechanized Infantry", civInfo, centerTile)
        val civilianUnit = testGame.addUnit("Worker", civInfo, centerTile)
        hillTile.addTerrainFeature("Hill")
        destinationTile.addTerrainFeature("Hill")
        militaryUnit.startEscorting()
        militaryUnit.movement.moveToTile(destinationTile)
        assertEquals(civilianUnit.getTile(), destinationTile)
        assertEquals(militaryUnit.getTile(), destinationTile)
    }
}
