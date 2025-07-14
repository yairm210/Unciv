package com.unciv.logic.map.mapgenerator.mapregions

import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.math.max
import kotlin.math.min

// Holds a bunch of tile info that is only interesting during map gen
class MapGenTileData(val tile: Tile, val region: Region?, ruleset: Ruleset) {
    var closeStartPenalty = 0
    val impacts = HashMap<MapRegions.ImpactType, Int>()
    var isFood = false
        private set
    var isProd = false
        private set
    var isGood = false
        private set
    var isJunk = false
        private set
    var isTwoFromCoast = false
        private set

    var isGoodStart = true
    var startScore = 0

    init {
        evaluate(ruleset)
    }

    fun addCloseStartPenalty(penalty: Int) {
        if (closeStartPenalty == 0)
            closeStartPenalty = penalty
        else {
            // Multiple overlapping values - take the higher one and add 20 %
            closeStartPenalty = max(closeStartPenalty, penalty)
            closeStartPenalty = min(97, (closeStartPenalty * 1.2f).toInt())
        }
    }

    /** Populates all private-set fields */
    private fun evaluate(ruleset: Ruleset) {
        // Check if we are two tiles from coast (a bad starting site)
        if (!tile.isCoastalTile() && tile.neighbors.any { it.isCoastalTile() })
            isTwoFromCoast = true

        // Check first available out of unbuildable features, then other features, then base terrain
        val terrainToCheck = if (tile.terrainFeatures.isEmpty()) tile.getBaseTerrain()
        else tile.terrainFeatureObjects.firstOrNull { it.unbuildable }
            ?: tile.terrainFeatureObjects.first()

        // Add all applicable qualities
        for (unique in terrainToCheck.getMatchingUniques(
            UniqueType.HasQuality,
            GameContext(region = region)
        )) {
            when (unique.params[0]) {
                "Food" -> isFood = true
                "Desirable" -> isGood = true
                "Production" -> isProd = true
                "Undesirable" -> isJunk = true
            }
        }

        // Were there in fact no explicit qualities defined for any region at all? If so let's guess at qualities to preserve mod compatibility.
        if (terrainToCheck.uniqueObjects.none { it.type == UniqueType.HasQuality }) {
            if (tile.isWater) return // Most water type tiles have no qualities

            // is it junk???
            if (terrainToCheck.impassable) {
                isJunk = true
                return // Don't bother checking the rest, junk is junk
            }

            // Take possible improvements into account
            val improvements = ruleset.tileImprovements.values.filter {
                terrainToCheck.name in it.terrainsCanBeBuiltOn &&
                    it.uniqueTo == null &&
                    !it.hasUnique(UniqueType.GreatImprovement)
            }

            val maxFood = terrainToCheck.food + (improvements.maxOfOrNull { it.food } ?: 0f)
            val maxProd = terrainToCheck.production + (improvements.maxOfOrNull { it.production } ?: 0f)
            val bestImprovementValue = improvements.maxOfOrNull { it.food + it.production + it.gold + it.culture + it.science + it.faith } ?: 0f
            val maxOverall = terrainToCheck.food + terrainToCheck.production + terrainToCheck.gold +
                terrainToCheck.culture + terrainToCheck.science + terrainToCheck.faith + bestImprovementValue

            if (maxFood >= 2) isFood = true
            if (maxProd >= 2) isProd = true
            if (maxOverall >= 3) isGood = true
        }
    }
}
