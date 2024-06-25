package com.unciv.logic.battle

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
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
    private var defaultArcherMovePoints = 2f

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

        val tile = testGame.setTileTerrain(Vector2(-2f, -1f), Constants.coast)
        testGame.addTileToCity(attackerCity, tile)
        testGame.addUnit("Submarine", defenderCiv, tile) // enemy unit inside city range, invisible, not bombardable

        // when
        val bombardableTiles = TargetHelper.getBombardableTiles(attackerCity)

        // then
        assertEquals(1, bombardableTiles.toList().size)
        assertTrue(bombardableTiles.contains(testGame.getTile(Vector2(1f, 1f))))
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
    fun `should get no attackable tiles when unit next to allied unit`() {
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

    @Test
    fun `should get attackable tiles when ranged next to melee enemy unit with ZOC`() {
        // given
        val attackerTile = testGame.getTile(Vector2.Zero)
        val attackedTile = testGame.getTile(Vector2(1f, 0f))
        val attackerUnit = testGame.addUnit("Archer", attackerCiv, attackerTile)
        attackerUnit.currentMovement = defaultArcherMovePoints
        testGame.addUnit("Warrior", defenderCiv, attackedTile)

        /*  _____         _____         _____
        *  /     \       /     \       /     \          Legend:
        * /       \_____/  ZOC  \_____/       \         A-O: Archer, attacker
        * \       /     \       /     \       /         W-1: Warrior enemy
        *  \_____/  W-1  \_____/   X   \_____/          ZOC: Zone of Control from W-1
        *  /     \       /     \       /     \          X: possible expected tile movements
        * /       \_____/  A-0  \_____/       \
        * \       /     \   X   /     \       /
        *  \_____/  ZOC  \__ __/   X   \_____/
        *  /     \       /     \       /     \
        * /       \_____/   X   \_____/       \
        * \       /     \       /     \       /
        *  \_____/       \_____/       \_____/
        *        \       /     \       /
        *         \_____/       \_____/
        */


        // when
        val attackableEnemies = TargetHelper.getAttackableEnemies(attackerUnit, attackerUnit.movement.getDistanceToTiles())

        // then
        assertEquals(4, attackableEnemies.toList().size)
        val attackableEnemyWithoutMoving = attackableEnemies[0]
        assertEquals(attackerTile, attackableEnemyWithoutMoving.tileToAttackFrom)
        assertEquals(attackedTile, attackableEnemyWithoutMoving.tileToAttack)
        assertEquals(defaultArcherMovePoints, attackableEnemyWithoutMoving.movementLeftAfterMovingToAttackTile)
        val attackableEnemyMovingDown = attackableEnemies[1]
        assertEquals(testGame.getTile(Vector2(-1f, -1f)), attackableEnemyMovingDown.tileToAttackFrom)
        assertEquals(attackedTile, attackableEnemyMovingDown.tileToAttack)
        assertEquals(defaultArcherMovePoints - 1, attackableEnemyMovingDown.movementLeftAfterMovingToAttackTile)
        val attackableEnemyMovingUpRigth = attackableEnemies[2]
        assertEquals(testGame.getTile(Vector2(0f, 1f)), attackableEnemyMovingUpRigth.tileToAttackFrom)
        assertEquals(attackedTile, attackableEnemyMovingUpRigth.tileToAttack)
        assertEquals(defaultArcherMovePoints - 1, attackableEnemyMovingUpRigth.movementLeftAfterMovingToAttackTile)
        val attackableEnemyMovingDownLeft = attackableEnemies[3]
        assertEquals(testGame.getTile(Vector2(-1f, 0f)), attackableEnemyMovingDownLeft.tileToAttackFrom)
        assertEquals(attackedTile, attackableEnemyMovingDownLeft.tileToAttack)
        assertEquals(defaultArcherMovePoints - 1, attackableEnemyMovingDownLeft.movementLeftAfterMovingToAttackTile)
    }

    @Test
    fun `should get attackable tiles when ranged already in range to enemy unit`() {
        // given
        val attackerTile = testGame.getTile(Vector2(-1f, 0f))
        val attackedTile = testGame.getTile(Vector2(1f, 0f))
        val attackerUnit = testGame.addUnit("Archer", attackerCiv, attackerTile)
        attackerUnit.currentMovement = defaultArcherMovePoints
        testGame.addUnit("Warrior", defenderCiv, attackedTile)

        /*  _____         _____         _____
        *  /     \       /     \       /     \          Legend:
        * /       \_____/       \_____/       \         A-O: Archer, attacker
        * \       /     \       /     \       /         W-1: Warrior enemy
        *  \_____/  W-1  \_____/   X   \_____/          X: possible expected tile movements
        *  /     \       /     \       /     \
        * /       \_____/   X   \_____/       \
        * \       /     \       /     \       /
        *  \_____/       \__ __/  A-0  \_____/
        *  /     \       /     \   X   /     \
        * /       \_____/   X   \_____/       \
        * \       /     \       /     \       /
        *  \_____/       \_____/       \_____/
        *        \       /     \       /
        *         \_____/       \_____/
        */


        // when
        val attackableEnemies = TargetHelper.getAttackableEnemies(attackerUnit, attackerUnit.movement.getDistanceToTiles())

        // then
        assertEquals(4, attackableEnemies.toList().size)
        val attackableEnemyWithoutMoving = attackableEnemies[0]
        assertEquals(attackerTile, attackableEnemyWithoutMoving.tileToAttackFrom)
        assertEquals(attackedTile, attackableEnemyWithoutMoving.tileToAttack)
        assertEquals(defaultArcherMovePoints, attackableEnemyWithoutMoving.movementLeftAfterMovingToAttackTile)
        val attackableEnemyMovingUp = attackableEnemies[1]
        assertEquals(testGame.getTile(Vector2(0f, 1f)), attackableEnemyMovingUp.tileToAttackFrom)
        assertEquals(attackedTile, attackableEnemyMovingUp.tileToAttack)
        assertEquals(defaultArcherMovePoints - 1, attackableEnemyMovingUp.movementLeftAfterMovingToAttackTile)
        val attackableEnemyMovingDownLeft = attackableEnemies[2]
        assertEquals(testGame.getTile(Vector2(-1f, -1f)), attackableEnemyMovingDownLeft.tileToAttackFrom)
        assertEquals(attackedTile, attackableEnemyMovingDownLeft.tileToAttack)
        assertEquals(defaultArcherMovePoints - 1, attackableEnemyMovingDownLeft.movementLeftAfterMovingToAttackTile)
        val attackableEnemyMovingUpLeft = attackableEnemies[3]
        assertEquals(testGame.getTile(Vector2.Zero), attackableEnemyMovingUpLeft.tileToAttackFrom)
        assertEquals(attackedTile, attackableEnemyMovingUpLeft.tileToAttack)
        assertEquals(defaultArcherMovePoints - 1, attackableEnemyMovingUpLeft.movementLeftAfterMovingToAttackTile)
    }

    @Test
    fun `should get attackable tiles when ranged movable in range to enemy unit`() {
        // given
        val attackerTile = testGame.getTile(Vector2(-2f, 0f))
        val attackedTile = testGame.getTile(Vector2(1f, 0f))
        val attackerUnit = testGame.addUnit("Archer", attackerCiv, attackerTile)
        testGame.addUnit("Warrior", attackerCiv, testGame.getTile(Vector2(0f, 1f))) // otherwise the archer cannot see the enemy warrior
        attackerUnit.currentMovement = defaultArcherMovePoints
        testGame.addUnit("Warrior", defenderCiv, attackedTile)

        /*  _____         _____         _____
        *  /     \       /     \       /     \          Legend:
        * /       \_____/       \_____/       \         A-O: Archer, attacker
        * \       /     \       /     \       /         W-0: Warrior, allied to archer
        *  \_____/  W-1  \_____/  W-0  \_____/          W-1: Warrior, defender
        *  /     \       /     \       /     \          X: possible expected tile movements
        * /       \_____/       \_____/       \
        * \       /     \       /     \       /
        *  \_____/       \__ __/   X   \_____/
        *  /     \       /     \       /     \
        * /       \_____/       \_____/  A-0  \
        * \       /     \       /     \       /
        *  \_____/       \_____/       \_____/
        *        \       /     \       /
        *         \_____/       \_____/
        */


        // when
        val attackableEnemies = TargetHelper.getAttackableEnemies(attackerUnit, attackerUnit.movement.getDistanceToTiles())

        // then
        assertEquals(1, attackableEnemies.toList().size)
        val attackableEnemyMovingThenFiring = attackableEnemies[0]
        assertEquals(testGame.getTile(Vector2(-1f, 0f)), attackableEnemyMovingThenFiring.tileToAttackFrom)
        assertEquals(attackedTile, attackableEnemyMovingThenFiring.tileToAttack)
        assertEquals(defaultArcherMovePoints - 1, attackableEnemyMovingThenFiring.movementLeftAfterMovingToAttackTile)
    }

    @Test
    fun `should get no attackable tiles when enemy unit is out of sight`() {
        // given
        val attackerTile = testGame.getTile(Vector2(-2f, 0f))
        val attackedTile = testGame.getTile(Vector2(1f, 0f))
        val attackerUnit = testGame.addUnit("Archer", attackerCiv, attackerTile) // two title radius sight -> cannot see enemy warrior
        attackerUnit.currentMovement = defaultArcherMovePoints
        testGame.addUnit("Warrior", defenderCiv, attackedTile)

        /*  _____         _____         _____
        *  /     \       /     \       /     \          Legend:
        * /       \_____/       \_____/       \         A-O: Archer, attacker
        * \       /     \       /     \       /         W-1: defender warrior
        *  \_____/  W-1  \_____/       \_____/
        *  /     \       /     \       /     \
        * /       \_____/       \_____/       \
        * \       /     \       /     \       /
        *  \_____/       \__ __/       \_____/
        *  /     \       /     \       /     \
        * /       \_____/       \_____/  A-0  \
        * \       /     \       /     \       /
        *  \_____/       \_____/       \_____/
        *        \       /     \       /
        *         \_____/       \_____/
        */


        // when
        val attackableEnemies = TargetHelper.getAttackableEnemies(attackerUnit, attackerUnit.movement.getDistanceToTiles())

        // then
        assertTrue(attackableEnemies.isEmpty())
    }

    @Test
    fun `should get no attackable tiles when terrain obstacle`() {
        // given
        testGame.setTileFeatures(Vector2.Zero, Constants.hill)
        testGame.setTileFeatures(Vector2(1f, 1f), Constants.hill)
        testGame.setTileFeatures(Vector2(0f, -1f), Constants.hill)

        val attackerTile = testGame.getTile(Vector2(-1f, 0f))
        val attackedTile = testGame.getTile(Vector2(1f, 0f))
        val attackerUnit = testGame.addUnit("Archer", attackerCiv, attackerTile)
        attackerUnit.currentMovement = defaultArcherMovePoints
        testGame.addUnit("Warrior", defenderCiv, attackedTile)

        /*  _____         _____         _____
        *  /     \       /     \       /     \          Legend:
        * /       \_____/   H   \_____/       \         A-O: Archer, attacker
        * \       /     \       /     \       /         W-1: defender warrior
        *  \_____/  W-1  \_____/       \_____/          H: hill
        *  /     \       /     \       /     \
        * /       \_____/   H   \_____/       \
        * \       /     \       /     \       /
        *  \_____/   H   \__ __/  A-0  \_____/
        *  /     \       /     \       /     \
        * /       \_____/       \_____/       \
        * \       /     \       /     \       /
        *  \_____/       \_____/       \_____/
        *        \       /     \       /
        *         \_____/       \_____/
        */


        // when
        val attackableEnemies = TargetHelper.getAttackableEnemies(attackerUnit, attackerUnit.movement.getDistanceToTiles())

        // then
        assertTrue(attackableEnemies.isEmpty())
    }

    @Test
    fun `should get no attackable tiles when has cannot attack unique`() {
        // given
        val attackerUnit = testGame.addDefaultMeleeUnitWithUniques(attackerCiv, testGame.getTile(Vector2.Zero), "Cannot attack")
        attackerUnit.currentMovement = 2f
        testGame.addUnit("Warrior", defenderCiv, testGame.getTile(Vector2.X))

        // when
        val attackableEnemies = TargetHelper.getAttackableEnemies(attackerUnit, attackerUnit.movement.getDistanceToTiles())

        // then
        assertTrue(attackableEnemies.isEmpty())
    }
}
