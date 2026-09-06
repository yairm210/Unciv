package com.unciv.logic.battle

import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.logic.map.HexCoord
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.attackEventsForTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class AttackOutcomeTest {
    private val testGame = TestGame().apply { makeHexagonalMap(7) }
    private val game = testGame.gameInfo
    private val attackingCiv = testGame.addCiv()
    private val defendingCiv = testGame.addCiv()
    private val attackingCapital = testGame.addCity(attackingCiv, testGame.getTile(-5, 0))
    private val defendingCapital = testGame.addCity(defendingCiv, testGame.getTile(5, 0))
    private val source = testGame.getTile(0, 0)
    private val target = testGame.getTile(1, 0)

    init {
        // City conquest must not try to update a WorldScreen in a headless test.
        game.currentPlayerCiv = testGame.addCiv(isPlayer = true)
        game.currentPlayer = game.currentPlayerCiv.civID
    }

    @Test
    fun `combat snapshots survive unit renaming and destruction without changing recorded identity`() {
        val attacker = testGame.addUnit("Archer", attackingCiv, source)
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        attacker.instanceName = "First archers"
        defender.instanceName = "Northern guard"
        defendingCiv.viewableTiles = setOf(source, target)

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))
        val event = game.attackEventsForTesting.single()
        val attackerHealthAfter = attacker.health
        val defenderHealthAfter = defender.health
        attacker.instanceName = "Renamed archers"
        defender.instanceName = "Renamed guard"
        attacker.destroy()
        defender.destroy()

        assertEquals(attacker.id, event.attacker!!.unitId)
        assertEquals("Archer", event.attacker!!.name)
        assertEquals("First archers", event.attacker!!.instanceName)
        assertEquals(attackingCiv.civID, event.attacker!!.civId)
        assertTrue(defendingCiv.civID in event.attacker!!.knownBy)
        assertEquals("Northern guard", event.targets.single().instanceName)
        assertEquals(defendingCiv.civID, event.targets.single().civId)
        assertEquals(attackerHealthAfter, event.attacker!!.healthAfter)
        assertEquals(defenderHealthAfter, event.targets.single().healthAfter)
        assertEquals(AttackParticipantOutcome.Survived, event.targets.single().outcome)
    }

    @Test
    fun `melee damage records both actual HP losses and surviving participants`() {
        val attacker = testGame.addUnit("Warrior", attackingCiv, source)
        val defender = testGame.addUnit("Warrior", defendingCiv, target)

        val damage = Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))
        val event = game.attackEventsForTesting.single()

        assertTrue(damage.attackerDealt > 0)
        assertTrue(damage.defenderDealt > 0)
        assertEquals(damage.defenderDealt, event.attacker!!.damageReceived)
        assertEquals(damage.attackerDealt, event.targets.single().damageReceived)
        assertEquals(100 - attacker.health, event.attacker!!.damageReceived)
        assertEquals(100 - defender.health, event.targets.single().damageReceived)
        assertEquals(AttackResolution.Completed, event.resolution)
        assertEquals(AttackParticipantOutcome.Survived, event.attacker!!.outcome)
        assertEquals(AttackParticipantOutcome.Survived, event.targets.single().outcome)
    }

    @Test
    fun `killing and healing afterwards does not subtract healing from damage received`() {
        val attacker = testGame.addDefaultMeleeUnitWithUniques(attackingCiv, source, "Heals [100] damage if it kills a unit")
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        attacker.health = 60
        defender.health = 10

        val damage = Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))
        val event = game.attackEventsForTesting.single()

        assertTrue(defender.isDestroyed)
        assertEquals(100, attacker.health)
        assertEquals(60, event.attacker!!.healthBefore)
        assertEquals(100, event.attacker!!.healthAfter)
        assertEquals(damage.defenderDealt, event.attacker!!.damageReceived)
        assertEquals(10, event.targets.single().damageReceived)
        assertEquals(AttackParticipantOutcome.Destroyed, event.targets.single().outcome)
    }

    @Test
    fun `settler replacement is recorded as capture with its original identity and owner`() {
        val attacker = testGame.addUnit("Warrior", attackingCiv, source)
        val settler = testGame.addUnit("Settler", defendingCiv, target)
        settler.instanceName = "Northern settlers"

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(settler))

        val worker = target.civilianUnit!!
        assertEquals("Worker", worker.name)
        assertEquals(attackingCiv, worker.civ)
        val record = game.attackEventsForTesting.single().targets.single()
        assertEquals("Settler", record.name)
        assertEquals(settler.id, record.unitId)
        assertEquals("Northern settlers", record.instanceName)
        assertEquals(defendingCiv.civID, record.civId)
        assertEquals(0, record.damageReceived)
        assertEquals(AttackParticipantOutcome.Captured, record.outcome)
    }

    @Test
    fun `uncapturable civilian destruction is distinguished from zero damage capture`() {
        val attacker = testGame.addUnit("Warrior", attackingCiv, source)
        val civilianType = testGame.createBaseUnit("Civilian", UniqueType.Uncapturable.text)
        val civilian = testGame.addUnit(civilianType.name, defendingCiv, target)

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(civilian))

        assertTrue(civilian.isDestroyed)
        val record = game.attackEventsForTesting.single().targets.single()
        assertEquals(0, record.damageReceived)
        assertEquals(AttackParticipantOutcome.Destroyed, record.outcome)
        assertEquals(100, record.healthAfter)
    }

    @Test
    fun `recruiting a defeated military unit records capture instead of destruction`() {
        val attacker = testGame.addDefaultMeleeUnitWithUniques(attackingCiv, source,
            "When defeating a [Military] unit, earn [0] Gold and recruit it")
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        defender.health = 1

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        assertTrue(defender.isDestroyed)
        assertTrue(attackingCiv.units.getCivUnits().any { it.name == "Warrior" && it.health == 50 })
        val record = game.attackEventsForTesting.single().targets.single()
        assertEquals(defender.id, record.unitId)
        assertEquals(defendingCiv.civID, record.civId)
        assertEquals(1, record.damageReceived)
        assertEquals(AttackParticipantOutcome.Captured, record.outcome)
    }

    @Test
    fun `withdrawal records original target and no damage without revealing the retreat location`() {
        val attacker = testGame.addUnit("Warrior", attackingCiv, source)
        val defender = testGame.addDefaultMeleeUnitWithUniques(defendingCiv, target, UniqueType.WithdrawsBeforeMeleeCombat.text)

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        assertFalse(defender.getTile() == target)
        val event = game.attackEventsForTesting.single()
        assertEquals(AttackResolution.Withdrawn, event.resolution)
        assertEquals(AttackParticipantOutcome.Withdrew, event.targets.single().outcome)
        assertEquals(target.position, event.targets.single().position)
        assertEquals(0, event.targets.single().damageReceived)
        assertEquals(0, event.attacker!!.damageReceived)
    }

    @Test
    fun `ranged defeat of city defenses is not reported as city capture`() {
        val attacker = testGame.addUnit("Archer", attackingCiv, source)
        val city = testGame.addCity(defendingCiv, target)
        city.health = 1

        Battle.attack(MapUnitCombatant(attacker), CityCombatant(city))

        assertEquals(defendingCiv, city.civ)
        val record = game.attackEventsForTesting.single().targets.single()
        assertEquals(AttackParticipantKind.City, record.kind)
        assertEquals(city.id, record.cityId)
        assertEquals(AttackParticipantOutcome.DefensesReduced, record.outcome)
    }

    @Test
    fun `city conquest preserves pre conquest city name and civilization`() {
        val attacker = testGame.addUnit("Warrior", attackingCiv, source)
        val city = testGame.addCity(defendingCiv, target)
        city.name = "Old capital"
        city.health = 1

        Battle.attack(MapUnitCombatant(attacker), CityCombatant(city))
        city.name = "New capital"

        assertEquals(attackingCiv, city.civ)
        val record = game.attackEventsForTesting.single().targets.single()
        assertEquals("Old capital", record.name)
        assertEquals(defendingCiv.civID, record.civId)
        assertEquals(AttackParticipantOutcome.Captured, record.outcome)
    }

    @Test
    fun `barbarian city raid records the raid and positive health attacker destruction`() {
        val barbarians = testGame.addBarbarianCiv()
        val attacker = testGame.addUnit("Brute", barbarians, source)
        val city = testGame.addCity(defendingCiv, target)
        city.health = 1

        Battle.attack(MapUnitCombatant(attacker), CityCombatant(city))

        assertTrue(attacker.isDestroyed)
        assertTrue(attacker.health > 0)
        assertEquals(defendingCiv, city.civ)
        val event = game.attackEventsForTesting.single()
        assertEquals(AttackParticipantOutcome.Destroyed, event.attacker!!.outcome)
        assertEquals(AttackParticipantOutcome.Raided, event.targets.single().outcome)
    }

    @Test
    fun `interception records a failed attack and actual damage to its attacker`() {
        val attacker = testGame.addUnit("Bomber", attackingCiv, attackingCapital.getCenterTile())
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        val interceptor = testGame.addUnit("Fighter", defendingCiv, defendingCapital.getCenterTile())
        interceptor.promotions.addPromotion(testGame.createUnitPromotion("[100]% chance to intercept air attacks").name)
        attacker.health = 1

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        assertTrue(attacker.isDestroyed)
        assertEquals(100, defender.health)
        val event = game.attackEventsForTesting.single()
        assertEquals(AttackResolution.Intercepted, event.resolution)
        assertEquals(1, event.attacker!!.damageReceived)
        assertEquals(AttackParticipantOutcome.Destroyed, event.attacker!!.outcome)
        assertEquals(0, event.targets.single().damageReceived)
    }

    @Test
    fun `nuclear attack retains all original blast victims and self destructed attacker`() {
        val city = testGame.addCity(defendingCiv, target, initialPopulation = 8)
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        val worker = testGame.addUnit("Worker", defendingCiv, testGame.getTile(1, 1))
        val friendlyUnit = testGame.addUnit("Warrior", attackingCiv, testGame.getTile(-1, 0))
        val attacker = testGame.addUnit("Atomic Bomb", attackingCiv, attackingCapital.getCenterTile())

        Nuke.NUKE(MapUnitCombatant(attacker), target)

        val event = game.attackEventsForTesting.single()
        assertEquals(AttackKind.Nuclear, event.kind)
        assertEquals(AttackResolution.Completed, event.resolution)
        assertEquals(setOf(defender.id, worker.id, friendlyUnit.id), event.targets.mapNotNull { it.unitId }.toSet())
        assertEquals(listOf(city.id), event.targets.mapNotNull { it.cityId })
        assertFalse(event.targets.any { it.unitId == attacker.id })
        assertEquals(AttackParticipantOutcome.Destroyed, event.attacker!!.outcome)
        assertEquals(100, event.attacker!!.healthAfter)
        assertEquals(0, event.attacker!!.damageReceived)
        assertTrue(event.targets.single { it.unitId == defender.id }.damageReceived > 0)
        assertTrue(event.targets.single { it.cityId == city.id }.damageReceived > 0)
        assertTrue(event.targets.none { it.outcome == AttackParticipantOutcome.Pending })
    }

    @Test
    fun `nuclear attack records a unit spawned by the war declaration before detonation`() {
        val spawningCiv = testGame.addCiv("Free [Warrior] appears <upon being declared war on by [All] Civilizations>")
        testGame.addCity(spawningCiv, target, initialPopulation = 8)
        attackingCiv.diplomacyFunctions.makeCivilizationsMeet(spawningCiv)
        val attacker = testGame.addUnit("Atomic Bomb", attackingCiv, attackingCapital.getCenterTile())
        assertTrue(spawningCiv.units.getCivUnits().none())

        Nuke.NUKE(MapUnitCombatant(attacker), target)

        val spawnedUnitRecord = game.attackEventsForTesting.single().targets.single {
            it.kind == AttackParticipantKind.Unit && it.civId == spawningCiv.civID
        }
        assertEquals("Warrior", spawnedUnitRecord.name)
        assertEquals(100, spawnedUnitRecord.healthBefore)
        assertTrue(spawnedUnitRecord.damageReceived > 0)
        assertFalse(spawnedUnitRecord.outcome == AttackParticipantOutcome.Pending)
    }

    @Test
    fun `nested participant data survives serialization and clone changes stay isolated`() {
        val attacker = testGame.addUnit("Archer", attackingCiv, source)
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        attacker.instanceName = "First archers"
        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))
        val event = game.attackEventsForTesting.single()

        val restored = json().fromJson(GameInfo::class.java, json().toJson(game)).attackEventsForTesting.single()
        assertEquals(event.resolution, restored.resolution)
        assertEquals(event.attacker!!.unitId, restored.attacker!!.unitId)
        assertEquals("First archers", restored.attacker!!.instanceName)
        assertEquals(HexCoord(0, 0), restored.attacker!!.position)
        assertEquals(event.attacker!!.knownBy, restored.attacker!!.knownBy)
        assertEquals(event.targets.single().outcome, restored.targets.single().outcome)
        assertEquals(event.targets.single().damageReceived, restored.targets.single().damageReceived)
        assertEquals(event.targets.single().healthAfter, restored.targets.single().healthAfter)

        val clone = game.clone().attackEventsForTesting.single()
        assertNotSame(event.attacker, clone.attacker)
        assertNotSame(event.targets.single(), clone.targets.single())
        clone.attacker!!.knownBy.clear()
        clone.targets.single().knownBy.clear()
        clone.targets.single().name = "Changed"
        clone.targets.clear()
        assertTrue(attackingCiv.civID in event.attacker!!.knownBy)
        assertTrue(defendingCiv.civID in event.targets.single().knownBy)
        assertEquals("Warrior", event.targets.single().name)
    }
}
