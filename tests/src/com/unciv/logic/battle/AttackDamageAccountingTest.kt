package com.unciv.logic.battle

import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.attackEventsForTesting
import com.unciv.testing.finishForTesting
import com.unciv.testing.newAttackRecorderForTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class AttackDamageAccountingTest {
    private val testGame = TestGame().apply { makeHexagonalMap(7) }
    private val game = testGame.gameInfo
    private val attackingCiv = testGame.addCiv()
    private val defendingCiv = testGame.addCiv()
    private val source = testGame.getTile(0, 0)
    private val target = testGame.getTile(1, 0)

    init {
        testGame.addCity(attackingCiv, testGame.getTile(-5, 0))
        testGame.addCity(defendingCiv, testGame.getTile(5, 0))
        attackingCiv.diplomacyFunctions.makeCivilizationsMeet(defendingCiv)
        game.currentPlayerCiv = testGame.addCiv(isPlayer = true)
        game.currentPlayer = game.currentPlayerCiv.civID
    }

    @Test
    fun `upgrade upon killing retains original identity and records the living successor`() {
        val attacker = testGame.addUnit("Archer", attackingCiv, source)
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        attacker.instanceName = "Veteran archers"
        attacker.health = 63
        defender.health = 1
        attacker.promotions.addPromotion(testGame.createUnitPromotion(
            "[This Unit] upgrades for free including special upgrades <upon defeating a [Military] unit>"
        ).name)

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        val successor = attackingCiv.units.getCivUnits().single { it.id == attacker.id }
        val record = game.attackEventsForTesting.single().attacker!!
        assertNotSame(attacker, successor)
        assertEquals("Composite Bowman", successor.name)
        assertEquals("Archer", record.name)
        assertEquals("Veteran archers", record.instanceName)
        assertEquals(attacker.id, record.unitId)
        assertEquals(attackingCiv.civID, record.civId)
        assertEquals(63, record.healthBefore)
        assertEquals(successor.health, record.healthAfter)
        assertEquals(AttackParticipantOutcome.Survived, record.outcome)
    }

    @Test
    fun `final health comes from upgraded successor without changing the original snapshot`() {
        val unit = testGame.addUnit("Archer", attackingCiv, source)
        unit.health = 63
        val combatant = MapUnitCombatant(unit)
        val recorder = newAttackRecorderForTesting(combatant, target)
        unit.upgrade.performUpgrade(testGame.ruleset.units.getValue("Composite Bowman"), isFree = true)
        val successor = attackingCiv.units.getCivUnits().single { it.id == unit.id }
        successor.takeDamage(9)

        val record = recorder.finishForTesting().attacker!!

        assertEquals(63, record.healthBefore)
        assertEquals(54, record.healthAfter)
        assertEquals("Archer", record.name)
        assertEquals(AttackParticipantOutcome.Survived, record.outcome)
    }

    @Test
    fun `failed upgrade restores the original stable identity and surviving combat outcome`() {
        val attacker = testGame.addDefaultRangedUnitWithUniques(attackingCiv, source,
            "[This Unit] upgrades for free including special upgrades <upon defeating a [Military] unit>")
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        // There is no water on this map, so the attempted upgrade must restore the archer.
        // This is a test-only base unit; modifying the shared vanilla Archer would leak to other tests.
        attacker.baseUnit.upgradesTo = "Trireme"
        attacker.health = 63
        defender.health = 1

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        val restored = attackingCiv.units.getCivUnits().single()
        val record = game.attackEventsForTesting.single().attacker!!
        assertNotSame(attacker, restored)
        assertEquals(attacker.name, restored.name)
        assertEquals(attacker.id, restored.id)
        assertEquals(63, record.healthAfter)
        assertEquals(AttackParticipantOutcome.Survived, record.outcome)
    }

    @Test
    fun `entering combat damage is included in actual damage received`() {
        val attacker = testGame.addDefaultRangedUnitWithUniques(attackingCiv, source,
            "[Target Unit] takes [10] damage <upon entering combat>")
        val defender = testGame.addUnit("Warrior", defendingCiv, target)

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        val record = game.attackEventsForTesting.single().targets.single()
        assertTrue(defender.health in 1..89)
        assertEquals(100 - defender.health, record.damageReceived)
        assertEquals(defender.health, record.healthAfter)
    }

    @Test
    fun `damage and healing triggered by HP loss are accumulated separately`() {
        val attacker = testGame.addUnit("Archer", attackingCiv, source)
        val defender = testGame.addDefaultMeleeUnitWithUniques(defendingCiv, target,
            "[This Unit] takes [10] damage <upon losing at least [1] HP in a single attack>",
            "[This Unit] heals [10] HP <upon losing at least [1] HP in a single attack>")
        defender.health = 60

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        val record = game.attackEventsForTesting.single().targets.single()
        assertTrue(defender.health in 11..59)
        assertEquals(60 - defender.health + 10, record.damageReceived)
        assertEquals(defender.health, record.healthAfter)
    }

    @Test
    fun `post kill damage is counted even when healing finishes above initial health`() {
        val attacker = testGame.addDefaultRangedUnitWithUniques(attackingCiv, source,
            "Heals [100] damage if it kills a unit",
            "[This Unit] takes [7] damage <upon defeating a [Military] unit>")
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        attacker.health = 60
        defender.health = 1

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        val record = game.attackEventsForTesting.single().attacker!!
        assertTrue(defender.isDestroyed)
        assertEquals(93, attacker.health)
        assertEquals(60, record.healthBefore)
        assertEquals(93, record.healthAfter)
        assertEquals(7, record.damageReceived)
    }

    @Test
    fun `air sweep directional damage includes loss triggers without subtracting their healing`() {
        attackingCiv.getDiplomacyManager(defendingCiv)!!.diplomaticStatus = DiplomaticStatus.War
        defendingCiv.getDiplomacyManager(attackingCiv)!!.diplomaticStatus = DiplomaticStatus.War
        val attacker = testGame.addUnit("Fighter", attackingCiv, testGame.getTile(-5, 0))
        val interceptor = testGame.addUnit("Fighter", defendingCiv, testGame.getTile(5, 0))
        interceptor.promotions.addPromotion(testGame.createUnitPromotion(
            "[This Unit] takes [10] damage <upon losing at least [1] HP in a single attack>",
            "[This Unit] heals [10] HP <upon losing at least [1] HP in a single attack>"
        ).name)

        AirInterception.airSweep(MapUnitCombatant(attacker), target)

        val event = game.attackEventsForTesting.single()
        val interception = event.interceptions.single()
        assertTrue(interceptor.health in 11..99)
        assertEquals(100 - interceptor.health + 10, interception.damageToInterceptor)
        assertEquals(interception.interceptor!!.damageReceived, interception.damageToInterceptor)
        assertEquals(event.attacker!!.damageReceived, interception.damageToAttacker)
    }

    @Test
    fun `trigger overkill records only HP actually lost`() {
        val attacker = testGame.addDefaultRangedUnitWithUniques(attackingCiv, source,
            "[Target Unit] takes [10] damage <upon entering combat>")
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        defender.health = 5

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        val record = game.attackEventsForTesting.single().targets.single()
        assertTrue(defender.isDestroyed)
        assertEquals(5, record.damageReceived)
        assertEquals(0, record.healthAfter)
        assertEquals(AttackParticipantOutcome.Destroyed, record.outcome)
    }

    @Test
    fun `later damage and another battle cannot add damage to a completed attack`() {
        val attacker = testGame.addUnit("Archer", attackingCiv, source)
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))
        val firstRecord = game.attackEventsForTesting.single().targets.single()
        val firstDamage = firstRecord.damageReceived
        val firstHealthAfter = firstRecord.healthAfter

        defender.takeDamage(3)
        defender.healBy(100)
        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        val secondRecord = game.attackEventsForTesting.last().targets.single()
        assertEquals(firstDamage, firstRecord.damageReceived)
        assertEquals(firstHealthAfter, firstRecord.healthAfter)
        assertEquals(100, secondRecord.healthBefore)
        assertEquals(100 - defender.health, secondRecord.damageReceived)
    }

}
