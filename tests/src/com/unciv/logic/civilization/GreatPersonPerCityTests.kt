package com.unciv.logic.civilization

import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class GreatPersonPerCityTests {

    private lateinit var testGame: TestGame

    @Before
    fun setUp() {
        testGame = TestGame()
        testGame.makeHexagonalMap(4)
    }

    private fun enablePerCityGreatPersonProgress() {
        testGame.ruleset.modOptions.uniques.add(UniqueType.GreatPersonPointsAccumulatePerCity.text)
    }

    @Test
    fun `per-city mode consumes points from the city that reached the threshold`() {
        enablePerCityGreatPersonProgress()
        val civ = testGame.addCiv()

        val cityA = testGame.addCity(civ, testGame.getTile(0, 0), initialPopulation = 5)
        val cityB = testGame.addCity(civ, testGame.getTile(1, 0), initialPopulation = 5)

        assertTrue(civ.greatPeople.usesPerCityGreatPersonProgress())

        val required = civ.greatPeople.getPointsRequiredForGreatPerson("Great Artist")
        cityA.greatPersonPointsCounter["Great Artist"] = required
        cityB.greatPersonPointsCounter["Great Artist"] = required / 2

        assertEquals("Great Artist", civ.greatPeople.getNewGreatPersonFromCity(cityA))
        assertEquals(0, cityA.greatPersonPointsCounter["Great Artist"])
        assertEquals(required / 2, cityB.greatPersonPointsCounter["Great Artist"])
        assertNull(civ.greatPeople.getNewGreatPersonFromCity(cityB))
    }

    @Test
    fun `per-city mode spawns the unit near the city that earned it`() {
        enablePerCityGreatPersonProgress()
        val civ = testGame.addCiv()
        testGame.gameInfo.currentPlayerCiv = civ
        testGame.gameInfo.currentPlayer = civ.civID

        val cityA = testGame.addCity(civ, testGame.getTile(0, 0), initialPopulation = 3)
        val cityB = testGame.addCity(civ, testGame.getTile(2, 0), initialPopulation = 3)

        assertNotNull(testGame.ruleset.units["Great Artist"])
        val required = civ.greatPeople.getPointsRequiredForGreatPerson("Great Artist")
        cityA.greatPersonPointsCounter["Great Artist"] = required

        val unitName = civ.greatPeople.getNewGreatPersonFromCity(cityA)!!
        val unit = civ.units.addUnit(unitName, cityA)
        assertNotNull(unit)
        val distA = unit!!.currentTile.aerialDistanceTo(cityA.getCenterTile())
        val distB = unit.currentTile.aerialDistanceTo(cityB.getCenterTile())
        assertTrue("GP should spawn nearer city A (distA=$distA distB=$distB)", distA < distB)
    }

    @Test
    fun `default mode still uses empire-wide counter`() {
        val civ = testGame.addCiv()
        assertFalse(civ.greatPeople.usesPerCityGreatPersonProgress())

        val required = civ.greatPeople.getPointsRequiredForGreatPerson("Great Artist")
        civ.greatPeople.greatPersonPointsCounter["Great Artist"] = required
        assertEquals("Great Artist", civ.greatPeople.getNewGreatPerson())
    }
}
