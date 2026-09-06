package com.unciv.logic.battle

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.finishForTesting
import com.unciv.testing.finishIncompleteForTesting
import com.unciv.testing.newAttackRecorderForTesting
import com.unciv.testing.markUnitAffectedForTesting
import com.unciv.testing.recordDamageForTesting
import com.unciv.testing.recordDestructionForTesting
import com.unciv.testing.recordOutcomeForTesting
import com.unciv.testing.snapshotTargetForTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class AttackRecorderTest {
    private val testGame = TestGame().apply { makeHexagonalMap(4) }
    private val game = testGame.gameInfo
    private val attackingCiv = testGame.addCiv()
    private val defendingCiv = testGame.addCiv()
    private val source = testGame.getTile(0, 0)
    private val target = testGame.getTile(1, 0)
    private val attacker = testGame.addUnit("Archer", attackingCiv, source)
    private val defender = testGame.addUnit("Warrior", defendingCiv, target)

    private fun recorder() = newAttackRecorderForTesting(MapUnitCombatant(attacker), target)

    @Test
    fun `explicit damage and healing retain cumulative HP loss rather than net damage`() {
        defender.health = 60
        val recorder = recorder()
        recorder.snapshotTargetForTesting(MapUnitCombatant(defender))

        dealDamage(recorder, defender, 10)
        recorder.markUnitAffectedForTesting(defender)
        defender.healBy(80)
        dealDamage(recorder, defender, 7)
        val record = recorder.finishForTesting().targets.single()

        assertEquals(60, record.healthBefore)
        assertEquals(93, defender.health)
        assertEquals(93, record.healthAfter)
        assertEquals(17, record.damageReceived)
        assertEquals(AttackParticipantOutcome.Survived, record.outcome)
    }

    @Test
    fun `reported actual HP loss stays distinct from the raw overkill amount`() {
        defender.health = 5
        val recorder = recorder()

        // The caller supplies actual HP lost; the recorder does not apply or calculate damage.
        recorder.recordDamageForTesting(defender, 5)
        defender.takeDamage(500)
        val record = recorder.finishForTesting().targets.single()

        assertTrue(defender.isDestroyed)
        assertNull(defendingCiv.units.getUnitById(defender.id))
        assertEquals(5, record.healthBefore)
        assertEquals(0, record.healthAfter)
        assertEquals(5, record.damageReceived)
        assertEquals(AttackParticipantOutcome.Destroyed, record.outcome)
    }

    @Test
    fun `explicit destruction retains positive HP without inventing damage`() {
        defender.health = 60
        val recorder = recorder()

        recorder.recordDestructionForTesting(defender)
        defender.destroy()
        val record = recorder.finishForTesting().targets.single()

        assertTrue(defender.isDestroyed)
        assertEquals(defender.id, record.unitId)
        assertEquals(60, record.healthBefore)
        assertEquals(60, record.healthAfter)
        assertEquals(0, record.damageReceived)
        assertEquals(AttackParticipantOutcome.Destroyed, record.outcome)
    }

    @Test
    fun `healing alone records the affected unit before its HP changes`() {
        defender.health = 60
        val recorder = recorder()

        recorder.markUnitAffectedForTesting(defender)
        defender.healBy(50)
        val record = recorder.finishForTesting().targets.single()

        assertEquals(defender.id, record.unitId)
        assertEquals(60, record.healthBefore)
        assertEquals(100, record.healthAfter)
        assertEquals(0, record.damageReceived)
        assertEquals(AttackParticipantOutcome.Survived, record.outcome)
    }

    @Test
    fun `zero and negative damage do not invent HP loss or additional targets`() {
        attacker.health = 60
        val recorder = recorder()

        dealDamage(recorder, attacker, 0)
        dealDamage(recorder, attacker, -9)
        dealDamage(recorder, defender, 0)
        val event = recorder.finishForTesting()

        assertEquals(69, event.attacker!!.healthAfter)
        assertEquals(0, event.attacker!!.damageReceived)
        assertTrue(event.targets.isEmpty())
    }

    @Test
    fun `overlapping recorders attribute only their explicit effects`() {
        val outer = recorder()
        dealDamage(outer, attacker, 2)
        val inner = recorder()
        dealDamage(inner, attacker, 3)
        dealDamage(outer, attacker, 5)
        attacker.takeDamage(7)
        defender.takeDamage(11) // No recorder: this does not register a target with either attack.
        val innerEvent = inner.finishForTesting()
        dealDamage(outer, attacker, 11)
        val outerEvent = outer.finishForTesting()

        assertEquals(3, innerEvent.attacker!!.damageReceived)
        assertEquals(83, innerEvent.attacker!!.healthAfter)
        assertEquals(18, outerEvent.attacker!!.damageReceived)
        assertEquals(72, outerEvent.attacker!!.healthAfter)
        assertTrue(innerEvent.targets.isEmpty())
        assertTrue(outerEvent.targets.isEmpty())
        assertEquals(89, defender.health)
    }

    @Test
    fun `intended target is retained once even when it receives no damage`() {
        val recorder = recorder()
        recorder.snapshotTargetForTesting(MapUnitCombatant(defender))
        recorder.snapshotTargetForTesting(MapUnitCombatant(defender))

        val record = recorder.finishForTesting().targets.single()

        assertEquals(defender.id, record.unitId)
        assertEquals(100, record.healthBefore)
        assertEquals(100, record.healthAfter)
        assertEquals(0, record.damageReceived)
        assertEquals(AttackParticipantOutcome.Survived, record.outcome)
    }

    @Test
    fun `another game with identical save and unit IDs is rejected before mutation`() {
        val otherGame = TestGame().apply { makeHexagonalMap(4) }
        otherGame.gameInfo.gameId = game.gameId
        val otherCiv = otherGame.addCiv()
        val otherUnit = otherGame.addUnit("Archer", otherCiv, otherGame.getTile(0, 0)).apply { health = 60 }
        assertEquals(attacker.id, otherUnit.id)
        val recorder = recorder()

        assertThrows(IllegalArgumentException::class.java) {
            newAttackRecorderForTesting(MapUnitCombatant(attacker), otherGame.getTile(1, 0))
        }
        for (amount in listOf(0, -9, 9))
            assertThrows(IllegalArgumentException::class.java) { recorder.recordDamageForTesting(otherUnit, amount) }
        assertThrows(IllegalArgumentException::class.java) { recorder.markUnitAffectedForTesting(otherUnit) }
        assertThrows(IllegalArgumentException::class.java) { recorder.recordDestructionForTesting(otherUnit) }
        assertThrows(IllegalArgumentException::class.java) {
            recorder.snapshotTargetForTesting(MapUnitCombatant(otherUnit))
        }
        assertThrows(IllegalArgumentException::class.java) {
            recorder.recordOutcomeForTesting(MapUnitCombatant(otherUnit), AttackParticipantOutcome.Withdrew)
        }

        assertEquals(60, otherUnit.health)
        assertSame(otherUnit, otherCiv.units.getUnitById(otherUnit.id))
        assertSame(otherUnit, otherGame.getTile(0, 0).militaryUnit)
        dealDamage(recorder, attacker, 2)
        val event = recorder.finishForTesting()
        assertEquals(2, event.attacker!!.damageReceived)
        assertTrue(event.targets.isEmpty())
    }

    @Test
    fun `finished recorder rejects reuse before unit changes and releases live state`() {
        val recorder = recorder()
        dealDamage(recorder, attacker, 7)
        val event = recorder.finishForTesting()

        for (amount in listOf(0, -3, 3))
            assertThrows(IllegalStateException::class.java) { recorder.recordDamageForTesting(attacker, amount) }
        assertThrows(IllegalStateException::class.java) { recorder.markUnitAffectedForTesting(attacker) }
        assertThrows(IllegalStateException::class.java) { recorder.recordDestructionForTesting(attacker) }
        assertThrows(IllegalStateException::class.java) {
            recorder.snapshotTargetForTesting(MapUnitCombatant(defender))
        }
        assertThrows(IllegalStateException::class.java) {
            recorder.recordOutcomeForTesting(MapUnitCombatant(defender), AttackParticipantOutcome.Withdrew)
        }
        assertThrows(IllegalStateException::class.java) { recorder.finishForTesting() }
        assertThrows(IllegalStateException::class.java) { recorder.finishIncompleteForTesting() }
        assertEquals(93, attacker.health)
        assertFalse(attacker.isDestroyed)
        assertSame(attacker, attackingCiv.units.getUnitById(attacker.id))
        assertEquals(93, event.attacker!!.healthAfter)
        assertEquals(7, event.attacker!!.damageReceived)
        assertReleased(recorder)
    }

    @Test
    fun `incomplete finalization preserves partial effects without rolling them back`() {
        val recorder = recorder()
        recorder.snapshotTargetForTesting(MapUnitCombatant(defender))
        dealDamage(recorder, defender, 7)
        assertThrows(IllegalArgumentException::class.java) { recorder.finishForTesting(AttackResolution.Pending) }

        val event = recorder.finishIncompleteForTesting()
        assertThrows(IllegalStateException::class.java) { recorder.recordDamageForTesting(defender, 3) }
        defender.takeDamage(11)

        assertEquals(82, defender.health)
        assertEquals(AttackResolution.Pending, event.resolution)
        assertEquals(93, event.targets.single().healthAfter)
        assertEquals(7, event.targets.single().damageReceived)
        assertEquals(AttackParticipantOutcome.Survived, event.targets.single().outcome)
        assertReleased(recorder)
    }

    @Test
    fun `final health follows an unreported upgrade by stable ID without rewriting original identity`() {
        attacker.health = 63
        attacker.instanceName = "Original archers"
        val recorder = recorder()
        dealDamage(recorder, attacker, 4)

        attacker.upgrade.performUpgrade(testGame.ruleset.units.getValue("Composite Bowman"), isFree = true)
        val successor = attackingCiv.units.getUnitById(attacker.id)!!
        successor.instanceName = "New composite bowmen"
        successor.takeDamage(9) // No callback replaces the recorder's old unit reference.
        val record = recorder.finishForTesting().attacker!!

        assertNotSame(attacker, successor)
        assertEquals("Composite Bowman", successor.name)
        assertEquals(attacker.id, record.unitId)
        assertEquals("Archer", record.name)
        assertEquals("Original archers", record.instanceName)
        assertEquals(63, record.healthBefore)
        assertEquals(50, record.healthAfter)
        assertEquals(4, record.damageReceived)
        assertEquals(AttackParticipantOutcome.Survived, record.outcome)
    }

    @Test
    fun `recording APIs do not apply damage healing destruction or movement themselves`() {
        defender.health = 60
        val recorder = recorder()
        recorder.snapshotTargetForTesting(MapUnitCombatant(defender))
        recorder.recordDamageForTesting(attacker, 7)
        recorder.markUnitAffectedForTesting(defender)
        recorder.recordDestructionForTesting(defender)

        assertEquals(100, attacker.health)
        assertEquals(60, defender.health)
        assertFalse(defender.isDestroyed)
        assertEquals(source, attacker.getTile())
        assertEquals(target, defender.getTile())
        assertEquals(0, attacker.attacksThisTurn)
        assertSame(defender, defendingCiv.units.getUnitById(defender.id))
        val event = recorder.finishForTesting()
        assertEquals(7, event.attacker!!.damageReceived)
        assertEquals(100, event.attacker!!.healthAfter)
        assertEquals(AttackParticipantOutcome.Survived, event.targets.single().outcome)
    }

    @Test
    fun `capture and explicit withdrawal retain the participant's original owner and tile`() {
        val captureRecorder = recorder()
        captureRecorder.snapshotTargetForTesting(MapUnitCombatant(defender))
        val originalOwner = defender.civ.civID
        defender.capturedBy(attackingCiv)
        val captured = captureRecorder.finishForTesting().targets.single()
        assertEquals(originalOwner, captured.civId)
        assertEquals(defender.id, captured.unitId)
        assertEquals(AttackParticipantOutcome.Captured, captured.outcome)
        assertEquals(0, captured.damageReceived)

        val withdrawing = testGame.addUnit("Warrior", defendingCiv, testGame.getTile(2, 0))
        val originalPosition = withdrawing.getTile().position
        val withdrawalRecorder = newAttackRecorderForTesting(MapUnitCombatant(attacker), withdrawing.getTile())
        withdrawalRecorder.snapshotTargetForTesting(MapUnitCombatant(withdrawing))
        withdrawing.movement.moveToTile(testGame.getTile(2, 1))
        withdrawalRecorder.recordOutcomeForTesting(MapUnitCombatant(withdrawing), AttackParticipantOutcome.Withdrew)
        val withdrawnEvent = withdrawalRecorder.finishForTesting(AttackResolution.Withdrawn)
        val withdrawn = withdrawnEvent.targets.single()
        assertEquals(AttackResolution.Withdrawn, withdrawnEvent.resolution)
        assertEquals(originalPosition, withdrawn.position)
        assertEquals(AttackParticipantOutcome.Withdrew, withdrawn.outcome)
        assertEquals(0, withdrawn.damageReceived)
    }

    /** Emulates a future caller that explicitly reports an effect before applying it. */
    private fun dealDamage(recorder: AttackRecorder, unit: MapUnit, amount: Int) {
        recorder.recordDamageForTesting(unit, amount)
        unit.takeDamage(amount)
    }

    private fun assertReleased(recorder: AttackRecorder) {
        for (name in listOf("event", "gameInfo")) {
            val field = AttackRecorder::class.java.getDeclaredField(name).apply { isAccessible = true }
            assertNull("Finished recorder retains $name", field.get(recorder))
        }
        val participants = AttackRecorder::class.java.getDeclaredField("participants").apply { isAccessible = true }
        assertTrue((participants.get(recorder) as Map<*, *>).isEmpty())
    }
}
