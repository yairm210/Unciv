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
import com.unciv.ui.utils.extensions.toPercent


enum class ImprovementBuildingProblem {
    WrongCiv, MissingTech, Unbuildable, NotJustOutsideBorders, OutsideBorders, UnmetConditional, Obsolete, MissingResources, Other
}

class TileInfoImprovementFunctions(val tile: Tile) {

    /** Returns true if the [improvement] can be built on this [Tile] */
    fun canBuildImprovement(improvement: TileImprovement, civInfo: Civilization): Boolean = getImprovementBuildingProblems(improvement, civInfo).none()

    /** Generates a sequence of reasons that prevent building given [improvement].
     *  If the sequence is empty, improvement can be built immediately.
     */
    fun getImprovementBuildingProblems(improvement: TileImprovement, civInfo: Civilization): Sequence<ImprovementBuildingProblem> = sequence {
        val stateForConditionals = StateForConditionals(civInfo, tile = tile)

        if (improvement.uniqueTo != null && improvement.uniqueTo != civInfo.civName)
            yield(ImprovementBuildingProblem.WrongCiv)
        if (improvement.techRequired != null && !civInfo.tech.isResearched(improvement.techRequired!!))
            yield(ImprovementBuildingProblem.MissingTech)
        if (improvement.hasUnique(UniqueType.Unbuildable, stateForConditionals))
            yield(ImprovementBuildingProblem.Unbuildable)

        if (tile.getOwner() != civInfo && !improvement.hasUnique(UniqueType.CanBuildOutsideBorders, stateForConditionals)) {
            if (!improvement.hasUnique(UniqueType.CanBuildJustOutsideBorders, stateForConditionals))
                yield(ImprovementBuildingProblem.OutsideBorders)
            else if (tile.neighbors.none { it.getOwner() == civInfo })
                yield(ImprovementBuildingProblem.NotJustOutsideBorders)
        }

        if (improvement.getMatchingUniques(UniqueType.OnlyAvailableWhen, StateForConditionals.IgnoreConditionals).any {
                    !it.conditionalsApply(stateForConditionals)
                })
            yield(ImprovementBuildingProblem.UnmetConditional)

        if (improvement.getMatchingUniques(UniqueType.ObsoleteWith, stateForConditionals).any {
                    civInfo.tech.isResearched(it.params[0])
                })
            yield(ImprovementBuildingProblem.Obsolete)

        if (improvement.getMatchingUniques(UniqueType.ConsumesResources, stateForConditionals).any {
                    civInfo.getCivResourcesByName()[it.params[1]]!! < it.params[0].toInt()
                })
            yield(ImprovementBuildingProblem.MissingResources)

        val knownFeatureRemovals = tile.ruleset.tileImprovements.values
            .filter { rulesetImprovement ->
                rulesetImprovement.name.startsWith(Constants.remove)
                        && RoadStatus.values().none { it.removeAction == rulesetImprovement.name }
                        && (rulesetImprovement.techRequired == null || civInfo.tech.isResearched(rulesetImprovement.techRequired!!))
            }

        if (!canImprovementBeBuiltHere(improvement, tile.hasViewableResource(civInfo), knownFeatureRemovals, stateForConditionals))
        // There are way too many conditions in that functions, besides, they are not interesting
        // at least for the current usecases. Improve if really needed.
            yield(ImprovementBuildingProblem.Other)
    }

    /** Without regards to what CivInfo it is, a lot of the checks are just for the improvement on the tile.
     *  Doubles as a check for the map editor.
     */
    internal fun canImprovementBeBuiltHere(
        improvement: TileImprovement,
        resourceIsVisible: Boolean = tile.resource != null,
        knownFeatureRemovals: List<TileImprovement>? = null,
        stateForConditionals: StateForConditionals = StateForConditionals(tile=tile)
    ): Boolean {

        fun TileImprovement.canBeBuildOnThisUnbuildableTerrain(
            knownFeatureRemovals: List<TileImprovement>? = null,
        ): Boolean {
            val topTerrain = tile.getLastTerrain()
            // We can build if we are specifically allowed to build on this terrain
            if (isAllowedOnFeature(topTerrain.name)) return true

            // Otherwise, we can if this improvement removes the top terrain
            if (!hasUnique(UniqueType.RemovesFeaturesIfBuilt, stateForConditionals)) return false
            val removeAction = tile.ruleset.tileImprovements[Constants.remove + topTerrain.name] ?: return false
            // and we have the tech to remove that top terrain
            if (removeAction.techRequired != null && (knownFeatureRemovals == null || removeAction !in knownFeatureRemovals)) return false
            // and we can build it on the tile without the top terrain
            val clonedTile = tile.clone()
            clonedTile.removeTerrainFeature(topTerrain.name)
            return clonedTile.improvementFunctions.canImprovementBeBuiltHere(improvement, resourceIsVisible, knownFeatureRemovals, stateForConditionals)
        }

        return when {
            improvement.name == tile.improvement -> false
            tile.isCityCenter() -> false

            // First we handle a few special improvements

            // Can only cancel if there is actually an improvement being built
            improvement.name == Constants.cancelImprovementOrder -> (tile.improvementInProgress != null)
            // Can only remove roads if that road is actually there
            RoadStatus.values().any { it.removeAction == improvement.name } -> tile.roadStatus.removeAction == improvement.name
            // Can only remove features if that feature is actually there
            improvement.name.startsWith(Constants.remove) -> tile.terrainFeatures.any { it == improvement.name.removePrefix(
                Constants.remove) }
            // Can only build roads if on land and they are better than the current road
            RoadStatus.values().any { it.name == improvement.name } -> !tile.isWater
                    && RoadStatus.valueOf(improvement.name) > tile.roadStatus

            // Then we check if there is any reason to not allow this improvement to be build

            // Can't build if there is already an irremovable improvement here
            tile.improvement != null && tile.getTileImprovement()!!.hasUnique(UniqueType.Irremovable, stateForConditionals) -> false

            // Can't build if this terrain is unbuildable, except when we are specifically allowed to
            tile.getLastTerrain().unbuildable && !improvement.canBeBuildOnThisUnbuildableTerrain(knownFeatureRemovals) -> false

            // Can't build if any terrain specifically prevents building this improvement
            tile.getTerrainMatchingUniques(UniqueType.RestrictedBuildableImprovements, stateForConditionals).any {
                    unique -> !improvement.matchesFilter(unique.params[0])
            } -> false

            // Can't build if the improvement specifically prevents building on some present feature
            improvement.getMatchingUniques(UniqueType.CannotBuildOnTile, stateForConditionals).any {
                    unique -> tile.matchesTerrainFilter(unique.params[0])
            } -> false

            // Can't build if an improvement is only allowed to be built on specific tiles and this is not one of them
            // If multiple uniques of this type exists, we want all to match (e.g. Hill _and_ Forest would be meaningful)
            improvement.getMatchingUniques(UniqueType.CanOnlyBeBuiltOnTile, stateForConditionals).let {
                it.any() && it.any { unique -> !tile.matchesTerrainFilter(unique.params[0]) }
            } -> false

            // Can't build if the improvement requires an adjacent terrain that is not present
            improvement.getMatchingUniques(UniqueType.MustBeNextTo, stateForConditionals).any {
                !tile.isAdjacentTo(it.params[0])
            } -> false

            // Can't build it if it is only allowed to improve resources and it doesn't improve this resource
            improvement.hasUnique(UniqueType.CanOnlyImproveResource, stateForConditionals) && (
                    !resourceIsVisible || !tile.tileResource.isImprovedBy(improvement.name)
                    ) -> false

            // At this point we know this is a normal improvement and that there is no reason not to allow it to be built.

            // Lastly we check if the improvement may be built on this terrain or resource
            improvement.canBeBuiltOn(tile.getLastTerrain().name) -> true
            tile.isLand && improvement.canBeBuiltOn("Land") -> true
            tile.isWater && improvement.canBeBuiltOn("Water") -> true
            // DO NOT reverse this &&. isAdjacentToFreshwater() is a lazy which calls a function, and reversing it breaks the tests.
            improvement.hasUnique(UniqueType.ImprovementBuildableByFreshWater, stateForConditionals)
                    && tile.isAdjacentTo(Constants.freshWater) -> true

            // I don't particularly like this check, but it is required to build mines on non-hill resources
            resourceIsVisible && tile.tileResource.isImprovedBy(improvement.name) -> true
            // DEPRECATED since 4.0.14, REMOVE SOON:
            tile.isLand && improvement.terrainsCanBeBuiltOn.isEmpty() && !improvement.hasUnique(
                UniqueType.CanOnlyImproveResource) -> true
            // No reason this improvement should be built here, so can't build it
            else -> false
        }
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
