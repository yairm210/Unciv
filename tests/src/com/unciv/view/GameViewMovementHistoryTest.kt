package com.unciv.view

import com.unciv.Constants
import com.unciv.logic.civilization.Civilization.HistoricalAttackMemory
import com.unciv.logic.map.mapunit.MapUnit.UnitMovementMemory
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.ui.components.UnitMovementMemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class GameViewMovementHistoryTest {
    private val testGame = TestGame().apply { makeHexagonalMap(2) }
    private val player = testGame.addCiv(testGame.ruleset.nations["Germany"]!!, isPlayer = true)
    private val enemy = testGame.addCiv(testGame.ruleset.nations["Egypt"]!!)
    private val spectator = testGame.addCiv(testGame.ruleset.nations[Constants.spectator]!!).apply {
        viewableTiles = testGame.tileMap.values.toSet()
        viewableInvisibleUnitsTiles = viewableTiles
    }
    private val playerView = GameView(testGame.gameInfo, player, spectatorMode = true)
    private val spectatorView = GameView(testGame.gameInfo, spectator, spectatorMode = true)

    @Test
    fun `foreign movement history requires every remembered and current tile to be visible`() {
        val origin = testGame.getTile(0, 0)
        val previous = testGame.getTile(0, 1)
        val current = testGame.getTile(1, 1)
        val unit = testGame.addUnit("Warrior", enemy, current)
        unit.movementMemories = arrayListOf(
            UnitMovementMemory(origin.position, UnitMovementMemoryType.UnitMoved),
            UnitMovementMemory(previous.position, UnitMovementMemoryType.UnitMoved)
        )
        val pathTiles = setOf(origin, previous, current)
        for (tile in pathTiles) tile.setExplored(player, true)

        for (foggedTile in pathTiles) {
            player.viewableTiles = pathTiles - foggedTile
            assertTrue("History must stay hidden when ${foggedTile.position} is fogged",
                playerView.getUnitsWithVisibleMovementHistory().none())
            assertSame(unit, spectatorView.getUnitsWithVisibleMovementHistory().single().getUnit())
        }

        player.viewableTiles = pathTiles
        val visibleUnit = playerView.getUnitsWithVisibleMovementHistory().single()
        assertSame(unit, visibleUnit.getUnit())
        assertSame(playerView, visibleUnit.gameView)
        assertTrue(visibleUnit.spectatorMode)
    }

    @Test
    fun `invisible foreign movement history requires detection on the current tile`() {
        val origin = testGame.getTile(0, 0)
        val current = testGame.getTile(1, 0)
        val unit = testGame.addDefaultMeleeUnitWithUniques(enemy, current, UniqueType.Invisible.text)
        unit.movementMemories = arrayListOf(
            UnitMovementMemory(origin.position, UnitMovementMemoryType.UnitMoved)
        )
        player.viewableTiles = setOf(origin, current)

        assertTrue(playerView.getUnitsWithVisibleMovementHistory().none())
        assertSame(unit, spectatorView.getUnitsWithVisibleMovementHistory().single().getUnit())

        player.viewableInvisibleUnitsTiles = setOf(origin)
        assertTrue(playerView.getUnitsWithVisibleMovementHistory().none())

        player.viewableInvisibleUnitsTiles = setOf(current)
        assertSame(unit, playerView.getUnitsWithVisibleMovementHistory().single().getUnit())
    }

    @Test
    fun `own movement history stays visible after its old tiles leave sight`() {
        val origin = testGame.getTile(0, 0)
        val current = testGame.getTile(1, 0)
        val unit = testGame.addUnit("Warrior", player, current)
        unit.movementMemories = arrayListOf(
            UnitMovementMemory(origin.position, UnitMovementMemoryType.UnitMoved)
        )
        player.viewableTiles = setOf(current)

        assertSame(unit, playerView.getUnitsWithVisibleMovementHistory().single().getUnit())
    }

    @Test
    fun `foreign live and stored attacks are visible when either endpoint is in sight`() {
        val unitSource = testGame.getTile(0, 0)
        val unitTarget = testGame.getTile(1, 0)
        val storedSource = testGame.getTile(0, 1)
        val storedTarget = testGame.getTile(1, 1)
        val unit = testGame.addUnit("Warrior", enemy, unitSource)
        unit.attacksSinceTurnStart.add(unitTarget.position)
        enemy.attacksSinceTurnStart.add(HistoricalAttackMemory(null, storedSource.position, storedTarget.position))
        val liveAttack = unitSource.position to unitTarget.position
        val storedAttack = storedSource.position to storedTarget.position
        for (tile in listOf(unitSource, unitTarget, storedSource, storedTarget)) tile.setExplored(player, true)

        player.viewableTiles = emptySet()
        assertTrue(playerView.getVisibleAttacks().none())
        assertEquals(setOf(liveAttack, storedAttack), spectatorView.getVisibleAttacks().toSet())

        for (visibleTile in listOf(unitSource, unitTarget)) {
            player.viewableTiles = setOf(visibleTile)
            assertEquals(listOf(liveAttack), playerView.getVisibleAttacks().toList())
        }
        for (visibleTile in listOf(storedSource, storedTarget)) {
            player.viewableTiles = setOf(visibleTile)
            assertEquals(listOf(storedAttack), playerView.getVisibleAttacks().toList())
        }
    }

    @Test
    fun `own live and stored attacks stay visible when neither endpoint is in sight`() {
        val unitSource = testGame.getTile(0, 0)
        val unitTarget = testGame.getTile(1, 0)
        val storedSource = testGame.getTile(0, 1)
        val storedTarget = testGame.getTile(1, 1)
        val unit = testGame.addUnit("Warrior", player, unitSource)
        unit.attacksSinceTurnStart.add(unitTarget.position)
        player.attacksSinceTurnStart.add(HistoricalAttackMemory("Archer", storedSource.position, storedTarget.position))
        player.viewableTiles = emptySet()

        assertEquals(
            setOf(unitSource.position to unitTarget.position, storedSource.position to storedTarget.position),
            playerView.getVisibleAttacks().toSet()
        )
    }
}
