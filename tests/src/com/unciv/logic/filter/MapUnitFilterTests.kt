package com.unciv.logic.filter

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Filters relative to a civilization (`Friendly`, `Hostile`) are relative to the unit that applies the filter, when it is passed as state. */
@RunWith(BaseTestRunner::class)
class MapUnitFilterTests {
    private val game = TestGame()
    private lateinit var civ: Civilization
    private lateinit var enemy: Civilization
    private lateinit var applier: MapUnit
    private lateinit var ownUnit: MapUnit
    private lateinit var enemyUnit: MapUnit

    @Before
    fun setUp() {
        game.makeHexagonalMap(3)
        civ = game.addCiv()
        enemy = game.addCiv()
        civ.diplomacyFunctions.makeCivilizationsMeet(enemy)
        civ.getDiplomacyManager(enemy)!!.declareWar()
        applier = game.addUnit("Warrior", civ, game.getTile(0, 0))
        ownUnit = game.addUnit("Warrior", civ, game.getTile(1, 0))
        enemyUnit = game.addUnit("Warrior", enemy, game.getTile(2, 0))
    }

    private fun MapUnit.matches(filter: String) = matchesFilter(filter, state = GameContext(applier))

    @Test
    fun `Friendly is relative to the unit that applies the filter`() {
        assertTrue(ownUnit.matches("Friendly"))
        assertFalse(enemyUnit.matches("Friendly"))
        assertFalse(enemyUnit.matches("{Friendly} {Land}"))
    }

    @Test
    fun `Hostile is relative to the unit that applies the filter`() {
        assertFalse(ownUnit.matches("Hostile"))
        assertTrue(enemyUnit.matches("Hostile"))
        assertTrue(enemyUnit.matches("{Hostile} {Land}"))
    }
}
