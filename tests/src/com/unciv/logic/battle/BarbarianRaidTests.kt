package com.unciv.logic.battle

import com.unciv.logic.map.HexCoord
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Barbarians cannot capture a city: a melee barbarian that reaches one at its minimum health steals gold and is removed. */
@RunWith(BaseTestRunner::class)
class BarbarianRaidTests {
    private val game = TestGame()

    @Test
    fun `a raided city keeps its owner and its health`() {
        game.makeHexagonalMap(4)
        val owner = game.addCiv()
        val city = game.addCity(owner, game.getTile(HexCoord.Zero))
        game.addCity(owner, game.getTile(0, 3)) // so that the city is not the owner's only one
        city.health = 1
        owner.addGold(100)
        val barbarianCiv = game.addBarbarianCiv()
        val barbarian = game.addUnit("Brute", barbarianCiv, game.getTile(1, 0))

        Battle.attack(MapUnitCombatant(barbarian), CityCombatant(city))

        assertEquals(owner, city.civ)
        assertEquals(1, city.health)
        assertEquals("ransom", 0, owner.gold)
        assertTrue(barbarian.isDestroyed)
        assertTrue(barbarianCiv.cities.isEmpty())
    }
}
