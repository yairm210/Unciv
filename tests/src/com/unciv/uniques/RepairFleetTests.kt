package com.unciv.uniques

import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.actions.UnitActions
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The Great Admiral's Repair Fleet (`for each [filter] unit within [n] tiles` on a unit trigger), on an all-ocean map.
 *  Runs under G&K, where the Great Admiral and its unique are defined. */
@RunWith(BaseTestRunner::class)
class RepairFleetTests {
    private val game = TestGame()
    private lateinit var civ: Civilization
    private lateinit var enemy: Civilization
    private lateinit var center: Tile
    private lateinit var admiral: MapUnit

    @Before
    fun setUp() {
        game.makeHexagonalMap(4)
        for (tile in game.tileMap.tileList) {
            tile.baseTerrain = Constants.ocean
            tile.setTransients()
        }
        civ = game.addCiv()
        enemy = game.addCiv()
        for (tech in game.ruleset.technologies.keys) civ.tech.addTechnology(tech) // lets land units embark on ocean
        center = game.getTile(HexCoord.Zero)
        admiral = game.addUnit("Great Admiral", civ, center)
    }

    private fun neighbor(index: Int) = center.neighbors.elementAt(index)

    private fun wounded(name: String, owner: Civilization, tile: Tile, health: Int = 40) =
        game.addUnit(name, owner, tile).also { it.health = health }

    /** The action the unit offers for its trigger unique, null if it is offered but not performable */
    private fun repairAction(unit: MapUnit): (() -> Unit)? =
        UnitActions.getUnitActions(unit, UnitActionType.TriggerUnique).single().action

    @Test
    fun `heals own naval and embarked units on this tile and adjacent tiles, and consumes the admiral`() {
        val stacked = wounded("Frigate", civ, center)
        val adjacentShip = wounded("Frigate", civ, neighbor(0))
        val embarked = wounded("Warrior", civ, neighbor(1))
        assertTrue(embarked.isEmbarked())

        repairAction(admiral)!!.invoke()

        assertEquals(100, stacked.health)
        assertEquals(100, adjacentShip.health)
        assertEquals(100, embarked.health)
        assertTrue(admiral.isDestroyed)
    }

    @Test
    fun `leaves out units that are too far, foreign or on land`() {
        val own = wounded("Frigate", civ, neighbor(0))
        val tooFar = wounded("Frigate", civ, center.getTilesAtDistance(2).first())
        val foreign = wounded("Frigate", enemy, neighbor(2))
        val landTile = neighbor(3).apply { baseTerrain = Constants.grassland; setTransients() }
        val onLand = wounded("Warrior", civ, landTile)
        assertFalse(onLand.isEmbarked())

        repairAction(admiral)!!.invoke()

        assertEquals(100, own.health)
        assertEquals(40, tooFar.health)
        assertEquals(40, foreign.health)
        assertEquals(40, onLand.health)
    }

    @Test
    fun `is offered but not performable when nothing in range is wounded`() {
        wounded("Frigate", civ, neighbor(0), health = 100)
        wounded("Frigate", civ, center.getTilesAtDistance(2).first())

        assertNull(repairAction(admiral))
    }

    /** A water civilian with [unique], placed where [center]'s admiral and its neighbors are out of the way */
    private fun healerWith(unique: String, x: Int): MapUnit {
        val type = game.createBaseUnit(game.ruleset.units["Great Admiral"]!!.unitType, unique)
        return game.addUnit(type.name, civ, game.getTile(x, 0))
    }

    private fun freeNeighbor(unit: MapUnit) = unit.currentTile.neighbors.first { it.militaryUnit == null }

    @Test
    fun `without other the triggering unit is a target too, with other it is left out`() {
        val includesSelf = healerWith("[This Unit] heals [50] HP <for each [Water] unit within [1] tiles> <by consuming this unit>", 3)
        includesSelf.health = 40
        val ship = wounded("Frigate", civ, freeNeighbor(includesSelf))
        repairAction(includesSelf)!!.invoke()
        assertEquals(90, includesSelf.health)
        assertEquals(90, ship.health)

        val leavesSelfOut = healerWith("[This Unit] heals [50] HP <for each [{other} {Water}] unit within [1] tiles> <by consuming this unit>", -3)
        leavesSelfOut.health = 40
        val otherShip = wounded("Frigate", civ, freeNeighbor(leavesSelfOut))
        repairAction(leavesSelfOut)!!.invoke()
        assertEquals(40, leavesSelfOut.health)
        assertEquals(90, otherShip.health)
    }

    @Test
    fun `distance 0 only covers the tile of the triggering unit`() {
        val healer = healerWith("[This Unit] heals [50] HP <for each [Water] unit within [0] tiles> <by consuming this unit>", 3)
        val sameTile = wounded("Frigate", civ, healer.currentTile)
        val adjacent = wounded("Frigate", civ, freeNeighbor(healer))

        repairAction(healer)!!.invoke()

        assertEquals(90, sameTile.health)
        assertEquals(40, adjacent.health)
    }
}
