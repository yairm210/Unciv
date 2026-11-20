package com.unciv.logic.city.managers

import com.unciv.logic.city.City
import com.unciv.logic.city.CityFlags
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class CityTurnManagerWltkTest {
    private val testGame = TestGame()
    private lateinit var civ: Civilization
    private lateinit var city: City

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(3)
        civ = testGame.addCiv()
        city = testGame.addCity(civ, testGame.getTile(HexCoord.Zero))
    }

    @Test
    fun `ResourceDemand expiry during WLTKD does not rewrite demandedResource`() {
        city.demandedResource = "Salt"
        city.setFlag(CityFlags.WeLoveTheKing, 5)
        city.setFlag(CityFlags.ResourceDemand, 1)

        CityTurnManager(city).startTurn()

        assertEquals("Salt", city.demandedResource)
        assertTrue(city.hasFlag(CityFlags.WeLoveTheKing))
        assertFalse(city.hasFlag(CityFlags.ResourceDemand))
    }

    @Test
    fun `starting WLTKD clears pending ResourceDemand`() {
        // City-center Salt needs Mining researched to count as extracted
        civ.tech.addTechnology("Mining")
        city.getCenterTile().setTileResource("Salt")
        city.getCenterTile().resourceAmount = 1
        civ.cache.updateCivResources()

        city.demandedResource = "Salt"
        city.setFlag(CityFlags.ResourceDemand, 10)
        assertFalse(city.hasFlag(CityFlags.WeLoveTheKing))

        CityTurnManager(city).startTurn()

        assertTrue(city.hasFlag(CityFlags.WeLoveTheKing))
        assertFalse(city.hasFlag(CityFlags.ResourceDemand))
        assertEquals("Salt", city.demandedResource)
    }
}
