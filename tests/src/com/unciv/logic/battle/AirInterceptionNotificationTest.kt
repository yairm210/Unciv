package com.unciv.logic.battle

import com.badlogic.gdx.utils.JsonReader
import com.unciv.json.json
import com.unciv.logic.civilization.Notification
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.GameContext
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
class AirInterceptionNotificationTest {
    private val testGame = TestGame().apply { makeHexagonalMap(7) }
    private val attackingCiv = testGame.addCiv(isPlayer = true)
    private val defendingCiv = testGame.addCiv(isPlayer = true)
    private val attackerBase = testGame.addCity(attackingCiv, testGame.getTile(-5, 0)).getCenterTile()
    private val interceptorBase = testGame.addCity(defendingCiv, testGame.getTile(5, 0)).getCenterTile()
    private val target = testGame.getTile(1, 0)

    init {
        attackingCiv.diplomacyFunctions.makeCivilizationsMeet(defendingCiv)
        attackingCiv.getDiplomacyManager(defendingCiv)!!.declareWar()
        attackingCiv.notifications.clear()
        defendingCiv.notifications.clear()
    }

    @Test
    fun `interception reports unit types and damage without disclosing either hidden base`() {
        val bomber = testGame.addUnit("Bomber", attackingCiv, attackerBase)
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        addInterceptor()
        hideForeignBases()

        Battle.attack(MapUnitCombatant(bomber), MapUnitCombatant(defender))

        val attackingNotice = attackingCiv.notifications.single { "intercepting" in it.text }
        val defendingNotice = defendingCiv.notifications.single { "intercepted" in it.text }
        assertTrue(attackingNotice.text.contains("[Fighter]"))
        assertTrue(attackingNotice.text.contains("HP"))
        assertTrue(defendingNotice.text.contains("[Bomber]"))
        assertEquals(setOf(target.position, attackerBase.position), locations(attackingNotice).toSet())
        assertEquals(setOf(target.position, interceptorBase.position), locations(defendingNotice).toSet())
        assertFalse(attackingNotice.icons.contains(defendingCiv.civName))
        assertFalse(defendingNotice.icons.contains(attackingCiv.civName))

        // A saved notification must retain the knowledge available at the interception.
        attackingCiv.viewableTiles = setOf(attackerBase, target, interceptorBase)
        defendingCiv.viewableTiles = attackingCiv.viewableTiles
        val saved = json().fromJson(Notification::class.java, json().toJson(defendingNotice))
        assertEquals(setOf(target.position, interceptorBase.position), locations(saved).toSet())
    }

    @Test
    fun `fatal interception still names the hidden interceptor without revealing its base`() {
        val bomber = testGame.addUnit("Bomber", attackingCiv, attackerBase)
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        addInterceptor()
        bomber.health = 1
        hideForeignBases()

        Battle.attack(MapUnitCombatant(bomber), MapUnitCombatant(defender))

        assertTrue(bomber.isDestroyed)
        val notice = attackingCiv.notifications.single { "was destroyed" in it.text }
        assertTrue(notice.text.contains("destroyed by an intercepting [Fighter]"))
        assertTrue(notice.icons.contains("Fighter"))
        assertEquals(setOf(target.position, attackerBase.position), locations(notice).toSet())
    }

    @Test
    fun `an observed interceptor remains in the notification after bomber destruction removes visibility`() {
        val bomber = testGame.addUnit("Bomber", attackingCiv, attackerBase)
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        addInterceptor()
        bomber.health = 1
        attackingCiv.viewableTiles = setOf(attackerBase, target, interceptorBase)
        defendingCiv.viewableTiles = setOf(interceptorBase, target, attackerBase)

        Battle.attack(MapUnitCombatant(bomber), MapUnitCombatant(defender))

        assertTrue(bomber.isDestroyed)
        val attackingNotice = attackingCiv.notifications.single { "was destroyed" in it.text }
        val defendingNotice = defendingCiv.notifications.single { "intercepted" in it.text }
        val allPositions = setOf(target.position, attackerBase.position, interceptorBase.position)
        assertEquals(allPositions, locations(attackingNotice).toSet())
        assertEquals(allPositions, locations(defendingNotice).toSet())
    }

    @Test
    fun `seeing the base tile does not reveal an undetected interceptor's position`() {
        val bomber = testGame.addUnit("Bomber", attackingCiv, attackerBase)
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        val interceptor = addInterceptor()
        interceptor.promotions.addPromotion(testGame.createUnitPromotion(UniqueType.Invisible.text).name)
        attackingCiv.viewableTiles = setOf(attackerBase, target, interceptorBase)
        attackingCiv.viewableInvisibleUnitsTiles = emptySet()
        defendingCiv.viewableTiles = setOf(interceptorBase, target)

        Battle.attack(MapUnitCombatant(bomber), MapUnitCombatant(defender))

        val notice = attackingCiv.notifications.single { "intercepting" in it.text }
        assertTrue(notice.text.contains("[Fighter]"))
        assertFalse(locations(notice).contains(interceptorBase.position))
    }

    @Test
    fun `land interception of an air sweep reveals neither unseen base`() {
        val sweeper = testGame.addUnit("Fighter", attackingCiv, attackerBase)
        val interceptorTile = testGame.getTile(3, 0)
        testGame.addUnit("Anti-Aircraft Gun", defendingCiv, interceptorTile)
        hideForeignBases()
        defendingCiv.viewableTiles = setOf(interceptorBase, interceptorTile, target)

        AirInterception.airSweep(MapUnitCombatant(sweeper), target)

        val attackingNotice = attackingCiv.notifications.single { "intercepting" in it.text }
        val defendingNotice = defendingCiv.notifications.single { "intercepted" in it.text }
        assertTrue(attackingNotice.text.contains("[Anti-Aircraft Gun]"))
        assertEquals(setOf(target.position, attackerBase.position), locations(attackingNotice).toSet())
        assertEquals(setOf(target.position, interceptorTile.position), locations(defendingNotice).toSet())
    }

    @Test
    fun `fatal air sweep interception keeps type information while hiding bases`() {
        val sweeper = testGame.addUnit("Fighter", attackingCiv, attackerBase)
        addInterceptor()
        sweeper.health = 1
        hideForeignBases()

        AirInterception.airSweep(MapUnitCombatant(sweeper), target)

        assertTrue(sweeper.isDestroyed)
        val attackingNotice = attackingCiv.notifications.single { "was destroyed" in it.text }
        val defendingNotice = defendingCiv.notifications.single { "intercepted" in it.text }
        assertTrue(attackingNotice.text.contains("destroyed by an intercepting [Fighter]"))
        assertTrue(attackingNotice.icons.contains("Fighter"))
        assertEquals(setOf(target.position, attackerBase.position), locations(attackingNotice).toSet())
        assertEquals(setOf(target.position, interceptorBase.position), locations(defendingNotice).toSet())
    }

    @Test
    fun `sweep notifications use engagement damage including loss triggers without subtracting healing`() {
        val sweeper = testGame.addUnit("Fighter", attackingCiv, attackerBase)
        val interceptor = addInterceptor()
        interceptor.promotions.addPromotion(testGame.createUnitPromotion(
            "[This Unit] takes [10] damage <upon losing at least [1] HP in a single attack>",
            "[This Unit] heals [10] HP <upon losing at least [1] HP in a single attack>"
        ).name)
        hideForeignBases()

        AirInterception.airSweep(MapUnitCombatant(sweeper), target)

        val interception = testGame.gameInfo.attackEventsForTesting.single().interceptions.single()
        assertEquals(100 - interceptor.health + 10, interception.damageToInterceptor)
        val attackingNotice = attackingCiv.notifications.single { "intercepting" in it.text }
        val defendingNotice = defendingCiv.notifications.single { "intercepted" in it.text }
        assertEquals(
            "Our [Fighter] ([-${interception.damageToAttacker}] HP) was attacked by an intercepting [Fighter] ([-${interception.damageToInterceptor}] HP)",
            attackingNotice.text
        )
        assertEquals(
            "Our [Fighter] ([-${interception.damageToInterceptor}] HP) intercepted and attacked an enemy [Fighter] ([-${interception.damageToAttacker}] HP)",
            defendingNotice.text
        )
    }

    @Test
    fun `sweep notifications report destruction even when the removed fighter has positive HP`() {
        val sweeper = testGame.addUnit("Fighter", attackingCiv, attackerBase)
        val interceptor = addInterceptor()
        interceptor.promotions.addPromotion(testGame.createUnitPromotion(
            "[This Unit] is destroyed <upon losing at least [1] HP in a single attack>"
        ).name)
        hideForeignBases()

        AirInterception.airSweep(MapUnitCombatant(sweeper), target)

        assertTrue(interceptor.isDestroyed)
        assertTrue(interceptor.health > 0)
        val interception = testGame.gameInfo.attackEventsForTesting.single().interceptions.single()
        assertEquals(AttackParticipantOutcome.Destroyed, interception.interceptorOutcome)
        assertEquals(100 - interceptor.health, interception.damageToInterceptor)
        assertTrue(attackingCiv.notifications.single { "intercepting" in it.text }.text
            .contains("destroyed an intercepting [Fighter]"))
        assertTrue(defendingCiv.notifications.single { "intercepted" in it.text }.text
            .contains("intercepted and was destroyed"))
    }

    @Test
    fun `sweep notifications acknowledge both fighters being destroyed`() {
        val sweeper = testGame.addUnit("Fighter", attackingCiv, attackerBase)
        val interceptor = addInterceptor()
        val destruction = testGame.createUnitPromotion(
            "[This Unit] is destroyed <upon losing at least [1] HP in a single attack>"
        )
        sweeper.promotions.addPromotion(destruction.name)
        interceptor.promotions.addPromotion(destruction.name)
        hideForeignBases()

        AirInterception.airSweep(MapUnitCombatant(sweeper), target)

        assertTrue(sweeper.isDestroyed)
        assertTrue(interceptor.isDestroyed)
        val interception = testGame.gameInfo.attackEventsForTesting.single().interceptions.single()
        assertEquals(AttackParticipantOutcome.Destroyed, interception.attackerOutcome)
        assertEquals(AttackParticipantOutcome.Destroyed, interception.interceptorOutcome)
        assertTrue(attackingCiv.notifications.single { "both destroyed" in it.text }.text.contains("intercepting"))
        assertTrue(defendingCiv.notifications.single { "both destroyed" in it.text }.text.contains("during interception"))
    }

    @Test
    fun `missed interception remains silent and does not disclose the unseen fighter`() {
        val bomber = testGame.addUnit("Bomber", attackingCiv, attackerBase)
        val defender = testGame.addUnit("Warrior", defendingCiv, target)
        val interceptor = testGame.addUnit("Fighter", defendingCiv, interceptorBase)
        interceptor.promotions.addPromotion(testGame.createUnitPromotion("[-99]% chance to intercept air attacks").name)
        val state = GameContext(MapUnitCombatant(bomber), MapUnitCombatant(defender), target, CombatAction.Intercept)
        while (state.stateBasedRandom("AirInterception.tryInterceptAirAttack").nextFloat() <= 0.01f)
            testGame.gameInfo.turns++
        hideForeignBases()

        Battle.attack(MapUnitCombatant(bomber), MapUnitCombatant(defender))

        assertEquals(1, interceptor.attacksThisTurn)
        assertFalse(testGame.gameInfo.attackEventsForTesting.single().interceptions.single().intercepted)
        assertTrue(attackingCiv.notifications.none { "intercept" in it.text || "Fighter" in it.icons })
        assertTrue(defendingCiv.notifications.none { "intercept" in it.text })
    }

    @Test
    fun `unopposed sweep retains the existing notification wording`() {
        val sweeper = testGame.addUnit("Fighter", attackingCiv, attackerBase)

        AirInterception.airSweep(MapUnitCombatant(sweeper), target)

        assertEquals("Nothing tried to intercept our [Fighter]", attackingCiv.notifications.single().text)
    }

    private fun addInterceptor(): MapUnit = testGame.addUnit("Fighter", defendingCiv, interceptorBase).apply {
        promotions.addPromotion(testGame.createUnitPromotion("[100]% chance to intercept air attacks").name)
    }

    private fun hideForeignBases() {
        attackingCiv.viewableTiles = setOf(attackerBase, target)
        defendingCiv.viewableTiles = setOf(interceptorBase, target)
    }

    private fun locations(notification: Notification): List<HexCoord> =
        JsonReader().parse(json().toJson(notification)).get("actions").map {
            val location = it.get("LocationAction").get("location")
            HexCoord(location.getInt("x", 0), location.getInt("y", 0))
        }
}
