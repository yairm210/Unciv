package com.unciv.logic.map.tile

import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.components.extensions.toPercent

class TileStatFunctions(val tile: Tile) {

    fun getTileStats(observingCiv: Civilization?): Stats = getTileStats(tile.getCity(), observingCiv)

    fun getTileStats(city: City?, observingCiv: Civilization?,
                     localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)
    ): Stats {
        val stats = getTerrainStats()
        var minimumStats = if (tile.isCityCenter()) Stats.DefaultCityCenterMinimum else Stats.ZERO

        val stateForConditionals = StateForConditionals(civInfo = observingCiv, city = city, tile = tile)

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
                stats.add(getImprovementStats(improvement, observingCiv, city, localUniqueCache))

            if (stats.gold != 0f && observingCiv.goldenAges.isGoldenAge())
                stats.gold++

            if (improvement != null) {
                val ensureMinUnique = improvement
                    .getMatchingUniques(UniqueType.EnsureMinimumStats, stateForConditionals)
                    .firstOrNull()
                if (ensureMinUnique != null) minimumStats = ensureMinUnique.stats
            }
        }

        stats.coerceAtLeast(minimumStats)  // Minimum 0 or as defined by City center

        for ((stat, value) in getTilePercentageStats(observingCiv, city)) {
            stats[stat] *= value.toPercent()
        }

        return stats
    }

    /** Ensures each stat is >= [other].stat - modifies in place */
    private fun Stats.coerceAtLeast(other: Stats) {
        for ((stat, value) in other)
            if (this[stat] < value) this[stat] = value
    }

    /** Gets basic stats to start off [getTileStats] or [getTileStartYield], independently mutable result */
    private fun getTerrainStats(): Stats {
        var stats: Stats? = null

        // allTerrains iterates over base, natural wonder, then features
        for (terrain in tile.allTerrains) {
            when {
                terrain.hasUnique(UniqueType.NullifyYields) ->
                    return terrain.cloneStats()
                terrain.overrideStats || stats == null ->
                    stats = terrain.cloneStats()
                else ->
                    stats.add(terrain)
            }
        }
        return stats ?: Stats.ZERO // For tests
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

    fun getTileStartScore(cityCenterMinStats: Stats): Float {
        var sum = 0f
        for (closeTile in tile.getTilesInDistance(2)) {
            val tileYield = closeTile.stats.getTileStartYield(
                if (closeTile == tile) cityCenterMinStats else Stats.ZERO
            )
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

    private fun getTileStartYield(minimumStats: Stats) =
        getTerrainStats().run {
            if (tile.resource != null) add(tile.tileResource)
            coerceAtLeast(minimumStats)
            food + production + gold
        }


    // Also multiplies the stats by the percentage bonus for improvements (but not for tiles)
    fun getImprovementStats(
        improvement: TileImprovement,
        observingCiv: Civilization,
        city: City?,
        cityUniqueCache: LocalUniqueCache = LocalUniqueCache(false)
    ): Stats {
        val stats = improvement.cloneStats()
        if (tile.hasViewableResource(observingCiv) && tile.tileResource.isImprovedBy(improvement.name)
                && tile.tileResource.improvementStats != null
        )
            stats.add(tile.tileResource.improvementStats!!.clone()) // resource-specific improvement

        val conditionalState = StateForConditionals(civInfo = observingCiv, city = city, tile = tile)
        for (unique in improvement.getMatchingUniques(UniqueType.Stats, conditionalState)) {
            stats.add(unique.stats)
        }

        for (unique in improvement.getMatchingUniques(UniqueType.ImprovementStatsForAdjacencies, conditionalState)) {
            val adjacent = unique.params[1]
            val numberOfBonuses = tile.neighbors.count {
                it.matchesFilter(adjacent, observingCiv)
                        || it.getUnpillagedRoad().name == adjacent
            }
            stats.add(unique.stats.times(numberOfBonuses.toFloat()))
        }

        if (city != null) stats.add(getImprovementStatsForCity(improvement, city, conditionalState, cityUniqueCache))

        for ((stat, value) in getImprovementPercentageStats(improvement, observingCiv, city, cityUniqueCache)) {
            stats[stat] *= value.toPercent()
        }

        return stats
    }

    private fun getImprovementStatsForCity(
        improvement: TileImprovement,
        city: City,
        conditionalState: StateForConditionals,
        cityUniqueCache: LocalUniqueCache
    ): Stats {
        val stats = Stats()

        fun statsFromTiles(){
            // Since the conditionalState contains the current tile, it is different for each tile,
            //  therefore if we want the cache to be useful it needs to hold the pre-filtered uniques,
            //  and then for each improvement we'll filter the uniques locally.
            //  This is still a MASSIVE save of RAM!
            val tileUniques = cityUniqueCache.get(UniqueType.StatsFromTiles.name,
                city.getMatchingUniques(UniqueType.StatsFromTiles, StateForConditionals.IgnoreConditionals)
                    .filter { city.matchesFilter(it.params[2]) }) // These are the uniques for all improvements for this city,
                .filter { it.conditionalsApply(conditionalState) } // ...and this is those with applicable conditions
            val improvementUniques =
                    improvement.getMatchingUniques(UniqueType.ImprovementStatsOnTile, conditionalState)

            for (unique in tileUniques + improvementUniques) {
                if (improvement.matchesFilter(unique.params[1])
                        || unique.params[1] == Constants.freshWater && tile.isAdjacentTo(Constants.freshWater)
                        || unique.params[1] == "non-fresh water" && !tile.isAdjacentTo(Constants.freshWater)
                )
                    stats.add(unique.stats)
            }
        }
        statsFromTiles()

        fun statsFromObject() {
            // Same as above - cache holds unfiltered uniques for the city, while we use only the filtered ones
            val uniques = cityUniqueCache.get(UniqueType.StatsFromObject.name,
                city.getMatchingUniques(UniqueType.StatsFromObject, StateForConditionals.IgnoreConditionals))
                .filter { it.conditionalsApply(conditionalState) }
            for (unique in uniques) {
                if (improvement.matchesFilter(unique.params[1])) {
                    stats.add(unique.stats)
                }
            }
        }
        statsFromObject()
        return stats
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun getImprovementPercentageStats(
        improvement: TileImprovement,
        observingCiv: Civilization,
        city: City?,
        cityUniqueCache: LocalUniqueCache
    ): Stats {
        val stats = Stats()
        val conditionalState = StateForConditionals(civInfo = observingCiv, city = city, tile = tile)

        // I would love to make an interface 'canCallMatchingUniques'
        // from which both cityInfo and CivilizationInfo derive, so I don't have to duplicate all this code
        // But something something too much for this PR.

        if (city != null) {
            // As above, since the conditional is tile-dependant,
            //  we save uniques in the cache without conditional filtering, and use only filtered ones
            val allStatPercentUniques = cityUniqueCache.get(UniqueType.AllStatsPercentFromObject.name,
                city.getMatchingUniques(UniqueType.AllStatsPercentFromObject, StateForConditionals.IgnoreConditionals))
                .filter { it.conditionalsApply(conditionalState) }
            for (unique in allStatPercentUniques) {
                if (!improvement.matchesFilter(unique.params[1])) continue
                for (stat in Stat.values()) {
                    stats[stat] += unique.params[0].toFloat()
                }
            }

            // Same trick different unique - not sure if worth generalizing this 'late apply' of conditions?
            val statPercentUniques = cityUniqueCache.get(UniqueType.StatPercentFromObject.name,
                city.getMatchingUniques(UniqueType.StatPercentFromObject, StateForConditionals.IgnoreConditionals))
                .filter { it.conditionalsApply(conditionalState) }

            for (unique in statPercentUniques) {
                if (!improvement.matchesFilter(unique.params[2])) continue
                val stat = Stat.valueOf(unique.params[1])
                stats[stat] += unique.params[0].toFloat()
            }

        } else {
            for (unique in observingCiv.getMatchingUniques(UniqueType.AllStatsPercentFromObject, conditionalState)) {
                if (!improvement.matchesFilter(unique.params[1])) continue
                for (stat in Stat.values()) {
                    stats[stat] += unique.params[0].toFloat()
                }
            }
            for (unique in observingCiv.getMatchingUniques(UniqueType.StatPercentFromObject, conditionalState)) {
                if (!improvement.matchesFilter(unique.params[2])) continue
                val stat = Stat.valueOf(unique.params[1])
                stats[stat] += unique.params[0].toFloat()
            }
        }

        return stats
    }

}
