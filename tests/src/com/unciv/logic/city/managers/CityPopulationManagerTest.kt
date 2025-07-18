package com.unciv.logic.city.managers

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.city.CityFocus
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
class CityPopulationManagerTest {

    private lateinit var civ: Civilization
    private lateinit var city: City

    private val testGame = TestGame()

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(3)
        civ = testGame.addCiv()
        city = testGame.addCity(civ, testGame.getTile(Vector2.Zero), initialPopulation = 1)
    }

    @Test
    fun `should increase food requirements for next pop`() {
        // given
        val biggerCity = testGame.addCity(civ, testGame.getTile(Vector2.X), initialPopulation = 2)

        // when
        val smallerCityFoodRequirements = city.population.getFoodToNextPopulation()
        val greaterCityFoodRequirements = biggerCity.population.getFoodToNextPopulation()

        // then
        assertTrue(smallerCityFoodRequirements < greaterCityFoodRequirements)
    }

    @Test
    fun `should change food requirements for different gamespeeds`() {
        // given
        val quickSpeedGame = TestGame().apply { setSpeed("Quick") }
        val quickSpeedCity = quickSpeedGame.addCity(quickSpeedGame.addCiv(), quickSpeedGame.getTile(Vector2.Zero), initialPopulation = 1)

        val epicSpeedGame = TestGame().apply { setSpeed("Epic") }
        val epicSpeedCity = epicSpeedGame.addCity(epicSpeedGame.addCiv(), epicSpeedGame.getTile(Vector2.Zero), initialPopulation = 1)

        // when
        val quickFoodRequirements = quickSpeedCity.population.getFoodToNextPopulation()
        val standardCityFoodRequirements = city.population.getFoodToNextPopulation()
        val epicCityFoodRequirements = epicSpeedCity.population.getFoodToNextPopulation()

        // then
        assertTrue(quickFoodRequirements < standardCityFoodRequirements)
        assertTrue(standardCityFoodRequirements < epicCityFoodRequirements)
    }

    @Test
    fun `should increase food requirements for city states`() {
        // given
        val cityState = testGame.addCiv(cityStateType = "Militaristic")
        val cityStateCity = testGame.addCity(cityState, testGame.getTile(Vector2.X), initialPopulation = 1)

        // when
        val cityFoodRequirements = city.population.getFoodToNextPopulation()
        val cityStateCityFoodRequirements = cityStateCity.population.getFoodToNextPopulation()

        // then
        assertTrue(cityFoodRequirements < cityStateCityFoodRequirements)
    }

    @Test
    fun `should increase food requirements for AI on easier difficulties`() {
        // given
        val easierDifficultyGame = TestGame().apply { setDifficulty("Chieftain") }
        val easierCity = easierDifficultyGame.addCity(easierDifficultyGame.addCiv(), easierDifficultyGame.getTile(Vector2.Zero), initialPopulation = 1)

        // when
        val cityFoodRequirements = city.population.getFoodToNextPopulation()
        val easierCityFoodRequirements = easierCity.population.getFoodToNextPopulation()

        // then
        assertTrue(easierCityFoodRequirements > cityFoodRequirements)
    }

    @Test
    fun `should decrease food requirements for AI on higher difficulties`() {
        // given
        val harderDifficultyGame = TestGame().apply { setDifficulty("Deity") }
        val harderCity = harderDifficultyGame.addCity(harderDifficultyGame.addCiv(), harderDifficultyGame.getTile(Vector2.Zero), initialPopulation = 1)

        // when
        val cityFoodRequirements = city.population.getFoodToNextPopulation()
        val harderCityFoodRequirements = harderCity.population.getFoodToNextPopulation()

        // then
        assertTrue(harderCityFoodRequirements < cityFoodRequirements)
    }

    @Test
    fun `should remove one pop when on starvation and no food reserves`() {
        // given
        city.population.setPopulation(2)
        city.population.foodStored = 0

        // when
        city.population.nextTurn(-1)

        // then
        assertEquals(1, city.population.population)
    }

    @Test
    fun `should not remove one pop when on starvation but with enough food reserves`() {
        // given
        city.population.setPopulation(2)
        city.population.foodStored = 5

        // when
        city.population.nextTurn(-1)

        // then
        assertEquals(2, city.population.population)
    }

    @Test
    fun `should not remove one pop when on starvation but with 1 pop`() {
        // given
        city.population.foodStored = 0

        // when
        city.population.nextTurn(-1)

        // then
        assertEquals(1, city.population.population)
    }

    @Test
    fun `should increase pop when enough food is stored`() {
        // given
        city.population.foodStored = 14

        // when
        city.population.nextTurn(1)

        // then
        assertEquals(2, city.population.population)
        assertEquals(0, city.population.foodStored)
    }

    @Test
    fun `should carry over excess food when new pop is born`() {
        // given
        city.population.foodStored = 14

        // when
        city.population.nextTurn(4)

        // then
        assertEquals(2, city.population.population)
        assertEquals(3, city.population.foodStored)
    }

    @Test
    fun `should start with food stored when new pop is born and uniques`() {
        // given
        val building = testGame.createBuilding("[50]% Food is carried over after population increases [in this city]")
        city.cityConstructions.addBuilding(building)
        city.population.foodStored = 14

        // when
        city.population.nextTurn(1)

        // then
        assertEquals(2, city.population.population)
        assertEquals(7, city.population.foodStored)
    }

    @Test
    fun `should automatically assign new pop to job`() {
        // given
        city.population.foodStored = 14

        // when
        city.population.nextTurn(1)

        // then
        assertEquals(2, city.population.population)
        assertEquals(0, city.population.getFreePopulation())
    }


    @Test
    fun `should automatically assign new pop to best job`() {
        // given
        city.workedTiles.clear()
        city.workedTiles.add(Vector2(-1f, 0f))
        city.lockedTiles.add(Vector2(-1f, 0f)) // force the first pop to work on a specific tile to avoid being reassigned
        val goodTile = testGame.setTileTerrain(Vector2.X, Constants.grassland)
        goodTile.improvement = "Farm"

        assertFalse(city.workedTiles.contains(goodTile.position))

        city.population.foodStored = 14

        // when
        city.population.nextTurn(1)

        // then
        assertEquals(2, city.population.population)
        assertTrue(city.workedTiles.contains(goodTile.position))
    }

    @Test
    fun `should automatically assign new pop to best job according to city focus`() {
        // given
        city.setCityFocus(CityFocus.GoldFocus)
        city.workedTiles.clear()
        city.workedTiles.add(Vector2(-1f, 0f))
        city.lockedTiles.add(Vector2(-1f, 0f)) // force the first pop to work on a specific tile to avoid being reassigned
        val goodFoodTile = testGame.setTileTerrain(Vector2.X, Constants.grassland)
        goodFoodTile.improvement = "Farm"
        assertFalse(city.workedTiles.contains(goodFoodTile.position))

        val goodGoldTile = testGame.setTileTerrain(Vector2.Y, Constants.grassland)
        val goldImprovement = testGame.createTileImprovement("[+5 Gold]")
        goodGoldTile.improvement = goldImprovement.name
        assertFalse(city.workedTiles.contains(goodGoldTile.position))

        city.population.foodStored = 14

        // when
        city.population.nextTurn(1)

        // then
        assertEquals(2, city.population.population)
        assertTrue(city.workedTiles.contains(goodGoldTile.position))
        assertFalse(city.workedTiles.contains(goodFoodTile.position))
    }

    @Test
    fun `should automatically assign new pop to best job with specialists`() {
        // given
        city.setCityFocus(CityFocus.GoldFocus)
        city.workedTiles.clear()
        city.workedTiles.add(Vector2(-1f, 0f))
        city.lockedTiles.add(Vector2(-1f, 0f)) // force the first pop to work on a specific tile to avoid being reassigned
        val specialistBuilding = testGame.createBuilding()
        specialistBuilding.specialistSlots.add("Merchant", 1)
        city.cityConstructions.addBuilding(specialistBuilding)

        city.population.foodStored = 14
        assertTrue(city.population.specialistAllocations.isEmpty())

        // when
        city.population.nextTurn(1)

        // then
        assertEquals(2, city.population.population)
        assertTrue(city.population.specialistAllocations.containsKey("Merchant"))
    }
}
