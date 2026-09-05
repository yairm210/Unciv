package com.unciv.logic.battle

import com.badlogic.gdx.utils.JsonReader
import com.unciv.json.json
import com.unciv.logic.civilization.Notification
import com.unciv.logic.map.HexCoord
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.attackEventsForTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class AttackNotificationTest {
    private val testGame = TestGame().apply { makeHexagonalMap(7) }
    private val game = testGame.gameInfo
    private val attackingCiv = testGame.addCiv(testGame.ruleset.nations["Mongolia"]!!, isPlayer = true)
    private val defendingCiv = testGame.addCiv(testGame.ruleset.nations["Egypt"]!!, isPlayer = true)
    private val source = testGame.getTile(0, 0)
    private val target = testGame.getTile(3, 0)
    private val attacker = testGame.addUnit("Artillery", attackingCiv, source)
    private val defender = testGame.addUnit("Infantry", defendingCiv, target)

    init {
        testGame.addCity(attackingCiv, testGame.getTile(-6, 0))
        testGame.addCity(defendingCiv, testGame.getTile(6, 0))
        // Set the current player after introductions to avoid the UI tutorial's settings save.
        attackingCiv.diplomacyFunctions.makeCivilizationsMeet(defendingCiv)
        game.currentPlayer = attackingCiv.civID
        game.currentPlayerCiv = attackingCiv
        attacker.instanceName = "Secret battery"
        defender.instanceName = "C6 B Target"
        defendingCiv.viewableTiles = setOf(target)
    }

    private fun attack(): Notification {
        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))
        return defendingCiv.notifications.single { "[C6 B Target]" in it.text }
    }

    private fun locations(notification: Notification): List<HexCoord> =
        JsonReader().parse(json().toJson(notification)).get("actions").map {
            val location = it.get("LocationAction").get("location")
            if (location == null) HexCoord.Zero
            else HexCoord(location.getInt("x", 0), location.getInt("y", 0))
        }

    @Test
    fun `unseen artillery reports its type and damage but links only to the target`() {
        val notification = attack()

        assertTrue(notification.text.startsWith("An enemy [Artillery] has attacked [C6 B Target]"))
        assertTrue(notification.text.contains("[-${game.attackEventsForTesting.single().targets.single().damageReceived}] HP"))
        assertTrue("Artillery" in notification.icons)
        assertFalse("Mongolia" in notification.text)
        assertFalse("Mongolia" in notification.icons)
        assertFalse("Secret battery" in notification.text)
        assertEquals(listOf(target.position), locations(notification))
        assertFalse(defendingCiv.civID in game.attackEventsForTesting.single().attacker!!.knownBy)
    }

    @Test
    fun `declaring war reveals the civilization separately without revealing the firing position`() {
        attackingCiv.getDiplomacyManager(defendingCiv)!!.declareWar()
        defendingCiv.viewableTiles = setOf(target)

        val notification = attack()

        assertTrue(defendingCiv.notifications.any { it.text == "[Mongolia] has declared war on us!" })
        assertFalse("Mongolia" in notification.text)
        assertFalse("Mongolia" in notification.icons)
        assertEquals(listOf(target.position), locations(notification))
        assertFalse(defendingCiv.civID in game.attackEventsForTesting.single().knowsSource)
    }

    @Test
    fun `seeing or renaming the attacker later and reloading cannot expand a notification`() {
        val notification = attack()
        defendingCiv.viewableTiles = setOf(source, target)
        attacker.instanceName = "New battery name"
        attacker.destroy()
        game.attackEventsForTesting.clear() // Notifications also outlive the short-lived arrow history.

        val restored = json().fromJson(Notification::class.java, json().toJson(notification))

        assertEquals(notification.text, restored.text)
        assertEquals(notification.icons, restored.icons)
        assertEquals(listOf(target.position), locations(notification))
        assertEquals(listOf(target.position), locations(restored))
    }

    @Test
    fun `a witnessed firing position remains clickable after combat removes the observer`() {
        defendingCiv.viewableTiles = setOf(source, target)
        defender.health = 1

        val notification = attack()

        assertTrue(defender.isDestroyed)
        assertFalse(source in defendingCiv.viewableTiles)
        assertTrue(notification.text.contains("has destroyed"))
        assertEquals(listOf(target.position, source.position), locations(notification))
    }

    @Test
    fun `a visible tile does not identify an undetected invisible attacker`() {
        attacker.promotions.addPromotion(testGame.createUnitPromotion(UniqueType.Invisible.text).name)
        defendingCiv.viewableTiles = setOf(source, target)
        defendingCiv.viewableInvisibleUnitsTiles = emptySet()

        val notification = attack()

        assertTrue(notification.text.contains("[Artillery]"))
        assertEquals(listOf(target.position), locations(notification))
    }

    @Test
    fun `detecting an invisible attacker at attack time permits its firing position`() {
        attacker.promotions.addPromotion(testGame.createUnitPromotion(UniqueType.Invisible.text).name)
        defendingCiv.viewableTiles = setOf(source, target)
        defendingCiv.viewableInvisibleUnitsTiles = setOf(source)

        assertEquals(listOf(target.position, source.position), locations(attack()))
    }

    @Test
    fun `an unseen city bombardment does not reveal the city's name`() {
        attacker.destroy()
        val city = testGame.addCity(attackingCiv, source)
        city.name = "Secret city"
        defendingCiv.viewableTiles = setOf(target)

        Battle.attack(CityCombatant(city), MapUnitCombatant(defender))
        val notification = defendingCiv.notifications.single { "[C6 B Target]" in it.text }

        assertTrue(notification.text.startsWith("An enemy city has attacked"))
        assertFalse("Secret city" in notification.text)
        assertEquals(listOf(target.position), locations(notification))
    }

    @Test
    fun `a witnessed city bombardment retains the city name and location`() {
        attacker.destroy()
        val city = testGame.addCity(attackingCiv, source)
        city.name = "Observed city"
        defendingCiv.viewableTiles = setOf(source, target)

        Battle.attack(CityCombatant(city), MapUnitCombatant(defender))
        val notification = defendingCiv.notifications.single { "[C6 B Target]" in it.text }

        assertTrue(notification.text.startsWith("Enemy city [Observed city] has attacked"))
        assertEquals(listOf(target.position, source.position), locations(notification))
    }

    @Test
    fun `withdrawal does not reveal the attackers unseen source or a hidden retreat destination`() {
        attacker.destroy()
        defender.destroy()
        val melee = testGame.addUnit("Warrior", attackingCiv, source)
        val originalTarget = testGame.getTile(1, 0)
        val withdrawing = testGame.addDefaultMeleeUnitWithUniques(defendingCiv, originalTarget,
            UniqueType.WithdrawsBeforeMeleeCombat.text)
        defendingCiv.viewableTiles = setOf(originalTarget)
        attackingCiv.viewableTiles = setOf(source, originalTarget)

        Battle.attack(MapUnitCombatant(melee), MapUnitCombatant(withdrawing))

        assertEquals(AttackResolution.Withdrawn, game.attackEventsForTesting.single().resolution)
        val defenderNotification = defendingCiv.notifications.single { "withdrew" in it.text }
        val attackerNotification = attackingCiv.notifications.single { "withdrew" in it.text }
        assertEquals(listOf(withdrawing.getTile().position), locations(defenderNotification))
        assertEquals(listOf(originalTarget.position, source.position), locations(attackerNotification))
    }

    @Test
    fun `improvement destruction does not provide a second link to a hidden attacker`() {
        attacker.promotions.addPromotion(testGame.createUnitPromotion(UniqueType.DestroysImprovementUponAttack.text).name)
        target.improvement = "Farm"

        attack()
        val notification = defendingCiv.notifications.single { "tile improvement" in it.text }

        assertEquals(null, target.improvement)
        assertEquals(listOf(target.position), locations(notification))
    }
}
