package com.unciv.logic.battle

import com.badlogic.gdx.utils.JsonReader
import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.CivilopediaAction
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.map.HexCoord
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.attackEventsForTesting
import com.unciv.view.GameView
import com.unciv.view.ObservedAttack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class NukeNotificationTest {
    private val testGame = TestGame().apply { makeHexagonalMap(10) }
    private val game = testGame.gameInfo
    private val attackingCiv = testGame.addCiv(isPlayer = true)
    private val defendingCiv = testGame.addCiv(isPlayer = true)
    private val observingCiv = testGame.addCiv(isPlayer = true)
    private val source = testGame.getTile(-6, 0)
    private val target = testGame.getTile(1, 0)

    init {
        testGame.addCity(attackingCiv, source)
        testGame.addCity(defendingCiv, testGame.getTile(6, 0))
        testGame.addCity(observingCiv, testGame.getTile(0, 6))
        // Introductions must not invoke the current player's UI tutorial/settings save.
        attackingCiv.diplomacyFunctions.makeCivilizationsMeet(defendingCiv)
        attackingCiv.diplomacyFunctions.makeCivilizationsMeet(observingCiv)
        defendingCiv.diplomacyFunctions.makeCivilizationsMeet(observingCiv)
        game.currentPlayerCiv = observingCiv
        game.currentPlayer = observingCiv.civID
        game.civilizations.forEach { it.notifications.clear() }
    }

    @Test
    fun `unseen launch stays anonymous while a separate declaration identifies the warring civ`() {
        testGame.addCity(defendingCiv, target, initialPopulation = 8)
        val attacker = testGame.addUnit("Atomic Bomb", attackingCiv, source)
        defendingCiv.viewableTiles = setOf(target)

        Nuke.NUKE(MapUnitCombatant(attacker), target)

        assertTrue(attackingCiv.isAtWarWith(defendingCiv))
        assertFalse(defendingCiv.civID in game.attackEventsForTesting.single().knowsSource)
        val declaration = defendingCiv.notifications.single {
            it.text == "[${attackingCiv.civName}] has declared war on us!"
        }
        assertEquals(NotificationCategory.Diplomacy, declaration.category)
        assertTrue(attackingCiv.civName in declaration.icons)
        assertTrue(attackingCiv.notifications.any {
            it.text == "[${attackingCiv.civName}] has declared war on [${defendingCiv.civName}]!"
        })
        assertFalse(attackingCiv.notifications.any {
            it.text.contains("[${defendingCiv.civName}] has declared war on us!")
        })

        val detonation = detonationReport(defendingCiv)
        assertEquals("A(n) [Atomic Bomb] has been detonated by [an unknown civilization]!", detonation.text)
        assertFalse(attackingCiv.civName in detonation.icons)
        assertEquals(listOf(target.position), locations(detonation))
        for (notification in defendingCiv.notifications.filter { it.category == NotificationCategory.War }) {
            assertFalse(notification.text.contains(attackingCiv.civName))
            assertFalse(attackingCiv.civName in notification.icons)
            assertFalse(source.position in locations(notification))
        }
    }

    @Test
    fun `observed launch identifies the civilization in the nuclear attack report`() {
        testGame.addCity(defendingCiv, target, initialPopulation = 8)
        val attacker = testGame.addUnit("Atomic Bomb", attackingCiv, source)
        defendingCiv.viewableTiles = setOf(source, target)

        Nuke.NUKE(MapUnitCombatant(attacker), target)

        assertTrue(defendingCiv.civID in game.attackEventsForTesting.single().knowsSource)
        val detonation = detonationReport(defendingCiv)
        assertEquals("A(n) [Atomic Bomb] from [${attackingCiv.civName}] has exploded in our territory!", detonation.text)
        assertTrue(attackingCiv.civName in detonation.icons)
        assertEquals(listOf(target.position), locations(detonation))
    }

    @Test
    fun `uninvolved civilization cannot locate an unseen explosion or attribute an unseen launch`() {
        val attacker = testGame.addUnit("Atomic Bomb", attackingCiv, source)
        observingCiv.viewableTiles = emptySet()

        Nuke.NUKE(MapUnitCombatant(attacker), target)

        assertTrue(observingCiv.knows(attackingCiv))
        val detonation = detonationReport(observingCiv)
        assertEquals("A(n) [Atomic Bomb] has been detonated by [an unknown civilization]!", detonation.text)
        assertFalse(attackingCiv.civName in detonation.icons)
        assertTrue(locations(detonation).isEmpty())
        assertEquals(1, detonation.actions.filterIsInstance<CivilopediaAction>().size)
    }

    @Test
    fun `observing an explosion provides its location without identifying the unseen launcher`() {
        val attacker = testGame.addUnit("Atomic Bomb", attackingCiv, source)
        observingCiv.viewableTiles = setOf(target)

        Nuke.NUKE(MapUnitCombatant(attacker), target)

        val detonation = detonationReport(observingCiv)
        assertEquals("A(n) [Atomic Bomb] has been detonated by [an unknown civilization]!", detonation.text)
        assertFalse(attackingCiv.civName in detonation.icons)
        assertEquals(listOf(target.position), locations(detonation))
    }

    @Test
    fun `civilization whose unit is in the blast knows the impact even if ground zero was unseen`() {
        val attacker = testGame.addUnit("Atomic Bomb", attackingCiv, source)
        val victimTile = testGame.getTile(2, 0)
        testGame.addUnit("Warrior", defendingCiv, victimTile)
        defendingCiv.viewableTiles = setOf(victimTile)

        Nuke.NUKE(MapUnitCombatant(attacker), target)

        val event = game.attackEventsForTesting.single()
        assertTrue(defendingCiv.civID in event.knowsTarget)
        assertTrue(event.targets.any { it.civId == defendingCiv.civID })
        val detonation = detonationReport(defendingCiv)
        assertFalse(attackingCiv.civName in detonation.icons)
        assertEquals(listOf(target.position), locations(detonation))
        val expected = listOf(ObservedAttack(event.turn, null, target.position))
        assertEquals(expected, GameView(game, defendingCiv).attackEventsView.getObservedAttacks())

        val restored = json().fromJson(GameInfo::class.java, json().toJson(game))
        restored.ruleset = game.ruleset
        val restoredViewer = restored.civilizations.single { it.civID == defendingCiv.civID }
        restoredViewer.gameInfo = restored
        restoredViewer.nation = defendingCiv.nation
        assertEquals(expected, GameView(restored, restoredViewer).attackEventsView.getObservedAttacks())
        assertEquals(listOf(target.position), locations(detonationReport(restoredViewer)))
    }

    @Test
    fun `a worker destroyed without HP loss receives a destruction report`() {
        val worker = testGame.addUnit("Worker", defendingCiv, target).apply {
            instanceName = "Worker casualty"
            // The fixture has no uranium; even the reduced blast crosses the civilian death threshold.
            health = 40
        }
        val attacker = testGame.addUnit("Atomic Bomb", attackingCiv, source)
        defendingCiv.viewableTiles = setOf(target)

        Nuke.NUKE(MapUnitCombatant(attacker), target)

        assertTrue(worker.isDestroyed)
        assertEquals(40, worker.health)
        val record = game.attackEventsForTesting.single().targets.single { it.unitId == worker.id }
        assertEquals(AttackParticipantOutcome.Destroyed, record.outcome)
        assertEquals(0, record.damageReceived)
        val casualty = defendingCiv.notifications.single { it.text.contains("Worker casualty") }
        assertTrue(casualty.text.contains("destroyed"))
        assertEquals(listOf(target.position), locations(casualty))
        assertAnonymousAttackReports(defendingCiv)
    }

    @Test
    fun `destroying a city reports its destruction and its aircraft casualties exactly once`() {
        val city = testGame.addCity(defendingCiv, target, initialPopulation = 2).apply {
            name = "City casualty"
        }
        val fighter = testGame.addUnit("Fighter", defendingCiv, target).apply {
            instanceName = "Aircraft casualty"
            attacksThisTurn = 99 // Exercise city teardown rather than interception.
        }
        val cityHealth = city.health
        val attacker = testGame.addUnit("Nuclear Missile", attackingCiv, source)
        defendingCiv.viewableTiles = setOf(target)

        Nuke.NUKE(MapUnitCombatant(attacker), target)

        assertFalse(city in defendingCiv.cities)
        assertEquals(cityHealth, city.health)
        assertTrue(fighter.isDestroyed)
        assertEquals(100, fighter.health)
        val event = game.attackEventsForTesting.single()
        for (record in event.targets.filter { it.cityId == city.id || it.unitId == fighter.id }) {
            assertEquals(AttackParticipantOutcome.Destroyed, record.outcome)
            assertEquals(0, record.damageReceived)
        }
        for (name in listOf("City casualty", "Aircraft casualty")) {
            val casualty = defendingCiv.notifications.single { it.text.contains(name) }
            assertTrue(casualty.text.contains("destroyed"))
            assertEquals(listOf(target.position), locations(casualty))
        }
        assertTrue(defendingCiv.civID in event.nuclearTerritoryCivIds)
        assertAnonymousAttackReports(defendingCiv)
    }

    @Test
    fun `fatal interception produces no detonation report or unseen impact knowledge`() {
        val victimTile = testGame.getTile(2, 0)
        val worker = testGame.addUnit("Worker", defendingCiv, victimTile)
        val interceptorBase = testGame.getTile(6, 0)
        testGame.addUnit("Fighter", defendingCiv, interceptorBase)
        val attacker = testGame.addUnit("Atomic Bomb", attackingCiv, source).apply { health = 1 }
        defendingCiv.viewableTiles = setOf(victimTile, interceptorBase)

        Nuke.NUKE(MapUnitCombatant(attacker), target)

        val event = game.attackEventsForTesting.single()
        assertEquals(AttackResolution.Intercepted, event.resolution)
        assertTrue(attacker.isDestroyed)
        assertFalse(worker.isDestroyed)
        assertEquals(100, worker.health)
        assertTrue(event.targets.isEmpty())
        assertTrue(event.nuclearTerritoryCivIds.isEmpty())
        assertFalse(defendingCiv.civID in event.knowsTarget)
        assertTrue(GameView(game, defendingCiv).attackEventsView.getObservedAttacks().isEmpty())
        assertFalse(game.civilizations.flatMap { it.notifications }.any {
            it.text.contains("has been detonated") || it.text.contains("has exploded in our territory")
        })
        assertAnonymousAttackReports(defendingCiv)
        for (notification in defendingCiv.notifications)
            assertFalse(target.position in locations(notification))
    }

    private fun assertAnonymousAttackReports(civ: Civilization) {
        for (notification in civ.notifications.filter { it.category == NotificationCategory.War }) {
            assertFalse(notification.text.contains(attackingCiv.civName))
            assertFalse(attackingCiv.civName in notification.icons)
            assertFalse(source.position in locations(notification))
        }
    }

    private fun detonationReport(civ: Civilization): Notification = civ.notifications.single {
        it.text.contains("has been detonated") || it.text.contains("has exploded in our territory")
    }

    private fun locations(notification: Notification): List<HexCoord> =
        JsonReader().parse(json().toJson(notification)).get("actions")?.mapNotNull {
            val action = it.get("LocationAction") ?: return@mapNotNull null
            val location = action.get("location")
            HexCoord(location.getInt("x", 0), location.getInt("y", 0))
        }.orEmpty()
}
