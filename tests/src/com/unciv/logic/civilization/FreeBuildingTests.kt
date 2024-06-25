@file:Suppress("UNUSED_VARIABLE")  // These are tests and the names serve readability

package com.unciv.logic.civilization

import com.badlogic.gdx.math.Vector2
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class FreeBuildingTests {

    private val testGame = TestGame()

    @Before
    fun setup() {
        testGame.makeHexagonalMap(5)
    }

    @Test
    fun `should only give cheapest stat building in set amount of cities`() {
        val civ = testGame.addCiv("Provides the cheapest [Culture] building in your first [4] cities for free")
        for (tech in testGame.ruleset.technologies.keys)
            civ.tech.addTechnology(tech)
        val capitalCity = testGame.addCity(civ, testGame.getTile(Vector2(1f,1f)))
        val city2 = testGame.addCity(civ, testGame.getTile(Vector2(1f,2f)))
        val city3 = testGame.addCity(civ, testGame.getTile(Vector2(2f,2f)))
        val city4 = testGame.addCity(civ, testGame.getTile(Vector2(2f,1f)))
        val city5 = testGame.addCity(civ, testGame.getTile(Vector2(0f,1f)))

        val numberOfMonuments = civ.cities.count { it.cityConstructions.isBuilt("Monument") }

        Assert.assertTrue(numberOfMonuments == 4)
    }

    @Test
    fun `should only give 1 stat building`() {
        val civ = testGame.addCiv("Provides the cheapest [Culture] building in your first [4] cities for free")
        for (tech in testGame.ruleset.technologies.keys)
            civ.tech.addTechnology(tech)
        val capitalCity = testGame.addCity(civ, testGame.getTile(Vector2(1f,1f)))

        Assert.assertTrue(capitalCity.cityConstructions.isBuilt("Monument"))
        Assert.assertFalse(capitalCity.cityConstructions.getBuiltBuildings().any { it.name != "Monument" && it.name != "Palace" })
    }

    @Test
    fun `should only give the specific building in set amount of cities`() {
        val civ = testGame.addCiv("Provides a [Monument] in your first [4] cities for free")
        for (tech in testGame.ruleset.technologies.keys)
            civ.tech.addTechnology(tech)
        val capitalCity = testGame.addCity(civ, testGame.getTile(Vector2(1f,1f)))
        val city2 = testGame.addCity(civ, testGame.getTile(Vector2(1f,2f)))
        val city3 = testGame.addCity(civ, testGame.getTile(Vector2(2f,2f)))
        val city4 = testGame.addCity(civ, testGame.getTile(Vector2(2f,1f)))
        val city5 = testGame.addCity(civ, testGame.getTile(Vector2(0f,1f)))

        val numberOfMonuments = civ.cities.count { it.cityConstructions.isBuilt("Monument") }

        Assert.assertTrue(numberOfMonuments == 4)
    }

    @Test
    fun `free specific buildings should ONLY give the specific building`() {
        val civ = testGame.addCiv("Provides a [Monument] in your first [4] cities for free")
        for (tech in testGame.ruleset.technologies.keys)
            civ.tech.addTechnology(tech)
        val capitalCity = testGame.addCity(civ, testGame.getTile(Vector2(1f,1f)))

        val numberOfMonuments = civ.cities.count { it.cityConstructions.isBuilt("Monument") }

        Assert.assertTrue(capitalCity.cityConstructions.isBuilt("Monument"))
        Assert.assertFalse(capitalCity.cityConstructions.getBuiltBuildings().any { it.name != "Monument" && it.name != "Palace" })
    }

    @Test
    fun `can give specific buildings in all cities`() {
        val civ = testGame.addCiv("Gain a free [Monument] [in all cities]")
        for (tech in testGame.ruleset.technologies.keys)
            civ.tech.addTechnology(tech)
        val capitalCity = testGame.addCity(civ, testGame.getTile(Vector2(1f,1f)))
        val city2 = testGame.addCity(civ, testGame.getTile(Vector2(1f,2f)))
        val city3 = testGame.addCity(civ, testGame.getTile(Vector2(2f,2f)))
        val city4 = testGame.addCity(civ, testGame.getTile(Vector2(2f,1f)))
        val city5 = testGame.addCity(civ, testGame.getTile(Vector2(0f,1f)))

        val numberOfMonuments = civ.cities.count { it.cityConstructions.isBuilt("Monument") }

        Assert.assertTrue(numberOfMonuments == 5)
    }
}
