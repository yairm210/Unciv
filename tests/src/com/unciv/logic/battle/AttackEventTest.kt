package com.unciv.logic.battle

import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.managers.TurnManager
import com.unciv.logic.map.HexCoord
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.attackEventsForTesting
import com.unciv.testing.recordAttackForTesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class AttackEventTest {
    private val testGame = TestGame().apply { makeHexagonalMap(5) }
    private val game = testGame.gameInfo
    private val attackingCiv = testGame.addCiv(isPlayer = true)
    private val defendingCiv = testGame.addCiv()
    private val source = testGame.getTile(0, 0)
    private val target = testGame.getTile(1, 0)
    private val archer = testGame.addUnit("Archer", attackingCiv, source)
    private val defender = testGame.addUnit("Warrior", defendingCiv, target)

    init {
        // Keep combat from eliminating a test civilization and destroying its remaining units.
        testGame.addCity(attackingCiv, testGame.getTile(-4, 0))
        testGame.addCity(defendingCiv, testGame.getTile(4, 0))
        game.currentPlayer = attackingCiv.civID
        game.currentPlayerCiv = attackingCiv
    }

    @Test
    fun `real combat records original coordinates before a melee attacker advances`() {
        archer.destroy()
        val warrior = testGame.addUnit("Warrior", attackingCiv, source)
        defender.health = 1
        defendingCiv.viewableTiles = setOf(source, target)
        game.turns = 8

        Battle.attack(MapUnitCombatant(warrior), MapUnitCombatant(defender))

        assertEquals(target, warrior.getTile())
        val event = game.attackEventsForTesting.single()
        assertEquals(8, event.turn)
        assertEquals(source.position, event.source)
        assertEquals(target.position, event.target)
        assertTrue(defendingCiv.civID in event.knowsSource)
        assertTrue(defendingCiv.civID in event.knowsTarget)
    }

    @Test
    fun `city bombardment is recorded once and survives the loss of the city`() {
        val city = attackingCiv.cities.single()
        Battle.attack(CityCombatant(city), MapUnitCombatant(defender))
        val position = city.location
        city.destroyCity(overrideSafeties = true)

        val event = game.attackEventsForTesting.single()
        assertEquals(position, event.source)
        assertEquals(target.position, event.target)
        assertEquals(attackingCiv.civID, event.attackingCivId)
    }

    @Test
    fun `history expires on its attacker next turn rather than another civilization turn`() {
        game.recordAttackForTesting(MapUnitCombatant(archer), target)
        game.recordAttackForTesting(MapUnitCombatant(defender), source)
        game.turns++

        TurnManager(defendingCiv).startTurn()
        assertEquals(attackingCiv.civID, game.attackEventsForTesting.single().attackingCivId)
        TurnManager(attackingCiv).startTurn()
        assertTrue(game.attackEventsForTesting.isEmpty())
    }

    @Test
    fun `cloning preserves attack history without sharing mutable knowledge or lists`() {
        defendingCiv.viewableTiles = setOf(target)
        game.recordAttackForTesting(MapUnitCombatant(archer), target)
        val clone = game.clone()
        val event = game.attackEventsForTesting.single()
        val clonedEvent = clone.attackEventsForTesting.single()

        assertNotSame(event, clonedEvent)
        assertEquals(event.knowsSource, clonedEvent.knowsSource)
        assertEquals(event.knowsTarget, clonedEvent.knowsTarget)
        clonedEvent.knowsSource.add(defendingCiv.civID)
        clonedEvent.knowsTarget.clear()
        clone.attackEventsForTesting.clear()

        assertFalse(defendingCiv.civID in event.knowsSource)
        assertTrue(defendingCiv.civID in event.knowsTarget)
        assertEquals(1, game.attackEventsForTesting.size)
    }

    @Test
    fun `undo checkpoint retains precisely the events and knowledge from before an attack`() {
        defendingCiv.viewableTiles = setOf(target)
        game.recordAttackForTesting(MapUnitCombatant(archer), target)
        // UndoHandler stores and restores a GameInfo clone.
        val checkpoint = game.clone()
        defendingCiv.viewableTiles = setOf(source, target)
        Battle.attack(MapUnitCombatant(archer), MapUnitCombatant(defender))

        assertEquals(2, game.attackEventsForTesting.size)
        assertEquals(1, checkpoint.attackEventsForTesting.size)
        assertFalse(defendingCiv.civID in checkpoint.attackEventsForTesting.single().knowsSource)
        assertTrue(defendingCiv.civID in checkpoint.attackEventsForTesting.single().knowsTarget)
    }

    @Test
    fun `save round trip preserves original endpoints and exact witness sets`() {
        defendingCiv.viewableTiles = setOf(target)
        game.turns = 12
        Battle.attack(MapUnitCombatant(archer), MapUnitCombatant(defender))
        val original = game.attackEventsForTesting.single()
        val restored = json().fromJson(GameInfo::class.java, json().toJson(game)).attackEventsForTesting.single()

        assertEquals(original.turn, restored.turn)
        assertEquals(HexCoord(0, 0), restored.source)
        assertEquals(HexCoord(1, 0), restored.target)
        assertEquals(original.attackingCivId, restored.attackingCivId)
        assertEquals(original.knowsSource, restored.knowsSource)
        assertEquals(original.knowsTarget, restored.knowsTarget)
    }

    @Test
    fun `old saves load without inventing witnesses for obsolete attack history`() {
        val oldSave = """{
            "civilizations": [{
                "civID": "Germany",
                "attacksSinceTurnStart": [{"source": {"x": 0, "y": 0}, "target": {"x": 1, "y": 0}}]
            }]
        }"""

        val restored = json().fromJson(GameInfo::class.java, oldSave)

        assertEquals("Germany", restored.civilizations.single().civID)
        assertTrue(restored.attackEventsForTesting.isEmpty())
    }
}
