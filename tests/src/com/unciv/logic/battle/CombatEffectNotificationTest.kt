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
import com.unciv.ui.components.UnitMovementMemoryType
import com.unciv.view.GameView
import com.unciv.view.ObservedWithdrawal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class CombatEffectNotificationTest {
    private val testGame = TestGame().apply { makeHexagonalMap(7) }
    private val game = testGame.gameInfo
    private val attackingCiv = testGame.addCiv(testGame.ruleset.nations["Mongolia"]!!, isPlayer = true)
    private val defendingCiv = testGame.addCiv(testGame.ruleset.nations["Egypt"]!!, isPlayer = true)
    private val source = testGame.getTile(0, 0)
    private val target = testGame.getTile(1, 0)
    private val retreat = testGame.getTile(2, 0)

    init {
        testGame.addCity(attackingCiv, testGame.getTile(-6, 0))
        testGame.addCity(defendingCiv, testGame.getTile(6, 0))
        attackingCiv.diplomacyFunctions.makeCivilizationsMeet(defendingCiv)
        game.currentPlayer = attackingCiv.civID
        game.currentPlayerCiv = attackingCiv
    }

    /** Leave one legal destination so the assertion does not depend on the withdrawal RNG. */
    private fun withdrawalUnits(vararg otherUniques: String): Pair<MapUnit, MapUnit> {
        val attacker = testGame.addUnit("Warrior", attackingCiv, source)
        val defender = testGame.addDefaultMeleeUnitWithUniques(defendingCiv, target,
            UniqueType.WithdrawsBeforeMeleeCombat.text, *otherUniques)
        for (tile in target.neighbors.filter { it != source && it != retreat })
            testGame.setTileTerrain(tile.position, "Mountain")
        attacker.instanceName = "Secret assault force"
        defender.instanceName = "Secret withdrawing force"
        return attacker to defender
    }

    @Test
    fun `a withdrawal onto a visible tile does not disclose an undetected destination`() {
        val (attacker, defender) = withdrawalUnits(UniqueType.InvisibleToNonAdjacent.text)
        attackingCiv.viewableTiles = testGame.tileMap.values.toSet()
        attackingCiv.viewableInvisibleUnitsTiles = emptySet()
        defendingCiv.viewableTiles = setOf(target)
        assertFalse(defender.isInvisible(attackingCiv)) // Adjacent before the withdrawal.
        val defenderMovement = defender.currentMovement

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        val event = game.attackEventsForTesting.single()
        assertEquals(AttackResolution.Withdrawn, event.resolution)
        assertEquals(retreat, defender.getTile())
        assertEquals(source, attacker.getTile())
        assertEquals(100, attacker.health)
        assertEquals(100, defender.health)
        assertEquals(1, attacker.attacksThisTurn)
        assertEquals(defenderMovement, defender.currentMovement, 0f)
        assertEquals(UnitMovementMemoryType.UnitWithdrew, defender.mostRecentMoveType)
        assertTrue(retreat in attackingCiv.viewableTiles)
        assertTrue(defender.isInvisible(attackingCiv))
        assertEquals(retreat.position, event.withdrawalDestination)
        assertFalse(attackingCiv.civID in event.withdrawalKnownBy)
        assertTrue(defendingCiv.civID in event.withdrawalKnownBy)
        assertEquals(AttackParticipantOutcome.Withdrew, event.targets.single().outcome)
        val attackerNotice = attackingCiv.notifications.single { "withdrew" in it.text }
        val defenderNotice = defendingCiv.notifications.single { "withdrew" in it.text }
        assertEquals("[${defender.name}] withdrew from a [Warrior]", attackerNotice.text)
        assertEquals(listOf(target.position, source.position), locations(attackerNotice))
        assertEquals(listOf(retreat.position), locations(defenderNotice))
        assertNoSecretIdentity(attackerNotice)
        assertNoSecretIdentity(defenderNotice)
    }

    @Test
    fun `witnessed withdrawal and original source remain available after units disappear`() {
        val (attacker, defender) = withdrawalUnits()
        attackingCiv.viewableTiles = testGame.tileMap.values.toSet()
        defendingCiv.viewableTiles = setOf(source, target)

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        val event = game.attackEventsForTesting.single()
        assertTrue(attackingCiv.civID in event.withdrawalKnownBy)
        val attackerNotice = attackingCiv.notifications.single { "withdrew" in it.text }
        val defenderNotice = defendingCiv.notifications.single { "withdrew" in it.text }
        attacker.destroy()
        defender.destroy()
        attackingCiv.viewableTiles = emptySet()
        defendingCiv.viewableTiles = emptySet()

        val expectedLocations = listOf(retreat.position, source.position)
        assertEquals(expectedLocations, locations(attackerNotice))
        assertEquals(expectedLocations, locations(defenderNotice))
        for (civ in listOf(attackingCiv, defendingCiv)) {
            val report = GameView(game, civ).attackEventsView.getCombatReports().single() as ObservedWithdrawal
            assertEquals(expectedLocations, report.locations)
        }
    }

    @Test
    fun `improvement destruction is reported to the original defender after civilian capture`() {
        val attacker = testGame.addUnit("Warrior", attackingCiv, source)
        attacker.promotions.addPromotion(testGame.createUnitPromotion(UniqueType.DestroysImprovementUponAttack.text).name)
        attacker.instanceName = "Secret assault force"
        val defender = testGame.addUnit("Worker", defendingCiv, target)
        defender.instanceName = "Secret withdrawing force"
        target.improvement = "Farm"
        defendingCiv.viewableTiles = setOf(target)

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        assertEquals(attackingCiv, defender.civ)
        assertNull(target.improvement)
        val event = game.attackEventsForTesting.single()
        assertEquals("Farm", event.destroyedImprovement)
        assertEquals(defendingCiv.civID, event.targets.single().civId)
        assertEquals(AttackParticipantOutcome.Captured, event.targets.single().outcome)
        assertTrue(attackingCiv.notifications.none { "tile improvement" in it.text })
        val notice = defendingCiv.notifications.single { "tile improvement" in it.text }
        assertEquals("An enemy [Warrior] has destroyed our tile improvement [Farm]", notice.text)
        assertEquals(listOf(target.position), locations(notice))
        assertNoSecretIdentity(notice)
    }

    @Test
    fun `improvement notice retains witnessed source when combat removes its observer`() {
        val attacker = testGame.addUnit("Artillery", attackingCiv, source)
        attacker.promotions.addPromotion(testGame.createUnitPromotion(UniqueType.DestroysImprovementUponAttack.text).name)
        val defender = testGame.addUnit("Warrior", defendingCiv, target).apply { health = 1 }
        target.improvement = "Farm"
        defendingCiv.viewableTiles = setOf(source, target)

        Battle.attack(MapUnitCombatant(attacker), MapUnitCombatant(defender))

        assertTrue(defender.isDestroyed)
        assertFalse(source in defendingCiv.viewableTiles)
        assertNull(target.improvement)
        val notice = defendingCiv.notifications.single { "tile improvement" in it.text }
        assertEquals(listOf(target.position, source.position), locations(notice))
        assertEquals("Farm", game.attackEventsForTesting.single().destroyedImprovement)
    }

    private fun assertNoSecretIdentity(notification: Notification) {
        for (secret in listOf("Secret assault force", "Secret withdrawing force", "Mongolia", "Egypt")) {
            assertFalse(notification.text.contains(secret))
            assertFalse(secret in notification.icons)
        }
    }

    private fun locations(notification: Notification): List<HexCoord> =
        JsonReader().parse(json().toJson(notification)).get("actions").map {
            val location = it.get("LocationAction").get("location")
            if (location == null) HexCoord.Zero
            else HexCoord(location.getInt("x", 0), location.getInt("y", 0))
        }
}
