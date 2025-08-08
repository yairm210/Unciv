package com.unciv.logic.automation.unit

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.ThreatLevel
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.automation.unit.UnitAutomation.wander
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.map.tile.toStats
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsFromUniques
import com.unciv.utils.debug
import yairm210.purity.annotations.Readonly
import kotlin.math.abs

/**
 * Contains the logic for worker automation.
 *
 * This is instantiated from [Civilization.getWorkerAutomation] and cached there.
 *
 * @param civInfo       The Civilization - data common to all automated workers is cached once per Civ
 * @param cachedForTurn The turn number this was created for - a recreation of the instance is forced on different turn numbers
 */
class WorkerAutomation(
    val civInfo: Civilization,
    val cachedForTurn: Int,
    cloningSource: WorkerAutomation? = null
) {
    ///////////////////////////////////////// Cached data /////////////////////////////////////////

    val roadToAutomation = RoadToAutomation(civInfo)
    val roadBetweenCitiesAutomation: RoadBetweenCitiesAutomation =
        RoadBetweenCitiesAutomation(civInfo, cachedForTurn, cloningSource?.roadBetweenCitiesAutomation)

    private val ruleSet = civInfo.gameInfo.ruleset



    //todo: UnitMovement.canReach still very expensive and could benefit from caching, it's not using BFS


    ///////////////////////////////////////// Helpers /////////////////////////////////////////

    /**
     * Each object has two stages, this first one is checking the basic priority without any improvements.
     * If tilePriority is -1 then it must be a dangerous tile.
     * The improvementPriority and bestImprovement are by default not set.
     * Once improvementPriority is set we have already checked for the best improvement, repairImprovement.
     */
    data class TileImprovementRank(val tilePriority: Float, var improvementPriority: Float? = null,
                                   var bestImprovement: TileImprovement? = null,
                                   var repairImprovment: Boolean? = null)

    private val tileRankings = HashMap<Tile, TileImprovementRank>()

    ///////////////////////////////////////// Methods /////////////////////////////////////////


    /**
     * Automate one Worker - decide what to do and where, move, start or continue work.
     */
    fun automateWorkerAction(unit: MapUnit, dangerousTiles: HashSet<Tile>, localUniqueCache: LocalUniqueCache = LocalUniqueCache()) {
        val currentTile = unit.getTile()
        // Must be called before any getPriority checks to guarantee the local road cache is processed
        val citiesToConnect = roadBetweenCitiesAutomation.getNearbyCitiesToConnect(unit)
        // Shortcut, we are working a suitable tile, and we're better off minimizing worker-turns by finishing everything on this tile
        if (currentTile.improvementInProgress != null && !dangerousTiles.contains(currentTile)
            && getFullPriority(unit.getTile(), unit, localUniqueCache) >= 2) {
            return
        }
        val tileToWork = findTileToWork(unit, dangerousTiles, localUniqueCache)

        if (tileToWork != currentTile && tileToWork != null) {
            headTowardsTileToWork(unit, tileToWork, localUniqueCache)
            return
        }

        if (currentTile.improvementInProgress != null) return // we're working!

        if (tileToWork == currentTile && tileHasWorkToDo(currentTile, unit, localUniqueCache)) 
            return startWorkOnCurrentTile(unit)

        // Support Alpha Frontier-Style Workers that _also_ have the "May create improvements on water resources" unique
        if (unit.cache.hasUniqueToCreateWaterImprovements && automateWorkBoats(unit)) return

        // Priotirize connecting grown cities without connection to capital over undeveloped cities (for gold income)
        val grownCities: List<City> = citiesToConnect.filter { it.population.population > 6 }
        if (roadBetweenCitiesAutomation.tryConnectingCities(unit, grownCities)) return
        
        if (tryHeadTowardsUndevelopedCity(unit, localUniqueCache, currentTile)) return

        // Nothing to do, try again to connect cities
        if (roadBetweenCitiesAutomation.tryConnectingCities(unit, citiesToConnect)) return


        debug("WorkerAutomation: %s -> nothing to do", unit.toString())
        unit.civ.addNotification("${unit.shortDisplayName()} has no work to do.", MapUnitAction(unit), NotificationCategory.Units, unit.name, "OtherIcons/Sleep")

        // Idle CS units should wander so they don't obstruct players so much
        if (unit.civ.isCityState)
            wander(unit, stayInTerritory = true, tilesToAvoid = dangerousTiles)
    }

    private fun tryHeadTowardsUndevelopedCity(
        unit: MapUnit,
        localUniqueCache: LocalUniqueCache,
        currentTile: Tile
    ): Boolean {
        val citiesToNumberOfUnimprovedTiles = HashMap<String, Int>()
        
        for (city in unit.civ.cities) {
            citiesToNumberOfUnimprovedTiles[city.id] = city.getTiles()
                .count { tile ->
                    tile.isLand
                            && tile.getUnits().none { unit -> unit.cache.hasUniqueToBuildImprovements }
                            && (tile.isPillaged() || tileHasWorkToDo(tile, unit, localUniqueCache))
                }
        }

        val closestUndevelopedCity = unit.civ.cities.asSequence()
            .filter { citiesToNumberOfUnimprovedTiles[it.id]!! > 0 }
            .sortedByDescending { it.getCenterTile().aerialDistanceTo(currentTile) }
            .firstOrNull { unit.movement.canReach(it.getCenterTile()) } //goto most undeveloped city

        if (closestUndevelopedCity != null && closestUndevelopedCity != currentTile.owningCity) {
            debug("WorkerAutomation: %s -> head towards undeveloped city %s", unit, closestUndevelopedCity.name)
            val reachedTile = unit.movement.headTowards(closestUndevelopedCity.getCenterTile())
            if (reachedTile != currentTile) unit.doAction() // since we've moved, maybe we can do something here - automate
            return true
        }
        return false
    }

    private fun startWorkOnCurrentTile(unit: MapUnit) {
        val currentTile = unit.currentTile
        val tileRanking = tileRankings[currentTile]!!
        if (tileRanking.repairImprovment == true) {
            debug("WorkerAutomation: $unit -> repairs $currentTile")
            UnitActionsFromUniques.getRepairAction(unit)?.action?.invoke()
            return
        }
        
        if (tileRanking.bestImprovement != null) {
            debug("WorkerAutomation: $unit} -> start improving $currentTile")
            return currentTile.startWorkingOnImprovement(tileRanking.bestImprovement!!, civInfo, unit)
        } else {
            throw IllegalStateException("We didn't find anything to improve on this tile even though there was supposed to be something to improve!")
        }
    }

    private fun headTowardsTileToWork(
        unit: MapUnit,
        tileToWork: Tile,
        localUniqueCache: LocalUniqueCache
    ) {
        debug("WorkerAutomation: %s -> head towards %s", unit.toString(), tileToWork)
        val currentTile = unit.getTile()
        val reachedTile = unit.movement.headTowards(tileToWork)

        if (tileToWork in reachedTile.neighbors
            && unit.movement.canMoveTo(tileToWork, allowSwap = true)
            && !unit.movement.canMoveTo(tileToWork, allowSwap = false)
            && unit.movement.canUnitSwapTo(tileToWork)
        ) {
            // There must be a unit on the target tile! Let's swap with it.
            unit.movement.swapMoveToTile(tileToWork)
        }

        if (reachedTile != currentTile)  // otherwise, we get a situation where the worker is automated, so it tries to move but doesn't, then tries to automate, then move, etc, forever. Stack overflow exception!
            unit.doAction()

        // If we have reached a fort tile that is in progress and shouldn't be there, cancel it.
        // TODO: Replace this code entirely and change [chooseImprovement] to not continue building the improvement by default
        if (reachedTile == tileToWork
            && reachedTile.improvementInProgress == Constants.fort
            && evaluateFortSurroundings(currentTile, false) <= 0
        ) {
            debug("Replacing fort in progress with new improvement")
            reachedTile.stopWorkingOnImprovement()
        }

        if (!unit.hasMovement() || reachedTile != tileToWork) return
        
        // If there's move still left, and this is even a tile we want, perform action
        // Unit may stop due to Enemy Unit within walking range during doAction() call

        // tileRankings is updated in getBasePriority, which is only called if isAutomationWorkableTile is true
        // Meaning, there are tiles we can't/shouldn't work, and they won't even be in tileRankings
        if (tileHasWorkToDo(unit.currentTile, unit, localUniqueCache))
            startWorkOnCurrentTile(unit)
    }


    /**
     * Looks for a worthwhile tile to improve
     * @return Null if no tile to work was found
     */
    private fun findTileToWork(unit: MapUnit, tilesToAvoid: Set<Tile>, localUniqueCache: LocalUniqueCache): Tile? {
        val currentTile = unit.getTile()
        
        if (isAutomationWorkableTile(currentTile, tilesToAvoid, currentTile, unit)
            && getBasePriority(currentTile, unit) >= 5
            && (currentTile.isPillaged() || currentTile.hasFalloutEquivalent() || tileHasWorkToDo(currentTile, unit, localUniqueCache)))
            return currentTile
        
        val workableTilesCenterFirst = currentTile.getTilesInDistance(3)
            .filter {
                isAutomationWorkableTile(it, tilesToAvoid, currentTile, unit) 
                        && getBasePriority(it, unit) > 1
            }

        val workableTilesPrioritized = workableTilesCenterFirst.groupBy { getBasePriority(it, unit) }
            .asSequence().sortedByDescending { it.key }

        // Search through each group by priority
        // If we can find an eligible best tile in the group lets return that
        // under the assumption that best tile is better than tiles in all lower groups
        for (tilePriorityGroup in workableTilesPrioritized) {
            var bestTile: Tile? = null
            for (tileInGroup in tilePriorityGroup.value.sortedBy { unit.getTile().aerialDistanceTo(it) }) {
                // These are the expensive calculations (tileCanBeImproved, canReach), so we only apply these filters after everything else it done.
                if (!tileHasWorkToDo(tileInGroup, unit, localUniqueCache)) continue
                if (unit.getTile() == tileInGroup) return unit.getTile()
                if (!unit.movement.canReach(tileInGroup)) continue
                if (bestTile == null || getFullPriority(tileInGroup, unit, localUniqueCache) > getFullPriority(bestTile, unit, localUniqueCache)) {
                    bestTile = tileInGroup
                }
            }
            if (bestTile != null) {
                return bestTile
            }
        }
        return null
    }

    private fun isAutomationWorkableTile(
        tile: Tile,
        tilesToAvoid: Set<Tile>,
        currentTile: Tile,
        unit: MapUnit
    ): Boolean {
        if (tile in tilesToAvoid) return false
        if (!(tile == currentTile
                    || (unit.isCivilian() && (tile.civilianUnit == null || !tile.civilianUnit!!.cache.hasUniqueToBuildImprovements))
                    || (unit.isMilitary() && (tile.militaryUnit == null || !tile.militaryUnit!!.cache.hasUniqueToBuildImprovements))))
            return false
        if (tile.owningCity != null && tile.getOwner() != civInfo) return false
        if (tile.isCityCenter()) return false
        // Don't try to improve tiles we can't benefit from at all
        if (!tile.hasViewableResource(civInfo) && tile.getTilesInDistance(civInfo.gameInfo.ruleset.modOptions.constants.cityWorkRange)
                .none { it.isCityCenter() && it.getCity()?.civ == civInfo }
        ) return false
        if (tile.getTileImprovement()?.hasUnique(UniqueType.AutomatedUnitsWillNotReplace) == true && !tile.isPillaged()) return false
        return true
    }

    /**
     * Calculate a priority for the tile without accounting for the improvement it'self
     * This is a cheap guess on how helpful it might be to do work on this tile
     */
    fun getBasePriority(tile: Tile, unit: MapUnit): Float {
        val unitSpecificPriority = 2 - (tile.aerialDistanceTo(unit.getTile()) / 2.0f).coerceIn(0f, 2f) * 3
        if (tileRankings.containsKey(tile))
            return tileRankings[tile]!!.tilePriority + unitSpecificPriority

        var priority = 0f
        if (tile.getOwner() == civInfo) {
            priority += Automation.rankStatsValue(tile.stats.getTerrainStatsBreakdown().toStats(), civInfo)
            if (tile.providesYield()) priority += 2
            if (tile.isPillaged()) priority += 1
            if (tile.hasFalloutEquivalent()) priority += 1
            if (tile.terrainFeatures.isNotEmpty() && tile.lastTerrain.hasUnique(UniqueType.ProductionBonusWhenRemoved)) priority += 0.5f// removing our forests is good for tempo
            if (tile.terrainHasUnique(UniqueType.FreshWater)) priority += 1 // we want our farms up when unlocking Civil Service
        }
        // give a minor priority to tiles that we could expand onto
        else if (tile.getOwner() == null && tile.neighbors.any { it.getOwner() == civInfo })
            priority += 1

        if (tile.hasViewableResource(civInfo)) {
            priority += 1
            if (tile.tileResource.resourceType == ResourceType.Luxury) priority += 3
            //luxuries are more important than other types of resources
        }
    
        if (tile in roadBetweenCitiesAutomation.tilesOfRoadsMap)
            priority += 3
            
        tileRankings[tile] = TileImprovementRank(priority)
        return priority + unitSpecificPriority
    }
    /**
     * Calculates the priority building the improvement on the tile
     */
    private fun getImprovementPriority(tile: Tile, unit: MapUnit, localUniqueCache: LocalUniqueCache): Float {
        getBasePriority(tile, unit)
        val rank = tileRankings[tile]
        if (rank!!.improvementPriority == null) {
            // All values of rank have to be initialized
            rank.improvementPriority = -100f
            rank.bestImprovement = null
            rank.repairImprovment = false

            val bestImprovement = chooseImprovement(unit, tile, localUniqueCache)
            if (bestImprovement != null) {
                rank.bestImprovement = bestImprovement
                // Increased priority if the improvement has been worked on longer
                val timeSpentPriority = if (tile.improvementInProgress == bestImprovement.name)
                    bestImprovement.getTurnsToBuild(unit.civ,unit) - tile.turnsToImprovement else 0

                rank.improvementPriority = getImprovementRanking(tile, unit, rank.bestImprovement!!.name, localUniqueCache) + timeSpentPriority
            }

            if (tile.improvement != null && tile.isPillaged() && tile.owningCity != null) {
                // Value repairing higher when it is quicker and is in progress
                var repairBonusPriority = tile.getImprovementToRepair()!!.getTurnsToBuild(unit.civ,unit) - UnitActionsFromUniques.getRepairTurns(unit)
                if (tile.improvementInProgress == Constants.repair) repairBonusPriority += UnitActionsFromUniques.getRepairTurns(unit) - tile.turnsToImprovement

                val repairPriority = repairBonusPriority + Automation.rankStatsValue(tile.stats.getStatDiffForImprovement(tile.getTileImprovement()!!, unit.civ, tile.owningCity), unit.civ)
                if (repairPriority > rank.improvementPriority!!) {
                    rank.improvementPriority = repairPriority
                    rank.bestImprovement = null
                    rank.repairImprovment = true
                }
            }
        }
        // A better tile than this unit can build might have been stored in the cache
        if (!rank.repairImprovment!! && (rank.bestImprovement == null ||
                !unit.canBuildImprovement(rank.bestImprovement!!, tile))) return -100f
        return rank.improvementPriority!!
    }

    /**
     * Calculates the full priority of the tile
     */
    private fun getFullPriority(tile: Tile, unit: MapUnit, localUniqueCache: LocalUniqueCache): Float {
        return getBasePriority(tile, unit) + getImprovementPriority(tile, unit, localUniqueCache)
    }

    /**
     * Returns the best improvement
     */
    private fun tileHasWorkToDo(tile: Tile, unit: MapUnit, localUniqueCache: LocalUniqueCache): Boolean {
        if (getImprovementPriority(tile, unit, localUniqueCache) <= 0) return false
        if (!(tileRankings[tile]!!.bestImprovement != null || tileRankings[tile]!!.repairImprovment!!))
            throw IllegalStateException("There was an improvementPriority > 0 and nothing to do")
        return true
    }

    /**
     * Determine the improvement appropriate to a given tile and worker
     * Returns null if none is worth it
     * */
    private fun chooseImprovement(unit: MapUnit, tile: Tile, localUniqueCache: LocalUniqueCache): TileImprovement? {
        // You can keep working on half-built improvements, even if they're unique to another civ
        if (tile.improvementInProgress != null) return ruleSet.tileImprovements[tile.improvementInProgress!!]

        val gameContext = GameContext(civInfo = unit.civ, unit = unit, tile = tile)
        val potentialTileImprovements = ruleSet.tileImprovements.filter {
            (it.value.uniqueTo == null || unit.civ.matchesFilter(it.value.uniqueTo!!, gameContext))
                    && unit.canBuildImprovement(it.value, tile)
                    && tile.improvementFunctions.canBuildImprovement(it.value, gameContext)
        }
        if (potentialTileImprovements.isEmpty()) return null

        val currentTileStats = tile.stats.getTileStats(tile.getCity(), civInfo, localUniqueCache)
        var bestBuildableImprovement = potentialTileImprovements.values.asSequence()
            .map { Pair(it, getImprovementRanking(tile, unit, it.name, localUniqueCache, currentTileStats)) }
            .filter { it.second > 0f }
            .maxByOrNull { it.second }?.first

        if (tile.improvement != null && civInfo.isHuman() && !UncivGame.Current.settings.automatedWorkersReplaceImprovements
            && UncivGame.Current.worldScreen?.autoPlay?.isAutoPlayingAndFullAutoPlayAI() == false) {
            // Note that we might still want to build roads or remove fallout, so we can't exit the function immedietly
            bestBuildableImprovement = null
        }

        val lastTerrain = tile.lastTerrain

        fun isRemovable(terrain: Terrain): Boolean = potentialTileImprovements.containsKey(Constants.remove + terrain.name)

        val improvementStringForResource: String? = when {
            tile.resource == null || !tile.hasViewableResource(civInfo) -> null
            
            tile.terrainFeatures.isNotEmpty()
                && lastTerrain.unbuildable
                && isRemovable(lastTerrain)
                && !tile.providesResources(civInfo)
                && !isResourceImprovementAllowedOnFeature(tile, potentialTileImprovements)
                    -> Constants.remove + lastTerrain.name
            
            else -> tile.tileResource.getImprovements().filter { it in potentialTileImprovements || it == tile.improvement }
                .maxByOrNull { getImprovementRanking(tile, unit, it, localUniqueCache) }
        }

        // After gathering all the data, we conduct the hierarchy in one place
        val improvementString = when {
            bestBuildableImprovement != null && bestBuildableImprovement.isRoad() -> bestBuildableImprovement.name
            
            // For bonus resources we just want the highest-yield improvement, not necessarily the resource-yielding improvement
            improvementStringForResource != null && tile.tileResource.resourceType != ResourceType.Bonus ->
                if (improvementStringForResource==tile.improvement) null else improvementStringForResource
            
            // If this is a resource that HAS an improvement that we can see, but this unit can't build it, don't waste your time
            tile.resource != null && tile.hasViewableResource(civInfo)
                    && tile.tileResource.resourceType != ResourceType.Bonus
                    && tile.tileResource.getImprovements().any() -> return null
            
            bestBuildableImprovement == null -> null

            tile.improvement != null &&
                    getImprovementRanking(tile, unit, tile.improvement!!, localUniqueCache) > getImprovementRanking(tile, unit, bestBuildableImprovement.name, localUniqueCache)
                -> null // What we have is better, even if it's pillaged we should repair it

            lastTerrain.let {
                isRemovable(it)
                        && (Automation.rankStatsValue(it, civInfo) < 0 || it.hasUnique(UniqueType.NullifyYields))
            } -> Constants.remove + lastTerrain.name

            else -> bestBuildableImprovement.name
        }
        return ruleSet.tileImprovements[improvementString] // For mods, the tile improvement may not exist, so don't assume.
    }

    private fun getImprovementRanking(tile: Tile, unit: MapUnit, improvementName: String,
                                      localUniqueCache: LocalUniqueCache,
                                      /** Provide for performance */ currentTileStats: Stats? = null): Float {
        val improvement = ruleSet.tileImprovements[improvementName]!!

        // Add the value of roads if we want to build it here
        if (improvement.isRoad() && roadBetweenCitiesAutomation.bestRoadAvailable.improvement(ruleSet) == improvement
            && tile in roadBetweenCitiesAutomation.tilesOfRoadsMap) {
            val roadPlan = roadBetweenCitiesAutomation.tilesOfRoadsMap[tile]!!
            val value = (roadPlan.priority - 5) // We want some forest chopping and farm building first if the road doesn't have high priority
            return value
        }

        // If this tile is not in our territory or neighboring it, it has no value
        if (tile.getOwner() != unit.civ
            // Check if it is not an unowned neighboring tile that can be in city range
            && !(ruleSet.tileImprovements[improvementName]!!.hasUnique(UniqueType.CanBuildOutsideBorders)
            && tile.neighbors.any { it.getOwner() == unit.civ && it.owningCity != null
            && tile.aerialDistanceTo(it.owningCity!!.getCenterTile()) <= civInfo.modConstants.cityWorkRange } ))
            return 0f

        val stats = tile.stats.getStatDiffForImprovement(improvement, civInfo, tile.getCity(), localUniqueCache, currentTileStats)

        var isResourceImprovedByNewImprovement = tile.hasViewableResource(civInfo) && tile.tileResource.isImprovedBy(improvementName)

        if (improvementName.startsWith(Constants.remove)) {
            // We need to look beyond what we are doing right now and at the final improvement that will be on this tile
            val removedObject = improvementName.replace(Constants.remove, "")
            val removedFeature = tile.terrainFeatures.firstOrNull { it == removedObject }
            val removedImprovement = if (removedObject == tile.improvement) removedObject else null

            if (removedFeature != null || removedImprovement != null) {
                val newTile = tile.clone(addUnits = false)
                newTile.setTerrainTransients()
                if (removedFeature != null)
                    newTile.removeTerrainFeature(removedFeature)
                if (removedImprovement != null)
                    newTile.removeImprovement()
                val wantedFinalImprovement = chooseImprovement(unit, newTile, localUniqueCache)
                if (wantedFinalImprovement != null){
                    val statDiff = newTile.stats.getStatDiffForImprovement(wantedFinalImprovement, civInfo, newTile.getCity(), localUniqueCache)
                    stats.add(statDiff)
                    // Take into account that the resource might be improved by the *final* improvement
                    isResourceImprovedByNewImprovement = newTile.resource != null && newTile.tileResource.isImprovedBy(wantedFinalImprovement.name)
                if (tile.terrainFeatures.isNotEmpty() && tile.lastTerrain.hasUnique(UniqueType.ProductionBonusWhenRemoved))
                    stats.add(Stat.Production, 0.5f) //We're gaining tempo by chopping the forest, adding an imaginary yield per turn is a way to correct for this
                }

            }
        }

        // If the tile is a neighboring tile it has a lower value
        if (tile.getOwner() != unit.civ)
            stats.div(3f)

        var value = Automation.rankStatsValue(stats, unit.civ)
        // Calculate the bonus from gaining the resources, this isn't included in the stats above
        if (tile.resource != null && tile.tileResource.resourceType != ResourceType.Bonus) {
            // A better resource ranking system might be required, we don't want the improvement
            // ranking for resources to be too high
            if (tile.improvement != null && tile.tileResource.isImprovedBy(tile.improvement!!)) {
                value -= (tile.resourceAmount / 2).coerceIn(1,2)
            }
            if (isResourceImprovedByNewImprovement) {
                value += (tile.resourceAmount / 2).coerceIn(1,2)
            }
        }
        if (isImprovementProbablyAFort(improvement)) {
            value += evaluateFortSurroundings(tile, improvement.hasUnique(UniqueType.OneTimeTakeOverTilesInRadius))
        } else if (tile.getTileImprovement() != null && isImprovementProbablyAFort(tile.getTileImprovement()!!)) {
            // Replace/build improvements on other tiles before this one
            value /= 2
        }
        return value
    }

    /**
     * Checks whether the improvement matching the tile resource requires any terrain feature to be removed first.
     *
     * Assumes the caller ensured that terrainFeature and resource are both present!
     */
    private fun isResourceImprovementAllowedOnFeature(
        tile: Tile,
        potentialTileImprovements: Map<String, TileImprovement>
    ): Boolean {
        return tile.tileResource.getImprovements().any { resourceImprovementName ->
            if (resourceImprovementName !in potentialTileImprovements) return@any false
            val resourceImprovement = potentialTileImprovements[resourceImprovementName]!!
            tile.terrainFeatureObjects.any { resourceImprovement.isAllowedOnFeature(it) }
        }
    }

    /**
     * Checks whether a given tile allows a Fort and whether a Fort may be undesirable (without checking surroundings or if there is a fort already on the tile).
     *
     * -> Checks: city, already built, resource, great improvements.
     * Used only in [evaluateFortPlacement].
     */
    private fun isAcceptableTileForFort(tile: Tile): Boolean {
        //todo Should this not also check impassable and the fort improvement's terrainsCanBeBuiltOn/uniques?
        if (tile.isCityCenter() // don't build fort in the city
            || !tile.isLand // don't build fort in the water
            || (tile.hasViewableResource(civInfo)
                && tile.tileResource.resourceType != ResourceType.Bonus) // don't build on resource tiles
            || tile.containsGreatImprovement() // don't build on great improvements (including citadel)
        ) return false

        return true
    }

    /**
     * Do we want a Fort [here][tile] considering surroundings?
     * (but does not check if if there is already a fort here)
     *
     * @param  isCitadel Controls within borders check - true also allows 1 tile outside borders
     * @return Yes the location is good for a Fort here
     */
    private fun evaluateFortSurroundings(tile: Tile, isCitadel: Boolean): Float {
        
        // build on our land only
        if (tile.owningCity?.civ != civInfo &&
            // except citadel which can be built near-by
            (!isCitadel || tile.neighbors.all { it.getOwner() != civInfo }) ||
            !isAcceptableTileForFort(tile)) return 0f
        val enemyCivs = civInfo.getCivsAtWarWith()

        // no potential enemies
        if (enemyCivs.none()) return 0f

        var valueOfFort = 1f

        if (civInfo.isCityState && civInfo.getAllyCivName() != null) valueOfFort -= 1f // Allied city states probably don't need to build forts

        if (tile.hasViewableResource(civInfo)) valueOfFort -= 1

        // if this place is not perfect, let's see if there is a better one
        val nearestTiles = tile.getTilesInDistance(1).filter { it.owningCity?.civ == civInfo }
        for (closeTile in nearestTiles) {
            // don't build forts too close to the cities
            if (closeTile.isCityCenter()) {
                valueOfFort -= .5f
                continue
            }
            // don't build forts too close to other forts
            if (closeTile.improvement != null
                && isImprovementProbablyAFort(closeTile.getTileImprovement()!!)
                || (closeTile.improvementInProgress != null && isImprovementProbablyAFort(closeTile.improvementInProgress!!)))
                valueOfFort -= 1f
            // there is probably another better tile for the fort
            if (!tile.isHill() && closeTile.isHill() &&
                isAcceptableTileForFort(closeTile)) valueOfFort -= .2f
            // We want to build forts more in choke points
            if (tile.isImpassible()) valueOfFort += .2f
        }

        val threatMapping: (Civilization) -> Int = {
            // the war is already a good nudge to build forts
            (if (civInfo.isAtWarWith(it)) 5 else 0) +
                // let's check also the force of the enemy
                when (Automation.threatAssessment(civInfo, it)) {
                    ThreatLevel.VeryLow -> 1 // do not build forts
                    ThreatLevel.Low -> 6 // too close, let's build until it is late
                    ThreatLevel.Medium -> 10
                    ThreatLevel.High -> 15 // they are strong, let's built until they reach us
                    ThreatLevel.VeryHigh -> 20
                }
        }
        val enemyCivsIsCloseEnough = enemyCivs.filter { NextTurnAutomation.getMinDistanceBetweenCities(
            civInfo, it) <= threatMapping(it) }
        // No threat, let's not build fort
        if (enemyCivsIsCloseEnough.none()) return 0f

        // Make a list of enemy cities as sources of threat
        val enemyCities = mutableListOf<Tile>()
        enemyCivsIsCloseEnough.forEach { enemyCities.addAll(it.cities.map { city -> city.getCenterTile() }) }

        // Find closest enemy city
        val closestEnemyCity = enemyCities.minByOrNull { it.aerialDistanceTo(tile) }!!
        val distanceToEnemyCity = tile.aerialDistanceTo(closestEnemyCity)
        // Find our closest city to defend from this enemy city
        val closestCity = civInfo.cities.minByOrNull { it.getCenterTile().aerialDistanceTo(tile) }!!.getCenterTile()
        val distanceBetweenCities = closestEnemyCity.aerialDistanceTo(closestCity)
        // Find the distance between the target enemy city to our closest city
        val distanceOfEnemyCityToClosestCityOfUs = civInfo.cities.map {  it.getCenterTile().aerialDistanceTo(closestEnemyCity) }.minBy { it }

        // We don't want to defend city closest to this this tile if it is behind other cities
        if (distanceBetweenCities >= distanceOfEnemyCityToClosestCityOfUs + 2) return 0f

        // This location is not between the city and the enemy
        if (distanceToEnemyCity >= distanceBetweenCities
            // Don't build in enemy city range
            || distanceToEnemyCity <= 2) return 0f

        valueOfFort += 2 - abs(distanceBetweenCities - 1 - distanceToEnemyCity)
        // +2 is a acceptable deviation from the straight line between cities
        return valueOfFort.coerceAtLeast(0f)
    }

    /**
     * Do we want to build a Fort [here][tile] considering surroundings?
     *
     * @param  isCitadel Controls within borders check - true also allows 1 tile outside borders
     * @return Yes the location is good for a Fort here
     */
    fun evaluateFortPlacement(tile: Tile, isCitadel: Boolean): Boolean {
        return tile.improvement != Constants.fort // don't build fort if it is already here
            && evaluateFortSurroundings(tile, isCitadel) > 0
    }

    fun isImprovementProbablyAFort(improvementName: String): Boolean = isImprovementProbablyAFort(ruleSet.tileImprovements[improvementName]!!)
    fun isImprovementProbablyAFort(improvement: TileImprovement): Boolean = improvement.hasUnique(UniqueType.DefensiveBonus)


    /** Try improving a Water Resource
     *
     *  todo: No logic to avoid capture by enemies yet!
     *
     *  @return Whether any progress was made (improved a tile or at least moved towards an opportunity)
     */
    fun automateWorkBoats(unit: MapUnit): Boolean {
        val closestReachableResource = unit.civ.cities.asSequence()
            .flatMap { city -> city.getTiles() }
            .filter {
                hasWorkableSeaResource(it, unit.civ)
                    && (unit.currentTile == it || unit.movement.canMoveTo(it))
            }
            .sortedBy { it.aerialDistanceTo(unit.currentTile) }
            .firstOrNull { unit.movement.canReach(it) && isNotBonusResourceOrWorkable(it, unit.civ) }
            ?: return false

        unit.movement.headTowards(closestReachableResource)
        if (unit.currentTile != closestReachableResource) return true // moving counts as progress

        return UnitActions.invokeUnitAction(unit, UnitActionType.CreateImprovement)
    }

    companion object {
        // Static methods so they can be reused in ConstructionAutomation
        /** Checks whether [tile] is water and has a resource [civInfo] can improve
         *
         *  Does check whether a matching improvement can currently be built (e.g. Oil before Refrigeration).
         *  Can return `true` if there is an improvement that does not match the resource (for future modding abilities).
         *  Does not check tile ownership - caller [automateWorkBoats] already did, other callers need to ensure this explicitly.
         */
        @Readonly
        fun hasWorkableSeaResource(tile: Tile, civInfo: Civilization) = when {
            !tile.isWater -> false
            tile.resource == null -> false
            tile.improvement != null && tile.tileResource.isImprovedBy(tile.improvement!!) -> false
            !tile.hasViewableResource(civInfo) -> false
            else -> tile.tileResource.getImprovements().any {
                val improvement = civInfo.gameInfo.ruleset.tileImprovements[it]!!
                tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
            }
        }

        /** Test whether improving the resource on [tile] benefits [civInfo] (yields or strategic or luxury)
         *
         *  Only tests resource type and city range, not any improvement requirements.
         *  @throws NullPointerException on tiles without a resource
         */
        @Readonly
        fun isNotBonusResourceOrWorkable(tile: Tile, civInfo: Civilization): Boolean =
            tile.tileResource.resourceType != ResourceType.Bonus // Improve Oil even if no City reaps the yields
                || civInfo.cities.any { it.tilesInRange.contains(tile) } // Improve Fish only if any of our Cities reaps the yields
    }
}
