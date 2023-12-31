package com.unciv.logic.map.tile

import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.components.extensions.toPercent

fun Iterable<Pair<String, Stats>>.toStats(): Stats {
    val stats = Stats()
    for ((_, statsToAdd) in this)
        stats.add(statsToAdd)
    return stats
}

class TileStatFunctions(val tile: Tile) {
    private val riverTerrain by lazy { tile.ruleset.terrains[Constants.river] }

    fun getTileStats(
        observingCiv: Civilization?,
        localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)
    ): Stats = getTileStats(tile.getCity(), observingCiv, localUniqueCache)

    fun getTileStats(city: City?, observingCiv: Civilization?,
                             localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)
    ): Stats {
        val stats = Stats()
        for (statsToAdd in getTileStatsBreakdown(city, observingCiv, localUniqueCache))
            stats.add(statsToAdd.second)

        for ((stat, value) in getTilePercentageStats(observingCiv, city, localUniqueCache)) {
            stats[stat] *= value.toPercent()
        }

        return stats
    }

    fun getTileStatsBreakdown(city: City?, observingCiv: Civilization?,
                              localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)
    ): List<Pair<String, Stats>> {
        val stateForConditionals = StateForConditionals(civInfo = observingCiv, city = city, tile = tile)
        val listOfStats = getTerrainStatsBreakdown(stateForConditionals)

        if (city != null) {
            val statsFromTilesUniques =
                localUniqueCache.forCityGetMatchingUniques(city, UniqueType.StatsFromTiles, stateForConditionals)
                    .filter { city.matchesFilter(it.params[2]) }

            val statsFromObjectsUniques = localUniqueCache.forCityGetMatchingUniques(
                city, UniqueType.StatsFromObject, stateForConditionals)

            for (unique in statsFromTilesUniques + statsFromObjectsUniques) {
                val tileType = unique.params[1]
                if (!tile.matchesTerrainFilter(tileType, observingCiv)) continue
                listOfStats.add("{${unique.sourceObjectName}} ({${unique.text}})" to unique.stats)
            }

            for (unique in localUniqueCache.forCityGetMatchingUniques(
                    city, UniqueType.StatsFromTilesWithout, stateForConditionals)) {
                if (
                        tile.matchesTerrainFilter(unique.params[1]) &&
                        !tile.matchesTerrainFilter(unique.params[2]) &&
                        city.matchesFilter(unique.params[3])
                )
                    listOfStats.add("{${unique.sourceObjectName}} ({${unique.text}})" to unique.stats)
            }
        }

        if (tile.isAdjacentToRiver()) {
            if (riverTerrain == null)
                listOfStats.add("River" to Stats(gold = 1f))  // Fallback for legacy mods
            else
                //TODO this is one approach to get these stats in - supporting only the Stats UniqueType.
                //     Alternatives: append riverTerrain to allTerrains, or append riverTerrain.uniques to
                //     the Tile's UniqueObjects/UniqueMap (while copying onl<e> base Stats directly here)
                listOfStats += getSingleTerrainStats(riverTerrain!!, stateForConditionals)
        }


        var minimumStats = if (tile.isCityCenter()) Stats.DefaultCityCenterMinimum else Stats.ZERO
        if (observingCiv != null) {
            // resource base
            if (tile.hasViewableResource(observingCiv)) listOfStats.add(tile.tileResource.name to tile.tileResource)

            val improvement = tile.getUnpillagedTileImprovement()
            if (improvement != null)
                listOfStats.add(improvement.name to getImprovementStats(improvement, observingCiv, city, localUniqueCache))

            if (listOfStats.toStats().gold != 0f && observingCiv.goldenAges.isGoldenAge())
                listOfStats.add("Golden Age" to Stats(gold = 1f))

            if (improvement != null) {
                val ensureMinUnique = improvement
                    .getMatchingUniques(UniqueType.EnsureMinimumStats, stateForConditionals)
                    .firstOrNull()
                if (ensureMinUnique != null) minimumStats = ensureMinUnique.stats
            }
        }

        val statsFromMinimum = missingFromMinimum(listOfStats.toStats(), minimumStats)
        listOfStats.add("Minimum" to statsFromMinimum)

        return listOfStats.filter { !it.second.isEmpty() }
    }

    /** Ensures each stat is >= [other].stat - modifies in place */
    private fun missingFromMinimum(current: Stats, minimumStats: Stats): Stats {
        // Note: Not `for ((stat, value) in other)` - that would skip zero values
        val missingStats = Stats()
        for (stat in Stat.values()) {
            if (current[stat] < minimumStats[stat]) missingStats[stat] = minimumStats[stat] - current[stat]
        }
        return missingStats
    }

    /** Gets stats of a single Terrain, unifying the Stats class a Terrain inherits and the Stats Unique
     *  @return A Stats reference, must not be mutated
     */
    private fun getSingleTerrainStats(terrain: Terrain, stateForConditionals: StateForConditionals): ArrayList<Pair<String, Stats>> {
        val list = arrayListOf(terrain.name to (terrain as Stats))

        for (unique in terrain.getMatchingUniques(UniqueType.Stats, stateForConditionals)) {
            list.add(terrain.name+": "+unique.text to unique.stats)
        }
        return list
    }

    /** Gets basic stats to start off [getTileStats] or [getTileStartYield], independently mutable result */
    fun getTerrainStatsBreakdown(stateForConditionals: StateForConditionals = StateForConditionals()): ArrayList<Pair<String, Stats>> {
        var list = ArrayList<Pair<String, Stats>>()

        // allTerrains iterates over base, natural wonder, then features
        for (terrain in tile.allTerrains) {
            val terrainStats = getSingleTerrainStats(terrain, stateForConditionals)
            when {
                terrain.hasUnique(UniqueType.NullifyYields, stateForConditionals) ->
                    return terrainStats
                terrain.overrideStats ->
                    list = terrainStats
                else ->
                    list += terrainStats
            }
        }
        return list
    }

    // Only gets the tile percentage bonus, not the improvement percentage bonus
    @Suppress("MemberVisibilityCanBePrivate")
    fun getTilePercentageStats(observingCiv: Civilization?, city: City?, uniqueCache: LocalUniqueCache): Stats {
        val stats = Stats()
        val stateForConditionals = StateForConditionals(civInfo = observingCiv, city = city, tile = tile)

        if (city != null) {
            val cachedStatPercentFromObjectCityUniques = uniqueCache.forCityGetMatchingUniques(
                city, UniqueType.StatPercentFromObject, stateForConditionals)

            for (unique in cachedStatPercentFromObjectCityUniques) {
                val tileFilter = unique.params[2]
                if (tile.matchesTerrainFilter(tileFilter, observingCiv))
                    stats[Stat.valueOf(unique.params[1])] += unique.params[0].toFloat()
            }

            val cachedAllStatPercentFromObjectCityUniques = uniqueCache.forCityGetMatchingUniques(
                city, UniqueType.AllStatsPercentFromObject, stateForConditionals)
            for (unique in cachedAllStatPercentFromObjectCityUniques) {
                val tileFilter = unique.params[1]
                if (!tile.matchesTerrainFilter(tileFilter, observingCiv)) continue
                val statPercentage = unique.params[0].toFloat()
                for (stat in Stat.values())
                    stats[stat] += statPercentage
            }

        } else if (observingCiv != null) {
            val cachedStatPercentFromObjectCivUniques = uniqueCache.forCivGetMatchingUniques(
                observingCiv, UniqueType.StatPercentFromObject, stateForConditionals)
            for (unique in cachedStatPercentFromObjectCivUniques) {
                val tileFilter = unique.params[2]
                if (tile.matchesTerrainFilter(tileFilter, observingCiv))
                    stats[Stat.valueOf(unique.params[1])] += unique.params[0].toFloat()
            }

            val cachedAllStatPercentFromObjectCivUniques = uniqueCache.forCivGetMatchingUniques(
                observingCiv, UniqueType.AllStatsPercentFromObject, stateForConditionals)
            for (unique in cachedAllStatPercentFromObjectCivUniques) {
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
        getTerrainStatsBreakdown().toStats().run {
            if (tile.resource != null) add(tile.tileResource)
            add(missingFromMinimum(this, minimumStats))
            food + production + gold
        }

    /** Returns the extra stats that we would get if we switched to this improvement
     * Can be negative if we're switching to a worse improvement */
    fun getStatDiffForImprovement(
        improvement: TileImprovement,
        observingCiv: Civilization,
        city: City?,
        cityUniqueCache: LocalUniqueCache = LocalUniqueCache(false)): Stats {

        val currentStats = getTileStats(city, observingCiv, cityUniqueCache)

        val tileClone = tile.clone()
        tileClone.setTerrainTransients()

        tileClone.changeImprovement(improvement.name)
        val futureStats = tileClone.stats.getTileStats(city, observingCiv, cityUniqueCache)

        return futureStats.minus(currentStats)
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
        uniqueCache: LocalUniqueCache
    ): Stats {
        val stats = Stats()

        fun statsFromTiles() {
            for (unique in improvement.getMatchingUniques(UniqueType.ImprovementStatsOnTile, conditionalState)) {
                if (tile.matchesFilter(unique.params[1])
                        || unique.params[1] == Constants.freshWater && tile.isAdjacentTo(Constants.freshWater)
                        || unique.params[1] == "non-fresh water" && !tile.isAdjacentTo(Constants.freshWater)
                )
                    stats.add(unique.stats)
            }
        }
        statsFromTiles()

        fun statsFromObject() {
            val uniques = uniqueCache.forCityGetMatchingUniques(city,
                UniqueType.StatsFromObject, conditionalState) +
                uniqueCache.forCityGetMatchingUniques(city,
                    UniqueType.StatsFromTiles, conditionalState).filter { city.matchesFilter(it.params[2]) }
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

        if (city != null) {
            val allStatPercentUniques = cityUniqueCache.forCityGetMatchingUniques(
                    city,
                    UniqueType.AllStatsPercentFromObject,
                    conditionalState
                )
            for (unique in allStatPercentUniques) {
                if (!improvement.matchesFilter(unique.params[1])) continue
                for (stat in Stat.values()) {
                    stats[stat] += unique.params[0].toFloat()
                }
            }

            val statPercentUniques = cityUniqueCache.forCityGetMatchingUniques(
                    city,
                    UniqueType.StatPercentFromObject,
                    conditionalState
                )

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
