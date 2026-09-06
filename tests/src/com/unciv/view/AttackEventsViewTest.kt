package com.unciv.view

import com.unciv.Constants
import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.managers.TurnManager
import com.unciv.logic.map.HexCoord
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.attackEventsForTesting
import com.unciv.testing.recordAttackForTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class AttackEventsViewTest {
    private val testGame = TestGame().apply { makeHexagonalMap(5) }
    private val game = testGame.gameInfo
    private val player = testGame.addCiv(testGame.ruleset.nations["Germany"]!!, isPlayer = true)
    private val enemy = testGame.addCiv(testGame.ruleset.nations["Egypt"]!!)
    private val spectator = testGame.addCiv(testGame.ruleset.nations[Constants.spectator]!!)
    private val source = testGame.getTile(0, 0)
    private val target = testGame.getTile(1, 0)
    private val attacker = testGame.addUnit("Archer", enemy, source)
    private val playerView = GameView(game, player)

    init {
        testGame.addCity(player, testGame.getTile(-4, 0))
        testGame.addCity(enemy, testGame.getTile(4, 0))
        // Moving a unit must not trigger the UI tutorial's settings save in this fixture.
        player.diplomacyFunctions.makeCivilizationsMeet(enemy)
        game.currentPlayer = player.civID
        game.currentPlayerCiv = player
        game.turns = 17
    }

    @Test
    fun `returned lists capture their values when requested`() {
        player.viewableTiles = setOf(target)
        val event = game.recordAttackForTesting(MapUnitCombatant(attacker), target)
        val expected = listOf(ObservedAttack(17, null, target.position))
        val snapshot = playerView.attackEventsView.getObservedAttacks()

        event.turn = 18
        event.source = HexCoord(-2, 0)
        event.target = HexCoord(2, 0)
        event.knowsSource.add(player.civID)
        event.knowsTarget.clear()
        game.turns = 18
        game.recordAttackForTesting(MapUnitCombatant(attacker), target)

        assertEquals(expected, snapshot)
        assertEquals(listOf(
            ObservedAttack(18, HexCoord(-2, 0), null),
            ObservedAttack(18, null, target.position)
        ), playerView.attackEventsView.getObservedAttacks())
    }

    @Test
    fun `expiry removes future observations without changing retained results`() {
        player.viewableTiles = setOf(source, target)
        game.recordAttackForTesting(MapUnitCombatant(attacker), target)
        val snapshot = playerView.attackEventsView.getObservedAttacks()
        val expected = listOf(ObservedAttack(17, source.position, target.position))

        game.turns++
        TurnManager(enemy).startTurn()

        assertTrue(game.attackEventsForTesting.isEmpty())
        assertTrue(playerView.attackEventsView.getObservedAttacks().isEmpty())
        assertEquals(expected, snapshot)
    }

    @Test
    fun `cloned and deserialized games bind views to their own independent filtered history`() {
        player.viewableTiles = setOf(target)
        val event = game.recordAttackForTesting(MapUnitCombatant(attacker), target)
        val clone = game.clone()
        val restored = json().fromJson(GameInfo::class.java, json().toJson(game))
        fun viewOf(copy: GameInfo): GameView {
            // Only the viewer and ruleset transients are needed to construct these Views.
            copy.ruleset = game.ruleset
            val copiedPlayer = copy.civilizations.single { it.civID == player.civID }
            copiedPlayer.gameInfo = copy
            copiedPlayer.nation = player.nation
            return GameView(copy, copiedPlayer)
        }
        val clonedView = viewOf(clone).attackEventsView
        val restoredView = viewOf(restored).attackEventsView
        val partial = listOf(ObservedAttack(17, null, target.position))
        assertEquals(partial, clonedView.getObservedAttacks())
        assertEquals(partial, restoredView.getObservedAttacks())

        event.knowsSource.add(player.civID)
        event.target = HexCoord(2, 0)
        val changedOriginal = listOf(ObservedAttack(17, source.position, HexCoord(2, 0)))
        assertEquals(changedOriginal, playerView.attackEventsView.getObservedAttacks())
        assertEquals(partial, clonedView.getObservedAttacks())
        assertEquals(partial, restoredView.getObservedAttacks())

        clone.attackEventsForTesting.single().apply {
            knowsSource.add(player.civID)
            knowsTarget.clear()
            source = HexCoord(-2, 0)
        }
        val changedClone = listOf(ObservedAttack(17, HexCoord(-2, 0), null))
        assertEquals(changedClone, clonedView.getObservedAttacks())
        assertEquals(changedOriginal, playerView.attackEventsView.getObservedAttacks())
        assertEquals(partial, restoredView.getObservedAttacks())

        restored.attackEventsForTesting.clear()
        assertTrue(restoredView.getObservedAttacks().isEmpty())
        assertEquals(changedClone, clonedView.getObservedAttacks())
        assertEquals(changedOriginal, playerView.attackEventsView.getObservedAttacks())
    }

    @Test
    fun `retained results keep their bound perspective while other perspectives are queried`() {
        player.viewableTiles = setOf(target)
        game.recordAttackForTesting(MapUnitCombatant(attacker), target)
        val normal = playerView.attackEventsView.getObservedAttacks()
        val asCivilization = GameView(game, player, spectatorMode = true)
        val unrestricted = GameView(game, spectator, spectatorMode = true)
        val restrictedSnapshot = asCivilization.attackEventsView.getObservedAttacks()
        val unrestrictedSnapshot = unrestricted.attackEventsView.getObservedAttacks()

        // Switching perspective and gaining sight must not upgrade previously returned observations.
        player.viewableTiles = setOf(source, target)
        val restrictedAgain = asCivilization.attackEventsView.getObservedAttacks()
        val partial = listOf(ObservedAttack(17, null, target.position))
        val complete = listOf(ObservedAttack(17, source.position, target.position))

        assertEquals(partial, normal)
        assertEquals(partial, restrictedSnapshot)
        assertEquals(partial, restrictedAgain)
        assertEquals(complete, unrestrictedSnapshot)
        assertEquals(complete, unrestricted.attackEventsView.getObservedAttacks())
    }

    @Test
    fun `mutating a returned collection or copying a result cannot change engine records`() {
        player.viewableTiles = setOf(target)
        game.recordAttackForTesting(MapUnitCombatant(attacker), target)
        game.recordAttackForTesting(MapUnitCombatant(attacker), target)
        val snapshot = playerView.attackEventsView.getObservedAttacks()
        val expected = listOf(
            ObservedAttack(17, null, target.position),
            ObservedAttack(17, null, target.position)
        )
        val fabricated = snapshot.first().copy(turn = 99, source = HexCoord(-4, 0), target = null)
        try {
            @Suppress("UNCHECKED_CAST")
            val mutable = snapshot as MutableList<ObservedAttack>
            mutable.clear()
            mutable.add(fabricated)
        } catch (_: UnsupportedOperationException) {
            // An unmodifiable detached collection also satisfies the boundary.
        } catch (_: ClassCastException) {
            // A Kotlin implementation may reject the mutable collection cast itself.
        }

        assertEquals(2, game.attackEventsForTesting.size)
        for (event in game.attackEventsForTesting) {
            assertEquals(17, event.turn)
            assertEquals(source.position, event.source)
            assertEquals(target.position, event.target)
        }
        assertEquals(expected, playerView.attackEventsView.getObservedAttacks())
    }

    @Test
    fun `query retains known unit associations and hides later discovered attackers`() {
        player.viewableTiles = setOf(source, target)
        game.recordAttackForTesting(MapUnitCombatant(attacker), target)
        attacker.movement.moveToTile(testGame.getTile(-1, 0))
        attacker.instanceName = "Renamed witnessed archers"

        val hiddenSource = testGame.getTile(0, 2)
        val hiddenTarget = testGame.getTile(1, 2)
        val hiddenAttacker = testGame.addUnit("Archer", enemy, hiddenSource)
        player.viewableTiles = setOf(hiddenTarget)
        game.recordAttackForTesting(MapUnitCombatant(hiddenAttacker), hiddenTarget)
        hiddenAttacker.movement.moveToTile(testGame.getTile(-1, 2))
        player.viewableTiles = game.tileMap.values.toSet()

        val selectedKnown = playerView.getMapUnitView(attacker)
        val selectedHidden = playerView.getMapUnitView(hiddenAttacker)
        val expected = listOf(ObservedAttack(17, source.position, target.position))
        assertEquals(expected, playerView.attackEventsView.getObservedAttacks(selectedKnown))
        assertTrue(playerView.attackEventsView.getObservedAttacks(selectedHidden).isEmpty())
        assertEquals(2, playerView.attackEventsView.getObservedAttacks().size)
    }
}
