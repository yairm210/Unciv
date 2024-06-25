//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.map.tile.Tile
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
        val grassland = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        val forest = testGame.setTileTerrainAndFeatures(Vector2(1f, 0f), Constants.grassland, Constants.forest)
        val viewableTiles = grassland.getViewableTilesList(1)
        assertTrue(viewableTiles.contains(forest))
    }

    @Test
    fun canSeeForestOverPlains() {
        val grassland = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrain(Vector2(1f,0f), Constants.plains)
        val forest = testGame.setTileTerrainAndFeatures(Vector2(2f, 0f), Constants.grassland, Constants.forest)

        val viewableTiles = grassland.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(forest))
    }

    @Test
    fun cannotSeePlainsOverForest() {
        val grassland = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(Vector2(1f, 0f), Constants.grassland, Constants.forest)
        val plains = testGame.setTileTerrain(Vector2(2f,0f), Constants.plains)

        val viewableTiles = grassland.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(plains))
    }

    @Test
    fun cannotSeeForestOverForest() {
        val grassland = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(Vector2(1f, 0f), Constants.grassland, Constants.forest)
        val plains = testGame.setTileTerrainAndFeatures(Vector2(2f,0f), Constants.plains, Constants.forest)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(plains))
    }

    @Test
    fun canSeeHillOverPlains() {
        val grassland = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrain(Vector2(1f,0f), Constants.plains)
        val hill = testGame.setTileTerrainAndFeatures(Vector2(2f, 0f), Constants.grassland, Constants.hill)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(hill))
    }

    @Test
    fun cannotSeePlainsOverHill() {
        val grassland = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(Vector2(1f, 0f), Constants.grassland, Constants.hill)
        val plains = testGame.setTileTerrain(Vector2(2f,0f), Constants.plains)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(plains))
    }

    @Test
    fun cannotSeeHillOverHill() {
        val grassland = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(Vector2(1f, 0f), Constants.grassland, Constants.hill)
        val hill = testGame.setTileTerrainAndFeatures(Vector2(2f, 0f), Constants.grassland, Constants.hill)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(hill))
    }


    @Test
    fun cannotSeeHillOverForest() {
        val grassland = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(Vector2(1f, 0f), Constants.grassland, Constants.forest)
        val hill = testGame.setTileTerrainAndFeatures(Vector2(2f, 0f), Constants.grassland, Constants.hill)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(hill))
    }

    @Test
    fun cannotSeeForestOverHill() {
        val grassland = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(Vector2(1f, 0f), Constants.grassland, Constants.hill)
        val hill = testGame.setTileTerrainAndFeatures(Vector2(2f, 0f), Constants.grassland, Constants.forest)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(hill))
    }

    @Test
    fun canSeeHillForestOverHill() {
        val grassland = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(Vector2(1f, 0f), Constants.grassland, Constants.forest)
        val hill = testGame.setTileTerrainAndFeatures(Vector2(2f, 0f), Constants.grassland, Constants.hill, Constants.forest)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(hill))
    }

    @Test
    fun canSeeMountainOverHill() {
        val grassland = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(Vector2(1f, 0f), Constants.grassland, Constants.hill)
        val mountain = testGame.setTileTerrain(Vector2(2f, 0f), Constants.mountain)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(mountain))
    }

    @Test
    fun cannotSeeMountainOverHillForest() {
        val grassland = testGame.setTileTerrainAndFeatures(Vector2.Zero, Constants.grassland, Constants.hill)
        testGame.setTileTerrainAndFeatures(Vector2(1f, 0f), Constants.grassland, Constants.hill, Constants.forest)
        val mountain = testGame.setTileTerrain(Vector2(2f, 0f), Constants.mountain)
        val viewableTiles = grassland.getViewableTilesList(4)

        assertFalse(viewableTiles.contains(mountain))
    }


    @Test
    fun cannotSee3TilesAwayPlain() {
        val source = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrain(Vector2(1f, 0f), Constants.grassland)
        testGame.setTileTerrain(Vector2(2f, 0f), Constants.grassland)
        val beyondSight = testGame.setTileTerrain(Vector2(3f, 0f), Constants.grassland)

        val viewableTiles = source.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(beyondSight))
    }

    @Test
    fun canSeeElevation3Tiles() {
        val source = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrain(Vector2(1f, 0f), Constants.grassland)
        testGame.setTileTerrain(Vector2(2f, 0f), Constants.grassland)
        val beyondSight = testGame.setTileTerrainAndFeatures(Vector2(3f, 0f), Constants.grassland, Constants.hill)

        val viewableTiles = source.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(beyondSight))
    }

    @Test
    fun canSeeElevation3TilesEvenWithInvisibleIntermediate() {
        val source = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(Vector2(1f, 0f), Constants.grassland, Constants.hill)
        val intermediate = testGame.setTileTerrainAndFeatures(Vector2(2f, 0f), Constants.grassland, Constants.hill)
        val beyondSight = testGame.setTileTerrainAndFeatures(Vector2(3f, 0f), Constants.grassland, Constants.hill, Constants.forest)

        val viewableTiles = source.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(beyondSight))
        assertFalse(viewableTiles.contains(intermediate))
    }

    @Test
    fun cannotSeeHiddenElevation3Tiles() {
        val source = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrain(Vector2(1f, 0f), Constants.grassland)
        testGame.setTileTerrainAndFeatures(Vector2(2f, 0f), Constants.grassland, Constants.forest)
        val beyondSight = testGame.setTileTerrainAndFeatures(Vector2(3f, 0f), Constants.grassland, Constants.hill)

        val viewableTiles = source.getViewableTilesList(2)

        assertFalse(viewableTiles.contains(beyondSight))
    }

    @Test
    fun canSeeButNotAttackHillForestOverHill() {
        val grassland = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(Vector2(1f, 0f), Constants.grassland, Constants.hill)
        val hillForest = testGame.setTileTerrainAndFeatures(Vector2(2f, 0f), Constants.grassland, Constants.hill, Constants.forest)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(hillForest))

        val attackableTiles = testGame.tileMap.getViewableTiles(grassland.position, 2, true)

        assertFalse(attackableTiles.contains(hillForest))
    }

    @Test
    fun canSeeAndAttackMountainOverHill() {
        val grassland = testGame.setTileTerrain(Vector2.Zero, Constants.grassland)
        testGame.setTileTerrainAndFeatures(Vector2(1f, 0f), Constants.grassland, Constants.hill)
        val mountain = testGame.setTileTerrain(Vector2(2f, 0f), Constants.mountain)
        val viewableTiles = grassland.getViewableTilesList(2)

        assertTrue(viewableTiles.contains(mountain))

        val attackableTiles = testGame.tileMap.getViewableTiles(grassland.position, 2, true)

        assertTrue(attackableTiles.contains(mountain))
    }

}
