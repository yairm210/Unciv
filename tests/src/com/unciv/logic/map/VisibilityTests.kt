//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic.map

import com.unciv.Constants
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class VisibilityTests {

    val testGame = TestGame()

    @Before
    fun initTheWorld() {
        testGame.makeHexagonalMap(5)
    }
//

    @Test
    fun canSeeNearbyForest() {
        val grassland = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        val forest = testGame.setTileTerrainAndFeatures(HexCoord(1,0), Constants.grassland, Constants.forest)
        val viewableTiles = grassland.getViewableTilesList(1)
        assertTrue(viewableTiles.contains(forest))
    }

    @Test
    fun canSeeForestOverPlains() {
        val grassland = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrain(HexCoord(1,0), Constants.plains)
        val forest = testGame.setTileTerrainAndFeatures(HexCoord(2,0), Constants.grassland, Constants.forest)

        val viewableTiles = grassland.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(forest))
    }

    @Test
    fun cannotSeePlainsOverForest() {
        val grassland = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(HexCoord(1,0), Constants.grassland, Constants.forest)
        val plains = testGame.setTileTerrain(HexCoord(2,0), Constants.plains)

        val viewableTiles = grassland.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(plains))
    }

    @Test
    fun cannotSeeForestOverForest() {
        val grassland = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(HexCoord(1,0), Constants.grassland, Constants.forest)
        val plains = testGame.setTileTerrainAndFeatures(HexCoord(2,0), Constants.plains, Constants.forest)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(plains))
    }

    @Test
    fun canSeeHillOverPlains() {
        val grassland = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrain(HexCoord(1,0), Constants.plains)
        val hill = testGame.setTileTerrainAndFeatures(HexCoord(2,0), Constants.grassland, Constants.hill)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(hill))
    }

    @Test
    fun cannotSeePlainsOverHill() {
        val grassland = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(HexCoord(1,0), Constants.grassland, Constants.hill)
        val plains = testGame.setTileTerrain(HexCoord(2,0), Constants.plains)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(plains))
    }

    @Test
    fun cannotSeeHillOverHill() {
        val grassland = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(HexCoord(1,0), Constants.grassland, Constants.hill)
        val hill = testGame.setTileTerrainAndFeatures(HexCoord(2,0), Constants.grassland, Constants.hill)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(hill))
    }


    @Test
    fun cannotSeeHillOverForest() {
        val grassland = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(HexCoord(1,0), Constants.grassland, Constants.forest)
        val hill = testGame.setTileTerrainAndFeatures(HexCoord(2,0), Constants.grassland, Constants.hill)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(hill))
    }

    @Test
    fun cannotSeeForestOverHill() {
        val grassland = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(HexCoord(1,0), Constants.grassland, Constants.hill)
        val hill = testGame.setTileTerrainAndFeatures(HexCoord(2,0), Constants.grassland, Constants.forest)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(hill))
    }

    @Test
    fun canSeeHillForestOverHill() {
        val grassland = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(HexCoord(1,0), Constants.grassland, Constants.forest)
        val hill = testGame.setTileTerrainAndFeatures(HexCoord(2,0), Constants.grassland, Constants.hill, Constants.forest)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(hill))
    }

    @Test
    fun canSeeMountainOverHill() {
        val grassland = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(HexCoord(1,0), Constants.grassland, Constants.hill)
        val mountain = testGame.setTileTerrain(HexCoord(2,0), Constants.mountain)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(mountain))
    }

    @Test
    fun cannotSeeMountainOverHillForest() {
        val grassland = testGame.setTileTerrainAndFeatures(HexCoord.Zero, Constants.grassland, Constants.hill)
        testGame.setTileTerrainAndFeatures(HexCoord(1,0), Constants.grassland, Constants.hill, Constants.forest)
        val mountain = testGame.setTileTerrain(HexCoord(2,0), Constants.mountain)
        val viewableTiles = grassland.getViewableTilesList(4)

        assertFalse(viewableTiles.contains(mountain))
    }


    @Test
    fun cannotSee3TilesAwayPlain() {
        val source = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrain(HexCoord(1,0), Constants.grassland)
        testGame.setTileTerrain(HexCoord(2,0), Constants.grassland)
        val beyondSight = testGame.setTileTerrain(HexCoord(3,0), Constants.grassland)

        val viewableTiles = source.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(beyondSight))
    }

    @Test
    fun canSeeElevation3Tiles() {
        val source = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrain(HexCoord(1,0), Constants.grassland)
        testGame.setTileTerrain(HexCoord(2,0), Constants.grassland)
        val beyondSight = testGame.setTileTerrainAndFeatures(HexCoord(3,0), Constants.grassland, Constants.hill)

        val viewableTiles = source.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(beyondSight))
    }

    @Test
    fun canSeeElevation3TilesEvenWithInvisibleIntermediate() {
        val source = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(HexCoord(1,0), Constants.grassland, Constants.hill)
        val intermediate = testGame.setTileTerrainAndFeatures(HexCoord(2,0), Constants.grassland, Constants.hill)
        val beyondSight = testGame.setTileTerrainAndFeatures(HexCoord(3,0), Constants.grassland, Constants.hill, Constants.forest)

        val viewableTiles = source.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(beyondSight))
        assertFalse(viewableTiles.contains(intermediate))
    }

    @Test
    fun cannotSeeHiddenElevation3Tiles() {
        val source = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrain(HexCoord(1,0), Constants.grassland)
        testGame.setTileTerrainAndFeatures(HexCoord(2,0), Constants.grassland, Constants.forest)
        val beyondSight = testGame.setTileTerrainAndFeatures(HexCoord(3,0), Constants.grassland, Constants.hill)

        val viewableTiles = source.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(beyondSight))
    }

    @Test
    fun canSeeButNotAttackHillForestOverHill() {
        val grassland = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(HexCoord(1,0), Constants.grassland, Constants.hill)
        val hillForest = testGame.setTileTerrainAndFeatures(HexCoord(2,0), Constants.grassland, Constants.hill, Constants.forest)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(hillForest))

        val attackableTiles = testGame.tileMap.getViewableTiles(grassland.position, 2, true)

        assertFalse(attackableTiles.contains(hillForest))
    }

    @Test
    fun canSeeAndAttackMountainOverHill() {
        val grassland = testGame.setTileTerrain(HexCoord.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(HexCoord(1,0), Constants.grassland, Constants.hill)
        val mountain = testGame.setTileTerrain(HexCoord(2,0), Constants.mountain)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(mountain))

        val attackableTiles = testGame.tileMap.getViewableTiles(grassland.position, 2, true)

        assertTrue(attackableTiles.contains(mountain))
    }

}
