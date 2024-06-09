package com.unciv.logic.map.tile

import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType


/** Reason why an Improvement cannot be built by a given civ */
enum class ImprovementBuildingProblem(
    /** `true` if this cannot change on the future of this game */
    val permanent: Boolean = false,
    /** `true` if the ImprovementPicker should report this problem */
    val reportable: Boolean = false
) {
    Replaced(permanent = true),
    WrongCiv(permanent = true),
    MissingTech(reportable = true),
    Unbuildable(permanent = true),
    ConditionallyUnbuildable,
    NotJustOutsideBorders(reportable = true),
    OutsideBorders(reportable = true),
    UnmetConditional,
    Obsolete(permanent = true),
    MissingResources(reportable = true),
    Other
}

class TileImprovementFunctions(val tile: Tile) {

    /** Returns true if the [improvement] can be built on this [Tile] */
    fun canBuildImprovement(improvement: TileImprovement, civInfo: Civilization): Boolean = getImprovementBuildingProblems(improvement, civInfo).none()

    /** Generates a sequence of reasons that prevent building given [improvement].
     *  If the sequence is empty, improvement can be built immediately.
     */
    fun getImprovementBuildingProblems(improvement: TileImprovement, civInfo: Civilization): Sequence<ImprovementBuildingProblem> = sequence {
        val stateForConditionals = StateForConditionals(civInfo, tile = tile)

        if (improvement.uniqueTo != null && improvement.uniqueTo != civInfo.civName)
            yield(ImprovementBuildingProblem.WrongCiv)
        if (civInfo.cache.uniqueImprovements.any { it.replaces == improvement.name })
            yield(ImprovementBuildingProblem.Replaced)
        if (improvement.techRequired != null && !civInfo.tech.isResearched(improvement.techRequired!!))
            yield(ImprovementBuildingProblem.MissingTech)
        if (improvement.getMatchingUniques(UniqueType.Unbuildable, StateForConditionals.IgnoreConditionals)
                .any { it.conditionals.isEmpty() })
            yield(ImprovementBuildingProblem.Unbuildable)
        else if (improvement.hasUnique(UniqueType.Unbuildable, stateForConditionals))
            yield(ImprovementBuildingProblem.ConditionallyUnbuildable)

        if (improvement.hasUnique(UniqueType.Unavailable, stateForConditionals))
            yield(ImprovementBuildingProblem.ConditionallyUnbuildable)

        if (tile.getOwner() != civInfo && !improvement.hasUnique(UniqueType.CanBuildOutsideBorders, stateForConditionals)) {
            if (!improvement.hasUnique(UniqueType.CanBuildJustOutsideBorders, stateForConditionals))
                yield(ImprovementBuildingProblem.OutsideBorders)
            else if (tile.neighbors.none { it.getOwner() == civInfo })
                yield(ImprovementBuildingProblem.NotJustOutsideBorders)
        }

        if (improvement.getMatchingUniques(UniqueType.OnlyAvailable, StateForConditionals.IgnoreConditionals)
                    .any { !it.conditionalsApply(stateForConditionals) })
            yield(ImprovementBuildingProblem.UnmetConditional)

        if (improvement.getMatchingUniques(UniqueType.ObsoleteWith, stateForConditionals)
                    .any { civInfo.tech.isResearched(it.params[0]) })
            yield(ImprovementBuildingProblem.Obsolete)

        if (improvement.getMatchingUniques(UniqueType.ConsumesResources, stateForConditionals)
                    .any { civInfo.getResourceAmount(it.params[1]) < it.params[0].toInt() })
            yield(ImprovementBuildingProblem.MissingResources)

        if (improvement.getMatchingUniques(UniqueType.CostsResources)
                    .any { civInfo.getResourceAmount(it.params[1]) < it.params[0].toInt() })
            yield(ImprovementBuildingProblem.MissingResources)

        val knownFeatureRemovals = tile.ruleset.tileRemovals
            .filter { rulesetImprovement ->
                        RoadStatus.values().none { it.removeAction == rulesetImprovement.name }
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
        stateForConditionals: StateForConditionals = StateForConditionals(tile=tile),
        isNormalizeCheck: Boolean = false
    ): Boolean {

        fun TileImprovement.canBeBuildOnThisUnbuildableTerrain(
            knownFeatureRemovals: List<TileImprovement>? = null,
        ): Boolean {
            val topTerrain = tile.lastTerrain
            // We can build if we are specifically allowed to build on this terrain
            if (isAllowedOnFeature(topTerrain)) return true

            // Otherwise, we can if this improvement removes the top terrain
            if (!hasUnique(UniqueType.RemovesFeaturesIfBuilt, stateForConditionals)) return false
            if (knownFeatureRemovals.isNullOrEmpty()) return false
            val featureRemovals = tile.terrainFeatures.mapNotNull { feature ->
                tile.ruleset.tileRemovals.firstOrNull { it.name == Constants.remove + feature } }
            if (featureRemovals.isEmpty()) return false
            if (featureRemovals.any { it !in knownFeatureRemovals }) return false
            val clonedTile = tile.clone()
            clonedTile.setTerrainFeatures(tile.terrainFeatures.filterNot {
                feature -> featureRemovals.any{ it.name.removePrefix(Constants.remove) == feature } })
            return clonedTile.improvementFunctions.canImprovementBeBuiltHere(improvement, resourceIsVisible, knownFeatureRemovals, stateForConditionals)
        }

        return when {
            improvement.name == tile.improvement && !isNormalizeCheck -> false
            tile.isCityCenter() -> isNormalizeCheck && improvement.name == Constants.cityCenter

            // First we handle a few special improvements

            // Can only cancel if there is actually an improvement being built
            improvement.name == Constants.cancelImprovementOrder -> (tile.improvementInProgress != null)
            // Can only remove roads if that road is actually there
            RoadStatus.values().any { it.removeAction == improvement.name } -> tile.roadStatus.removeAction == improvement.name
            // Can only remove features or improvement if that feature/improvement is actually there
            improvement.name.startsWith(Constants.remove) -> tile.terrainFeatures.any { Constants.remove + it == improvement.name }
                || Constants.remove + tile.improvement == improvement.name
            // Can only build roads if on land and they are better than the current road
            RoadStatus.values().any { it.name == improvement.name } -> !tile.isWater
                    && RoadStatus.valueOf(improvement.name) > tile.roadStatus

            // Then we check if there is any reason to not allow this improvement to be build

            // Can't build if there is already an irremovable improvement here
            tile.improvement != null && tile.getTileImprovement()!!.hasUnique(UniqueType.Irremovable, stateForConditionals) -> false

            // Can't build if this terrain is unbuildable, except when we are specifically allowed to
            tile.lastTerrain.unbuildable && !improvement.canBeBuildOnThisUnbuildableTerrain(knownFeatureRemovals) -> false

            // Can't build if any terrain specifically prevents building this improvement
            tile.getTerrainMatchingUniques(UniqueType.RestrictedBuildableImprovements, stateForConditionals).any {
                    unique -> !improvement.matchesFilter(unique.params[0])
            } -> false

            // Can't build if the improvement specifically prevents building on some present feature
            improvement.getMatchingUniques(UniqueType.CannotBuildOnTile, stateForConditionals).any {
                    unique -> tile.matchesTerrainFilter(unique.params[0])
            } ->
                false

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
            improvement.isAllowedOnFeature(tile.lastTerrain) -> true
            tile.isLand && improvement.canBeBuiltOn("Land") -> true
            tile.isWater && improvement.canBeBuiltOn("Water") -> true
            // DO NOT reverse this &&. isAdjacentToFreshwater() is a lazy which calls a function, and reversing it breaks the tests.
            improvement.hasUnique(UniqueType.ImprovementBuildableByFreshWater, stateForConditionals)
                    && tile.isAdjacentTo(Constants.freshWater) -> true

            // I don't particularly like this check, but it is required to build mines on non-hill resources
            resourceIsVisible && tile.tileResource.isImprovedBy(improvement.name) -> true
            // No reason this improvement should be built here, so can't build it
            else -> false
        }
    }


    fun changeImprovement(improvementName: String?,
                          /** For road assignment and taking over tiles - DO NOT pass when simulating improvement effects! */
                          civToActivateBroaderEffects: Civilization? = null, unit: MapUnit? = null) {
        val improvementObject = tile.ruleset.tileImprovements[improvementName]

        var improvementFieldHasChanged = false
        when {
            improvementName?.startsWith(Constants.remove) == true -> {
                activateRemovalImprovement(improvementName, civToActivateBroaderEffects)
            }
            improvementName == RoadStatus.Road.name -> tile.addRoad(RoadStatus.Road, civToActivateBroaderEffects)
            improvementName == RoadStatus.Railroad.name -> tile.addRoad(RoadStatus.Railroad, civToActivateBroaderEffects)
            improvementName == Constants.repair -> tile.setRepaired()
            else -> {
                tile.improvementIsPillaged = false
                tile.improvement = improvementName
                improvementFieldHasChanged = true

                removeCreatesOneImprovementMarker()
            }
        }

        if (improvementFieldHasChanged) {
            for (civ in tile.tileMap.gameInfo.civilizations) {
                if (civ.isDefeated() || !civ.isMajorCiv()) continue
                if (civ == civToActivateBroaderEffects || tile.isVisible(civ))
                    civ.setLastSeenImprovement(tile.position, improvementName)
            }
        }

        if (improvementObject != null && improvementObject.hasUnique(UniqueType.RemovesFeaturesIfBuilt)) {
            // Remove terrainFeatures that a Worker can remove
            // and that aren't explicitly allowed under the improvement
            val removableTerrainFeatures = tile.terrainFeatureObjects.filter { feature ->
                val removingAction = "${Constants.remove}${feature.name}"

                removingAction in tile.ruleset.tileImprovements // is removable
                    && !improvementObject.isAllowedOnFeature(feature) // cannot coexist
            }

            tile.setTerrainFeatures(tile.terrainFeatures.filterNot { feature -> removableTerrainFeatures.any { it.name == feature } })
        }

        if (civToActivateBroaderEffects != null && improvementObject != null
            && improvementObject.hasUnique(UniqueType.TakesOverAdjacentTiles)
        )
            takeOverTilesAround(civToActivateBroaderEffects, tile)

        if (civToActivateBroaderEffects != null && improvementObject != null)
            triggerImprovementUniques(improvementObject, civToActivateBroaderEffects, unit)

        val city = tile.owningCity
        if (civToActivateBroaderEffects != null && city != null) {
            city.cityStats.update()
            city.civ.cache.updateCivResources()
            city.reassignPopulationDeferred()
        }
    }

    private fun triggerImprovementUniques(
        improvement: TileImprovement,
        civ: Civilization,
        unit: MapUnit? = null
    ) {
        val stateForConditionals = StateForConditionals(civ, unit = unit, tile = tile)

        for (unique in improvement.uniqueObjects.filter { !it.hasTriggerConditional()
            && it.conditionalsApply(stateForConditionals) })
            UniqueTriggerActivation.triggerUnique(unique, civ, unit = unit, tile = tile)

        for (unique in civ.getTriggeredUniques(UniqueType.TriggerUponBuildingImprovement, stateForConditionals)
            .filter { improvement.matchesFilter(it.params[0]) })
            UniqueTriggerActivation.triggerUnique(unique, civ, unit = unit, tile = tile)

        if (unit == null) return
        for (unique in unit.getTriggeredUniques(UniqueType.TriggerUponBuildingImprovement, stateForConditionals)
            .filter { improvement.matchesFilter(it.params[0]) })
            UniqueTriggerActivation.triggerUnique(unique, civ, unit = unit, tile = tile)
    }

    private fun activateRemovalImprovement(
        improvementName: String,
        civToActivateBroaderEffects: Civilization?
    ) {
        val removedFeatureName = improvementName.removePrefix(Constants.remove)
        val currentTileImprovement = tile.getTileImprovement()
        // We removed a terrain (e.g. Forest) and the improvement (e.g. Lumber mill) requires it!
        if (currentTileImprovement != null
            && tile.terrainFeatures.any {
                currentTileImprovement.terrainsCanBeBuiltOn.contains(it) && it == removedFeatureName
            }
            && !currentTileImprovement.terrainsCanBeBuiltOn.contains(tile.baseTerrain)
        ) tile.removeImprovement()

        if (RoadStatus.values().any { improvementName == it.removeAction }) {
            tile.removeRoad()
        }
        else if (tile.improvement == removedFeatureName) tile.removeImprovement()
        else {
            val removedFeatureObject = tile.ruleset.terrains[removedFeatureName]
            if (removedFeatureObject != null
                && civToActivateBroaderEffects != null
                && removedFeatureObject.hasUnique(UniqueType.ProductionBonusWhenRemoved)
            )
                tryProvideProductionToClosestCity(removedFeatureName, civToActivateBroaderEffects)

            tile.removeTerrainFeature(removedFeatureName)
        }
    }

    private fun tryProvideProductionToClosestCity(removedTerrainFeature: String, civ: Civilization) {
        val closestCity = civ.cities.minByOrNull { it.getCenterTile().aerialDistanceTo(tile) }
        @Suppress("FoldInitializerAndIfToElvis")
        if (closestCity == null) return
        val distance = closestCity.getCenterTile().aerialDistanceTo(tile)
        var productionPointsToAdd = if (distance == 1) 20 else 20 - (distance - 2) * 5
        if (tile.owningCity == null || tile.owningCity!!.civ != civ) productionPointsToAdd =
            productionPointsToAdd * 2 / 3
        if (productionPointsToAdd > 0) {
            closestCity.cityConstructions.addProductionPoints(productionPointsToAdd)
            val locations = LocationAction(tile.position, closestCity.location)
            civ.addNotification(
                "Clearing a [$removedTerrainFeature] has created [$productionPointsToAdd] Production for [${closestCity.name}]",
                locations, NotificationCategory.Production, NotificationIcon.Construction
            )
        }
    }

    private fun takeOverTilesAround(civ: Civilization, tile: Tile) {
        // This method should only be called for a citadel - therefore one of the neighbour tile
        // must belong to unit's civ, so minByOrNull in the nearestCity formula should be never `null`.
        // That is, unless a mod does not specify the proper unique - then fallbackNearestCity will take over.

        fun priority(tile: Tile): Int { // helper calculates priority (lower is better): distance plus razing malus
            val city = tile.getCity()!!       // !! assertion is guaranteed by the outer filter selector.
            return city.getCenterTile().aerialDistanceTo(tile) +
                (if (city.isBeingRazed) 5 else 0)
        }
        fun fallbackNearestCity(civ: Civilization, tile: Tile) =
            civ.cities.minByOrNull {
                it.getCenterTile().aerialDistanceTo(tile) +
                    (if (it.isBeingRazed) 5 else 0)
            }!!

        // In the rare case more than one city owns tiles neighboring the citadel
        // this will prioritize the nearest one not being razed
        val nearestCity = tile.neighbors
            .filter { it.getOwner() == civ }
            .minByOrNull { priority(it) }?.getCity()
            ?: fallbackNearestCity(civ, tile)

        // capture all tiles which do not belong to unit's civ and are not enemy cities
        // we use getTilesInDistance here, not neighbours to include the current tile as well
        val tilesToTakeOver = tile.getTilesInDistance(1)
            .filter { !it.isCityCenter() && it.getOwner() != civ }

        val civsToNotify = mutableSetOf<Civilization>()
        for (tileToTakeOver in tilesToTakeOver) {
            val otherCiv = tileToTakeOver.getOwner()
            if (otherCiv != null) {
                // decrease relations for -10 pt/tile
                if (!otherCiv.knows(civ)) otherCiv.diplomacyFunctions.makeCivilizationsMeet(civ)
                otherCiv.getDiplomacyManager(civ).addModifier(DiplomaticModifiers.StealingTerritory, -10f)
                civsToNotify.add(otherCiv)
            }
            nearestCity.expansion.takeOwnership(tileToTakeOver)
        }

        for (otherCiv in civsToNotify)
            otherCiv.addNotification("Your territory has been stolen by [$civ]!",
                tile.position, NotificationCategory.Cities, civ.civName, NotificationIcon.War)
    }



    /** Marks tile as target tile for a building with a [UniqueType.CreatesOneImprovement] unique */
    fun markForCreatesOneImprovement(improvement: String) {
        tile.improvementInProgress = improvement
        tile.turnsToImprovement = -1
    }

    /** Un-Marks a tile as target tile for a building with a [UniqueType.CreatesOneImprovement] unique,
     *  and ensures that matching queued buildings are removed. */
    fun removeCreatesOneImprovementMarker() {
        if (!tile.isMarkedForCreatesOneImprovement()) return
        tile.owningCity?.cityConstructions?.removeCreateOneImprovementConstruction(tile.improvementInProgress!!)
        tile.stopWorkingOnImprovement()
    }


}
