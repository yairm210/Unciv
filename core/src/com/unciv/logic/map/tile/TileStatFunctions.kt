package com.unciv.logic.map.tile

import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.components.extensions.toPercent
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly
import java.util.EnumMap

@Readonly
fun List<Pair<String, Stats>>.toStats(): Stats {
    val stats = Stats()
    for ((_, statsToAdd) in this)
        stats.add(statsToAdd)
    return stats
}

class TileStatFunctions(val tile: Tile) {
    private val riverTerrain by lazy { tile.ruleset.terrains[Constants.river] }

    @Readonly
    fun getTileStats(
        observingCiv: Civilization?,
        localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)
    ): Stats = getTileStats(tile.getCity(), observingCiv, localUniqueCache)

    @Readonly
    fun getTileStats(
        city: City?, observingCiv: Civilization?,
        localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)
    ): Stats {
        val statsBreakdown = getTileStatsBreakdown(city, observingCiv, localUniqueCache)

        val improvement = tile.getUnpillagedImprovement()
        val road = tile.getUnpillagedRoad()

        val percentageStats = getTilePercentageStats(observingCiv, city, localUniqueCache)
        for ((cause, @LocalState stats) in statsBreakdown) {
            val tileType = when (cause) {
                improvement -> TilePercentageCategory.Improvement
                road.name -> TilePercentageCategory.Road
                else -> TilePercentageCategory.Terrain
            }
            for ((stat, value) in percentageStats[tileType]!!)
                stats[stat] *= value.toPercent()
        }

        return statsBreakdown.toStats()
    }

    @Readonly
    fun getTileStatsBreakdown(city: City?, observingCiv: Civilization?,
                              localUniqueCache: LocalUniqueCache = LocalUniqueCache(false)
    ): List<Pair<String, Stats>> {
        val gameContext = GameContext(civInfo = observingCiv, city = city, tile = tile)
        @LocalState val listOfStats = getTerrainStatsBreakdown(gameContext)
        
        val otherYieldsIgnored = tile.allTerrains.any { it.hasUnique(UniqueType.NullifyYields, gameContext) }

        val improvement = if (otherYieldsIgnored) null // Treat it as if there is no improvement
            else tile.getUnpillagedTileImprovement()
        @LocalState val improvementStats = improvement?.cloneStats() ?: Stats.ZERO // If improvement==null, will never be added to

        val road = if (otherYieldsIgnored) null
            else tile.getUnpillagedRoadImprovement()
        @LocalState val roadStats = road?.cloneStats() ?: Stats.ZERO

        if (city != null) {
            val statsFromTilesUniques =
                localUniqueCache.forCityGetMatchingUniques(city, UniqueType.StatsFromTiles, gameContext)
                    .filter { city.matchesFilter(it.params[2]) }

            val statsFromObjectsUniques = localUniqueCache.forCityGetMatchingUniques(
                city, UniqueType.StatsFromObject, gameContext)

            val statsFromTilesWithoutUniques = localUniqueCache.forCityGetMatchingUniques(
                city, UniqueType.StatsFromTilesWithout, gameContext)
                .filter { city.matchesFilter(it.params[3]) && !tile.matchesFilter(it.params[2]) }

            for (unique in statsFromTilesUniques + statsFromObjectsUniques + statsFromTilesWithoutUniques) {
                val tileType = unique.params[1]
                if (improvement != null && improvement.matchesFilter(tileType, gameContext))
                    improvementStats.add(unique.stats)
                else if (tile.matchesFilter(tileType, observingCiv))
                    listOfStats.add("{${unique.sourceObjectName}} ({${unique.getDisplayText()}})" to unique.stats)
                else if (road != null && road.matchesFilter(tileType, gameContext))
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
                listOfStats += getSingleTerrainStats(riverTerrain!!, gameContext)
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
                    .getMatchingUniques(UniqueType.EnsureMinimumStats, gameContext)
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
            listOfStats.add("Golden Age" to Stats(gold = 1f))

        // To ensure that the original stats (in uniques, terrains, etc) are not modified in getTileStats, we clone them all
        return listOfStats.filter { !it.second.isEmpty() }.map { it.first to it.second.clone() }
    }

    /** Ensures each stat is >= [minimumStats].stat - modifies in place */
    @Readonly
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
    @Readonly
    private fun getSingleTerrainStats(terrain: Terrain, gameContext: GameContext): ArrayList<Pair<String, Stats>> {
        val list = ArrayList<Pair<String,Stats>>()
        list.add(terrain.name to (terrain as Stats))

        for (unique in terrain.getMatchingUniques(UniqueType.Stats, gameContext)) {
            list.add(terrain.name+": "+unique.getDisplayText() to unique.stats)
        }
        return list
    }

    /** Gets basic stats to start off [getTileStats] or [getTileStartYield], independently mutable result */
    @Readonly
    fun getTerrainStatsBreakdown(gameContext: GameContext = GameContext()): ArrayList<Pair<String, Stats>> {
        // needs to be marked, because it's a var
        @LocalState var list = ArrayList<Pair<String, Stats>>()

        // allTerrains iterates over base, natural wonder, then features
        for (terrain in tile.allTerrains) {
            val terrainStats = getSingleTerrainStats(terrain, gameContext)
            when {
                terrain.hasUnique(UniqueType.NullifyYields, gameContext) ->
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
    @Readonly
    fun getTilePercentageStats(observingCiv: Civilization?, city: City?, uniqueCache: LocalUniqueCache): EnumMap<TilePercentageCategory, Stats> {
        val terrainStats = Stats()
        val gameContext = GameContext(civInfo = observingCiv, city = city, tile = tile)

        val improvement = tile.getUnpillagedTileImprovement()
        val improvementStats = Stats()

        val road = tile.getUnpillagedRoadImprovement()
        val roadStats = Stats()

        fun addStats(filter: String, stat: Stat, amount: Float) {
            if (improvement != null && improvement.matchesFilter(filter, gameContext))
                improvementStats.add(stat, amount)
            else if (tile.matchesFilter(filter, observingCiv))
                terrainStats.add(stat, amount)
            else if (road != null && road.matchesFilter(filter, gameContext))
                roadStats.add(stat, amount)
        }

        if (city != null) {
            val cachedStatPercentFromObjectCityUniques = uniqueCache.forCityGetMatchingUniques(
                city, UniqueType.StatPercentFromObject, gameContext)

            for (unique in cachedStatPercentFromObjectCityUniques) {
                addStats(unique.params[2], Stat.valueOf(unique.params[1]), unique.params[0].toFloat())
            }

            val cachedAllStatPercentFromObjectCityUniques = uniqueCache.forCityGetMatchingUniques(
                city, UniqueType.AllStatsPercentFromObject, gameContext)
            for (unique in cachedAllStatPercentFromObjectCityUniques) {
                for (stat in Stat.entries)
                    addStats(unique.params[1], stat, unique.params[0].toFloat())
            }

        } else if (observingCiv != null) {
            val cachedStatPercentFromObjectCivUniques = uniqueCache.forCivGetMatchingUniques(
                observingCiv, UniqueType.StatPercentFromObject, gameContext)
            for (unique in cachedStatPercentFromObjectCivUniques) {
                addStats(unique.params[2], Stat.valueOf(unique.params[1]), unique.params[0].toFloat())
            }

            val cachedAllStatPercentFromObjectCivUniques = uniqueCache.forCivGetMatchingUniques(
                observingCiv, UniqueType.AllStatsPercentFromObject, gameContext)
            for (unique in cachedAllStatPercentFromObjectCivUniques) {
                for (stat in Stat.entries)
                    addStats(unique.params[1], stat, unique.params[0].toFloat())
            }
        }
        
        val enumMap = EnumMap<TilePercentageCategory, Stats>(TilePercentageCategory::class.java)
        enumMap[TilePercentageCategory.Terrain] = terrainStats
        enumMap[TilePercentageCategory.Improvement] = improvementStats
        enumMap[TilePercentageCategory.Road] = roadStats
        return enumMap
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
    @Readonly
    fun getStatDiffForImprovement(
        improvement: TileImprovement,
        observingCiv: Civilization,
        city: City?,
        cityUniqueCache: LocalUniqueCache = LocalUniqueCache(false),
        /** Provide this for performance */
        currentTileStats: Stats? = null): Stats {

        val currentStats = currentTileStats
            ?: getTileStats(city, observingCiv, cityUniqueCache)

        @LocalState val tileClone = tile.clone(addUnits = false)
        tileClone.setTerrainTransients()

        tileClone.setImprovement(improvement.name)
        @LocalState val futureStats = tileClone.stats.getTileStats(city, observingCiv, cityUniqueCache)

        return futureStats.minus(currentStats)
    }

    // Also multiplies the stats by the percentage bonus for improvements (but not for tiles)
    @Readonly
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

        val conditionalState = GameContext(civInfo = observingCiv, city = city, tile = tile)
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
