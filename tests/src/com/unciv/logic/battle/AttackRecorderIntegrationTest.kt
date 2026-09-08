package com.unciv.logic.battle

import com.unciv.Constants
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.attackEventsForTesting
import com.unciv.testing.beginInterceptionForTesting
import com.unciv.testing.finishForTesting
import com.unciv.testing.newAttackRecorderForTesting
import com.unciv.testing.recordInterceptionForTesting
import com.unciv.testing.snapshotTargetForTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class AttackRecorderIntegrationTest {
    private val testGame = TestGame().apply { makeHexagonalMap(7) }
    private val game = testGame.gameInfo
    private val attackingCiv = testGame.addCiv()
    private val defendingCiv = testGame.addCiv()
    private val attackerBase = testGame.addCity(attackingCiv, testGame.getTile(-5, 0)).getCenterTile()
    private val target = testGame.getTile(1, 0)

    init {
        testGame.addCity(defendingCiv, testGame.getTile(5, 0))
        attackingCiv.diplomacyFunctions.makeCivilizationsMeet(defendingCiv)
        attackingCiv.getDiplomacyManager(defendingCiv)!!.declareWar()
        game.currentPlayerCiv = testGame.addCiv(isPlayer = true)
        game.currentPlayer = game.currentPlayerCiv.civID
    }

    @Test
    fun `city teardown destroys its interceptor in both roles without inventing HP damage`() {
        attackingCiv.playerType = PlayerType.Human
        defendingCiv.playerType = PlayerType.Human
        // An interceptable strength-two bomb destroys this noncapital city's low population.
        // Its aircraft disappear during city teardown, before the blast's unit-damage loop.
        val bombType = testGame.createBaseUnit("Atomic Bomber",
            "Nuclear weapon of Strength [2]", "Blast radius [1]",
            "Self-destructs when attacking", "Damage taken from interception reduced by [100]%"
        ).apply {
            strength = 150
            rangedStrength = 150
            movement = 1
            range = 10
        }
        val bomb = testGame.addUnit(bombType.name, attackingCiv, attackerBase)
        val city = testGame.addCity(defendingCiv, target, initialPopulation = 1)
        val interceptor = testGame.addUnit("Fighter", defendingCiv, target)
        val cityHealthBefore = city.health
        assertFalse(city.isOriginalCapital)
        assertTrue(city.canBeDestroyed())

        Nuke.NUKE(MapUnitCombatant(bomb), target)

        assertFalse(city in defendingCiv.cities)
        assertTrue(interceptor.isDestroyed)
        assertEquals(100, interceptor.health)
        val event = game.attackEventsForTesting.single()
        val interception = event.interceptions.single()
        val interceptorRecord = interception.interceptor!!
        val targetRecord = event.targets.single { it.unitId == interceptor.id }
        assertNotSame(targetRecord, interceptorRecord)
        assertEquals(AttackResolution.Completed, event.resolution)
        assertTrue(interception.intercepted)
        assertEquals(1, interceptor.attacksThisTurn)
        assertEquals(0, interception.damageToAttacker)
        assertEquals(0, interception.damageToInterceptor)
        assertEquals(AttackParticipantOutcome.Survived, interception.attackerOutcome)
        assertEquals(AttackParticipantOutcome.Survived, interception.interceptorOutcome)
        for (record in listOf(targetRecord, interceptorRecord)) {
            assertEquals(AttackParticipantOutcome.Destroyed, record.outcome)
            assertEquals(100, record.healthBefore)
            assertEquals(100, record.healthAfter)
            assertEquals(0, record.damageReceived)
        }
        val cityRecord = event.targets.single { it.cityId == city.id }
        assertEquals(AttackParticipantOutcome.Destroyed, cityRecord.outcome)
        assertEquals(cityHealthBefore, cityRecord.healthAfter)
        assertEquals(0, cityRecord.damageReceived)
        // The fighter survived interception, then died with its city. Finalization must not
        // rewrite the earlier engagement report as a victory against the interceptor.
        val interceptionNotice = defendingCiv.notifications.single { "intercepted" in it.text }
        assertTrue(interceptionNotice.text.contains("intercepted and attacked"))
        assertFalse(interceptionNotice.text.contains("destroyed"))
        assertTrue(defendingCiv.notifications.any { "has destroyed" in it.text && "[Fighter]" in it.text })
        val attackerNotice = attackingCiv.notifications.single { "intercepting" in it.text }
        assertTrue(attackerNotice.text.contains("was attacked by an intercepting"))
        assertFalse(attackerNotice.text.contains("destroyed"))
    }

    @Test
    fun `sinking a defending carrier retains its cargo identities and knowledge without fictitious damage`() {
        val source = testGame.setTileTerrain(testGame.getTile(0, 0).position, Constants.ocean)
        testGame.setTileTerrain(target.position, Constants.ocean)
        val attacker = testGame.addUnit("Battleship", attackingCiv, source)
        val carrier = testGame.addUnit("Carrier", defendingCiv, target).apply { health = 1 }
        val hiddenCargo = testGame.addUnit("Fighter", defendingCiv, target).apply {
            instanceName = "Hidden air wing"
            promotions.addPromotion(testGame.createUnitPromotion(UniqueType.Invisible.text).name)
        }
        val visibleCargo = testGame.addUnit("Bomber", defendingCiv, target).apply { health = 73 }
        assertTrue(hiddenCargo.isTransported)
        assertTrue(visibleCargo.isTransported)
        attackingCiv.viewableTiles = setOf(source, target)
        attackingCiv.viewableInvisibleUnitsTiles = emptySet()
        defendingCiv.viewableTiles = setOf(source, target)

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(carrier))

        assertTrue(carrier.isDestroyed)
        assertTrue(hiddenCargo.isDestroyed)
        assertTrue(visibleCargo.isDestroyed)
        assertTrue(target.airUnits.isEmpty())
        val event = game.attackEventsForTesting.single()
        assertEquals(setOf(carrier.id, hiddenCargo.id, visibleCargo.id), event.targets.map { it.unitId }.toSet())
        val carrierRecord = event.targets.single { it.unitId == carrier.id }
        assertEquals(AttackParticipantOutcome.Destroyed, carrierRecord.outcome)
        assertEquals(1, carrierRecord.healthBefore)
        assertEquals(0, carrierRecord.healthAfter)
        assertEquals(1, carrierRecord.damageReceived)
        assertTrue(attackingCiv.civID in carrierRecord.knownBy)
        for (cargo in listOf(hiddenCargo, visibleCargo)) {
            val record = event.targets.single { it.unitId == cargo.id }
            assertEquals(cargo.name, record.name)
            assertEquals(defendingCiv.civID, record.civId)
            assertEquals(target.position, record.position)
            assertEquals(AttackParticipantOutcome.Destroyed, record.outcome)
            assertEquals(cargo.health, record.healthBefore)
            assertEquals(cargo.health, record.healthAfter)
            assertEquals(0, record.damageReceived)
            assertTrue(defendingCiv.civID in record.knownBy)
        }
        val hiddenRecord = event.targets.single { it.unitId == hiddenCargo.id }
        assertEquals("Hidden air wing", hiddenRecord.instanceName)
        assertFalse(attackingCiv.civID in hiddenRecord.knownBy)
        assertTrue(attackingCiv.civID in event.targets.single { it.unitId == visibleCargo.id }.knownBy)
    }

    @Test
    fun `one unit can retain different target and interception knowledge with shared final accounting`() {
        val bomber = testGame.addUnit("Bomber", attackingCiv, attackerBase)
        testGame.addCity(defendingCiv, target)
        val interceptor = testGame.addUnit("Fighter", defendingCiv, target).apply { health = 60 }
        val combatant = MapUnitCombatant(interceptor)
        val recorder = newAttackRecorderForTesting(MapUnitCombatant(bomber), target, AttackKind.Nuclear)

        // Target knowledge is captured before the later encounter identifies the interceptor.
        attackingCiv.viewableTiles = setOf(attackerBase)
        recorder.snapshotTargetForTesting(combatant)
        attackingCiv.viewableTiles = setOf(attackerBase, target)
        val interception = recorder.beginInterceptionForTesting(combatant)
        interceptor.takeDamage(13, recorder)
        interceptor.healBy(7, recorder)
        recorder.recordInterceptionForTesting(interception, intercepted = true, damageToInterceptor = 13)
        interceptor.takeDamage(24, recorder) // A later part of the mission, outside interception.

        val event = recorder.finishForTesting()
        val targetRecord = event.targets.single()
        val interceptorRecord = event.interceptions.single().interceptor!!
        assertNotSame(targetRecord, interceptorRecord)
        assertNotSame(targetRecord.knownBy, interceptorRecord.knownBy)
        assertFalse(attackingCiv.civID in targetRecord.knownBy)
        assertTrue(attackingCiv.civID in interceptorRecord.knownBy)
        for (record in listOf(targetRecord, interceptorRecord)) {
            assertEquals(interceptor.id, record.unitId)
            assertEquals(60, record.healthBefore)
            assertEquals(30, record.healthAfter)
            assertEquals(37, record.damageReceived)
            assertEquals(AttackParticipantOutcome.Survived, record.outcome)
        }
        assertEquals(13, event.interceptions.single().damageToInterceptor)
    }

    @Test
    fun `battle preserves applied damage as incomplete history when a later combat trigger throws`() {
        val attacker = testGame.addDefaultRangedUnitWithUniques(attackingCiv, testGame.getTile(0, 0),
            "[Target Unit] takes [7] damage <upon entering combat>",
            "[This Unit] takes [not-a-number] damage <upon entering combat>")
        val defender = testGame.addUnit("Warrior", defendingCiv, target)

        val failure = assertThrows(NumberFormatException::class.java) {
            Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))
        }

        // The actual trigger exception is propagated, not replaced by recording cleanup.
        assertTrue(failure.message!!.contains("not-a-number"))
        assertNull(failure.cause)
        assertTrue(failure.stackTrace.any { it.className.endsWith("UniqueTriggerActivation") })
        assertTrue(failure.suppressed.isEmpty())
        assertEquals(93, defender.health)
        val event = game.attackEventsForTesting.single()
        assertEquals(AttackResolution.Pending, event.resolution)
        assertEquals(100, event.attacker!!.healthAfter)
        val defenderRecord = event.targets.single()
        assertEquals(defender.id, defenderRecord.unitId)
        assertEquals(7, defenderRecord.damageReceived)
        assertEquals(93, defenderRecord.healthAfter)
        assertEquals(AttackParticipantOutcome.Survived, defenderRecord.outcome)

        defender.takeDamage(5)
        assertEquals(88, defender.health)
        assertEquals(7, defenderRecord.damageReceived)
        assertEquals(93, defenderRecord.healthAfter)
    }
}
