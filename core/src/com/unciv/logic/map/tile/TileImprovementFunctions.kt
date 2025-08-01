package com.unciv.logic.map.tile

import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.managers.ImprovementFunctions
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stats
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly


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
    @Readonly
    fun canBuildImprovement(improvement: TileImprovement, gameContext: GameContext): Boolean = getImprovementBuildingProblems(improvement, gameContext).none()

    /** Generates a sequence of reasons that prevent building given [improvement].
     *  If the sequence is empty, improvement can be built immediately.
     */
    @Readonly
    fun getImprovementBuildingProblems(improvement: TileImprovement, gameContext: GameContext): Sequence<ImprovementBuildingProblem> =
        ImprovementFunctions.getImprovementBuildingProblems(improvement, gameContext, tile)

    /** Without regards to what CivInfo it is (so no tech requirement check), a lot of the checks are just for the improvement on the tile.
     *  Doubles as a check for the map editor.
     */
    @Readonly
    internal fun canImprovementBeBuiltHere(
        improvement: TileImprovement,
        resourceIsVisible: Boolean = tile.resource != null,
        knownFeatureRemovals: List<TileImprovement>? = null,
        gameContext: GameContext = GameContext(tile=tile),
        isNormalizeCheck: Boolean = false
    ): Boolean {

        @Readonly
        fun TileImprovement.canBeBuiltOnThisUnbuildableTerrain(
            knownFeatureRemovals: List<TileImprovement>? = null,
        ): Boolean {
            val topTerrain = tile.lastTerrain
            // We can build if we are specifically allowed to build on this terrain
            if (isAllowedOnFeature(topTerrain)) return true

            // Otherwise, we can if this improvement removes the top terrain
            if (!hasUnique(UniqueType.RemovesFeaturesIfBuilt, gameContext)) return false
            if (knownFeatureRemovals.isNullOrEmpty()) return false
            val featureRemovals = tile.terrainFeatures.mapNotNull { feature ->
                tile.ruleset.tileRemovals.firstOrNull { it.name == Constants.remove + feature } }
            if (featureRemovals.isEmpty()) return false
            if (featureRemovals.any { it !in knownFeatureRemovals }) return false
            @LocalState val clonedTile = tile.clone(addUnits = false)
            clonedTile.setTerrainFeatures(tile.terrainFeatures.filterNot {
                feature -> featureRemovals.any { it.name.removePrefix(Constants.remove) == feature } })
            return clonedTile.improvementFunctions.canImprovementBeBuiltHere(improvement, resourceIsVisible, knownFeatureRemovals, gameContext)
        }

        return when {
            improvement.name == tile.improvement && !isNormalizeCheck -> false
            tile.isCityCenter() -> isNormalizeCheck && improvement.name == Constants.cityCenter

            // First we handle a few special improvements

            // Can only cancel if there is actually an improvement being built
            improvement.name == Constants.cancelImprovementOrder -> (tile.improvementInProgress != null)
            // Can only remove roads if that road is actually there
            RoadStatus.entries.any { it.removeAction == improvement.name } -> tile.roadStatus.removeAction == improvement.name
            // Can only remove features or improvement if that feature/improvement is actually there
            improvement.name.startsWith(Constants.remove) -> tile.terrainFeatures.any { Constants.remove + it == improvement.name }
                || Constants.remove + tile.improvement == improvement.name
            // Can only build roads if on land and they are better than the current road
            RoadStatus.entries.any { it.name == improvement.name } -> !tile.isWater
                    && RoadStatus.valueOf(improvement.name) > tile.roadStatus

            // Then we check if there is any reason to not allow this improvement to be built

            // Can't build if there is already an irremovable improvement here
            tile.improvement != null && tile.getTileImprovement()!!.hasUnique(UniqueType.Irremovable, gameContext) -> false

            // Can't build if this terrain is unbuildable, except when we are specifically allowed to
            tile.lastTerrain.unbuildable && !improvement.canBeBuiltOnThisUnbuildableTerrain(knownFeatureRemovals) -> false

            // Can't build if any terrain specifically prevents building this improvement
            tile.getTerrainMatchingUniques(UniqueType.RestrictedBuildableImprovements, gameContext).toList()
                .let { it.any() && it.none {
                        unique -> improvement.matchesFilter(unique.params[0], GameContext(tile = tile))
                } } -> false

            // Can't build if the improvement specifically prevents building on some present feature
            improvement.getMatchingUniques(UniqueType.CannotBuildOnTile, gameContext).any {
                    unique -> tile.matchesFilter(unique.params[0], gameContext.civInfo)
            } ->
                false

            // Can't build if an improvement is only allowed to be built on specific tiles and this is not one of them
            // If multiple uniques of this type exists, we want all to match (e.g. Hill _and_ Forest would be meaningful)
            improvement.getMatchingUniques(UniqueType.CanOnlyBeBuiltOnTile, gameContext).let {
                it.any() && it.any { unique -> !tile.matchesFilter(unique.params[0], gameContext.civInfo) }
            } -> false

            // Can't build if the improvement requires an adjacent terrain that is not present
            improvement.getMatchingUniques(UniqueType.MustBeNextTo, gameContext).any {
                !tile.isAdjacentTo(it.params[0])
            } -> false

            // Can't build it if it is only allowed to improve resources and it doesn't improve this resource
            improvement.hasUnique(UniqueType.CanOnlyImproveResource, gameContext) && (
                    !resourceIsVisible || !tile.tileResource.isImprovedBy(improvement.name)
                    ) -> false

            // At this point we know this is a normal improvement and that there is no reason not to allow it to be built.

            // Lastly we check if the improvement may be built on this terrain or resource
            improvement.isAllowedOnFeature(tile.lastTerrain) -> true
            tile.isLand && improvement.canBeBuiltOn("Land") -> true
            tile.isWater && improvement.canBeBuiltOn("Water") -> true
            // DO NOT reverse this &&. isAdjacentToFreshwater() is a lazy which calls a function, and reversing it breaks the tests.
            improvement.hasUnique(UniqueType.ImprovementBuildableByFreshWater, gameContext)
                    && tile.isAdjacentTo(Constants.freshWater) -> true

            // I don't particularly like this check, but it is required to build mines on non-hill resources
            resourceIsVisible && tile.tileResource.isImprovedBy(improvement.name) -> true
            // No reason this improvement should be built here, so can't build it
            else -> false
        }
    }


    fun setImprovement(improvementName: String?,
                          /** For road assignment and taking over tiles - DO NOT pass when simulating improvement effects! */
                          civToActivateBroaderEffects: Civilization? = null, unit: MapUnit? = null) {
        val improvementObject = tile.ruleset.tileImprovements[improvementName]

        var improvementFieldHasChanged = false
        when {
            improvementName?.startsWith(Constants.remove) == true -> {
                activateRemovalImprovement(improvementName, civToActivateBroaderEffects)
            }
            improvementName == RoadStatus.Road.name -> tile.setRoadStatus(RoadStatus.Road, civToActivateBroaderEffects)
            improvementName == RoadStatus.Railroad.name -> tile.setRoadStatus(RoadStatus.Railroad, civToActivateBroaderEffects)
            improvementName == Constants.repair -> tile.setRepaired()
            else -> {
                tile.improvementIsPillaged = false
                tile.improvement = improvementName
                improvementFieldHasChanged = true
                if (improvementName != null && (improvementObject!!.hasUnique(UniqueType.Irremovable) || tile.isMarkedForCreatesOneImprovement(improvementName))) {
                    // I'm not sure what would happen if we try to replace an irremovable improvement
                    // Let's not cancel our "Districts" in progress unless when finishing it (don't mess it up with accidental worker movements etc.)
                    removeCreatesOneImprovementMarker()
                }
            }
        }

        if (improvementFieldHasChanged && tile.tileMap.hasGameInfo()) {
            // Update the separately-kept "what a civ sees" - unless in map editor where there are no civs
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
        val gameContext = GameContext(civ, unit = unit, tile = tile)
        
        for (unique in improvement.getMatchingUniques(UniqueType.CostsResources, gameContext)) {
            val resource = tile.ruleset.tileResources[unique.params[1]] ?: continue
            var amount = unique.params[0].toInt()
            if (unique.isModifiedByGameSpeed()) amount = (amount * civ.gameInfo.speed.modifier).toInt()
            civ.gainStockpiledResource(resource, -amount)
        }

        for (unique in improvement.uniqueObjects.filter { !it.hasTriggerConditional()
            && it.conditionalsApply(gameContext) })
            UniqueTriggerActivation.triggerUnique(unique, civ, unit = unit, tile = tile)

        for (unique in civ.getTriggeredUniques(UniqueType.TriggerUponBuildingImprovement, gameContext)
            { improvement.matchesFilter(it.params[0], gameContext) })
            UniqueTriggerActivation.triggerUnique(unique, civ, unit = unit, tile = tile)

        if (unit == null) return
        for (unique in unit.getTriggeredUniques(UniqueType.TriggerUponBuildingImprovement, gameContext)
            { improvement.matchesFilter(it.params[0], gameContext) })
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

        if (RoadStatus.entries.any { improvementName == it.removeAction }) {
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
            ?: return
        val distance = closestCity.getCenterTile().aerialDistanceTo(tile)
        if (distance > 5) return
        var stats = Stats()
        for (unique in tile.getTerrainMatchingUniques(UniqueType.ProductionBonusWhenRemoved)) {
            var statsToAdd = unique.stats
            if (unique.isModifiedByGameSpeed())
                statsToAdd *= civ.gameInfo.speed.modifier
            if (unique.isModifiedByGameProgress())
                statsToAdd *= unique.getGameProgressModifier(civ)
            stats.add(statsToAdd)
        }
        if (stats.isEmpty()) return
        if (distance != 1) stats *= (6 - distance) / 4f
        if (tile.owningCity == null || tile.owningCity!!.civ != civ) stats *= 2 / 3f
        for ((stat, value) in stats) {
            closestCity.addStat(stat, value.toInt())
            val locations = LocationAction(tile.position, closestCity.location)
            civ.addNotification(
                "Clearing a [$removedTerrainFeature] has created [${stats.toStringForNotifications()}] for [${closestCity.name}]",
                locations, NotificationCategory.Production, NotificationIcon.Construction
            )
        }
    }

    /** Marks tile as target tile for a building with a [UniqueType.CreatesOneImprovement] unique */
    fun markForCreatesOneImprovement(improvement: String) {
        tile.stopWorkingOnImprovement()
        tile.queueImprovement(improvement, -1)
    }

    /** Un-Marks a tile as target tile for a building with a [UniqueType.CreatesOneImprovement] unique,
     *  and ensures that matching queued buildings are removed. */
    fun removeCreatesOneImprovementMarker() {
        if (!tile.isMarkedForCreatesOneImprovement()) return
        tile.owningCity?.cityConstructions?.removeCreateOneImprovementConstruction(tile.improvementInProgress!!)
        tile.stopWorkingOnImprovement()
    }
}
