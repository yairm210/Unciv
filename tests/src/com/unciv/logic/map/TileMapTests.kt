package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class TileMapTests {

    private var map = TileMap()
    private var tile1 = TileInfo()
    private var tile2 = TileInfo()
    private var tile3 = TileInfo()
    private var ruleSet = Ruleset()

    @Before
    fun initTheWorld() {
        RulesetCache.loadRulesets()
        ruleSet = RulesetCache.getVanillaRuleset()
        map = TileMap()

        tile1.position = Vector2(0f, 0f)
        tile1.tileMap = map
        tile1.ruleset = ruleSet

        tile2.position = Vector2(0f, 1f)
        tile2.tileMap = map
        tile2.ruleset = ruleSet

        tile3.position = Vector2(0f, 2f)
        tile3.tileMap = map
        tile3.ruleset = ruleSet

        map.tileMatrix.add(arrayListOf(tile1, tile2, tile3))
    }

    @Test
    fun canSeeOnFlatGround() {
        tile1.baseTerrain = Constants.grassland
        tile1.setTerrainTransients()
        tile2.baseTerrain = Constants.desert
        tile2.setTerrainTransients()
        tile3.baseTerrain = Constants.snow
        tile3.setTerrainTransients()

        val visibleTiles = map.getViewableTiles(tile1.position,2)
        Assert.assertTrue(tile2 in visibleTiles)
        Assert.assertTrue(tile3 in visibleTiles)
    }

    @Test
    fun canSeeOnFlatWater() {
        tile1.baseTerrain = Constants.ocean
        tile1.setTerrainTransients()
        tile2.baseTerrain = Constants.coast
        tile2.setTerrainTransients()
        tile3.baseTerrain = Constants.ocean
        tile3.setTerrainTransients()

        val visibleTiles = map.getViewableTiles(tile1.position,2)
        Assert.assertTrue(tile2 in visibleTiles)
        Assert.assertTrue(tile3 in visibleTiles)
    }

    @Test
    fun canSeeFromHills() {
        tile1.baseTerrain = Constants.hill
        tile1.setTerrainTransients()
        tile2.baseTerrain = Constants.grassland
        tile2.setTerrainTransients()
        tile3.baseTerrain = Constants.coast
        tile3.setTerrainTransients()

        val visibleTiles = map.getViewableTiles(tile1.position,2)
        Assert.assertTrue(tile2 in visibleTiles)
        Assert.assertTrue(tile3 in visibleTiles)
    }

    @Test
    fun canSeeOverForestFromHills() {
        tile1.baseTerrain = Constants.hill
        tile1.setTerrainTransients()
        tile2.baseTerrain = Constants.grassland
        tile2.setTerrainTransients()
        tile2.setTerrainFeatures(listOf(Constants.forest))
        tile3.baseTerrain = Constants.coast
        tile3.setTerrainTransients()

        val visibleTiles = map.getViewableTiles(tile1.position,2)
        Assert.assertTrue(tile2 in visibleTiles)
        Assert.assertTrue(tile3 in visibleTiles)
    }

    @Test
    fun canSeeMountainOverHills() {
        tile1.baseTerrain = Constants.grassland
        tile1.setTerrainTransients()
        tile2.baseTerrain = Constants.hill
        tile2.setTerrainTransients()
        tile3.baseTerrain = Constants.mountain
        tile3.setTerrainTransients()

        val visibleTiles = map.getViewableTiles(tile1.position,2)
        Assert.assertTrue(tile2 in visibleTiles)
        Assert.assertTrue(tile3 in visibleTiles)
    }

    @Test
    fun canSeeMountainFromForestOverHills() {
        tile1.baseTerrain = Constants.grassland
        tile1.setTerrainTransients()
        tile1.setTerrainFeatures(listOf(Constants.forest))
        tile2.baseTerrain = Constants.hill
        tile2.setTerrainTransients()
        tile3.baseTerrain = Constants.mountain
        tile3.setTerrainTransients()

        val visibleTiles = map.getViewableTiles(tile1.position,2)
        Assert.assertTrue(tile2 in visibleTiles)
        Assert.assertTrue(tile3 in visibleTiles)
    }

    @Test
    fun canSeeHillsFromHills() {
        tile1.baseTerrain = Constants.hill
        tile1.setTerrainTransients()
        tile2.baseTerrain = Constants.grassland
        tile1.setTerrainFeatures(listOf(Constants.forest))
        tile2.setTerrainTransients()
        tile3.baseTerrain = Constants.hill
        tile3.setTerrainTransients()

        val visibleTiles = map.getViewableTiles(tile1.position,2)
        Assert.assertTrue(tile2 in visibleTiles)
        Assert.assertTrue(tile3 in visibleTiles)
    }

    @Test
    fun canNOTSeeGrassOverHills() {
        tile1.baseTerrain = Constants.grassland
        tile1.setTerrainTransients()
        tile2.baseTerrain = Constants.hill
        tile2.setTerrainTransients()
        tile3.baseTerrain = Constants.grassland
        tile3.setTerrainTransients()

        val visibleTiles = map.getViewableTiles(tile1.position,2)
        Assert.assertTrue(tile2 in visibleTiles)
        Assert.assertFalse(tile3 in visibleTiles)
    }

    @Test
    fun canNOTSeeHillsOverHills() {
        tile1.baseTerrain = Constants.grassland
        tile1.setTerrainTransients()
        tile2.baseTerrain = Constants.hill
        tile2.setTerrainTransients()
        tile3.baseTerrain = Constants.hill
        tile3.setTerrainTransients()

        val visibleTiles = map.getViewableTiles(tile1.position,2)
        Assert.assertTrue(tile2 in visibleTiles)
        Assert.assertFalse(tile3 in visibleTiles)
    }

    @Test
    fun canNOTSeeOutThroughForest() {
        tile1.baseTerrain = Constants.grassland
        tile1.setTerrainTransients()
        tile1.setTerrainFeatures(listOf(Constants.forest))
        tile2.baseTerrain = Constants.grassland
        tile2.setTerrainTransients()
        tile2.setTerrainFeatures(listOf(Constants.forest))
        tile3.baseTerrain = Constants.grassland
        tile3.setTerrainTransients()

        val visibleTiles = map.getViewableTiles(tile1.position,2)
        Assert.assertTrue(tile2 in visibleTiles)
        Assert.assertFalse(tile3 in visibleTiles)
    }

    @Test
    fun canNOTSeeWaterOverJungle() {
        tile1.baseTerrain = Constants.coast
        tile1.setTerrainTransients()
        tile2.baseTerrain = Constants.grassland
        tile2.setTerrainTransients()
        tile2.setTerrainFeatures(listOf(Constants.forest))
        tile3.baseTerrain = Constants.coast
        tile3.setTerrainTransients()

        val visibleTiles = map.getViewableTiles(tile1.position,2)
        Assert.assertTrue(tile2 in visibleTiles)
        Assert.assertFalse(tile3 in visibleTiles)
    }

    @Test
    fun canSeeWaterOverFlatLand() {
        tile1.baseTerrain = Constants.coast
        tile1.setTerrainTransients()
        tile2.baseTerrain = Constants.grassland
        tile2.setTerrainTransients()
        tile3.baseTerrain = Constants.coast
        tile3.setTerrainTransients()

        val visibleTiles = map.getViewableTiles(tile1.position,2)
        Assert.assertTrue(tile2 in visibleTiles)
        Assert.assertTrue(tile3 in visibleTiles)
    }
}
