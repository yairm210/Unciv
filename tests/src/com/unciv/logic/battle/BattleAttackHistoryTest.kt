package com.unciv.logic.battle

import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.finishForTesting
import com.unciv.testing.newAttackRecorderForTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class BattleAttackHistoryTest {

    private val testGame = TestGame()

    @Test
    fun `ranged attack should save a completed event with participants and actual damage`() {
        // given
        testGame.makeHexagonalMap(2)
        val attackerCiv = testGame.addCiv()
        val defenderCiv = testGame.addCiv()
        val attacker = testGame.addUnit("Archer", attackerCiv, testGame.getTile(0, 1))
        val defender = testGame.addUnit("Warrior", defenderCiv, testGame.getTile(0, 0))
        val defenderHealthBefore = defender.health

        // when
        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        // then
        val events = testGame.gameInfo.attackEvents
        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(AttackResolution.Completed, event.resolution)
        assertEquals(attacker.id.toString(), event.attacker.combatantID)
        assertEquals(attackerCiv.civID, event.attacker.civID)
        assertEquals(0, event.attacker.damageReceived)
        assertEquals(1, event.targets.size)
        val target = event.targets.single()
        assertEquals(defender.id.toString(), target.combatantID)
        assertEquals(defenderCiv.civID, target.civID)
        assertEquals(defenderHealthBefore - defender.health, target.damageReceived)
    }
}
