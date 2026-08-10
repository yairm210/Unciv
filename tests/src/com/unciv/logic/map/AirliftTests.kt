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

    private val airportUnique =
        "Can instantly move to [{your} {City center}] tiles up to [99] tiles away " +
            "<in [{your} {City center}] tiles> <for all movement>"

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(5)
        civ = testGame.addCiv()
        val cityA = testGame.addCity(civ, testGame.getTile(0, 0), replacePalace = true)
        val cityB = testGame.addCity(civ, testGame.getTile(3, 0), replacePalace = true)
        val airport = testGame.createBuilding(airportUnique)
        cityA.cityConstructions.addBuilding(airport)
        cityB.cityConstructions.addBuilding(airport)
        unit = testGame.addUnit("Warrior", civ, cityA.getCenterTile())
        civ.viewableTiles = testGame.tileMap.values.toSet()
    }

    private fun prepareInstantMove(forUnit: MapUnit = unit) {
        forUnit.cache.instantMoveUniques.clear()
        forUnit.cache.instantMoveUniques += forUnit.getMatchingUniques(UniqueType.CanInstantlyMoveTo, forUnit.cache.state)
        forUnit.cache.instantMoveUniques += forUnit.getMatchingUniques(UniqueType.MayParadropOld, forUnit.cache.state)
        val city = forUnit.getTile().getCity()
        if (city != null && city.civ == forUnit.civ && forUnit.baseUnit.isLandUnit) {
            forUnit.cache.instantMoveUniques += city.cityConstructions.builtBuildingUniqueMap
                .getMatchingUniques(UniqueType.CanInstantlyMoveTo, forUnit.cache.state)
        }
        forUnit.action = UnitActionType.Paradrop.value
    }

    @Test
    fun `airlift teleports to other airport city and consumes all movement`() {
        prepareInstantMove()
        val destination = civ.cities[1].getCenterTile()
        assertTrue(unit.movement.canInstantlyMoveTo(destination))

        unit.movement.moveToTile(destination)

        assertEquals(destination.position, unit.getTile().position)
        assertEquals(0f, unit.currentMovement)
        assertFalse(unit.isPreparingParadrop())
    }

    @Test
    fun `airlift unavailable without Can instantly move to building at destination`() {
        val cityCTile = testGame.getTile(0, 3)
        testGame.addCity(civ, cityCTile, replacePalace = true)
        prepareInstantMove()
        assertFalse(unit.movement.canInstantlyMoveTo(cityCTile))
    }

    @Test
    fun `unit Can instantly move to works without building`() {
        val para = testGame.addDefaultMeleeUnitWithUniques(
            civ,
            testGame.getTile(1, 1),
            "Can instantly move to [Land] tiles up to [5] tiles away <in [{Friendly} {Land}] tiles>"
        )
        civ.viewableTiles = testGame.tileMap.values.toSet()
        prepareInstantMove(para)

        val destination = testGame.getTile(2, 1)
        assertTrue(para.movement.canInstantlyMoveTo(destination))

        val movementBefore = para.currentMovement
        para.movement.moveToTile(destination)
        assertEquals(destination.position, para.getTile().position)
        assertEquals(movementBefore - 1f, para.currentMovement)
        assertEquals(1, para.attacksThisTurn)
    }
}
