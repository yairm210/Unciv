package com.unciv.logic.city.managers

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class CityExpansionManagerTest {

    private lateinit var civ: Civilization
    private lateinit var city: City
    private val cityExpansionManager = CityExpansionManager()

    private val testGame = TestGame()

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(6)
        civ = testGame.addCiv()
        city = testGame.addCity(civ, testGame.getTile(HexCoord.Zero), initialPopulation = 1)
        cityExpansionManager.city = city
    }

    @Test
    fun `should claim new tile when enough culture is stored`() {
        // given
        val cultureToNextTile = cityExpansionManager.getCultureToNextTile()
        assertEquals(7, city.tiles.size)  // city center + surrounding tiles

        // when
        cityExpansionManager.nextTurn(cultureToNextTile.toFloat())

        // then
        assertEquals(8, city.tiles.size)
    }

    @Test
    fun `should claim max one tile per turn due to culture`() {
        // given
        assertEquals(7, city.tiles.size)  // city center + surrounding tiles

        // when
        cityExpansionManager.nextTurn(10_000f)

        // then
        assertEquals(8, city.tiles.size)
    }

    @Test
    fun `should store excess culture for next tile`() {
        // given
        val cultureToNextTile = cityExpansionManager.getCultureToNextTile()

        // when
        cityExpansionManager.nextTurn(cultureToNextTile + 10f)

        // then
        assertEquals(10, cityExpansionManager.cultureStored)
    }

    @Test
    fun `should increase tile culture cost the more tiles a city has claimed by culture`() {
        // given
        val cultureToNextTile = cityExpansionManager.getCultureToNextTile()

        // when
        cityExpansionManager.nextTurn(cultureToNextTile.toFloat())

        // then
        val cultureToNextTileAfterExpansion = cityExpansionManager.getCultureToNextTile()
        assertTrue(cultureToNextTile < cultureToNextTileAfterExpansion)
    }

    @Test
    fun `should increase tile culture culture cost the more tiles a city has bought`() {
        // given
        val cultureToNextTile = cityExpansionManager.getCultureToNextTile()
        civ.addGold(500)

        // when
        cityExpansionManager.buyTile(testGame.getTile(2,0))

        // then
        val cultureToNextTileAfterExpansion = cityExpansionManager.getCultureToNextTile()
        assertTrue(cultureToNextTile < cultureToNextTileAfterExpansion)
    }

    @Test
    fun `should increase tile culture costs for city states`() {
        // given
        val cityStateCiv = testGame.addCiv(cityStateType = "Militaristic")
        val cityState = testGame.addCity(cityStateCiv, testGame.getTile(4,0))
        val cityStateExpansionManager = CityExpansionManager()
        cityStateExpansionManager.city = cityState

        val cultureToNextTileCiv = cityExpansionManager.getCultureToNextTile()


        // when
        val cultureToNextTileCityState = cityStateExpansionManager.getCultureToNextTile()

        // then
        assertTrue(cultureToNextTileCityState > cultureToNextTileCiv)
    }

    @Test
    fun `should change tile culture cost due to uniques`() {
        // given
        val uniqueCiv = testGame.addCiv("[-25]% Culture cost of natural border growth [in all cities]")
        val uniqueCity = testGame.addCity(uniqueCiv, testGame.getTile(4,0))
        val cityUniqueExpansionManager = CityExpansionManager()
        cityUniqueExpansionManager.city = uniqueCity

        val cultureToNextTile = cityExpansionManager.getCultureToNextTile()

        // when
        val cultureToNextTileUnique = cityUniqueExpansionManager.getCultureToNextTile()

        // then
        assertTrue(cultureToNextTileUnique < cultureToNextTile)
    }

    @Test
    fun `should change tile gold cost due to uniques`() {
        // given
        val uniqueCiv = testGame.addCiv("[-25]% Gold cost of acquiring tiles [in all cities]")
        val uniqueCity = testGame.addCity(uniqueCiv, testGame.getTile(4,0))
        val cityUniqueExpansionManager = CityExpansionManager()
        cityUniqueExpansionManager.city = uniqueCity

        val goldCost2TileDistance = cityExpansionManager.getGoldCostOfTile(testGame.getTile(2,0))

        // when
        val goldCost2TileDistanceWithUniques = cityUniqueExpansionManager.getGoldCostOfTile(testGame.getTile(4,-2))

        // then
        assertTrue(goldCost2TileDistanceWithUniques < goldCost2TileDistance)
    }

    @Test
    fun `should be able to buy neighbour tile`() {
        // given
        val tileToBuy = testGame.getTile(2,0)
        civ.addGold(500)
        assertFalse(city.tiles.contains(HexCoord(2,0)))

        // when
        cityExpansionManager.buyTile(tileToBuy)

        // then
        assertEquals(8, city.tiles.size)
        assertTrue(city.tiles.contains(HexCoord(2,0)))
    }

    @Test
    fun `should not be able to buy not-neighbour tile`() {
        // given
        val tileToBuy = testGame.getTile(3,0)

        // when
        try {
            cityExpansionManager.buyTile(tileToBuy)
        } catch (e: Exception) {
            // then
            assertEquals("$city tried to buy $tileToBuy, but it owns none of the neighbors", e.message)
            return
        }

        fail()
    }

    @Test
    fun `should not be able to buy tile without enough gold`() {
        // given
        val tileToBuy = testGame.getTile(2,0)

        // when
        try {
            cityExpansionManager.buyTile(tileToBuy)
        } catch (e: Exception) {
            // then
            e.message?.let { assertTrue(it.contains("$city tried to buy $tileToBuy, but lacks gold")) }
            return
        }

        fail()
    }

    @Test
    fun `should increase gold cost the more a tile is farther from the city center`() {
        // given
        val tileToBuyNear = testGame.getTile(2,0)  // two tiles distance
        val tileToBuyFar = testGame.getTile(3,0)  // three tiles distance

        // when
        val nearTileCost = cityExpansionManager.getGoldCostOfTile(tileToBuyNear)
        val farTileCost = cityExpansionManager.getGoldCostOfTile(tileToBuyFar)

        // then
        assertTrue(nearTileCost < farTileCost)
    }

    @Test
    fun `should increase gold cost the more tiles a city has claimed`() {
        // given
        val tileBought = testGame.getTile(-2,0)  // two tiles distance
        val tileToBuy = testGame.getTile(2,0)  // still two tiles distance
        val tileBoughtCost = cityExpansionManager.getGoldCostOfTile(tileBought)

        civ.addGold(500)

        // when
        cityExpansionManager.buyTile(tileBought)

        // then
        val tileToBuyCost = cityExpansionManager.getGoldCostOfTile(tileToBuy)
        assertTrue(tileBoughtCost < tileToBuyCost)
    }
}
