package com.unciv.logic.civilization

import com.unciv.logic.BackwardCompatibility.migrateGreatPersonPointsToCities
import com.unciv.logic.map.HexCoord
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class GreatPersonPointsTest {
    @Test
    fun `great person points accumulate per city and the person is born in that city`() {
        val game = TestGame()
        game.makeHexagonalMap(4)
        val civ = game.addCiv()
        val capital = game.addCity(civ, game.getTile(HexCoord.Zero))
        val other = game.addCity(civ, game.getTile(3, 0))
        val building = game.createBuilding()
        building.greatPersonPoints.add("Great Scientist", 3)
        building.greatPersonPoints.add("Great Prophet", 2)
        other.cityConstructions.addBuilding(building)

        // Old saves hold only civ-wide totals: they go to the city earning them
        civ.greatPeople.greatPersonPointsCounter.add("Great Scientist", 10)
        game.gameInfo.migrateGreatPersonPointsToCities()
        assertEquals(10, other.greatPersonPointsCounter["Great Scientist"])
        assertTrue(capital.greatPersonPointsCounter.isEmpty())

        civ.greatPeople.addGreatPersonPoints()
        assertEquals(13, other.greatPersonPointsCounter["Great Scientist"])
        assertTrue(capital.greatPersonPointsCounter.isEmpty())
        // Great Prophets stay pooled at civ level
        assertEquals(2, civ.greatPeople.greatPersonPointsCounter["Great Prophet"])

        other.greatPersonPointsCounter["Great Scientist"] = civ.greatPeople.getPointsRequiredForGreatPerson("Great Scientist")
        val (greatPerson, birthCity) = civ.greatPeople.getNewGreatPerson()!!
        assertEquals("Great Scientist", greatPerson)
        assertSame(other, birthCity)
        assertEquals(0, other.greatPersonPointsCounter["Great Scientist"])
    }
}
