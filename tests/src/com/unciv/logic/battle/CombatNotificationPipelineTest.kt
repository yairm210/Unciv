package com.unciv.logic.battle

import com.badlogic.gdx.utils.JsonReader
import com.unciv.json.json
import com.unciv.logic.civilization.Notification
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.attackEventsForTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class CombatNotificationPipelineTest {
    private val testGame = TestGame().apply { makeHexagonalMap(7) }
    private val game = testGame.gameInfo
    private val attackingCiv = testGame.addCiv(testGame.ruleset.nations.getValue("Mongolia"), isPlayer = true)
    private val defendingCiv = testGame.addCiv(testGame.ruleset.nations.getValue("Egypt"), isPlayer = true)
    private val source = testGame.getTile(0, 0)
    private val target = testGame.getTile(3, 0)

    init {
        testGame.addCity(attackingCiv, testGame.getTile(-6, 0))
        testGame.addCity(defendingCiv, testGame.getTile(6, 0))
        attackingCiv.diplomacyFunctions.makeCivilizationsMeet(defendingCiv)
        attackingCiv.getDiplomacyManager(defendingCiv)!!.declareWar()
        // Avoid interactive city-conquest settings work in this fixture.
        game.currentPlayerCiv = testGame.addCiv(isPlayer = true)
        game.currentPlayer = game.currentPlayerCiv.civID
        attackingCiv.notifications.clear()
        defendingCiv.notifications.clear()
    }

    private fun artillery(vararg uniques: String): MapUnit {
        val unit = testGame.addUnit("Artillery", attackingCiv, source)
        unit.instanceName = "Secret battery"
        if (uniques.isNotEmpty())
            unit.promotions.addPromotion(testGame.createUnitPromotion(*uniques).name)
        return unit
    }

    private fun defender(name: String = "Our defenders"): MapUnit =
        testGame.addUnit("Infantry", defendingCiv, target).apply { instanceName = name }

    private fun locations(notification: Notification): List<HexCoord> =
        JsonReader().parse(json().toJson(notification)).get("actions").mapNotNull {
            val action = it.get("LocationAction") ?: return@mapNotNull null
            val location = action.get("location")
            if (location == null) HexCoord.Zero
            else HexCoord(location.getInt("x", 0), location.getInt("y", 0))
        }

    @Test
    fun `ordinary damage notification includes entering combat damage from the completed record`() {
        val attacker = artillery("[Target Unit] takes [10] damage <upon entering combat>")
        val defender = defender()
        defendingCiv.viewableTiles = setOf(target)

        val exchange = Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        val record = game.attackEventsForTesting.single().targets.single()
        val notification = defendingCiv.notifications.single { "[Our defenders]" in it.text }
        assertEquals(exchange.attackerDealt + 10, record.damageReceived)
        assertTrue(notification.text.contains("[-${record.damageReceived}] HP"))
        assertEquals(listOf(target.position), locations(notification))
    }

    @Test
    fun `destruction after the damage exchange is reported even when the unit retains positive health`() {
        val attacker = artillery("[Target Unit] is destroyed <upon damaging a [Military] unit>")
        val defender = defender()
        defendingCiv.viewableTiles = setOf(target)

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        assertTrue(defender.isDestroyed)
        assertTrue(defender.health > 0)
        val record = game.attackEventsForTesting.single().targets.single()
        val notification = defendingCiv.notifications.single { "[Our defenders]" in it.text }
        assertEquals(AttackParticipantOutcome.Destroyed, record.outcome)
        assertTrue(notification.text.contains("has destroyed"))
        assertFalse(notification.text.contains("has attacked"))
        assertTrue(notification.text.contains("[-${record.damageReceived}] HP"))
    }

    @Test
    fun `the completed report does not expose self damage of an unseen attacker`() {
        val attacker = artillery("[This Unit] takes [37] damage <upon entering combat>")
        val defender = defender()
        defendingCiv.viewableTiles = setOf(target)

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        val event = game.attackEventsForTesting.single()
        assertEquals(37, event.attacker!!.damageReceived)
        assertFalse(defendingCiv.civID in event.knowsSource)
        val notification = defendingCiv.notifications.single { "[Our defenders]" in it.text }
        assertFalse(notification.text.contains("[-37] HP"))
        assertFalse(notification.text.contains("Secret battery"))
        assertFalse(notification.text.contains("Mongolia"))
        assertFalse("Mongolia" in notification.icons)
        assertEquals(listOf(target.position), locations(notification))
    }

    @Test
    fun `the completed report does not expose an unseen attackers destruction by its own effect`() {
        val attacker = artillery("[This Unit] is destroyed <upon damaging a [Military] unit>")
        val defender = defender()
        defendingCiv.viewableTiles = setOf(target)

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        assertTrue(attacker.isDestroyed)
        assertTrue(attacker.health > 0)
        assertFalse(defender.isDestroyed)
        val event = game.attackEventsForTesting.single()
        assertEquals(AttackParticipantOutcome.Destroyed, event.attacker!!.outcome)
        assertFalse(defendingCiv.civID in event.knowsSource)
        val notification = defendingCiv.notifications.single { "[Our defenders]" in it.text }
        assertTrue(notification.text.contains("has attacked"))
        assertFalse(notification.text.contains("destroyed"))
        assertFalse(notification.text.contains("Secret battery"))
        assertFalse(notification.text.contains("Mongolia"))
        assertFalse("Mongolia" in notification.icons)
        assertEquals(listOf(target.position), locations(notification))
    }

    @Test
    fun `defeat rewards do not disclose the name of an unseen bombarding city`() {
        val city = testGame.addCity(attackingCiv, source).apply { name = "Secret city" }
        val defender = testGame.addUnit("Warrior", defendingCiv, target).apply {
            instanceName = "Doomed defenders"
            health = 1
            promotions.addPromotion(testGame.createUnitPromotion("Gain [10] [Gold] <upon being defeated>").name)
        }
        defendingCiv.viewableTiles = setOf(target)
        defendingCiv.notifications.clear()

        Battle.attack(CityCombatant(city), MapUnitCombatant(defender))

        assertTrue(defender.isDestroyed)
        assertFalse(defendingCiv.civID in game.attackEventsForTesting.single().knowsSource)
        val reward = defendingCiv.notifications.single { "Gold" in it.text }
        assertTrue(reward.text.contains("being defeated"))
        assertTrue(defendingCiv.notifications.none { "Secret city" in it.text })
        assertTrue(defendingCiv.notifications.none { source.position in locations(it) })
    }

    @Test
    fun `city conquest notifies the original owner using the original city name`() {
        val city = testGame.addCity(defendingCiv, target).apply { name = "Original city"; health = 1 }
        val attacker = testGame.addUnit("Warrior", attackingCiv, testGame.getTile(2, 0))
        val oldName = city.name
        defendingCiv.notifications.clear()

        Battle.attack(MapUnitCombatant(attacker), CityCombatant(city))
        city.name = "Renamed after conquest"

        assertEquals(attackingCiv, city.civ)
        val record = game.attackEventsForTesting.single().targets.single()
        assertEquals(defendingCiv.civID, record.civId)
        assertEquals(oldName, record.name)
        val notification = defendingCiv.notifications.single { "has captured" in it.text && oldName in it.text }
        assertFalse(notification.text.contains(city.name))
        assertTrue(target.position in locations(notification))
    }

    @Test
    fun `direct civilian capture produces one notice with its original owned name`() {
        val attacker = testGame.addUnit("Warrior", attackingCiv, testGame.getTile(2, 0))
        val worker = testGame.addUnit("Worker", defendingCiv, target).apply { instanceName = "Our builders" }
        defendingCiv.notifications.clear()

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(worker))

        assertEquals(attackingCiv, worker.civ)
        val record = game.attackEventsForTesting.single().targets.single()
        assertTrue(record.captureAttempted)
        assertEquals(AttackParticipantOutcome.Captured, record.outcome)
        val captures = defendingCiv.notifications.filter { "has captured" in it.text }
        assertEquals(1, captures.size)
        assertTrue(captures.single().text.contains("[Our builders]"))
        assertEquals(listOf(target.position), locations(captures.single()))
    }

    @Test
    fun `advancing after a kill produces only one capture notice for the stacked civilian`() {
        val attacker = testGame.addUnit("Warrior", attackingCiv, testGame.getTile(2, 0))
        val defender = testGame.addUnit("Warrior", defendingCiv, target).apply { health = 1 }
        val worker = testGame.addUnit("Worker", defendingCiv, target).apply { instanceName = "Stacked builders" }
        defendingCiv.notifications.clear()

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        assertEquals(attackingCiv, worker.civ)
        val workerRecord = game.attackEventsForTesting.single().targets.single { it.unitId == worker.id }
        assertTrue(workerRecord.captureAttempted)
        val notices = defendingCiv.notifications.filter { "[Stacked builders]" in it.text }
        assertEquals(1, notices.size)
        assertTrue(notices.single().text.contains("has captured"))
    }

    @Test
    fun `advancing capture preserves a converted settlers identity and reports it once`() {
        val attacker = testGame.addUnit("Warrior", attackingCiv, testGame.getTile(2, 0))
        val defender = testGame.addUnit("Warrior", defendingCiv, target).apply { health = 1 }
        val settler = testGame.addUnit("Settler", defendingCiv, target).apply { instanceName = "Settler convoy" }
        defendingCiv.notifications.clear()

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        assertTrue(settler.isDestroyed)
        val successor = attackingCiv.units.getUnitById(settler.id)
        assertNotNull(successor)
        assertNotSame(settler, successor)
        assertEquals("Worker", successor!!.name)
        val record = game.attackEventsForTesting.single().targets.single { it.unitId == settler.id }
        assertEquals("Settler", record.name)
        assertEquals("Settler convoy", record.instanceName)
        assertEquals(defendingCiv.civID, record.civId)
        assertEquals(successor.health, record.healthAfter)
        assertTrue(record.captureAttempted)
        assertEquals(AttackParticipantOutcome.Captured, record.outcome)
        val notices = defendingCiv.notifications.filter { "[Settler convoy]" in it.text }
        assertEquals(1, notices.size)
        assertTrue(notices.single().text.contains("has captured"))
        assertEquals(listOf(target.position), locations(notices.single()))
    }

    @Test
    fun `military recruitment and its civilian placement capture each produce one notice`() {
        val attacker = testGame.addDefaultMeleeUnitWithUniques(attackingCiv, testGame.getTile(2, 0),
            "When defeating a [Military] unit, earn [0] Gold and recruit it")
        val defender = testGame.addUnit("Warrior", defendingCiv, target).apply {
            health = 1
            instanceName = "Recruitable guard"
        }
        val worker = testGame.addUnit("Worker", defendingCiv, target).apply { instanceName = "Recruitable builders" }
        defendingCiv.notifications.clear()

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        assertEquals(attackingCiv, worker.civ)
        val event = game.attackEventsForTesting.single()
        for (id in listOf(defender.id, worker.id)) {
            val record = event.targets.single { it.unitId == id }
            assertTrue(record.captureAttempted)
            assertEquals(AttackParticipantOutcome.Captured, record.outcome)
        }
        for (name in listOf("Recruitable guard", "Recruitable builders")) {
            val notices = defendingCiv.notifications.filter { "[$name]" in it.text }
            assertEquals(1, notices.size)
            assertTrue(notices.single().text.contains("has captured"))
        }
    }

    @Test
    fun `uncapturable civilian destruction produces one notice without inventing HP damage`() {
        val attacker = testGame.addUnit("Warrior", attackingCiv, testGame.getTile(2, 0))
        val worker = testGame.addUnit("Worker", defendingCiv, target).apply {
            instanceName = "Defiant builders"
            promotions.addPromotion(testGame.createUnitPromotion(UniqueType.Uncapturable.text).name)
        }
        defendingCiv.notifications.clear()

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(worker))

        val record = game.attackEventsForTesting.single().targets.single()
        assertTrue(record.captureAttempted)
        assertEquals(0, record.damageReceived)
        assertEquals(AttackParticipantOutcome.Destroyed, record.outcome)
        val notices = defendingCiv.notifications.filter { "[Defiant builders]" in it.text }
        assertEquals(1, notices.size)
        assertTrue(notices.single().text.contains("has destroyed"))
    }

    @Test
    fun `standalone capture keeps its original location when the new owner cannot embark`() {
        defendingCiv.tech.addTechnology("Optics")
        testGame.setTileTerrain(target.position, "Coast")
        val captureSource = testGame.setTileTerrain(HexCoord(2, 0), "Coast")
        val attacker = testGame.addUnit("Trireme", attackingCiv, captureSource)
        val worker = testGame.addUnit("Worker", defendingCiv, target).apply { instanceName = "Embarked builders" }
        assertEquals(target, worker.getTile())
        assertTrue(worker.isEmbarked())
        defendingCiv.viewableTiles = setOf(target)
        defendingCiv.notifications.clear()

        BattleUnitCapture.captureCivilianUnit(MapUnitCombatant(attacker), MapUnitCombatant(worker))

        assertEquals(attackingCiv, worker.civ)
        assertNotEquals(target, worker.getTile())
        assertFalse(worker.getTile() in defendingCiv.viewableTiles)
        val notification = defendingCiv.notifications.single { "has captured" in it.text }
        assertTrue(notification.text.contains("[Embarked builders]"))
        assertEquals(listOf(target.position), locations(notification))
        assertTrue(game.attackEventsForTesting.isEmpty())
    }
}
