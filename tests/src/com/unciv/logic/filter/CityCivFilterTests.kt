package com.unciv.logic.filter

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import com.unciv.testing.runTestParcours
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class CityCivFilterTests {
    private lateinit var game: TestGame
    private lateinit var civ: Civilization
    private lateinit var city: City

    @Before
    fun initTheWorld() {
        game = TestGame()
        game.makeHexagonalMap(6)
        civ = game.addCiv()
        city = game.addCity(civ, game.getTile(HexCoord.Zero))
    }

    @Test
    fun testFilteredCities() {
        UniqueTriggerActivation.triggerUnique(Unique("Turn this tile into a [Coast] tile"), civ, tile = game.tileMap[-3,0])

        val city2 = game.addCity(civ, game.tileMap[-2,0], initialPopulation = 9)
        city2.isPuppet = true
        val enemy = game.addCiv()
        val city3 = game.addCity(enemy, game.tileMap[3,0])

        runTestParcours("Filtered cities countable", { test: String ->
            game.gameInfo.getCities().count { it.matchesFilter(test, civ) }
        },
            "All", 3,
            "Capital", 2,
            "Puppeted", 1,
            "Coastal", 1,
            "Your", 2,
            "{Your} {Capital}", 1
        )
    }
    
    @Test
    fun testBuildingFilters() {
        val testCultureBuilding = game.createBuilding("[+1 Culture]")
        val testFilterBuilding = game.createBuilding("hasFilter")
        val testCultureWithOtherBuilding = game.createBuilding("[+1 Culture] <in cities with a [hasFilter]>")
        val testFilterWithOtherBuilding = game.createBuilding("hasFilter <in cities with a [${testCultureBuilding.name}]>") // Note: This does not work with normal building filters. Only names and tags
        val testWonder = game.createBuilding()
        testWonder.isWonder = true
        val buildingsToTest = listOf(testCultureBuilding, testFilterBuilding, testCultureWithOtherBuilding, testFilterWithOtherBuilding, testWonder)
        runTestParcours("", { filter: String ->
            buildingsToTest.count { it.matchesFilter(filter, city.state) }
        },
            "hasFilter", 1, // Other buildings hasn't been added
            "Culture", 1,
            "Wonder", 1,
            "All", 5,
        )
        city.cityConstructions.addBuilding(testCultureBuilding)
        city.cityConstructions.addBuilding(testFilterBuilding)
        city.cityConstructions.addBuilding(testCultureWithOtherBuilding) // Note: cannot correctly determine that it gives culture until it's added
        runTestParcours("", { filter: String ->
            buildingsToTest.count { it.matchesFilter(filter, city.state) }
        },
            "hasFilter", 2,
            "Culture", 1, // TODO: Bug: cities aren't given a city context for filtering
            "Wonder", 1,
            "All", 5,
        )
    }
    
}
