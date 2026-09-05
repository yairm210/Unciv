package com.unciv.logic.battle

import com.badlogic.gdx.utils.JsonReader
import com.unciv.json.json
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.CivilopediaAction
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.map.HexCoord
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
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
        assertFalse(defendingCiv.civID in game.attackEvents.single().knowsSource)
        val declaration = defendingCiv.notifications.single {
            it.text == "[${attackingCiv.civName}] has declared war on us!"
        }
        assertEquals(NotificationCategory.Diplomacy, declaration.category)
        assertTrue(attackingCiv.civName in declaration.icons)

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

        assertTrue(defendingCiv.civID in game.attackEvents.single().knowsSource)
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

        val event = game.attackEvents.single()
        assertFalse(defendingCiv.civID in event.knowsTarget)
        assertTrue(event.targets.any { it.civId == defendingCiv.civID })
        val detonation = detonationReport(defendingCiv)
        assertFalse(attackingCiv.civName in detonation.icons)
        assertEquals(listOf(target.position), locations(detonation))
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
