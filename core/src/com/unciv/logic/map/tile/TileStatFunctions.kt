package com.unciv.logic.map.tile

import com.unciv.Constants
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.extensions.toPercent

class TileStatFunctions(val tileInfo: TileInfo) {

    fun getTileStats(observingCiv: CivilizationInfo?): Stats = getTileStats(tileInfo.getCity(), observingCiv)

    fun getTileStats(city: CityInfo?, observingCiv: CivilizationInfo?,
                     localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)
    ): Stats {
        var stats = tileInfo.getBaseTerrain().cloneStats()

        val stateForConditionals = StateForConditionals(civInfo = observingCiv, cityInfo = city, tile = tileInfo)

        for (terrainFeatureBase in tileInfo.terrainFeatureObjects) {
            when {
                terrainFeatureBase.hasUnique(UniqueType.NullifyYields) ->
                    return terrainFeatureBase.cloneStats()
                terrainFeatureBase.overrideStats -> stats = terrainFeatureBase.cloneStats()
                else -> stats.add(terrainFeatureBase)
            }
        }

        if (tileInfo.naturalWonder != null) {
            val wonderStats = tileInfo.getNaturalWonder().cloneStats()

            if (tileInfo.getNaturalWonder().overrideStats)
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
                if (!tileInfo.matchesTerrainFilter(tileType, observingCiv)) continue
                stats.add(unique.stats)
            }

            for (unique in localUniqueCache.get("StatsFromTilesWithout",
                city.getMatchingUniques(UniqueType.StatsFromTilesWithout, StateForConditionals.IgnoreConditionals))
            ) {
                if (
                        unique.conditionalsApply(stateForConditionals) &&
                        tileInfo.matchesTerrainFilter(unique.params[1]) &&
                        !tileInfo.matchesTerrainFilter(unique.params[2]) &&
                        city.matchesFilter(unique.params[3])
                )
                    stats.add(unique.stats)
            }
        }

        if (tileInfo.isAdjacentToRiver()) stats.gold++

        if (observingCiv != null) {
            // resource base
            if (tileInfo.hasViewableResource(observingCiv)) stats.add(tileInfo.tileResource)

            val improvement = tileInfo.getUnpillagedTileImprovement()
            if (improvement != null)
                stats.add(tileInfo.improvementFunctions.getImprovementStats(improvement, observingCiv, city, localUniqueCache))

            if (stats.gold != 0f && observingCiv.goldenAges.isGoldenAge())
                stats.gold++
        }
        if (tileInfo.isCityCenter()) {
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
    fun getTilePercentageStats(observingCiv: CivilizationInfo?, city: CityInfo?): Stats {
        val stats = Stats()
        val stateForConditionals = StateForConditionals(civInfo = observingCiv, cityInfo = city, tile = tileInfo)

        if (city != null) {
            for (unique in city.getMatchingUniques(UniqueType.StatPercentFromObject, stateForConditionals)) {
                val tileFilter = unique.params[2]
                if (tileInfo.matchesTerrainFilter(tileFilter, observingCiv))
                    stats[Stat.valueOf(unique.params[1])] += unique.params[0].toFloat()
            }

            for (unique in city.getMatchingUniques(UniqueType.AllStatsPercentFromObject, stateForConditionals)) {
                val tileFilter = unique.params[1]
                if (!tileInfo.matchesTerrainFilter(tileFilter, observingCiv)) continue
                val statPercentage = unique.params[0].toFloat()
                for (stat in Stat.values())
                    stats[stat] += statPercentage
            }

        } else if (observingCiv != null) {
            for (unique in observingCiv.getMatchingUniques(UniqueType.StatPercentFromObject, stateForConditionals)) {
                val tileFilter = unique.params[2]
                if (tileInfo.matchesTerrainFilter(tileFilter, observingCiv))
                    stats[Stat.valueOf(unique.params[1])] += unique.params[0].toFloat()
            }

            for (unique in observingCiv.getMatchingUniques(UniqueType.AllStatsPercentFromObject, stateForConditionals)) {
                val tileFilter = unique.params[1]
                if (!tileInfo.matchesTerrainFilter(tileFilter, observingCiv)) continue
                val statPercentage = unique.params[0].toFloat()
                for (stat in Stat.values())
                    stats[stat] += statPercentage
            }
        }

        return stats
    }

    fun getTileStartScore(): Float {
        var sum = 0f
        for (closeTile in tileInfo.getTilesInDistance(2)) {
            val tileYield = closeTile.stats.getTileStartYield(closeTile == tileInfo)
            sum += tileYield
            if (closeTile in tileInfo.neighbors)
                sum += tileYield
        }

        if (tileInfo.isHill())
            sum -= 2f
        if (tileInfo.isAdjacentToRiver())
            sum += 2f
        if (tileInfo.neighbors.any { it.baseTerrain == Constants.mountain })
            sum += 2f
        if (tileInfo.isCoastalTile())
            sum += 3f
        if (!tileInfo.isCoastalTile() && tileInfo.neighbors.any { it.isCoastalTile() })
            sum -= 7f

        return sum
    }

    private fun getTileStartYield(isCenter: Boolean): Float {
        var stats = tileInfo.getBaseTerrain().cloneStats()

        for (terrainFeatureBase in tileInfo.terrainFeatureObjects) {
            if (terrainFeatureBase.overrideStats)
                stats = terrainFeatureBase.cloneStats()
            else
                stats.add(terrainFeatureBase)
        }
        if (tileInfo.resource != null) stats.add(tileInfo.tileResource)

        if (stats.production < 0) stats.production = 0f
        if (isCenter) {
            if (stats.food < 2) stats.food = 2f
            if (stats.production < 1) stats.production = 1f
        }

        return stats.food + stats.production + stats.gold
    }

}
