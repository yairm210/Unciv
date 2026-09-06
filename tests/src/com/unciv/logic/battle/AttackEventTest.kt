package com.unciv.logic.battle

import com.unciv.json.json
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.finishForTesting
import com.unciv.testing.newAttackRecorderForTesting
import com.unciv.testing.recordDamageForTesting
import com.unciv.testing.snapshotTargetForTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class AttackEventTest {
    private val testGame = TestGame().apply { makeHexagonalMap(4) }
    private val game = testGame.gameInfo
    private val attackingCiv = testGame.addCiv()
    private val defendingCiv = testGame.addCiv()
    private val observer = testGame.addCiv()
    private val source = testGame.getTile(0, 0)
    private val target = testGame.getTile(1, 0)
    private val attacker = testGame.addUnit("Archer", attackingCiv, source)
    private val defender = testGame.addUnit("Warrior", defendingCiv, target)

    init {
        attacker.instanceName = "Original archers"
        defender.instanceName = "Original defenders"
        attackingCiv.viewableTiles = setOf(source, target)
        defendingCiv.viewableTiles = setOf(target)
        observer.viewableTiles = setOf(source, target)
    }

    @Test
    fun `identity endpoints and witnesses remain those observed before movement or renaming`() {
        game.turns = 12
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(attacker), target)
        recorder.snapshotTargetForTesting(MapUnitCombatant(defender))
        attacker.movement.moveToTile(testGame.getTile(-1, 0))
        defender.movement.moveToTile(testGame.getTile(2, 0))
        attacker.instanceName = "Renamed archers"
        defender.instanceName = "Renamed defenders"
        defendingCiv.viewableTiles = testGame.tileMap.values.toSet()
        observer.viewableTiles = emptySet()
        game.turns++

        val event = recorder.finishForTesting(AttackResolution.Completed)

        assertEquals(12, event.turn)
        assertEquals(source.position, event.sourceTile)
        assertEquals(target.position, event.targetTile)
        assertEquals(attackingCiv.civID, event.attacker!!.civID)
        assertEquals(setOf(attackingCiv.civID, observer.civID), event.civIdsKnowingAttackSource)
        assertEquals(setOf(attackingCiv.civID, defendingCiv.civID, observer.civID), event.civIdsKnowingAttackTarget)
        assertEquals("Original archers", event.attacker!!.instanceName)
        assertEquals("Original defenders", event.targets.single().instanceName)
        assertEquals(source.position, event.attacker!!.position)
        assertEquals(target.position, event.targets.single().position)
        assertFalse(defendingCiv.civID in event.attacker!!.civIdsThatKnowMe)
        assertTrue(observer.civID in event.attacker!!.civIdsThatKnowMe)
    }

    @Test
    fun `invisible origin requires a matching current detector rather than a stale tile cache`() {
        attacker.promotions.addPromotion(testGame.createUnitPromotion(UniqueType.Invisible.text).name)
        defendingCiv.viewableTiles = setOf(source, target)
        defendingCiv.viewableInvisibleUnitsTiles = setOf(source)

        val undetected = AttackEvent(MapUnitCombatant(attacker), target)
        assertFalse(defendingCiv.civID in undetected.civIdsKnowingAttackSource)
        assertFalse(defendingCiv.civID in undetected.attacker!!.civIdsThatKnowMe)
        assertTrue(defendingCiv.civID in undetected.civIdsKnowingAttackTarget)
        assertTrue(attackingCiv.civID in undetected.civIdsKnowingAttackSource)

        testGame.addDefaultMeleeUnitWithUniques(defendingCiv, testGame.getTile(-1, 0),
            "Can see invisible [Melee] units")
        defendingCiv.viewableTiles = setOf(source, target)
        val wrongDetector = AttackEvent(MapUnitCombatant(attacker), target)
        assertFalse(defendingCiv.civID in wrongDetector.civIdsKnowingAttackSource)

        testGame.addDefaultMeleeUnitWithUniques(defendingCiv, testGame.getTile(0, 1),
            "Can see invisible [Ranged] units")
        defendingCiv.viewableTiles = setOf(source, target)
        val detected = AttackEvent(MapUnitCombatant(attacker), target)
        assertTrue(defendingCiv.civID in detected.civIdsKnowingAttackSource)
        assertTrue(defendingCiv.civID in detected.attacker!!.civIdsThatKnowMe)
        // Learning the origin now cannot add knowledge to an earlier record.
        assertFalse(defendingCiv.civID in undetected.civIdsKnowingAttackSource)
    }

    @Test
    fun `cloning deeply detaches participant data and all witness collections`() {
        val event = completedEvent()
        val clone = event.clone()
        assertNotSame(event, clone)
        assertNotSame(event.attacker, clone.attacker)
        assertNotSame(event.targets, clone.targets)
        assertNotSame(event.targets.single(), clone.targets.single())
        assertNotSame(event.attacker!!.civIdsThatKnowMe, clone.attacker!!.civIdsThatKnowMe)
        assertNotSame(event.targets.single().civIdsThatKnowMe, clone.targets.single().civIdsThatKnowMe)
        assertParticipantEquals(event.attacker!!, clone.attacker!!)
        assertParticipantEquals(event.targets.single(), clone.targets.single())

        clone.civIdsKnowingAttackSource.add(defendingCiv.civID)
        clone.civIdsKnowingAttackTarget.clear()
        clone.attacker!!.civIdsThatKnowMe.clear()
        clone.targets.single().civIdsThatKnowMe.clear()
        clone.targets.single().instanceName = "Changed clone"
        clone.targets.single().damageReceived = 99
        clone.targets.clear()

        assertFalse(defendingCiv.civID in event.civIdsKnowingAttackSource)
        assertTrue(defendingCiv.civID in event.civIdsKnowingAttackTarget)
        assertTrue(attackingCiv.civID in event.attacker!!.civIdsThatKnowMe)
        assertTrue(defendingCiv.civID in event.targets.single().civIdsThatKnowMe)
        assertEquals("Original defenders", event.targets.single().instanceName)
        assertEquals(5, event.targets.single().damageReceived)
    }

    @Test
    fun `event JSON round trip preserves outcomes identity health and observation permissions`() {
        game.turns = 12
        val event = completedEvent()
        val restored = json().fromJson(AttackEvent::class.java, json().toJson(event))

        assertEquals(event.turn, restored.turn)
        assertEquals(event.sourceTile, restored.sourceTile)
        assertEquals(event.targetTile, restored.targetTile)
        assertEquals(event.attacker!!.civID, restored.attacker!!.civID)
        assertEquals(event.resolution, restored.resolution)
        assertEquals(event.civIdsKnowingAttackSource, restored.civIdsKnowingAttackSource)
        assertEquals(event.civIdsKnowingAttackTarget, restored.civIdsKnowingAttackTarget)
        assertParticipantEquals(event.attacker!!, restored.attacker!!)
        assertParticipantEquals(event.targets.single(), restored.targets.single())
        restored.attacker!!.civIdsThatKnowMe.clear()
        restored.targets.clear()
        assertTrue(attackingCiv.civID in event.attacker!!.civIdsThatKnowMe)
        assertEquals(1, event.targets.size)
    }

    private fun completedEvent(): AttackEvent {
        attacker.health = 75
        defender.health = 40
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(attacker), target)
        recorder.snapshotTargetForTesting(MapUnitCombatant(defender))
        recorder.recordDamageForTesting(attacker, 9)
        attacker.takeDamage(9)
        recorder.recordDamageForTesting(defender, 5)
        defender.takeDamage(5)
        return recorder.finishForTesting(AttackResolution.Completed)
    }

    private fun assertParticipantEquals(expected: AttackParticipant, actual: AttackParticipant) {
        assertEquals(expected.unitID, actual.unitID)
        assertEquals(expected.civID, actual.civID)
        assertEquals(expected.name, actual.name)
        assertEquals(expected.instanceName, actual.instanceName)
        assertEquals(expected.position, actual.position)
        assertEquals(expected.healthBefore, actual.healthBefore)
        assertEquals(expected.healthAfter, actual.healthAfter)
        assertEquals(expected.damageReceived, actual.damageReceived)
        assertEquals(expected.outcome, actual.outcome)
        assertEquals(expected.civIdsThatKnowMe, actual.civIdsThatKnowMe)
    }
}
