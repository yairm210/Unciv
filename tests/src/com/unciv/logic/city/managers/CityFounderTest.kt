package com.unciv.logic.city.managers

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
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
class CityFounderTest {

    private lateinit var civ: Civilization
    private val cityFounder = CityFounder()

    private val testGame = TestGame()

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(3)
        civ = testGame.addCiv()
    }

    @Test
    fun `should save original founding civ`() {
        // when
        val foundedCity = cityFounder.foundCity(civ, Vector2.Zero)

        // then
        assertEquals(civ.civName, foundedCity.foundingCiv)
    }

    @Test
    fun `should generate new city names based on nation city name list`() {
        // given
        civ.nation.cities = arrayListOf("1", "2", "3")

        // when
        val foundedCity1 = cityFounder.foundCity(civ, Vector2.Zero)
        val foundedCity2 = cityFounder.foundCity(civ, Vector2.X)
        val foundedCity3 = cityFounder.foundCity(civ, Vector2.Y)

        // then
        assertEquals("1", foundedCity1.name)
        assertEquals("2", foundedCity2.name)
        assertEquals("3", foundedCity3.name)
    }

    @Test
    fun `should borrow city names based on alive civs`() {
        // given
        val borrowerCiv = testGame.addCiv("\"Borrows\" city names from other civilizations in the game")
        borrowerCiv.nation.cities = arrayListOf()
        civ.nation.cities = arrayListOf("1", "2", "3")
        cityFounder.foundCity(civ, Vector2.X) // otherwise "civ" is considered dead

        // when
        val foundedCity = cityFounder.foundCity(borrowerCiv, Vector2.Zero)

        // then
        assertEquals("3", foundedCity.name)
    }

    @Test
    fun `should make only first founded city capital`() {
        // when
        val capitalCity = cityFounder.foundCity(civ, Vector2.Zero)
        val nonCapitalCity = cityFounder.foundCity(civ, Vector2.X)

        // then
        assertTrue(capitalCity.isCapital())
        assertTrue(capitalCity.cityConstructions.containsBuildingOrEquivalent("Palace"))
        assertFalse(nonCapitalCity.isCapital())
        assertFalse(nonCapitalCity.cityConstructions.containsBuildingOrEquivalent("Palace"))
    }

    @Test
    fun `should get free buildings due to uniques upon city founding`() {
        // given
        val freeBuildingCiv = testGame.addCiv("Gain a free [Granary] [in all cities]")

        // when
        val foundedCity = cityFounder.foundCity(freeBuildingCiv, Vector2.Zero)

        // then
        assertTrue(foundedCity.cityConstructions.containsBuildingOrEquivalent("Granary"))
    }

    @Test
    fun `should remove removable terrain features upon city founding`() {
        // given
        testGame.setTileFeatures(Vector2.Zero, Constants.forest)
        assertTrue(testGame.getTile(Vector2.Zero).terrainFeatures.contains(Constants.forest))

        // when
        val foundedCity = cityFounder.foundCity(civ, Vector2.Zero)

        // then
        assertFalse(foundedCity.getCenterTile().terrainFeatures.contains(Constants.forest))
    }

    @Test
    fun `should keep non-removable terrain features upon city founding`() {
        // given
        testGame.setTileFeatures(Vector2.Zero, Constants.hill)
        assertTrue(testGame.getTile(Vector2.Zero).terrainFeatures.contains(Constants.hill))

        // when
        val foundedCity = cityFounder.foundCity(civ, Vector2.Zero)

        // then
        assertTrue(foundedCity.getCenterTile().terrainFeatures.contains(Constants.hill))
    }

    @Test
    fun `should remove improvements upon city founding`() {
        // given
        val tile = testGame.getTile(Vector2.Zero)
        tile.improvement = "Farm"

        // when
        cityFounder.foundCity(civ, Vector2.Zero)

        // then
        assertFalse(tile.improvement.equals("Farm"))
    }

    @Test
    fun `should exploit city center yields without requiring pop`() {
        // given
        testGame.setTileTerrainAndFeatures(Vector2.Zero, Constants.desert, Constants.hill) // 2 hammers expected
        testGame.setTileTerrainAndFeatures(Vector2.X, Constants.desert)  // no yields expected

        // when
        val city = cityFounder.foundCity(civ, Vector2.Zero)
        city.workedTiles = hashSetOf(Vector2.X)
        city.lockedTiles = hashSetOf(Vector2.X)
        city.cityStats.update()

        // then
        assertFalse(city.workedTiles.contains(city.getCenterTile().position)) // no pop required
        assertEquals(2.0f, city.cityStats.statsFromTiles.production)
    }

    @Test
    fun `should autoassign new population`() {
        // when
        val city = cityFounder.foundCity(civ, Vector2.Zero)

        // then
        assertEquals(0, city.population.getFreePopulation())
    }

    @Test
    fun `should acquire ownership of tiles surrounding the city center`() {
        // when
        val city = cityFounder.foundCity(civ, Vector2.Zero)

        // then
        assertEquals(7, city.tiles.size) // 6 surrounding + 1 due to city center itself
    }

    @Test
    fun `should trigger uniques from city founding`() {
        // given
        val uniqueCiv = testGame.addCiv("Gain [+5] [Gold] <upon founding a city>")
        assertEquals(0, uniqueCiv.gold)

        // when
        cityFounder.foundCity(uniqueCiv, Vector2.Zero)

        // then
        assertEquals(5, uniqueCiv.gold)
    }

    @Test
    fun `should not be able to found city in same tile of another city`() {
        // given
        cityFounder.foundCity(civ, Vector2.Zero)

        // when
        try {
            cityFounder.foundCity(civ, Vector2.Zero)
        } catch (e: IllegalStateException) {
            assertEquals("Trying to found a city in a tile that already has one", e.message)
            return
        }
        fail()
    }

}
