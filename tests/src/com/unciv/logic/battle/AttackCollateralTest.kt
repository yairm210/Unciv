package com.unciv.logic.battle

import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.attackEventsForTesting
import com.unciv.view.GameView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class AttackCollateralTest {
    private val testGame = TestGame().apply { makeHexagonalMap(7) }
    private val game = testGame.gameInfo
    private val attackerCiv = testGame.addCiv()
    private val defenderCiv = testGame.addCiv()
    private val source = testGame.getTile(0, 0)
    private val target = testGame.getTile(1, 0)

    init {
        testGame.addCity(attackerCiv, testGame.getTile(-5, 0))
        testGame.addCity(defenderCiv, testGame.getTile(5, 0))
        attackerCiv.diplomacyFunctions.makeCivilizationsMeet(defenderCiv)
        attackerCiv.getDiplomacyManager(defenderCiv)!!.declareWar()
        // Keep city-conquest tutorial/settings work out of the headless fixture.
        game.currentPlayerCiv = testGame.addCiv(isPlayer = true)
        game.currentPlayer = game.currentPlayerCiv.civID
    }

    @Test
    fun `capturing a stacked civilian retains its original identity and knowledge`() {
        val attacker = testGame.addUnit("Warrior", attackerCiv, source)
        val defender = testGame.addUnit("Warrior", defenderCiv, target).apply { health = 1 }
        val worker = testGame.addUnit("Worker", defenderCiv, target)
        worker.instanceName = "Hidden workers"
        worker.promotions.addPromotion(testGame.createUnitPromotion(UniqueType.Invisible.text).name)
        attackerCiv.viewableTiles = setOf(source, target)
        attackerCiv.viewableInvisibleUnitsTiles = emptySet()

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        assertEquals(attackerCiv, worker.civ)
        val event = game.attackEventsForTesting.single()
        assertEquals(setOf(defender.id, worker.id), event.targets.map { it.unitId }.toSet())
        val record = event.targets.single { it.unitId == worker.id }
        assertEquals("Worker", record.name)
        assertEquals("Hidden workers", record.instanceName)
        assertEquals(defenderCiv.civID, record.civId)
        assertEquals(target.position, record.position)
        assertEquals(AttackParticipantOutcome.Captured, record.outcome)
        assertEquals(0, record.damageReceived)
        assertEquals(100, record.healthAfter)
        assertTrue(defenderCiv.civID in record.knownBy)
        assertFalse(attackerCiv.civID in record.knownBy)

        worker.currentMovement = worker.getMaxMovement().toFloat()
        worker.movement.moveToTile(testGame.getTile(2, 0))
        val view = GameView(game, attackerCiv)
        assertTrue(view.attackEventsView.getObservedAttacks(view.getMapUnitView(worker)).isEmpty())
        assertFalse(attackerCiv.civID in record.knownBy)
    }

    @Test
    fun `stacked settler conversion records capture rather than destruction`() {
        val attacker = testGame.addUnit("Warrior", attackerCiv, source)
        val defender = testGame.addUnit("Warrior", defenderCiv, target).apply { health = 1 }
        val settler = testGame.addUnit("Settler", defenderCiv, target)

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        val worker = target.civilianUnit!!
        assertEquals("Worker", worker.name)
        assertEquals(settler.id, worker.id)
        val record = game.attackEventsForTesting.single().targets.single { it.unitId == settler.id }
        assertEquals("Settler", record.name)
        assertEquals(defenderCiv.civID, record.civId)
        assertEquals(AttackParticipantOutcome.Captured, record.outcome)
        assertEquals(worker.health, record.healthAfter)
        assertEquals(0, record.damageReceived)
    }

    @Test
    fun `uncapturable stacked civilian records destruction without fictitious HP damage`() {
        val attacker = testGame.addUnit("Warrior", attackerCiv, source)
        val defender = testGame.addUnit("Warrior", defenderCiv, target).apply { health = 1 }
        val worker = testGame.addUnit("Worker", defenderCiv, target)
        worker.promotions.addPromotion(testGame.createUnitPromotion(UniqueType.Uncapturable.text).name)

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        assertTrue(worker.isDestroyed)
        val record = game.attackEventsForTesting.single().targets.single { it.unitId == worker.id }
        assertEquals(AttackParticipantOutcome.Destroyed, record.outcome)
        assertEquals(0, record.damageReceived)
        assertEquals(100, record.healthAfter)
    }

    @Test
    fun `city conquest records its garrison aircraft and captured civilian before removing them`() {
        val attacker = testGame.addUnit("Warrior", attackerCiv, source)
        val city = testGame.addCity(defenderCiv, target).apply { health = 1 }
        val garrison = testGame.addUnit("Warrior", defenderCiv, target)
        val fighter = testGame.addUnit("Fighter", defenderCiv, target)
        val bomber = testGame.addUnit("Bomber", defenderCiv, target)
        val worker = testGame.addUnit("Worker", defenderCiv, target)
        defenderCiv.viewableTiles = setOf(source, target)

        Battle.attack(MapUnitCombatant(attacker), CityCombatant(city))

        val event = game.attackEventsForTesting.single()
        assertEquals(5, event.targets.size)
        assertEquals(AttackParticipantOutcome.Captured, event.targets.single { it.cityId == city.id }.outcome)
        for (unit in listOf(garrison, fighter, bomber)) {
            assertTrue(unit.isDestroyed)
            val record = event.targets.single { it.unitId == unit.id }
            assertEquals(AttackParticipantOutcome.Destroyed, record.outcome)
            assertEquals(0, record.damageReceived)
            assertEquals(defenderCiv.civID, record.civId)
            assertEquals(target.position, record.position)
        }
        assertEquals(attackerCiv, worker.civ)
        assertEquals(AttackParticipantOutcome.Captured, event.targets.single { it.unitId == worker.id }.outcome)

        val restored = json().fromJson(GameInfo::class.java, json().toJson(game)).attackEventsForTesting.single()
        assertEquals(event.targets.map { it.outcome }, restored.targets.map { it.outcome })
        assertEquals(event.targets.map { it.unitId }, restored.targets.map { it.unitId })
        val clone = game.clone().attackEventsForTesting.single()
        assertNotSame(event.targets.last(), clone.targets.last())
        clone.targets.last().knownBy.clear()
        assertTrue(defenderCiv.civID in event.targets.last().knownBy)
    }

    @Test
    fun `ranged damage does not invent participation for an untouched stacked civilian`() {
        val attacker = testGame.addUnit("Archer", attackerCiv, source)
        val defender = testGame.addUnit("Warrior", defenderCiv, target)
        val worker = testGame.addUnit("Worker", defenderCiv, target)

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        assertEquals(defenderCiv, worker.civ)
        assertEquals(defender.id, game.attackEventsForTesting.single().targets.single().unitId)
    }
}
