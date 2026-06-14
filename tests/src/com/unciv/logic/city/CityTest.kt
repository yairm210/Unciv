package com.unciv.logic.city

import com.unciv.Constants
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

    @Test
    fun `should mark selected tile when queueing CreatesOneImprovement building`() {
        val farm = testGame.ruleset.tileImprovements["Farm"]!!
        testCiv.tech.techsResearched.add(farm.techRequired!!)
        val building = createFarmPlacingBuilding()
        val targetTile = testGame.getTile(1, 1)
        targetTile.baseTerrain = Constants.grassland

        capitalCity.cityConstructions.addToQueue(building, tile = targetTile)

        assertTrue(capitalCity.cityConstructions.isBeingConstructedOrEnqueued(building.name))
        assertTrue(targetTile.isMarkedForCreatesOneImprovement("Farm"))
    }

    @Test
    fun `should require selected tile when queueing CreatesOneImprovement building`() {
        val building = createFarmPlacingBuilding()

        try {
            capitalCity.cityConstructions.addToQueue(building)
            fail("Expected queueing a CreatesOneImprovement building without a tile to fail")
        } catch (ex: IllegalArgumentException) {
            assertTrue(ex.message!!.contains("target tile"))
        }

        assertFalse(capitalCity.cityConstructions.isBeingConstructedOrEnqueued(building.name))
    }

    @Test
    fun `should clear marker when removing CreatesOneImprovement building from queue`() {
        val farm = testGame.ruleset.tileImprovements["Farm"]!!
        testCiv.tech.techsResearched.add(farm.techRequired!!)
        val building = createFarmPlacingBuilding()
        val targetTile = testGame.getTile(1, 1)
        targetTile.baseTerrain = Constants.grassland
        capitalCity.cityConstructions.addToQueue(building, tile = targetTile)

        capitalCity.cityConstructions.removeFromQueue(0, false)

        assertFalse(targetTile.isMarkedForCreatesOneImprovement())
        assertFalse(capitalCity.cityConstructions.isBeingConstructedOrEnqueued(building.name))
    }

    private fun createFarmPlacingBuilding() =
        testGame.createBuilding("Creates a [Farm] improvement on a specific tile")
            .apply { ruleset = testGame.ruleset }

}
