package com.unciv.view

import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.logic.battle.AttackEvent
import com.unciv.logic.battle.AttackKind
import com.unciv.logic.battle.AttackParticipant
import com.unciv.logic.battle.AttackParticipantOutcome
import com.unciv.logic.battle.AttackResolution
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.attackEventsForTesting
import com.unciv.testing.recordAttackForTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class AttackEventsCombatEffectTest {
    private val testGame = TestGame().apply { makeHexagonalMap(7) }
    private val game = testGame.gameInfo
    private val viewer = testGame.addCiv()
    private val enemy = testGame.addCiv()
    private val observer = testGame.addCiv()
    private val source = testGame.getTile(0, 0)
    private val target = testGame.getTile(1, 0)
    private val retreat = testGame.getTile(2, 0)
    private val attacker = testGame.addUnit("Warrior", enemy, source).apply { instanceName = "Enemy private name" }
    private val defender = testGame.addUnit("Scout", viewer, target).apply { instanceName = "Our original name" }

    init {
        viewer.viewableTiles = setOf(target)
        enemy.viewableTiles = setOf(source, target)
        observer.viewableTiles = testGame.tileMap.values.toSet()
    }

    private fun viewOf(civ: Civilization) = GameView(game, civ).attackEventsView

    private fun withdrawal(): AttackEvent = game.recordAttackForTesting(MapUnitCombatant(attacker), target).apply {
        resolution = AttackResolution.Withdrawn
        withdrawalDestination = retreat.position
        withdrawalKnownBy.add(viewer.civID)
        withdrawalKnownBy.add(observer.civID)
        targets.add(AttackParticipant(MapUnitCombatant(defender)).apply {
            outcome = AttackParticipantOutcome.Withdrew
            healthAfter = 100
        })
    }

    private fun improvement(): AttackEvent = game.recordAttackForTesting(MapUnitCombatant(attacker), target).apply {
        resolution = AttackResolution.Completed
        destroyedImprovement = "Farm"
        targets.add(AttackParticipant(MapUnitCombatant(defender)).apply {
            outcome = AttackParticipantOutcome.Survived
            healthAfter = 100
        })
    }

    @Test
    fun `withdrawal reveals only the recipient's observed locations and identities`() {
        withdrawal()

        val defending = viewOf(viewer).getCombatReports().single() as ObservedWithdrawal
        val attacking = viewOf(enemy).getCombatReports().single() as ObservedWithdrawal
        assertEquals(listOf(retreat.position), defending.locations)
        assertEquals(listOf(target.position, source.position), attacking.locations)
        assertEquals("Warrior", defending.attacker.name)
        assertNull(defending.attacker.instanceName)
        assertEquals("Our original name", defending.target.instanceName)
        assertEquals("Enemy private name", attacking.attacker.instanceName)
        assertNull(attacking.target.instanceName)
        assertTrue(viewOf(observer).getCombatReports().isEmpty())
        assertEquals(1, viewOf(observer).getObservedAttacks().size)

        // Current visibility cannot turn an unwitnessed retreat into a known destination.
        viewer.viewableTiles = testGame.tileMap.values.toSet()
        enemy.viewableTiles = testGame.tileMap.values.toSet()
        attacker.instanceName = "Later enemy name"
        defender.instanceName = "Later defender name"
        assertEquals(defending, viewOf(viewer).getCombatReports().single())
        assertEquals(attacking, viewOf(enemy).getCombatReports().single())
    }

    @Test
    fun `known retreat and source remain detached from later movement and visibility`() {
        viewer.viewableTiles = setOf(source, target)
        val event = withdrawal().apply { withdrawalKnownBy.add(enemy.civID) }
        val reports = viewOf(viewer).getCombatReports()
        val defending = reports.single() as ObservedWithdrawal
        val attacking = viewOf(enemy).getCombatReports().single() as ObservedWithdrawal
        val expected = listOf(retreat.position, source.position)
        assertEquals(expected, defending.locations)
        assertEquals(expected, attacking.locations)
        assertThrows(UnsupportedOperationException::class.java) {
            (reports as MutableList<ObservedCombatReport>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (defending.locations as MutableList<HexCoord>).clear()
        }

        defender.movement.moveToTile(testGame.getTile(1, 1))
        attacker.destroy()
        viewer.viewableTiles = emptySet()
        enemy.viewableTiles = emptySet()
        assertEquals(defending, viewOf(viewer).getCombatReports().single())
        assertEquals(attacking, viewOf(enemy).getCombatReports().single())

        // Even a privileged change to the backing event cannot mutate an already returned report.
        event.withdrawalDestination = HexCoord(3, 0)
        event.knowsSource.clear()
        event.withdrawalKnownBy.clear()
        assertEquals(expected, defending.locations)
        assertEquals(expected, attacking.locations)
    }

    @Test
    fun `improvement reports follow original defender ownership and original observed source`() {
        val event = improvement()
        val original = viewOf(viewer).getCombatReports().filterIsInstance<ObservedImprovementDestruction>().single()
        assertEquals("Farm", original.improvementName)
        assertEquals(listOf(target.position), original.locations)
        assertEquals("Warrior", original.attacker.name)
        assertNull(original.attacker.instanceName)
        assertFalse(original.attacker.isOwn)
        assertThrows(UnsupportedOperationException::class.java) {
            (original.locations as MutableList<HexCoord>).clear()
        }

        // The current owner and tile no longer describe who suffered the recorded loss.
        defender.capturedBy(enemy)
        event.targets.single().outcome = AttackParticipantOutcome.Captured
        target.improvement = "Mine"
        viewer.viewableTiles = testGame.tileMap.values.toSet()
        attacker.instanceName = "Renamed attacker"
        assertEquals(original, viewOf(viewer).getCombatReports().filterIsInstance<ObservedImprovementDestruction>().single())
        assertTrue(viewOf(enemy).getCombatReports().filterIsInstance<ObservedImprovementDestruction>().isEmpty())
        assertTrue(viewOf(observer).getCombatReports().filterIsInstance<ObservedImprovementDestruction>().isEmpty())
        assertFalse(json().toJson(original).contains("Enemy private name"))
    }

    @Test
    fun `incomplete attacks and old records do not invent completed effect reports`() {
        val withdrawal = withdrawal()
        val improvement = improvement()
        withdrawal.resolution = AttackResolution.Pending
        improvement.resolution = AttackResolution.Pending
        assertTrue(viewOf(viewer).getCombatReports().isEmpty())
        assertTrue(viewOf(enemy).getCombatReports().isEmpty())

        withdrawal.resolution = AttackResolution.Withdrawn
        withdrawal.withdrawalDestination = null // Old records have no recorded retreat destination.
        improvement.resolution = AttackResolution.Completed
        improvement.destroyedImprovement = null
        assertTrue(viewOf(viewer).getCombatReports().none {
            it is ObservedWithdrawal || it is ObservedImprovementDestruction
        })

        improvement.destroyedImprovement = "Farm"
        improvement.kind = AttackKind.Nuclear
        assertTrue(viewOf(viewer).getCombatReports().filterIsInstance<ObservedImprovementDestruction>().isEmpty())
    }

    @Test
    fun `cloning and save round trip retain effect history without sharing its witness sets`() {
        viewer.viewableTiles = setOf(source, target)
        val withdrawal = withdrawal()
        val improvement = improvement()
        val expectedDefending = viewOf(viewer).getCombatReports()
        val expectedAttacking = viewOf(enemy).getCombatReports()
        val clonedEvents = game.clone().attackEventsForTesting.toList()
        val restoredEvents = json().fromJson(GameInfo::class.java, json().toJson(game)).attackEventsForTesting.toList()
        for (events in listOf(clonedEvents, restoredEvents)) {
            assertNotSame(withdrawal.withdrawalKnownBy, events.first().withdrawalKnownBy)
            assertEquals(withdrawal.withdrawalKnownBy, events.first().withdrawalKnownBy)
        }

        withdrawal.withdrawalKnownBy.add(enemy.civID)
        withdrawal.withdrawalDestination = HexCoord(3, 0)
        improvement.destroyedImprovement = "Mine"
        viewer.viewableTiles = emptySet()
        enemy.viewableTiles = testGame.tileMap.values.toSet()
        for (events in listOf(clonedEvents, restoredEvents)) {
            // Rebind the saved records to the existing initialized fixture; no live unit lookup is needed.
            game.attackEventsForTesting.clear()
            game.attackEventsForTesting.addAll(events)
            assertEquals(expectedDefending, viewOf(viewer).getCombatReports())
            assertEquals(expectedAttacking, viewOf(enemy).getCombatReports())
            assertFalse(enemy.civID in events.first().withdrawalKnownBy)
            assertEquals(retreat.position, events.first().withdrawalDestination)
            assertEquals("Farm", events.last().destroyedImprovement)
        }
    }
}
