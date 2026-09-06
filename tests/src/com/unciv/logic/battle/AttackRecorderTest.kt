package com.unciv.logic.battle

import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.attackEventsForTesting
import com.unciv.testing.beginInterceptionForTesting
import com.unciv.testing.damageReceivedForTesting
import com.unciv.testing.finishForTesting
import com.unciv.testing.finishIncompleteForTesting
import com.unciv.testing.interceptorSnapshotForTesting
import com.unciv.testing.markUnitAffectedForTesting
import com.unciv.testing.newAttackRecorderForTesting
import com.unciv.testing.recordInterceptionForTesting
import com.unciv.testing.retainAllTargetsForTesting
import com.unciv.testing.snapshotForTesting
import com.unciv.testing.snapshotTargetForTesting
import com.unciv.testing.storeAttackForTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class AttackRecorderTest {
    private val testGame = TestGame().apply { makeHexagonalMap(4) }
    private val game = testGame.gameInfo
    private val civ = testGame.addCiv()
    private val unit = testGame.addUnit("Warrior", civ, testGame.getTile(0, 0))
    private val target = testGame.getTile(1, 0)

    @Test
    fun `only explicitly attributed damage belongs to the attack`() {
        val bystander = testGame.addUnit("Warrior", civ, testGame.getTile(0, 1))
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)

        unit.takeDamage(7, recorder)
        unit.takeDamage(3)
        bystander.takeDamage(11)
        val event = recorder.finishForTesting()

        assertEquals(7, event.attacker!!.damageReceived)
        assertEquals(90, event.attacker!!.healthAfter)
        assertEquals(89, bystander.health)
        assertTrue(event.targets.isEmpty())
    }

    @Test
    fun `overlapping recorders keep explicit attribution even while another attack is open`() {
        val outerRecorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)
        unit.takeDamage(2, outerRecorder)
        val innerRecorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)
        unit.takeDamage(3, innerRecorder)
        // An active inner attack cannot implicitly claim this outer attack's effect.
        unit.takeDamage(5, outerRecorder)
        unit.takeDamage(7)
        val inner = innerRecorder.finishForTesting()
        unit.takeDamage(11, outerRecorder)
        val outer = outerRecorder.finishForTesting()

        assertEquals(18, outer.attacker!!.damageReceived)
        assertEquals(72, outer.attacker!!.healthAfter)
        assertEquals(3, inner.attacker!!.damageReceived)
        assertEquals(83, inner.attacker!!.healthAfter)
    }

    @Test
    fun `same unit ID in another game cannot mutate through this recorder`() {
        val otherGame = TestGame().apply { makeHexagonalMap(4) }
        // Clones and loaded copies can share both the save's game ID and unit IDs.
        otherGame.gameInfo.gameId = game.gameId
        val otherCiv = otherGame.addCiv()
        val otherUnit = otherGame.addUnit("Warrior", otherCiv, otherGame.getTile(0, 0))
        otherUnit.health = 60
        assertEquals(unit.id, otherUnit.id)
        assertNotSame(game, otherUnit.civ.gameInfo)
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)

        assertFails<IllegalArgumentException> { otherUnit.takeDamage(9, recorder) }
        assertFails<IllegalArgumentException> { otherUnit.healBy(9, recorder) }
        assertFails<IllegalArgumentException> { otherUnit.destroy(attackRecorder = recorder) }
        assertEquals(60, otherUnit.health)
        assertFalse(otherUnit.isDestroyed)
        assertSame(otherUnit, otherCiv.units.getUnitById(otherUnit.id))
        assertSame(otherUnit, otherGame.getTile(0, 0).militaryUnit)
        unit.takeDamage(2, recorder)
        val event = recorder.finishForTesting()

        assertEquals(2, event.attacker!!.damageReceived)
        assertEquals(98, event.attacker!!.healthAfter)
        assertTrue(event.targets.isEmpty())
    }

    @Test
    fun `healing alone retains the affected participant without inventing damage`() {
        val healedUnit = testGame.addUnit("Warrior", civ, target)
        healedUnit.health = 60
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)

        healedUnit.healBy(50, recorder)
        val event = recorder.finishForTesting()

        val participant = event.targets.single()
        assertEquals(healedUnit.id, participant.unitId)
        assertEquals(60, participant.healthBefore)
        assertEquals(100, participant.healthAfter)
        assertEquals(0, participant.damageReceived)
        assertEquals(AttackParticipantOutcome.Survived, participant.outcome)
    }

    @Test
    fun `city mutations also reject a recorder from another game before health changes`() {
        val otherGame = TestGame().apply { makeHexagonalMap(4) }
        val otherCiv = otherGame.addCiv()
        val otherCity = otherGame.addCity(otherCiv, otherGame.getTile(0, 0))
        val originalHealth = otherCity.health
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)

        assertFails<IllegalArgumentException> { CityCombatant(otherCity).takeDamage(9, recorder) }
        val event = recorder.finishForTesting()

        assertEquals(originalHealth, otherCity.health)
        assertEquals(0, event.attacker!!.damageReceived)
        assertTrue(event.targets.isEmpty())
    }

    @Test
    fun `finished recorder rejects damage healing and destruction before mutating the unit`() {
        val city = testGame.addCity(civ, testGame.getTile(2, 0))
        val cityHealth = city.health
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)
        unit.takeDamage(7, recorder)
        val event = recorder.finishForTesting()

        assertFails<IllegalStateException> { unit.takeDamage(3, recorder) }
        assertFails<IllegalStateException> { unit.healBy(3, recorder) }
        assertFails<IllegalStateException> { unit.destroy(attackRecorder = recorder) }
        assertFails<IllegalStateException> { CityCombatant(city).takeDamage(3, recorder) }
        // Finishing twice rejects reuse and never reopens the recorder.
        assertFails<IllegalStateException> { recorder.finishForTesting() }

        assertEquals(93, unit.health)
        assertFalse(unit.isDestroyed)
        assertEquals(cityHealth, city.health)
        assertSame(unit, civ.units.getUnitById(unit.id))
        assertEquals(7, event.attacker!!.damageReceived)
        assertEquals(93, event.attacker!!.healthAfter)
    }

    @Test
    fun `explicit incomplete finalization preserves the event and prevents later recording`() {
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)
        val failure = IllegalStateException("Interrupted combat")
        lateinit var event: AttackEvent

        val thrown = assertFails<IllegalStateException> {
            try {
                unit.takeDamage(7, recorder)
                throw failure
            } catch (exception: Throwable) {
                event = recorder.finishIncompleteForTesting()
                throw exception
            }
        }
        assertSame(failure, thrown)
        assertFails<IllegalStateException> { unit.takeDamage(3, recorder) }
        unit.takeDamage(11)

        assertEquals(82, unit.health)
        assertEquals(7, event.attacker!!.damageReceived)
        assertEquals(93, event.attacker!!.healthAfter)
        assertEquals(AttackParticipantOutcome.Survived, event.attacker!!.outcome)
        assertEquals(AttackResolution.Pending, event.resolution)
    }

    @Test
    fun `snapshotting an optional target does not count as affecting it`() {
        val bystander = testGame.addUnit("Warrior", civ, target)
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)

        recorder.snapshotTargetForTesting(MapUnitCombatant(bystander), retainIfUnaffected = false)
        // Observation is available during execution, before final inclusion is decided.
        assertEquals(bystander.id, recorder.snapshotForTesting().targets.single().unitId)
        val event = recorder.finishForTesting()

        assertTrue(event.targets.isEmpty())
        assertEquals(100, bystander.health)
    }

    @Test
    fun `an intended target remains in history even if combat does no damage`() {
        val defender = testGame.addUnit("Warrior", civ, target)
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)

        recorder.snapshotTargetForTesting(MapUnitCombatant(defender))
        val record = recorder.finishForTesting().targets.single()

        assertEquals(defender.id, record.unitId)
        assertEquals(100, record.healthBefore)
        assertEquals(100, record.healthAfter)
        assertEquals(0, record.damageReceived)
        assertEquals(AttackParticipantOutcome.Survived, record.outcome)
    }

    @Test
    fun `zero damage neither retains a potential bystander nor invents a new target`() {
        val bystander = testGame.addUnit("Warrior", civ, target)
        val unrecordedUnit = testGame.addUnit("Warrior", civ, testGame.getTile(0, 1))
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)
        recorder.snapshotTargetForTesting(MapUnitCombatant(bystander), retainIfUnaffected = false)

        bystander.takeDamage(0, recorder)
        unrecordedUnit.takeDamage(0, recorder)
        val event = recorder.finishForTesting()

        assertTrue(event.targets.isEmpty())
        assertEquals(0, event.attacker!!.damageReceived)
        assertEquals(100, bystander.health)
        assertEquals(100, unrecordedUnit.health)
    }

    @Test
    fun `foreign game damage is rejected even when zero or negative`() {
        val otherGame = TestGame().apply { makeHexagonalMap(4) }
        val otherCiv = otherGame.addCiv()
        val otherUnit = otherGame.addUnit("Warrior", otherCiv, otherGame.getTile(0, 0))
        val otherCity = otherGame.addCity(otherCiv, otherGame.getTile(2, 0))
        otherUnit.health = 60
        val cityHealth = otherCity.health
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)

        for (amount in listOf(0, -9)) {
            assertFails<IllegalArgumentException> { otherUnit.takeDamage(amount, recorder) }
            assertFails<IllegalArgumentException> { CityCombatant(otherCity).takeDamage(amount, recorder) }
        }

        assertEquals(60, otherUnit.health)
        assertEquals(cityHealth, otherCity.health)
        assertTrue(recorder.finishForTesting().targets.isEmpty())
    }

    @Test
    fun `finished recorder rejects zero and negative damage before changing health`() {
        val city = testGame.addCity(civ, testGame.getTile(2, 0))
        val cityHealth = city.health
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)
        unit.health = 60
        val event = recorder.finishForTesting()

        for (amount in listOf(0, -9)) {
            assertFails<IllegalStateException> { unit.takeDamage(amount, recorder) }
            assertFails<IllegalStateException> { CityCombatant(city).takeDamage(amount, recorder) }
        }

        assertEquals(60, unit.health)
        assertEquals(cityHealth, city.health)
        assertEquals(60, event.attacker!!.healthAfter)
    }

    @Test
    fun `marking a unit affected retains its original snapshot without inventing damage`() {
        val affectedUnit = testGame.addUnit("Warrior", civ, target)
        affectedUnit.instanceName = "Original name"
        affectedUnit.health = 60
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)
        recorder.snapshotTargetForTesting(MapUnitCombatant(affectedUnit), retainIfUnaffected = false)

        affectedUnit.instanceName = "Changed name"
        recorder.markUnitAffectedForTesting(affectedUnit)
        affectedUnit.healBy(10)
        val record = recorder.finishForTesting().targets.single()

        assertEquals("Original name", record.instanceName)
        assertEquals(60, record.healthBefore)
        assertEquals(70, record.healthAfter)
        assertEquals(0, record.damageReceived)
        assertEquals(AttackParticipantOutcome.Survived, record.outcome)
    }

    @Test
    fun `notification snapshots cannot mutate participants interceptions or visibility in the recorder`() {
        val defender = testGame.addUnit("Warrior", civ, target)
        val defenderCombatant = MapUnitCombatant(defender)
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)
        recorder.snapshotTargetForTesting(defenderCombatant)
        val interception = recorder.beginInterceptionForTesting(defenderCombatant)
        unit.takeDamage(3, recorder)
        defender.takeDamage(7, recorder)
        recorder.recordInterceptionForTesting(interception, intercepted = true,
            damageToAttacker = 3, damageToInterceptor = 7)

        val snapshot = recorder.snapshotForTesting()
        snapshot.knowsSource.clear()
        snapshot.knowsTarget.clear()
        snapshot.attacker!!.damageReceived = 500
        snapshot.attacker!!.knownBy.clear()
        snapshot.targets.single().name = "Changed target"
        snapshot.targets.single().knownBy.clear()
        snapshot.interceptions.single().damageToAttacker = 500
        snapshot.interceptions.single().interceptor!!.name = "Changed interceptor"
        snapshot.interceptions.single().interceptor!!.knownBy.clear()
        snapshot.targets.clear()
        snapshot.interceptions.clear()
        // The more narrowly scoped interceptor accessor is detached as well.
        recorder.interceptorSnapshotForTesting(interception).knownBy.clear()

        val event = recorder.finishForTesting()
        assertTrue(civ.civID in event.knowsSource)
        assertTrue(civ.civID in event.knowsTarget)
        assertTrue(civ.civID in event.attacker!!.knownBy)
        assertTrue(civ.civID in event.targets.single().knownBy)
        assertTrue(civ.civID in event.interceptions.single().interceptor!!.knownBy)
        assertEquals(3, event.attacker!!.damageReceived)
        assertEquals("Warrior", event.targets.single().name)
        assertEquals(7, event.targets.single().damageReceived)
        assertEquals("Warrior", event.interceptions.single().interceptor!!.name)
        assertEquals(3, event.interceptions.single().damageToAttacker)
        assertEquals(7, event.interceptions.single().damageToInterceptor)
    }

    @Test
    fun `finished recorder also rejects observation and registration operations`() {
        val defender = testGame.addUnit("Warrior", civ, target)
        val combatant = MapUnitCombatant(defender)
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)
        val interception = recorder.beginInterceptionForTesting(combatant)
        val event = recorder.finishForTesting()

        assertFails<IllegalStateException> { recorder.snapshotForTesting() }
        assertFails<IllegalStateException> { recorder.interceptorSnapshotForTesting(interception) }
        assertFails<IllegalStateException> { recorder.damageReceivedForTesting(combatant) }
        assertFails<IllegalStateException> { recorder.snapshotTargetForTesting(combatant) }
        assertFails<IllegalStateException> { recorder.markUnitAffectedForTesting(defender) }
        assertFails<IllegalStateException> { recorder.beginInterceptionForTesting(combatant) }
        assertFails<IllegalStateException> { recorder.recordInterceptionForTesting(interception, true) }
        assertFails<IllegalStateException> { recorder.retainAllTargetsForTesting() }
        assertFails<IllegalStateException> { recorder.finishIncompleteForTesting() }
        defender.takeDamage(5)

        assertEquals(100, event.interceptions.single().interceptor!!.healthAfter)
        assertEquals(0, event.interceptions.single().interceptor!!.damageReceived)
    }

    @Test
    fun `construction rejects a target from another game instance`() {
        val otherGame = TestGame().apply { makeHexagonalMap(4) }
        otherGame.gameInfo.gameId = game.gameId

        assertFails<IllegalArgumentException> {
            newAttackRecorderForTesting(MapUnitCombatant(unit), otherGame.getTile(1, 0))
        }

        assertEquals(100, unit.health)
        assertSame(unit, civ.units.getUnitById(unit.id))
    }

    @Test
    fun `recording snapshots and finalization publish nothing until explicit storage`() {
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(unit), target)
        assertTrue(game.attackEventsForTesting.isEmpty())

        unit.takeDamage(7, recorder)
        assertTrue(game.attackEventsForTesting.isEmpty())
        assertEquals(7, recorder.snapshotForTesting().attacker!!.damageReceived)
        assertTrue(game.attackEventsForTesting.isEmpty())

        val event = recorder.finishForTesting()
        assertTrue(game.attackEventsForTesting.isEmpty())

        game.storeAttackForTesting(event)
        assertSame(event, game.attackEventsForTesting.single())
        assertEquals(7, game.attackEventsForTesting.single().attacker!!.damageReceived)
        assertEquals(AttackResolution.Completed, game.attackEventsForTesting.single().resolution)
    }

    private inline fun <reified T : Throwable> assertFails(action: () -> Unit): T {
        try {
            action()
        } catch (exception: Throwable) {
            if (exception is T) return exception
            throw AssertionError("Expected ${T::class.java.simpleName}, got $exception", exception)
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
