package com.unciv.logic.map.mapgenerator

import com.unciv.Constants
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class NaturalWonderGeneratorTests {
    private lateinit var game: TestGame
    private lateinit var wonder: Terrain
    private lateinit var location: Tile

    @Before
    fun setUp() {
        game = TestGame()
        game.makeHexagonalMap(3, Constants.grassland)
        wonder = game.ruleset.terrains["Rock of Gibraltar"]!!
        location = game.getTile(HexCoord.Zero)
    }

    @Test
    fun `Rock of Gibraltar converts connected Lakes outside its neighbor ring to Coast`() {
        val convertedNeighbor = game.getTile(1, 0)
        val lakeOutsideNeighborRing = game.getTile(2, 0)
        val endOfLakeComponent = game.getTile(3, 0)
        for (tile in listOf(convertedNeighbor, lakeOutsideNeighborRing, endOfLakeComponent))
            game.setTileTerrain(tile.position, "Lakes")

        NaturalWonderGenerator.placeNaturalWonder(wonder, location)

        for (tile in listOf(convertedNeighbor, lakeOutsideNeighborRing, endOfLakeComponent)) {
            assertEquals(Constants.coast, tile.baseTerrain)
            assertFalse(tile.neighbors.any { it.getBaseTerrain().hasUnique(UniqueType.FreshWater) })
        }
    }

    @Test
    fun `Rock of Gibraltar preserves filters and existing conversion clearing behavior`() {
        val land = game.getTile(1, 0)
        val existingCoast = game.setTileTerrain(HexCoord(0, 1), Constants.coast)
        val ocean = game.setTileTerrain(HexCoord(-1, 0), Constants.ocean)
        val mountain = game.setTileTerrain(HexCoord(0, -1), Constants.mountain)
        val unrelatedLake = game.setTileTerrain(HexCoord(-3, -3), "Lakes")
        game.setTileFeatures(land.position, Constants.forest)
        land.tileResource = game.ruleset.tileResources["Deer"]
        land.setImprovementBasic("Camp")

        NaturalWonderGenerator.placeNaturalWonder(wonder, location)

        assertEquals(Constants.coast, land.baseTerrain)
        assertTrue(land.isWater)
        assertTrue(land.terrainFeatures.isEmpty())
        assertNull(land.tileResource)
        assertNull(land.improvement)
        assertEquals(Constants.coast, existingCoast.baseTerrain)
        assertEquals(Constants.coast, ocean.baseTerrain)
        assertEquals(Constants.mountain, mountain.baseTerrain)
        assertEquals("Lakes", unrelatedLake.baseTerrain)
    }

    @Test
    fun `Rock of Gibraltar removes every river edge from all tiles converted to Coast`() {
        val convertedNeighbor = game.getTile(1, 0)
        val lakeOutsideNeighborRing = game.getTile(2, 0)
        game.setTileTerrain(convertedNeighbor.position, "Lakes")
        game.setTileTerrain(lakeOutsideNeighborRing.position, "Lakes")
        for (tile in listOf(convertedNeighbor, lakeOutsideNeighborRing))
            for (neighbor in tile.neighbors)
                tile.setConnectedByRiver(neighbor, true)

        NaturalWonderGenerator.placeNaturalWonder(wonder, location)

        for (tile in listOf(convertedNeighbor, lakeOutsideNeighborRing)) {
            assertEquals(Constants.coast, tile.baseTerrain)
            assertFalse(tile.neighbors.any { tile.isConnectedByRiver(it) })
        }
    }
}
