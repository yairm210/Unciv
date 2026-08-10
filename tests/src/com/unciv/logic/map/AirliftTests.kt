package com.unciv.logic.map

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class AirliftTests {
    private val testGame = TestGame()
    private lateinit var civ: Civilization
    private lateinit var unit: MapUnit

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(5)
        civ = testGame.addCiv()
        val cityA = testGame.addCity(civ, testGame.getTile(0, 0), replacePalace = true)
        val cityB = testGame.addCity(civ, testGame.getTile(3, 0), replacePalace = true)
        val airport = testGame.createBuilding(UniqueType.AllowsAirlift.text)
        cityA.cityConstructions.addBuilding(airport)
        cityB.cityConstructions.addBuilding(airport)
        unit = testGame.addUnit("Warrior", civ, cityA.getCenterTile())
    }

    @Test
    fun `airlift teleports to other airport city and consumes all movement`() {
        assertTrue(unit.movement.canAirliftFrom())
        unit.action = UnitActionType.Airlift.value
        val destination = civ.cities[1].getCenterTile()
        assertTrue(unit.movement.canAirliftTo(destination))

        unit.movement.moveToTile(destination)

        assertEquals(destination.position, unit.getTile().position)
        assertEquals(0f, unit.currentMovement)
        assertFalse(unit.isPreparingAirlift())
    }

    @Test
    fun `airlift unavailable without Allows airlifting building`() {
        val cityCTile = testGame.getTile(0, 3)
        testGame.addCity(civ, cityCTile, replacePalace = true)
        unit.action = UnitActionType.Airlift.value
        assertFalse(unit.movement.canAirliftTo(cityCTile))
    }
}
