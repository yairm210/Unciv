package com.unciv.logic.map.tile

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.GameContext

object TileNormalizer {

    fun normalizeToRuleset(tile: Tile, ruleset: Ruleset) {
        if (tile.naturalWonder != null && !ruleset.terrains.containsKey(tile.naturalWonder))
            tile.naturalWonder = null
        if (tile.naturalWonder != null) {
            val wonderTerrain = tile.getNaturalWonder()
            if (wonderTerrain.turnsInto != null) {
                tile.baseTerrain = wonderTerrain.turnsInto!!
                tile.removeTerrainFeatures()
            } else {
                tile.setTerrainFeatures(tile.terrainFeatures.filter { it in wonderTerrain.occursOn })
            }
            tile.resource = null
            tile.clearImprovement()
        }

        if (!ruleset.terrains.containsKey(tile.baseTerrain))
            tile.baseTerrain = ruleset.terrains.values.first { it.type == TerrainType.Land && !it.impassable }.name

        val newFeatures = ArrayList<String>()
        for (terrainFeature in tile.terrainFeatures) {
            val terrainFeatureObject = ruleset.terrains[terrainFeature]
                ?: continue
            if (terrainFeatureObject.occursOn.isNotEmpty() && !terrainFeatureObject.occursOn.contains(tile.baseTerrain))
                continue
            newFeatures.add(terrainFeature)
        }
        if (newFeatures.size != tile.terrainFeatures.size)
            tile.setTerrainFeatures(newFeatures)

        if (tile.resource != null && !ruleset.tileResources.containsKey(tile.resource)) tile.resource = null
        if (tile.resource != null) {
            val resourceObject = ruleset.tileResources[tile.resource]!!
            if (resourceObject.terrainsCanBeFoundOn.none { it == tile.baseTerrain || tile.terrainFeatures.contains(it) })
                tile.resource = null
        }

        // If we're checking this at gameInfo.setTransients, we can't check the top terrain
        if (tile.improvement != null) normalizeTileImprovement(tile, ruleset)
        if (tile.isWater || tile.isImpassible())
            tile.removeRoad()
    }

    private fun normalizeTileImprovement(tile: Tile, ruleset: Ruleset) {
        val improvementObject = ruleset.tileImprovements[tile.improvement]
            ?: return tile.clearImprovement()
        if (tile.improvementFunctions.canImprovementBeBuiltHere(improvementObject, gameContext = GameContext.IgnoreConditionals, isNormalizeCheck = true))
            return
        tile.clearImprovement()
    }

    private fun Tile.clearImprovement() {
        // This runs from mapgen, so don't go through the side-effect-triggering TileImprovementFunctions
        improvement = null
        stopWorkingOnImprovement()
    }
}
