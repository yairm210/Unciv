//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.map.tile.Tile
import com.unciv.testing.GdxTestRunner
import com.unciv.uniques.TestGame
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

    fun setTile(terrainName: String, position:Vector2) = setTile(listOf(terrainName), position)
//
    fun setTile(terrainNames: List<String>, position:Vector2): Tile {
        val tile = testGame.getTile(position)
        tile.baseTerrain = terrainNames.first()
        tile.setTerrainFeatures(terrainNames.subList(1,terrainNames.size))
        tile.setTransients()
        return tile
    }

    @Test
    fun canSeeNearbyForest() {
        val grassland = setTile("Grassland", Vector2(0f,0f))
        val forest = setTile(listOf("Grassland", "Forest"), Vector2(1f, 0f))
        val viewableTiles = grassland.getViewableTilesList(1)
        assert(viewableTiles.contains(forest))
    }

    @Test
    fun canSeeForestOverPlains() {
        val grassland = setTile("Grassland", Vector2(0f,0f))
        setTile("Plains", Vector2(1f,0f))
        val forest = setTile(listOf("Grassland", "Forest"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(viewableTiles.contains(forest))
    }

    @Test
    fun cannotSeePlainsOverForest() {
        val grassland = setTile("Grassland", Vector2(0f,0f))
        setTile(listOf("Grassland", "Forest"), Vector2(1f, 0f))
        val plains = setTile("Plains", Vector2(2f,0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(!viewableTiles.contains(plains))
    }

    @Test
    fun cannotSeeForestOverForest() {
        val grassland = setTile("Grassland", Vector2(0f,0f))
        setTile(listOf("Grassland", "Forest"), Vector2(1f, 0f))
        val plains = setTile(listOf("Plains", "Forest"), Vector2(2f,0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(!viewableTiles.contains(plains))
    }

    @Test
    fun canSeeHillOverPlains() {
        val grassland = setTile("Grassland", Vector2(0f,0f))
        setTile("Plains", Vector2(1f,0f))
        val hill = setTile(listOf("Grassland", "Hill"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(viewableTiles.contains(hill))
    }

    @Test
    fun cannotSeePlainsOverHill() {
        val grassland = setTile("Grassland", Vector2(0f,0f))
        setTile(listOf("Grassland", "Hill"), Vector2(1f, 0f))
        val plains = setTile("Plains", Vector2(2f,0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(!viewableTiles.contains(plains))
    }

    @Test
    fun cannotSeeHillOverHill() {
        val grassland = setTile("Grassland", Vector2(0f,0f))
        setTile(listOf("Grassland", "Hill"), Vector2(1f,0f))
        val hill = setTile(listOf("Grassland", "Hill"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(!viewableTiles.contains(hill))
    }


    @Test
    fun cannotSeeHillOverForest() {
        val grassland = setTile("Grassland", Vector2(0f,0f))
        setTile(listOf("Grassland", "Forest"), Vector2(1f,0f))
        val hill = setTile(listOf("Grassland", "Hill"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(!viewableTiles.contains(hill))
    }

    @Test
    fun cannotSeeForestOverHill() {
        val grassland = setTile("Grassland", Vector2(0f,0f))
        setTile(listOf("Grassland", "Hill"), Vector2(1f,0f))
        val hill = setTile(listOf("Grassland", "Forest"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(!viewableTiles.contains(hill))
    }

    @Test
    fun canSeeHillForestOverHill() {
        val grassland = setTile("Grassland", Vector2(0f,0f))
        setTile(listOf("Grassland", "Forest"), Vector2(1f,0f))
        val hill = setTile(listOf("Grassland", "Hill", "Forest"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(viewableTiles.contains(hill))
    }

    @Test
    fun canSeeMountainOverHill() {
        val grassland = setTile("Grassland", Vector2(0f,0f))
        setTile(listOf("Grassland", "Hill"), Vector2(1f,0f))
        val hill = setTile(listOf("Mountain"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(viewableTiles.contains(hill))
    }

    @Test
    fun cannotSeeMountainOverHillForest() {
        val grassland = setTile(listOf("Grassland", "Hill"), Vector2(0f,0f))
        setTile(listOf("Grassland", "Hill"), Vector2(0f,0f))
        setTile(listOf("Grassland", "Hill", "Forest"), Vector2(1f,0f))
        val mountain = setTile(listOf("Mountain"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(4)
        assert(!viewableTiles.contains(mountain))
    }


    @Test
    fun cannotSee3TilesAwayPlain() {
        val source = setTile("Grassland", Vector2(0f,0f))
        setTile("Grassland", Vector2(1f,0f))
        setTile("Grassland", Vector2(2f,0f))
        val beyondSight = setTile("Grassland", Vector2(3f,0f))

        val viewableTiles = source.getViewableTilesList(2)
        assert(!viewableTiles.contains(beyondSight))
    }

    @Test
    fun canSeeElevation3Tiles() {
        val source = setTile("Grassland", Vector2(0f,0f))
        setTile("Grassland", Vector2(1f,0f))
        setTile("Grassland", Vector2(2f,0f))
        val beyondSight = setTile(listOf("Grassland", "Hill"), Vector2(3f,0f))

        val viewableTiles = source.getViewableTilesList(2)
        assert(viewableTiles.contains(beyondSight))
    }

    @Test
    fun canSeeElevation3TilesEvenWithInvisibleIntermediate() {
        val source = setTile("Grassland", Vector2(0f,0f))
        setTile(listOf("Grassland", "Hill"), Vector2(1f,0f))
        val intermediate = setTile(listOf("Grassland", "Hill"), Vector2(2f,0f))
        val beyondSight = setTile(listOf("Grassland", "Hill", "Forest"), Vector2(3f,0f))

        val viewableTiles = source.getViewableTilesList(2)
        assert(viewableTiles.contains(beyondSight))
        assert(!viewableTiles.contains(intermediate))
    }

    @Test
    fun cannotSeeHiddenElevation3Tiles() {
        val source = setTile("Grassland", Vector2(0f,0f))
        setTile("Grassland", Vector2(1f,0f))
        setTile(listOf("Grassland", "Forest"), Vector2(2f,0f))
        val beyondSight = setTile(listOf("Grassland", "Hill"), Vector2(3f,0f))

        val viewableTiles = source.getViewableTilesList(2)
        assert(!viewableTiles.contains(beyondSight))
    }

    @Test
    fun canSeeButNotAttackHillForestOverHill() {
        val grassland = setTile("Grassland", Vector2(0f,0f))
        setTile(listOf("Grassland", "Hill"), Vector2(1f,0f))
        val hillForest = setTile(listOf("Grassland", "Hill", "Forest"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(viewableTiles.contains(hillForest))
        val attackableTiles = testGame.tileMap.getViewableTiles(grassland.position, 2, true)
        assert(!attackableTiles.contains(hillForest))
    }

    @Test
    fun canSeeAndAttackMountainOverHill() {
        val grassland = setTile("Grassland", Vector2(0f,0f))
        setTile(listOf("Grassland", "Hill"), Vector2(1f,0f))
        val mountain = setTile(listOf("Mountain"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(viewableTiles.contains(mountain))
        val attackableTiles = testGame.tileMap.getViewableTiles(grassland.position, 2, true)
        assert(attackableTiles.contains(mountain))
    }

}
