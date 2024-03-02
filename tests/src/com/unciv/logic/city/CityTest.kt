package com.unciv.logic.city

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.Civilization
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class CityTest {
    private lateinit var testCiv: Civilization
    private lateinit var capitalCity: City

    private val testGame = TestGame()

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(2)
        testCiv = testGame.addCiv()
        capitalCity = testGame.addCity(testCiv, testGame.getTile(Vector2.Zero))
    }

    @Test
    fun `should not destroy city when it's an original capital`() {
        // when
        capitalCity.destroyCity()

        // then
        assertTrue(testCiv.cities.contains(capitalCity))
    }

    @Test
    fun `should destroy city when it's not original capital`() {
        // given
        val nonCapitalCity = testGame.addCity(testCiv, testGame.getTile(Vector2(1f, 1f)))

        // when
        nonCapitalCity.destroyCity()

        // then
        assertTrue(testCiv.cities.contains(capitalCity))
        assertFalse(testCiv.cities.contains(nonCapitalCity))
    }

    @Test
    fun `should move capital when destroyed`() {
        // given
        val nonCapitalCity = testGame.addCity(testCiv, testGame.getTile(Vector2(1f, 1f)))
        nonCapitalCity.name = "Not capital"

        // when
        capitalCity.destroyCity(overrideSafeties = true)

        // then
        assertFalse(testCiv.cities.contains(capitalCity))
        assertTrue(testCiv.cities[0].isCapital())
        assertEquals("Not capital", testCiv.cities[0].name)
    }

    @Test
    fun `should set terrain as city ruin when city is destroyed`() {
        // when
        capitalCity.destroyCity(overrideSafeties = true)

        // then
        assertEquals("City ruins", testGame.getTile(Vector2.Zero).improvement)
    }

    @Test
    fun `should sell building`() {
        // given
        val expectedMonumentSellingGoldGain = 4
        capitalCity.cityConstructions.addBuilding("Monument")

        // when
        capitalCity.sellBuilding("Monument")

        // then
        assertEquals(expectedMonumentSellingGoldGain, testCiv.gold)
    }

    @Test
    fun `should sell only one building per turn`() {
        // given
        capitalCity.cityConstructions.addBuilding("Monument")

        // when
        capitalCity.sellBuilding("Monument")

        // then
        assertTrue(capitalCity.hasSoldBuildingThisTurn)
    }

    @Test
    fun `should get resources from tiles`() {
        // given
        testCiv.tech.addTechnology("Iron Working")
        testCiv.tech.addTechnology("Mining")

        val tile = testGame.getTile(Vector2(1f, 1f))
        tile.resource = "Iron"
        tile.resourceAmount = 4
        tile.improvement = "Mine"

        // when
        val resources = testCiv.detailedCivResources

        // then
        assertEquals(1, resources.size)
        assertEquals("4 Iron from Tiles", resources[0].toString())
    }

    @Test
    fun `should get resources from unique buildings`() {
        // given
        val building = testGame.createBuilding("Provides [4] [Iron]")
        capitalCity.cityConstructions.addBuilding(building)

        // when
        val resources = testCiv.detailedCivResources

        // then
        assertEquals(1, resources.size)
        assertEquals("4 Iron from Buildings", resources[0].toString())
    }

    @Test
    fun `should reduce resources due to buildings`() {
        // given
        capitalCity.cityConstructions.addBuilding("Factory")

        // when
        val resources = testCiv.detailedCivResources

        // then
        assertEquals(1, resources.size)
        assertEquals("-1 Coal from Buildings", resources[0].toString())
    }
}
