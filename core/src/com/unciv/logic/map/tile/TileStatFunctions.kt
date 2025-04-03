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
import java.util.EnumMap

fun List<Pair<String, Stats>>.toStats(): Stats {
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

    fun getTileStats(
        city: City?, observingCiv: Civilization?,
        localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)
    ): Stats {
        val statsBreakdown = getTileStatsBreakdown(city, observingCiv, localUniqueCache)

        val improvement = tile.getUnpillagedImprovement()
        val road = tile.getUnpillagedRoad()

        val percentageStats = getTilePercentageStats(observingCiv, city, localUniqueCache)
        for (stats in statsBreakdown) {
            val tileType = when (stats.first) {
                improvement -> TilePercentageCategory.Improvement
                road.name -> TilePercentageCategory.Road
                else -> TilePercentageCategory.Terrain
            }
            for ((stat, value) in percentageStats[tileType]!!)
                stats.second[stat] *= value.toPercent()
        }

        return statsBreakdown.toStats()
    }

    fun getTileStatsBreakdown(city: City?, observingCiv: Civilization?,
                              localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)
    ): List<Pair<String, Stats>> {
        val stateForConditionals = StateForConditionals(civInfo = observingCiv, city = city, tile = tile)
        val listOfStats = getTerrainStatsBreakdown(stateForConditionals)

        val improvement = tile.getUnpillagedTileImprovement()
        val improvementStats = improvement?.cloneStats() ?: Stats.ZERO // If improvement==null, will never be added to

        val road = tile.getUnpillagedRoadImprovement()
        val roadStats = road?.cloneStats() ?: Stats.ZERO

        if (city != null) {
            val statsFromTilesUniques =
                localUniqueCache.forCityGetMatchingUniques(city, UniqueType.StatsFromTiles, stateForConditionals)
                    .filter { city.matchesFilter(it.params[2]) }

            val statsFromObjectsUniques = localUniqueCache.forCityGetMatchingUniques(
                city, UniqueType.StatsFromObject, stateForConditionals)

            val statsFromTilesWithoutUniques = localUniqueCache.forCityGetMatchingUniques(
                city, UniqueType.StatsFromTilesWithout, stateForConditionals)
                .filter { city.matchesFilter(it.params[3]) && !tile.matchesFilter(it.params[2]) }

            for (unique in statsFromTilesUniques + statsFromObjectsUniques + statsFromTilesWithoutUniques) {
                val tileType = unique.params[1]
                if (improvement != null && improvement.matchesFilter(tileType, stateForConditionals))
                    improvementStats.add(unique.stats)
                else if (tile.matchesFilter(tileType, observingCiv))
                    listOfStats.add("{${unique.sourceObjectName}} ({${unique.getDisplayText()}})" to unique.stats)
                else if (road != null && road.matchesFilter(tileType, stateForConditionals))
                    roadStats.add(unique.stats)
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

            if (improvement != null)
                improvementStats.add(getExtraImprovementStats(improvement, observingCiv, city))

            if (road != null)
                roadStats.add(getExtraImprovementStats(road, observingCiv, city))

            if (improvement != null) {
                val ensureMinUnique = improvement
                    .getMatchingUniques(UniqueType.EnsureMinimumStats, stateForConditionals)
                    .firstOrNull()
                if (ensureMinUnique != null) minimumStats = ensureMinUnique.stats
            }
        }

        if (road != null) listOfStats.add(road.name to roadStats)
        if (improvement != null) listOfStats.add(improvement.name to improvementStats)

        val statsFromMinimum = missingFromMinimum(listOfStats.toStats(), minimumStats)
        listOfStats.add("Minimum" to statsFromMinimum)

        if (observingCiv != null &&
            listOfStats.toStats().gold != 0f && observingCiv.goldenAges.isGoldenAge())
            listOfStats.add(observingCiv.nation.goldenAgeName to Stats(gold = 1f))

        // To ensure that the original stats (in uniques, terrains, etc) are not modified in getTileStats, we clone them all
        return listOfStats.filter { !it.second.isEmpty() }.map { it.first to it.second.clone() }
    }

    /** Ensures each stat is >= [minimumStats].stat - modifies in place */
    private fun missingFromMinimum(current: Stats, minimumStats: Stats): Stats {
        // Note: Not `for ((stat, value) in other)` - that would skip zero values
        val missingStats = Stats()
        for (stat in Stat.entries) {
            if (current[stat] < minimumStats[stat])
                missingStats[stat] = minimumStats[stat] - current[stat]
        }
        return missingStats
    }

    /** Gets stats of a single Terrain, unifying the Stats class a Terrain inherits and the Stats Unique
     *  @return A Stats reference, must not be mutated
     */
    private fun getSingleTerrainStats(terrain: Terrain, stateForConditionals: StateForConditionals): ArrayList<Pair<String, Stats>> {
        val list = arrayListOf(terrain.name to (terrain as Stats))

        for (unique in terrain.getMatchingUniques(UniqueType.Stats, stateForConditionals)) {
            list.add(terrain.name+": "+unique.getDisplayText() to unique.stats)
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
    
    enum class TilePercentageCategory{
        Terrain,
        Improvement,
        Road
    }

    // Only gets the tile percentage bonus, not the improvement percentage bonus
    @Suppress("MemberVisibilityCanBePrivate")
    fun getTilePercentageStats(observingCiv: Civilization?, city: City?, uniqueCache: LocalUniqueCache): EnumMap<TilePercentageCategory, Stats> {
        val terrainStats = Stats()
        val stateForConditionals = StateForConditionals(civInfo = observingCiv, city = city, tile = tile)

        val improvement = tile.getUnpillagedTileImprovement()
        val improvementStats = Stats()

        val road = tile.getUnpillagedRoadImprovement()
        val roadStats = Stats()

        fun addStats(filter: String, stat: Stat, amount: Float) {
            if (improvement != null && improvement.matchesFilter(filter, stateForConditionals))
                improvementStats.add(stat, amount)
            else if (tile.matchesFilter(filter, observingCiv))
                terrainStats.add(stat, amount)
            else if (road != null && road.matchesFilter(filter, stateForConditionals))
                roadStats.add(stat, amount)
        }

        if (city != null) {
            val cachedStatPercentFromObjectCityUniques = uniqueCache.forCityGetMatchingUniques(
                city, UniqueType.StatPercentFromObject, stateForConditionals)

            for (unique in cachedStatPercentFromObjectCityUniques) {
                addStats(unique.params[2], Stat.valueOf(unique.params[1]), unique.params[0].toFloat())
            }

            val cachedAllStatPercentFromObjectCityUniques = uniqueCache.forCityGetMatchingUniques(
                city, UniqueType.AllStatsPercentFromObject, stateForConditionals)
            for (unique in cachedAllStatPercentFromObjectCityUniques) {
                for (stat in Stat.entries)
                    addStats(unique.params[1], stat, unique.params[0].toFloat())
            }

        } else if (observingCiv != null) {
            val cachedStatPercentFromObjectCivUniques = uniqueCache.forCivGetMatchingUniques(
                observingCiv, UniqueType.StatPercentFromObject, stateForConditionals)
            for (unique in cachedStatPercentFromObjectCivUniques) {
                addStats(unique.params[2], Stat.valueOf(unique.params[1]), unique.params[0].toFloat())
            }

            val cachedAllStatPercentFromObjectCivUniques = uniqueCache.forCivGetMatchingUniques(
                observingCiv, UniqueType.AllStatsPercentFromObject, stateForConditionals)
            for (unique in cachedAllStatPercentFromObjectCivUniques) {
                for (stat in Stat.entries)
                    addStats(unique.params[1], stat, unique.params[0].toFloat())
            }
        }
        
        return EnumMap<TilePercentageCategory, Stats>(TilePercentageCategory::class.java).apply {
            put(TilePercentageCategory.Terrain, terrainStats)
            put(TilePercentageCategory.Improvement, improvementStats)
            put(TilePercentageCategory.Road, roadStats)
        }
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
        cityUniqueCache: LocalUniqueCache = LocalUniqueCache(false),
        /** Provide this for performance */
        currentTileStats: Stats? = null): Stats {

        val currentStats = currentTileStats
            ?: getTileStats(city, observingCiv, cityUniqueCache)

        val tileClone = tile.clone(addUnits = false)
        tileClone.setTerrainTransients()

        tileClone.setImprovement(improvement.name)
        val futureStats = tileClone.stats.getTileStats(city, observingCiv, cityUniqueCache)

        return futureStats.minus(currentStats)
    }

    // Also multiplies the stats by the percentage bonus for improvements (but not for tiles)
    private fun getExtraImprovementStats(
        improvement: TileImprovement,
        observingCiv: Civilization,
        city: City?
    ): Stats {
        val stats = Stats()

        if (tile.hasViewableResource(observingCiv) && tile.tileResource.isImprovedBy(improvement.name)
                && tile.tileResource.improvementStats != null
        )
            stats.add(tile.tileResource.improvementStats!!) // resource-specific improvement

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

        for (unique in improvement.getMatchingUniques(UniqueType.ImprovementStatsOnTile, conditionalState)) {
            if (tile.matchesFilter(unique.params[1])
                || unique.params[1] == Constants.freshWater && tile.isAdjacentTo(Constants.freshWater)
                || unique.params[1] == "non-fresh water" && !tile.isAdjacentTo(Constants.freshWater)
            )
                stats.add(unique.stats)
        }

        return stats
    }

}
