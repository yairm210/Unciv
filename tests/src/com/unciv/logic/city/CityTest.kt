package com.unciv.logic.city

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.*
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
        capitalCity = testGame.addCity(testCiv, testGame.getTile(HexCoord.Zero))
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
        val nonCapitalCity = testGame.addCity(testCiv, testGame.getTile(1,1))

        // when
        nonCapitalCity.destroyCity()

        // then
        assertTrue(testCiv.cities.contains(capitalCity))
        assertFalse(testCiv.cities.contains(nonCapitalCity))
    }

    @Test
    fun `should move capital when destroyed`() {
        // given
        val nonCapitalCity = testGame.addCity(testCiv, testGame.getTile(1,1))
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
        assertEquals("City ruins", testGame.getTile(HexCoord.Zero).improvement)
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


}
