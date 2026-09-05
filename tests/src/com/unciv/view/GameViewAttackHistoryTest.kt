package com.unciv.view

import com.unciv.Constants
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.attackEventsForTesting
import com.unciv.testing.recordAttackForTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class GameViewAttackHistoryTest {
    private val testGame = TestGame().apply { makeHexagonalMap(3) }
    private val player = testGame.addCiv(testGame.ruleset.nations["Germany"]!!, isPlayer = true)
    private val enemy = testGame.addCiv(testGame.ruleset.nations["Egypt"]!!)
    private val spectator = testGame.addCiv(testGame.ruleset.nations[Constants.spectator]!!)
    private val source = testGame.getTile(0, 0)
    private val target = testGame.getTile(1, 0)
    private val attacker = testGame.addUnit("Archer", enemy, source)
    private val playerView = GameView(testGame.gameInfo, player)

    private fun recordAttack() {
        testGame.gameInfo.recordAttackForTesting(MapUnitCombatant(attacker), target)
    }

    @Test
    fun `each observed endpoint is exposed independently`() {
        val sightSets = listOf(emptySet(), setOf(source), setOf(target), setOf(source, target))
        for (sight in sightSets) {
            testGame.gameInfo.attackEventsForTesting.clear()
            player.viewableTiles = sight
            recordAttack()
            val observed = playerView.attackEventsView.getObservedAttacks()
            if (sight.isEmpty()) {
                assertTrue(observed.isEmpty())
            } else {
                assertEquals(1, observed.size)
                assertEquals(source.position.takeIf { source in sight }, observed.single().source)
                assertEquals(target.position.takeIf { target in sight }, observed.single().target)
            }
        }
    }

    @Test
    fun `exploration and later visibility do not reveal an unwitnessed attack`() {
        source.setExplored(player, true)
        target.setExplored(player, true)
        player.viewableTiles = emptySet()
        recordAttack()

        assertTrue(playerView.attackEventsView.getObservedAttacks().none())
        player.viewableTiles = setOf(source, target)
        assertTrue(playerView.attackEventsView.getObservedAttacks().none())
    }

    @Test
    fun `seeing the origin later does not expand a target-only memory`() {
        player.viewableTiles = setOf(target)
        recordAttack()
        player.viewableTiles = setOf(source, target)

        val observed = playerView.attackEventsView.getObservedAttacks().single()
        assertNull(observed.source)
        assertEquals(target.position, observed.target)
    }

    @Test
    fun `witnessed coordinates survive retreat destruction and loss of sight`() {
        player.viewableTiles = setOf(source, target)
        testGame.gameInfo.turns = 17
        recordAttack()
        attacker.movement.moveToTile(testGame.getTile(-1, 0))
        attacker.destroy()
        player.viewableTiles = emptySet()

        val observed = playerView.attackEventsView.getObservedAttacks().single()
        assertEquals(17, observed.turn)
        assertEquals(source.position, observed.source)
        assertEquals(target.position, observed.target)
        assertEquals(1, testGame.gameInfo.attackEventsForTesting.size)
    }

    @Test
    fun `invisible attacker does not reveal its source without detection`() {
        attacker.destroy()
        val invisibleAttacker = testGame.addDefaultMeleeUnitWithUniques(enemy, source, UniqueType.Invisible.text)
        player.viewableTiles = setOf(source, target)
        player.viewableInvisibleUnitsTiles = emptySet()
        testGame.gameInfo.recordAttackForTesting(MapUnitCombatant(invisibleAttacker), target)
        assertNull(playerView.attackEventsView.getObservedAttacks().single().source)

        // Detection after the attack must not expand the existing record.
        player.viewableInvisibleUnitsTiles = setOf(source)
        assertNull(playerView.attackEventsView.getObservedAttacks().single().source)
        testGame.gameInfo.recordAttackForTesting(MapUnitCombatant(invisibleAttacker), target)
        assertEquals(source.position, playerView.attackEventsView.getObservedAttacks().last().source)
    }

    @Test
    fun `own attacks retain their intended endpoints without current visibility`() {
        enemy.viewableTiles = emptySet()
        recordAttack()

        val observed = GameView(testGame.gameInfo, enemy).attackEventsView.getObservedAttacks().single()
        assertEquals(source.position, observed.source)
        assertEquals(target.position, observed.target)
    }

    @Test
    fun `spectator perspective switches use recorded civilization knowledge`() {
        player.viewableTiles = setOf(target)
        recordAttack()
        val restrictedView = GameView(testGame.gameInfo, player, spectatorMode = true)
        val unrestrictedView = GameView(testGame.gameInfo, spectator, spectatorMode = true)

        assertNull(restrictedView.attackEventsView.getObservedAttacks().single().source)
        assertEquals(source.position, unrestrictedView.attackEventsView.getObservedAttacks().single().source)
        assertEquals(target.position, unrestrictedView.attackEventsView.getObservedAttacks().single().target)
        assertNull(restrictedView.attackEventsView.getObservedAttacks().single().source)
    }

    @Test
    fun `knowledge belongs to stable civilization IDs not display names`() {
        player.viewableTiles = setOf(target)
        recordAttack()
        val sameNation = testGame.addCiv(testGame.ruleset.nations["Germany"]!!)
        sameNation.civID = "Germany-2"
        sameNation.viewableTiles = setOf(source, target)

        assertEquals(target.position, playerView.attackEventsView.getObservedAttacks().single().target)
        assertTrue(GameView(testGame.gameInfo, sameNation).attackEventsView.getObservedAttacks().none())
    }
}
