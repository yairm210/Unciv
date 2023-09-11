package com.unciv.logic.battle

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class BattleTest {
    private lateinit var attackerCiv: Civilization
    private lateinit var defenderCiv: Civilization

    private lateinit var defaultAttackerUnit: MapUnit
    private lateinit var defaultDefenderUnit: MapUnit

    private val testGame = TestGame()

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(4)
        attackerCiv = testGame.addCiv()
        defenderCiv = testGame.addCiv()

        defaultAttackerUnit = testGame.addUnit("Warrior", attackerCiv, testGame.getTile(Vector2.X))
        defaultAttackerUnit.currentMovement = 2f
        defaultDefenderUnit = testGame.addUnit("Warrior", defenderCiv, testGame.getTile(Vector2.Zero))
        defaultDefenderUnit.currentMovement = 2f
    }

    @Test
    fun `defender should withdraw from melee attack if has the unique to do so`() {
        val unitToConstruct = testGame.createBaseUnit("Sword", "May withdraw before melee ([100]%)")
        unitToConstruct.movement = 2
        unitToConstruct.strength = 8
        val defenderUnit = testGame.addUnit(unitToConstruct.name, defenderCiv, testGame.getTile(Vector2.Y))
        defenderUnit.currentMovement = 2f

        // when
        val damageDealt = Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defenderUnit))

        // thenvi
        assertEquals(0, damageDealt.attackerDealt)
        assertEquals(0, damageDealt.defenderDealt)
        assertFalse(defenderUnit.getTile().position == Vector2.Y) // defender moves away
        assertEquals(Vector2.X, defaultAttackerUnit.getTile().position)  // attacker didn't move
        assertEquals(0f, defaultAttackerUnit.currentMovement) // warriors cannot move anymore after attacking
    }

    @Test
    fun `both defender and attacker should do damage when are melee`() {
        // when
        val damageDealt = Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(31, damageDealt.attackerDealt)
        assertEquals(31, damageDealt.defenderDealt)
    }

    @Test
    fun `only attacker should do damage when he's ranged`() {
        // given
        val attackerUnit = testGame.addUnit("Archer", attackerCiv, testGame.getTile(Vector2.Y))

        // when
        val damageDealt = Battle.attack(MapUnitCombatant(attackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(28, damageDealt.attackerDealt)
        assertEquals(0, damageDealt.defenderDealt)
    }

    @Test
    fun `should move to enemy position when melee killing`() {
        // given
        defaultDefenderUnit.health = 1

        // when
        Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(Vector2.Zero, defaultAttackerUnit.getTile().position)
        assertTrue(defaultDefenderUnit.isDestroyed)
    }

    @Test
    fun `both attacker and defender should earn XP`() {
        // when
        Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(5, defaultAttackerUnit.promotions.XP)
        assertEquals(4, defaultDefenderUnit.promotions.XP)
    }

    @Test
    fun `attacker should expend all movement points without uniques`() {
        // when
        Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(0f, defaultAttackerUnit.currentMovement)
    }

    @Test // todo fix?
    fun `attacker should still have some points with uniques`() {
        // given
        val attackerUnit = testGame.addUnit("Chu-Ko-Nu", attackerCiv, testGame.getTile(Vector2.Y))
        attackerUnit.currentMovement = 2f
        defaultDefenderUnit.health = 1 // killing removes ZoC

        // when
        Battle.attack(MapUnitCombatant(attackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(1f, attackerUnit.currentMovement)
    }

    @Test
    fun `attacker should still have movement points left with uniques`() {
        // given
        val attackerUnit = testGame.addUnit("Knight", attackerCiv, testGame.getTile(Vector2.Y))
        attackerUnit.currentMovement = 5f

        // when
        Battle.attack(MapUnitCombatant(attackerUnit), MapUnitCombatant(defaultDefenderUnit))

        // then
        assertEquals(4f, attackerUnit.currentMovement)
    }

    @Test
    fun `should capture civilian`() {
        // given
        val defenderUnit = testGame.addUnit("Worker", defenderCiv, testGame.getTile(Vector2(2f, 0f)))

        // when
        val attack = Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defenderUnit))

        // then
        assertEquals(0, attack.attackerDealt)
        assertEquals(Vector2(2f, 0f), defaultAttackerUnit.getTile().position)
        assertEquals(attackerCiv, defaultAttackerUnit.getTile().civilianUnit!!.civ)  // captured unit
    }

    @Test
    fun `should destroy civilian`() {
        // given
        val defenderUnit = testGame.addUnit("Great Merchant", defenderCiv, testGame.getTile(Vector2(2f, 0f)))

        // when
        val attack = Battle.attack(MapUnitCombatant(defaultAttackerUnit), MapUnitCombatant(defenderUnit))

        // then
        assertTrue(defenderUnit.isDestroyed)
        assertEquals(0, attack.attackerDealt)
        assertEquals(Vector2(2f, 0f), defaultAttackerUnit.getTile().position)  // todo no move??
    }

}
