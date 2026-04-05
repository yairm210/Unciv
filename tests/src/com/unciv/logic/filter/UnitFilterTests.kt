package com.unciv.logic.filter

import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class UnitFilterTests {

    private lateinit var game: TestGame
    private val ruleset get() = game.ruleset
    private lateinit var civ: Civilization
    private lateinit var city: City

    @Before
    fun init() {
        setupModdedGame()
    }
    private fun setupModdedGame(vararg addGlobalUniques: String, withCiv: Boolean = true) {
        game = TestGame(*addGlobalUniques)
        game.makeHexagonalMap(3)
        if (!withCiv) return
        civ = game.addCiv()
        city = game.addCity(civ, game.tileMap[2,0])
    }

    @Test
    fun testFilteredUnits() {
        val wetTile = game.tileMap[3,1]
        val deSela = game.addCiv(game.ruleset.nations["Lhasa"]!!)
        val city2 = game.addCity(deSela, game.tileMap[-2,1])
        wetTile.setBaseTerrain(ruleset.terrains[Constants.coast]!!)
        val carrier = game.addUnit("Carrier", civ, wetTile)
        val carried = game.addUnit("Fighter", civ, wetTile)
        val warrior = game.addUnit("Warrior", civ, city.getCenterTile())
        val scout1 = game.addUnit("Scout", civ, game.tileMap.values.first())
        val scout2 = game.addUnit("Scout", civ, game.tileMap.values.last())
        val scout3 = game.addUnit("Scout", deSela, city2.getCenterTile())
        val worker1 = game.addUnit("Worker", civ, game.tileMap[2,1])
        val worker2 = game.addUnit("Worker", civ, game.tileMap[2,-1])
        val units = listOf(carrier, carried, warrior, scout1, scout2, scout3, worker1, worker2)
        val filters = listOf(
            "Military" to (listOf(carrier, carried, warrior, scout1, scout2, scout3) to 6),
            "Civilian" to (listOf(worker1, worker2) to 2),
            deSela.civName to (listOf(scout3) to 1),
            "All" to (units to units.size),
            "Melee" to (listOf(carrier, warrior, scout1, scout2, scout3) to 5),
        )
        val failures = ArrayList<String>()
        for (test in filters) {
            val filtered = units.filter { it.matchesFilter(test.first) }
            try {
                Assert.assertTrue(filtered.containsAll(test.second.first))
                Assert.assertTrue(filtered.size == test.second.second)
            }
            catch (_: AssertionError) {
                failures.add("Filter: ${test.first}\nExpected result: ${test.second.first}, expected size: ${test.second.second}\n" +
                    "Result: $filtered, size: ${filtered.size}\n")
            }
        }
        if (failures.any()) {
            println(failures.joinToString("\n"))
            throw AssertionError()
        }
    }
}
