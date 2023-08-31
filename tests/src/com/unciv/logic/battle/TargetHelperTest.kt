package com.unciv.logic.battle

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.testing.GdxTestRunner
import com.unciv.uniques.TestGame
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class TargetHelperTest {
    private lateinit var attackerCiv: Civilization
    private lateinit var defenderCiv: Civilization

    private var defaultWarriorMovePoints = 2f

    private val testGame = TestGame()

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(6)
        attackerCiv = testGame.addCiv()
        defenderCiv = testGame.addCiv()

        attackerCiv.diplomacyFunctions.makeCivilizationsMeet(defenderCiv)
        attackerCiv.diplomacy[defenderCiv.civName]?.declareWar()
    }

    @Test
    fun `should retrieve city bombardable tiles`() {
        // given
        val attackerCity = testGame.addCity(attackerCiv, testGame.getTile(Vector2.Zero))

        testGame.addUnit("Warrior", attackerCiv, testGame.getTile(Vector2.Zero)) // own unit in own city, not bombardable
        testGame.addUnit("Warrior", attackerCiv, testGame.getTile(Vector2(1f, 0f))) // own unit in own city range, not bombardable
        testGame.addUnit("Warrior", attackerCiv, testGame.getTile(Vector2(3f, 3f))) // own unit outside city range, not bombardable
        testGame.addUnit("Warrior", defenderCiv, testGame.getTile(Vector2(1f, 1f))) // enemy unit inside city range, bombardable
        testGame.addUnit("Warrior", defenderCiv, testGame.getTile(Vector2(-3f, -3f))) // enemy unit outside city range, not bombardable

        val tile = testGame.setTileFeatures(Vector2(-2f, -1f), Constants.coast)
        testGame.addTileToCity(attackerCity, tile)
        testGame.addUnit("Submarine", defenderCiv, tile) // enemy unit inside city range, invisible, not bombardable

        // when
        val bombardableTiles = TargetHelper.getBombardableTiles(attackerCity)

        // then
        Assert.assertEquals(1, bombardableTiles.toList().size)
        Assert.assertTrue(bombardableTiles.contains(testGame.getTile(Vector2(1f, 1f))))
    }

    @Test
    fun `should get attackable tile when melee next to enemy unit`() {
        // given
        val attackFromTile = testGame.getTile(Vector2.Zero)
        val attackedTile = testGame.getTile(Vector2(1f, 0f))
        val attackerUnit = testGame.addUnit("Warrior", attackerCiv, attackFromTile)
        attackerUnit.currentMovement = defaultWarriorMovePoints
        testGame.addUnit("Warrior", defenderCiv, attackedTile)

        // when
        val attackableEnemies = TargetHelper.getAttackableEnemies(attackerUnit, attackerUnit.movement.getDistanceToTiles())

        // then
        assertEquals(1, attackableEnemies.toList().size)
        val attackableEnemy = attackableEnemies[0]
        assertEquals(attackFromTile, attackableEnemy.tileToAttackFrom)
        assertEquals(attackedTile, attackableEnemy.tileToAttack)
        assertEquals(defaultWarriorMovePoints, attackableEnemy.movementLeftAfterMovingToAttackTile)
    }

    @Test
    fun `should get no attackable tiles when melee next to allied unit`() {
        // given
        val attackFromTile = testGame.getTile(Vector2.Zero)
        val attackedTile = testGame.getTile(Vector2(1f, 0f))
        val attackerUnit = testGame.addUnit("Warrior", attackerCiv, attackFromTile)
        attackerUnit.currentMovement = defaultWarriorMovePoints
        testGame.addUnit("Warrior", attackerCiv, attackedTile)

        // when
        val attackableEnemies = TargetHelper.getAttackableEnemies(attackerUnit, attackerUnit.movement.getDistanceToTiles())

        // then
        assertTrue(attackableEnemies.isEmpty())
    }

    @Test
    fun `should get attackable tile when melee enemy reachable`() {
        // given
        val attackFromTile = testGame.getTile(Vector2.Zero)
        val attackedTile = testGame.getTile(Vector2(2f, 0f))  // move one tile -> melee next to each other -> attack
        val attackerUnit = testGame.addUnit("Warrior", attackerCiv, attackFromTile)
        attackerUnit.currentMovement = defaultWarriorMovePoints
        testGame.addUnit("Warrior", defenderCiv, attackedTile)

        // when
        val attackableEnemies = TargetHelper.getAttackableEnemies(attackerUnit, attackerUnit.movement.getDistanceToTiles())

        // then
        assertEquals(1, attackableEnemies.toList().size)
        val attackableEnemy = attackableEnemies[0]
        assertEquals(testGame.getTile(Vector2(1f, 0f)), attackableEnemy.tileToAttackFrom) // the attacker unit has moved...
        assertEquals(attackedTile, attackableEnemy.tileToAttack)
        assertEquals(defaultWarriorMovePoints - 1, attackableEnemy.movementLeftAfterMovingToAttackTile) // ...and thus when attacking has one fewer move point
    }

    @Test
    fun `should get no attackable tiles when melee enemy out of reach`() {
        // given
        val attackFromTile = testGame.getTile(Vector2.Zero)
        val attackedTile = testGame.getTile(Vector2(3f, 0f))  // out of reach
        val attackerUnit = testGame.addUnit("Warrior", attackerCiv, attackFromTile)
        attackerUnit.currentMovement = defaultWarriorMovePoints
        testGame.addUnit("Warrior", defenderCiv, attackedTile)

        // when
        val attackableEnemies = TargetHelper.getAttackableEnemies(attackerUnit, attackerUnit.movement.getDistanceToTiles())

        // then
        assertTrue(attackableEnemies.isEmpty())
    }
}
