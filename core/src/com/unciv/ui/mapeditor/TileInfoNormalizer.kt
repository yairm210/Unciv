package com.unciv.ui.mapeditor

import com.unciv.logic.map.tile.TileInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.StateForConditionals

object TileInfoNormalizer {

    fun normalizeToRuleset(tileInfo: TileInfo, ruleset: Ruleset) {
        if (tileInfo.naturalWonder != null && !ruleset.terrains.containsKey(tileInfo.naturalWonder))
            tileInfo.naturalWonder = null
        if (tileInfo.naturalWonder != null) {
            tileInfo.baseTerrain = tileInfo.getNaturalWonder().turnsInto!!
            tileInfo.setTerrainFeatures(listOf())
            tileInfo.resource = null
            tileInfo.changeImprovement(null)
        }

        if (!ruleset.terrains.containsKey(tileInfo.baseTerrain))
            tileInfo.baseTerrain = ruleset.terrains.values.first { it.type == TerrainType.Land && !it.impassable }.name

        val newFeatures = ArrayList<String>()
        for (terrainFeature in tileInfo.terrainFeatures) {
            val terrainFeatureObject = ruleset.terrains[terrainFeature]
                ?: continue
            if (terrainFeatureObject.occursOn.isNotEmpty() && !terrainFeatureObject.occursOn.contains(tileInfo.baseTerrain))
                continue
            newFeatures.add(terrainFeature)
        }
        if (newFeatures.size != tileInfo.terrainFeatures.size)
            tileInfo.setTerrainFeatures(newFeatures)

        if (tileInfo.resource != null && !ruleset.tileResources.containsKey(tileInfo.resource)) tileInfo.resource = null
        if (tileInfo.resource != null) {
            val resourceObject = ruleset.tileResources[tileInfo.resource]!!
            if (resourceObject.terrainsCanBeFoundOn.none { it == tileInfo.baseTerrain || tileInfo.terrainFeatures.contains(it) })
                tileInfo.resource = null
        }

        // If we're checking this at gameInfo.setTransients, we can't check the top terrain
        if (tileInfo.improvement != null) normalizeTileImprovement(tileInfo, ruleset)
        if (tileInfo.isWater || tileInfo.isImpassible())
            tileInfo.removeRoad()
    }

    private fun normalizeTileImprovement(tileInfo: TileInfo, ruleset: Ruleset) {
        val improvementObject = ruleset.tileImprovements[tileInfo.improvement]
        if (improvementObject == null) {
            tileInfo.changeImprovement(null)
            return
        }
        tileInfo.changeImprovement(null) // Unset, and check if it can be reset. If so, do it, if not, invalid.
        if (tileInfo.improvementFunctions.canImprovementBeBuiltHere(improvementObject, stateForConditionals = StateForConditionals.IgnoreConditionals))
            tileInfo.changeImprovement(improvementObject.name)
    }
}
