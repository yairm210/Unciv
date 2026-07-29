package com.unciv.logic.map

import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Integer.min
import kotlin.math.max
import kotlin.text.get

@RunWith(GdxTestRunner::class)
class MapGenerationTests {
    private var ruleSet = Ruleset()
    
    @Test
    fun testAllMapGenerations() {
        RulesetCache.loadRulesets(noMods = true)
        ruleSet = RulesetCache.getVanillaRuleset()
        val mapGenerator = MapGenerator(ruleSet)
        for (mapType in MapType.allValues) {
            for (mapShape in MapShape.allValues) {
                for (worldWrap in listOf(true, false)) {
                    val mapParameters = MapParameters()
                    mapParameters.type = mapType
                    mapParameters.shape = mapShape
                    mapParameters.worldWrap = worldWrap
                    mapParameters.seed = 1
                    mapGenerator.generateMap(mapParameters)
                }
            }
        }
    }
    
    @Test
    fun terrainTileCountsReasonableForHexMaps() {
        RulesetCache.loadRulesets(noMods = true)
        ruleSet = RulesetCache.getVanillaRuleset()
        val terrainCounts = mutableMapOf<String,Int>()
        val featureCounts = mutableMapOf<String,Int>()
        // Terrain counts are non-deterministic, even with a seed, and WILDLY variable,
        // so we aggregate 10 Small maps to get more stable results.
        for (seed in 1L..20L) {
            val mapGenerator = MapGenerator(ruleSet)
            val mapParameters = MapParameters()
            mapParameters.type = MapType.perlin
            mapParameters.shape = MapShape.hexagonal
            mapParameters.mapSize = MapSize.Small
            mapParameters.worldWrap = true
            mapParameters.seed = seed
            val tileMap = mapGenerator.generateMap(mapParameters)
            for (tile in tileMap.tileList) {
                terrainCounts.compute(tile.baseTerrain) {_,oldCount -> 1 + (oldCount ?: 0)}
                for (feature in tile.terrainFeatures)
                    featureCounts.compute(feature) {_,oldCount -> 1 + (oldCount ?: 0)}
            }
        }
        println(terrainCounts) // if the test fails, make sure ALL the counts are listed.
        println(featureCounts)
        
        // These are not hard limits.
        // If the counts are *slightly* outside the range, then it's probably safe to extend the range.
        // The purpose is primarily to detect when the counts change *dramatically*.
        assertApproximately(terrainCounts["Ocean"], 4079)
        assertApproximately(terrainCounts["Coast"], 2443)
        assertApproximately( terrainCounts["Grassland"], 2140)
        assertApproximately(terrainCounts["Plains"], 2263)
        assertApproximately( terrainCounts["Tundra"], 1156)
        assertApproximately( terrainCounts["Desert"], 1038)
        assertApproximately(terrainCounts["Lakes"], 105)
        assertApproximately( terrainCounts["Mountain"], 212)
        assertApproximately( terrainCounts["Snow"], 664)
        assertApproximately(featureCounts["Ice"], 40)
        assertApproximately(featureCounts["Hill"], 1804)
        assertApproximately( featureCounts["Forest"], 1001)
        assertApproximately(featureCounts["Marsh"], 64)
        assertApproximately( featureCounts["Flood plains"], 47)
        assertApproximately( featureCounts["Oasis"], 34)
        assertApproximately(featureCounts["Atoll"], 135)
        assertApproximately( featureCounts["Jungle"], 393)
    }

    @Test
    fun terrainTileCountsReasonableForRectangleMaps() {
        RulesetCache.loadRulesets(noMods = true)
        ruleSet = RulesetCache.getVanillaRuleset()
        val terrainCounts = mutableMapOf<String,Int>()
        val featureCounts = mutableMapOf<String,Int>()
        // Terrain counts are non-deterministic, even with a seed, and WILDLY variable,
        // so we aggregate 10 Small maps to get more stable results.
        for (seed in 1L..20L) {
            val mapGenerator = MapGenerator(ruleSet)
            val mapParameters = MapParameters()
            mapParameters.type = MapType.perlin
            mapParameters.shape = MapShape.rectangular
            mapParameters.worldWrap = true
            mapParameters.seed = seed
            val tileMap = mapGenerator.generateMap(mapParameters)
            for (tile in tileMap.tileList) {
                terrainCounts.compute(tile.baseTerrain) {_,oldCount -> 1 + (oldCount ?: 0)}
                for (feature in tile.terrainFeatures)
                    featureCounts.compute(feature) {_,oldCount -> 1 + (oldCount ?: 0)}
            }
        }
        println(terrainCounts) // if the test fails, make sure ALL the counts are listed.
        println(featureCounts)
        
        // These are not hard limits.
        // If the counts are *slightly* outside the range, then it's probably safe to extend the range.
        // The purpose is primarily to detect when the counts change *dramatically*.
        assertApproximately(terrainCounts["Ocean"], 7826)
        assertApproximately(terrainCounts["Coast"], 4547)
        assertApproximately( terrainCounts["Grassland"], 3019)
        assertApproximately(terrainCounts["Plains"], 3168)
        assertApproximately( terrainCounts["Tundra"], 2345)
        assertApproximately( terrainCounts["Desert"], 1490)
        assertApproximately(terrainCounts["Lakes"], 118)
        assertApproximately( terrainCounts["Mountain"], 344)
        assertApproximately( terrainCounts["Snow"], 2663)
        assertApproximately(featureCounts["Ice"], 347)
        assertApproximately(featureCounts["Hill"], 3115)
        assertApproximately( featureCounts["Forest"], 1466)
        assertApproximately(featureCounts["Marsh"], 100)
        assertApproximately( featureCounts["Flood plains"], 78)
        assertApproximately( featureCounts["Oasis"], 64)
        assertApproximately(featureCounts["Atoll"], 248)
        assertApproximately( featureCounts["Jungle"], 511)
    }
    
    @Test
    fun terrainTileCountsReasonableForFlatEarthMaps() {
        RulesetCache.loadRulesets(noMods = true)
        ruleSet = RulesetCache.getVanillaRuleset()
        val terrainCounts = mutableMapOf<String,Int>()
        val featureCounts = mutableMapOf<String,Int>()
        // Terrain counts are non-deterministic, even with a seed, and WILDLY variable,
        // so we aggregate 10 Small maps to get more stable results.
        for (seed in 1L..20L) {
            val mapGenerator = MapGenerator(ruleSet)
            val mapParameters = MapParameters()
            mapParameters.type = MapType.perlin
            mapParameters.shape = MapShape.flatEarth
            mapParameters.worldWrap = true
            mapParameters.seed = seed
            
            val tileMap = mapGenerator.generateMap(mapParameters)
            for (tile in tileMap.tileList) {
                terrainCounts.compute(tile.baseTerrain) {_,oldCount -> 1 + (oldCount ?: 0)}
                for (feature in tile.terrainFeatures)
                    featureCounts.compute(feature) {_,oldCount -> 1 + (oldCount ?: 0)}
            }
        }
        println(terrainCounts) // if the test fails, make sure ALL the counts are listed.
        println(featureCounts)
        
        // These are not hard limits.
        // If the counts are *slightly* outside the range, then it's probably safe to extend the range.
        // The purpose is primarily to detect when the counts change *dramatically*.
        assertApproximately(terrainCounts["Ocean"], 8340)
        assertApproximately(terrainCounts["Coast"], 4847)
        assertApproximately( terrainCounts["Grassland"], 2771)
        assertApproximately(terrainCounts["Plains"], 2720)
        assertApproximately( terrainCounts["Tundra"], 1629)
        assertApproximately( terrainCounts["Desert"], 1350)
        assertApproximately(terrainCounts["Lakes"], 58)
        assertApproximately( terrainCounts["Mountain"], 906, 4.0) // flat earth has WIDLY unstable mountain counts
        assertApproximately( terrainCounts["Snow"], 2025)
        assertApproximately(featureCounts["Ice"], 1083)
        assertApproximately(featureCounts["Hill"], 2392)
        assertApproximately( featureCounts["Forest"], 1285)
        assertApproximately(featureCounts["Marsh"], 87)
        assertApproximately( featureCounts["Flood plains"], 84)
        assertApproximately( featureCounts["Oasis"], 48)
        assertApproximately(featureCounts["Atoll"], 239)
        assertApproximately( featureCounts["Jungle"], 526)
    }

    // Terrain generation is not supposed to be random, but it is.
    // Even when that is fixed, we should *still* use assertApproximately, because
    // we don't want the tests to break every time we make a change to map generation.
    inline fun assertApproximately(value: Int?, target: Int, ratio: Double=2.0) {
        val min = (target / ratio).toInt() - 1
        val max = (target * ratio).toInt() + 1
        Assert.assertTrue(
            "expected: $min to $max but was: $value",
            if (min == 0) value == null || value in 0..max
            else value != null && value in min..max
        )
    }
}
