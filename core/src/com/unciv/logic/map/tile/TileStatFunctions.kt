package com.unciv.logic.map.tile

import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.extensions.toPercent

class TileStatFunctions(val tile: Tile) {

    fun getTileStats(observingCiv: Civilization?): Stats = getTileStats(tile.getCity(), observingCiv)

    fun getTileStats(city: City?, observingCiv: Civilization?,
                     localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)
    ): Stats {
        var stats = tile.getBaseTerrain().cloneStats()

        val stateForConditionals = StateForConditionals(civInfo = observingCiv, city = city, tile = tile)

        for (terrainFeatureBase in tile.terrainFeatureObjects) {
            when {
                terrainFeatureBase.hasUnique(UniqueType.NullifyYields) ->
                    return terrainFeatureBase.cloneStats()
                terrainFeatureBase.overrideStats -> stats = terrainFeatureBase.cloneStats()
                else -> stats.add(terrainFeatureBase)
            }
        }

        if (tile.naturalWonder != null) {
            val wonderStats = tile.getNaturalWonder().cloneStats()

            if (tile.getNaturalWonder().overrideStats)
                stats = wonderStats
            else
                stats.add(wonderStats)
        }

        if (city != null) {
            var tileUniques = city.getMatchingUniques(UniqueType.StatsFromTiles, StateForConditionals.IgnoreConditionals)
                .filter { city.matchesFilter(it.params[2]) }
            tileUniques += city.getMatchingUniques(UniqueType.StatsFromObject, StateForConditionals.IgnoreConditionals)
            for (unique in localUniqueCache.get("StatsFromTilesAndObjects", tileUniques)) {
                if (!unique.conditionalsApply(stateForConditionals)) continue
                val tileType = unique.params[1]
                if (!tile.matchesTerrainFilter(tileType, observingCiv)) continue
                stats.add(unique.stats)
            }

            for (unique in localUniqueCache.get("StatsFromTilesWithout",
                city.getMatchingUniques(UniqueType.StatsFromTilesWithout, StateForConditionals.IgnoreConditionals))
            ) {
                if (
                        unique.conditionalsApply(stateForConditionals) &&
                        tile.matchesTerrainFilter(unique.params[1]) &&
                        !tile.matchesTerrainFilter(unique.params[2]) &&
                        city.matchesFilter(unique.params[3])
                )
                    stats.add(unique.stats)
            }
        }

        if (tile.isAdjacentToRiver()) stats.gold++

        if (observingCiv != null) {
            // resource base
            if (tile.hasViewableResource(observingCiv)) stats.add(tile.tileResource)

            val improvement = tile.getUnpillagedTileImprovement()
            if (improvement != null)
                stats.add(tile.improvementFunctions.getImprovementStats(improvement, observingCiv, city, localUniqueCache))

            if (stats.gold != 0f && observingCiv.goldenAges.isGoldenAge())
                stats.gold++
        }
        if (tile.isCityCenter()) {
            if (stats.food < 2) stats.food = 2f
            if (stats.production < 1) stats.production = 1f
        }

        for ((stat, value) in stats)
            if (value < 0f) stats[stat] = 0f

        for ((stat, value) in getTilePercentageStats(observingCiv, city)) {
            stats[stat] *= value.toPercent()
        }

        return stats
    }

    // Only gets the tile percentage bonus, not the improvement percentage bonus
    @Suppress("MemberVisibilityCanBePrivate")
    fun getTilePercentageStats(observingCiv: Civilization?, city: City?): Stats {
        val stats = Stats()
        val stateForConditionals = StateForConditionals(civInfo = observingCiv, city = city, tile = tile)

        if (city != null) {
            for (unique in city.getMatchingUniques(UniqueType.StatPercentFromObject, stateForConditionals)) {
                val tileFilter = unique.params[2]
                if (tile.matchesTerrainFilter(tileFilter, observingCiv))
                    stats[Stat.valueOf(unique.params[1])] += unique.params[0].toFloat()
            }

            for (unique in city.getMatchingUniques(UniqueType.AllStatsPercentFromObject, stateForConditionals)) {
                val tileFilter = unique.params[1]
                if (!tile.matchesTerrainFilter(tileFilter, observingCiv)) continue
                val statPercentage = unique.params[0].toFloat()
                for (stat in Stat.values())
                    stats[stat] += statPercentage
            }

        } else if (observingCiv != null) {
            for (unique in observingCiv.getMatchingUniques(UniqueType.StatPercentFromObject, stateForConditionals)) {
                val tileFilter = unique.params[2]
                if (tile.matchesTerrainFilter(tileFilter, observingCiv))
                    stats[Stat.valueOf(unique.params[1])] += unique.params[0].toFloat()
            }

            for (unique in observingCiv.getMatchingUniques(UniqueType.AllStatsPercentFromObject, stateForConditionals)) {
                val tileFilter = unique.params[1]
                if (!tile.matchesTerrainFilter(tileFilter, observingCiv)) continue
                val statPercentage = unique.params[0].toFloat()
                for (stat in Stat.values())
                    stats[stat] += statPercentage
            }
        }

        return stats
    }

    fun getTileStartScore(): Float {
        var sum = 0f
        for (closeTile in tile.getTilesInDistance(2)) {
            val tileYield = closeTile.stats.getTileStartYield(closeTile == tile)
            sum += tileYield
            if (closeTile in tile.neighbors)
                sum += tileYield
        }

        if (tile.isHill())
            sum -= 2f
        if (tile.isAdjacentToRiver())
            sum += 2f
        if (tile.neighbors.any { it.baseTerrain == Constants.mountain })
            sum += 2f
        if (tile.isCoastalTile())
            sum += 3f
        if (!tile.isCoastalTile() && tile.neighbors.any { it.isCoastalTile() })
            sum -= 7f

        return sum
    }

    private fun getTileStartYield(isCenter: Boolean): Float {
        var stats = tile.getBaseTerrain().cloneStats()

        for (terrainFeatureBase in tile.terrainFeatureObjects) {
            if (terrainFeatureBase.overrideStats)
                stats = terrainFeatureBase.cloneStats()
            else
                stats.add(terrainFeatureBase)
        }
        if (tile.resource != null) stats.add(tile.tileResource)

        if (stats.production < 0) stats.production = 0f
        if (isCenter) {
            if (stats.food < 2) stats.food = 2f
            if (stats.production < 1) stats.production = 1f
        }

        return stats.food + stats.production + stats.gold
    }

}
