package com.unciv.view

import com.unciv.json.json
import com.unciv.logic.battle.AttackEvent
import com.unciv.logic.battle.AttackInterception
import com.unciv.logic.battle.AttackKind
import com.unciv.logic.battle.AttackParticipant
import com.unciv.logic.battle.AttackParticipantKind
import com.unciv.logic.battle.AttackParticipantOutcome
import com.unciv.logic.battle.AttackResolution
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.recordAttackForTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class AttackEventsCombatReportTest {
    private val testGame = TestGame().apply { makeHexagonalMap(7) }
    private val game = testGame.gameInfo
    private val viewer = testGame.addCiv()
    private val enemy = testGame.addCiv()
    private val observer = testGame.addCiv()
    private val viewerCity = testGame.addCity(viewer, testGame.getTile(5, 0))
    private val enemyCity = testGame.addCity(enemy, testGame.getTile(-5, 0))
    private val observerCity = testGame.addCity(observer, testGame.getTile(0, 5))
    private val source = testGame.getTile(-3, 0)
    private val target = testGame.getTile(1, 0)
    private val attacker = testGame.addUnit("Archer", enemy, source).apply { instanceName = "SECRET_ENEMY_NAME" }
    private val defender = testGame.addUnit("Warrior", viewer, target).apply { instanceName = "Our defender" }

    init {
        viewer.viewableTiles = setOf(target)
        observer.viewableTiles = emptySet()
    }

    private fun viewOf(civ: Civilization) = GameView(game, civ).attackEventsView

    private fun attack(): AttackEvent = game.recordAttackForTesting(MapUnitCombatant(attacker), target).apply {
        resolution = AttackResolution.Completed
        targets.add(AttackParticipant(MapUnitCombatant(defender)).apply {
            damageReceived = 13
            healthAfter = 87
            outcome = AttackParticipantOutcome.Survived
        })
    }

    @Test
    fun `each owner sees its own loss without hidden attacker effects or other victims`() {
        observer.viewableTiles = setOf(source, target)
        val otherVictim = testGame.addUnit("Warrior", observer, testGame.getTile(1, 2))
        val event = attack().apply {
            attacker!!.damageReceived = 99
            attacker!!.outcome = AttackParticipantOutcome.Destroyed
            defenderRetaliationDamage = 4
            targets.add(AttackParticipant(MapUnitCombatant(otherVictim)).apply {
                outcome = AttackParticipantOutcome.Destroyed
                damageReceived = 5
            })
        }

        val report = viewOf(viewer).getCombatReports().single() as ObservedAttackResult
        assertEquals("Archer", report.attacker.name)
        assertNull(report.attacker.instanceName)
        assertFalse(report.attacker.isOwn)
        assertEquals("Our defender", report.target.instanceName)
        assertTrue(report.target.isOwn)
        assertEquals(13, report.damageReceived)
        assertEquals(4, report.retaliationDamage)
        assertFalse(report.attackerDefeatedByDefender)
        assertEquals(listOf(target.position), report.locations)
        assertEquals(otherVictim.name, (viewOf(observer).getCombatReports().single() as ObservedAttackResult).target.name)
        assertTrue(viewOf(enemy).getCombatReports().isEmpty())
        assertEquals(2, event.targets.size)
        assertNoPrivateFields(report)
    }

    @Test
    fun `an uninvolved observer receives no combat report even when both endpoints are visible`() {
        observer.viewableTiles = setOf(source, target)
        attack()

        assertTrue(viewOf(observer).getCombatReports().isEmpty())
        assertEquals(1, viewOf(observer).getObservedAttacks().size)
    }

    @Test
    fun `an unseen attacking city has no name even after later exploration`() {
        enemyCity.name = "SECRET_CITY_NAME"
        game.recordAttackForTesting(CityCombatant(enemyCity), target).apply {
            resolution = AttackResolution.Completed
            targets.add(AttackParticipant(MapUnitCombatant(defender)).apply {
                outcome = AttackParticipantOutcome.Survived
            })
        }
        viewer.viewableTiles = setOf(enemyCity.getCenterTile(), target)

        val report = viewOf(viewer).getCombatReports().single() as ObservedAttackResult
        assertEquals(AttackParticipantKind.City, report.attacker.kind)
        assertNull(report.attacker.name)
        assertNull(viewOf(viewer).getCombatantForTrigger(defender.id)!!.name)
        assertEquals(listOf(target.position), report.locations)
        assertFalse(json().toJson(report).contains("SECRET_CITY_NAME"))
    }

    @Test
    fun `report collections and locations are immutable detached snapshots`() {
        val event = attack()
        val reports = viewOf(viewer).getCombatReports()
        val report = reports.single() as ObservedAttackResult
        assertThrows(UnsupportedOperationException::class.java) {
            (reports as MutableList<ObservedCombatReport>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (report.locations as MutableList<HexCoord>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (viewOf(viewer).getObservedAttacks() as MutableList<ObservedAttack>).clear()
        }

        event.targets.single().damageReceived = 90
        event.targets.single().instanceName = "Changed defender"
        event.knowsSource.add(viewer.civID)

        assertEquals(13, report.damageReceived)
        assertEquals("Our defender", report.target.instanceName)
        assertEquals(listOf(target.position), report.locations)
        assertEquals(90, (viewOf(viewer).getCombatReports().single() as ObservedAttackResult).damageReceived)
    }

    @Test
    fun `nuclear victims learn ground zero without revealing the unseen launch civilization`() {
        val event = attack().apply {
            kind = AttackKind.Nuclear
            attacker!!.name = "Atomic Bomb"
            knowsTarget.add(viewer.civID)
            nuclearTerritoryCivIds.add(viewer.civID)
        }

        val detonation = viewOf(viewer).getCombatReports().filterIsInstance<ObservedNuclearDetonation>().single()
        assertEquals("Atomic Bomb", detonation.weaponType)
        assertNull(detonation.attackingCivilizationName)
        assertTrue(detonation.hitOurTerritory)
        assertEquals(target.position, detonation.location)
        assertEquals(listOf(ObservedAttack(event.turn, null, target.position)), viewOf(viewer).getObservedAttacks())
        val distantReport = viewOf(observer).getCombatReports().single() as ObservedNuclearDetonation
        assertNull(distantReport.attackingCivilizationName)
        assertNull(distantReport.location)
        assertFalse(distantReport.hitOurTerritory)
        assertTrue(viewOf(enemy).getCombatReports().isEmpty())
        assertNoPrivateFields(detonation)
    }

    @Test
    fun `interception reports retain phase knowledge and outcomes separate from the later blast`() {
        val fighter = testGame.addUnit("Fighter", viewer, viewerCity.getCenterTile()).apply {
            instanceName = "Our interceptor"
        }
        val event = attack().apply {
            kind = AttackKind.Nuclear
            attacker!!.name = "Atomic Bomb"
            attacker!!.outcome = AttackParticipantOutcome.Destroyed // Later self-destruction.
            knowsTarget.add(viewer.civID)
            targets.add(AttackParticipant(MapUnitCombatant(fighter)).apply {
                damageReceived = 37
                outcome = AttackParticipantOutcome.Destroyed
            })
            interceptions.add(AttackInterception(MapUnitCombatant(fighter)).apply {
                intercepted = true
                knowsTarget = hashSetOf(enemy.civID)
                damageToAttacker = 11
                damageToInterceptor = 7
                attackerOutcome = AttackParticipantOutcome.Survived
                interceptorOutcome = AttackParticipantOutcome.Survived
                interceptor!!.outcome = AttackParticipantOutcome.Destroyed
                interceptor!!.damageReceived = 37
            })
        }

        val reports = viewOf(viewer).getCombatReports()
        val interception = reports.filterIsInstance<ObservedInterception>().single()
        assertFalse(interception.isAttacking)
        assertFalse(interception.ourDestroyed)
        assertFalse(interception.enemyDestroyed)
        assertEquals(7, interception.ourDamage)
        assertEquals(11, interception.enemyDamage)
        assertNull(interception.enemyUnit.instanceName)
        assertEquals(listOf(fighter.getTile().position), interception.locations)
        val casualty = reports.filterIsInstance<ObservedAttackResult>().single { it.target.instanceName == "Our interceptor" }
        assertEquals(30, casualty.damageReceived)
        assertEquals(AttackParticipantOutcome.Destroyed, casualty.outcome)
        assertEquals(target.position, reports.filterIsInstance<ObservedNuclearDetonation>().single().location)
        val attackerReport = viewOf(enemy).getCombatReports().single() as ObservedInterception
        assertTrue(attackerReport.isAttacking)
        assertNull(attackerReport.enemyUnit.instanceName)
        assertEquals(listOf(target.position, source.position), attackerReport.locations)
        assertTrue(viewOf(observer).getCombatReports().filterIsInstance<ObservedInterception>().isEmpty())
        assertNoPrivateFields(interception)
        // Missing historical phase knowledge must not inherit the later blast's coordinates.
        event.interceptions.single().knowsTarget = null
        assertEquals(interception.locations,
            viewOf(viewer).getCombatReports().filterIsInstance<ObservedInterception>().single().locations)
    }

    @Test
    fun `trigger lookup requires our own participant and does not disclose hidden nuclear victims`() {
        val event = attack().apply { resolution = AttackResolution.Pending }

        assertEquals("Archer", viewOf(viewer).getCombatantForTrigger(defender.id)!!.name)
        assertNull(viewOf(viewer).getCombatantForTrigger(defender.id)!!.instanceName)
        assertNull(viewOf(viewer).getCombatantForTrigger(attacker.id))
        assertNull(viewOf(observer).getCombatantForTrigger(defender.id))
        assertEquals("Warrior", viewOf(enemy).getCombatantForTrigger(attacker.id)!!.name)
        event.kind = AttackKind.Nuclear
        assertNull(viewOf(enemy).getCombatantForTrigger(attacker.id))
        assertTrue(viewOf(viewer).getCombatReports().isEmpty())
    }

    @Test
    fun `capture facet exposes only the original owners permitted notice`() {
        val worker = testGame.addUnit("Worker", viewer, testGame.getTile(1, 1)).apply {
            instanceName = "Our worker"
        }
        val event = attack().apply {
            targets.clear()
            targets.add(AttackParticipant(MapUnitCombatant(worker)).apply {
                captureAttempted = true
                outcome = AttackParticipantOutcome.Captured
            })
        }

        val reports = viewOf(viewer).getCaptureReports()
        val report = reports.single()
        assertEquals(AttackParticipantOutcome.Captured, report.outcome)
        assertEquals("Our worker", report.target.instanceName)
        assertNull(report.attacker.instanceName)
        assertEquals(listOf(worker.getTile().position), report.locations)
        assertEquals(0, report.damageReceived)
        assertEquals(0, report.retaliationDamage)
        assertFalse(report.showZeroDamage)
        assertTrue(viewOf(enemy).getCaptureReports().isEmpty())
        assertTrue(viewOf(observer).getCaptureReports().isEmpty())
        assertTrue(viewOf(viewer).getCombatReports().isEmpty())
        assertThrows(UnsupportedOperationException::class.java) {
            (reports as MutableList<ObservedAttackResult>).clear()
        }
        event.targets.single().outcome = AttackParticipantOutcome.Destroyed
        assertEquals(AttackParticipantOutcome.Destroyed, viewOf(viewer).getCaptureReports().single().outcome)
        assertNoPrivateFields(report)
    }

    @Test
    fun `unopposed sweeps belong only to their owner while misses and incomplete attacks disclose nothing`() {
        val event = attack().apply {
            kind = AttackKind.AirSweep
            targets.clear()
        }
        assertTrue(viewOf(enemy).getCombatReports().single() is ObservedUnopposedAirSweep)
        assertTrue(viewOf(viewer).getCombatReports().isEmpty())
        assertTrue(viewOf(observer).getCombatReports().isEmpty())

        event.kind = AttackKind.Combat
        event.interceptions.add(AttackInterception(MapUnitCombatant(defender)))
        assertTrue(viewOf(enemy).getCombatReports().isEmpty())
        assertTrue(viewOf(viewer).getCombatReports().isEmpty())
        for (resolution in listOf(AttackResolution.Pending, AttackResolution.Withdrawn)) {
            event.resolution = resolution
            assertTrue(viewOf(enemy).getCombatReports().isEmpty())
            assertTrue(viewOf(viewer).getCombatReports().isEmpty())
        }
    }

    private fun assertNoPrivateFields(report: ObservedCombatReport) {
        val serialized = json().toJson(report)
        for (privateField in listOf("unitId", "cityId", "civId", "knownBy", "knowsSource", "knowsTarget", "SECRET_ENEMY_NAME"))
            assertFalse("Exposed $privateField in $serialized", serialized.contains(privateField))
        assertFalse(serialized.contains(enemy.civID))
        assertFalse(serialized.contains(observer.civID))
    }
}
