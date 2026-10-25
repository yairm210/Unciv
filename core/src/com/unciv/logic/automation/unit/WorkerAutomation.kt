package com.unciv.logic.automation.unit

import com.unciv.view.TileView
import com.unciv.view.CivView
import com.unciv.view.MapUnitView
import com.unciv.view.GameView
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.unit.UnitAutomation.wander
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.logic.map.mapunit.actions.UnitActions
import com.unciv.logic.map.mapunit.actions.UnitActionsFromUniques
import com.unciv.utils.debug
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly
import com.unciv.logic.automation.Timers.Companion.timeThis
import com.unciv.models.Counter

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

    @Cache private val tileRankings = HashMap<TileView, TileImprovementRank>()
    
    @Cache private val workersPerCity = Counter<City>()

    /** Temporary bridge: the [TileView] of a raw [Tile], as seen by [this] unit's civ */
    @Readonly
    private fun MapUnitView.viewOf(tile: Tile): TileView = getTile().getTileMap().getTile(tile)

    ///////////////////////////////////////// Methods /////////////////////////////////////////


    /**
     * Automate one Worker - decide what to do and where, move, start or continue work.
     */
    fun automateWorkerAction(unitView: MapUnitView, dangerousTiles: HashSet<TileView>): Unit = timeThis("automateWorkerAction") {
        val unit = unitView.getUnit()
        val currentTile = unit.getTile()
        val currentTileIsCreatesOneImprovementMarker = currentTile.isMarkedForCreatesOneImprovement()
        // Must be called before any getPriority checks to guarantee the local road cache is processed
        val citiesToConnect = roadBetweenCitiesAutomation.getNearbyCitiesToConnect(unit)
        // Shortcut, we are working a suitable tile, and we're better off minimizing worker-turns by finishing everything on this tile
        if (!currentTileIsCreatesOneImprovementMarker
            && currentTile.improvementInProgress != null && !dangerousTiles.contains(unitView.getTile())
            && getFullPriority(unit.getTile(), unitView) >= 2) {
            return
        }
        val tileToWork = findTileToWork(unitView, dangerousTiles)

        if (tileToWork != currentTile && tileToWork != null) {
            headTowardsTileToWork(unitView, tileToWork)
            return
        }

        if (!currentTileIsCreatesOneImprovementMarker && currentTile.improvementInProgress != null) return // we're working!

        if (tileToWork == currentTile && tileHasWorkToDo(currentTile, unitView)) 
            return startWorkOnCurrentTile(unitView)

        // Support Alpha Frontier-Style Workers that _also_ have the "May create improvements on water resources" unique
        if (unit.cache.hasUniqueToCreateWaterImprovements && automateWorkBoats(unitView)) return

        if (tryHeadTowardsUndevelopedCity(unitView, currentTile)) return

        // Nothing to do, try again to connect cities
        if (roadBetweenCitiesAutomation.tryConnectingCities(unit, citiesToConnect)) return


        debug("WorkerAutomation: %s -> nothing to do", unit.toString())
        unit.civ.addNotification("${unit.shortDisplayName()} has no work to do.", MapUnitAction(unit), NotificationCategory.Units, unit.name, "OtherIcons/Sleep")

        // Idle CS units should wander so they don't obstruct players so much
        if (unit.civ.isCityState)
            wander(unitView, stayInTerritory = true, tilesToAvoid = dangerousTiles)
    }
    
    @Readonly private fun MapUnit.closestCity()
        = currentTile.owningCity
        ?: this.civ.cities.minBy { this.getTile().aerialDistanceTo(it.getCenterTile()) }


    private fun fillWorkersPerCity() {
        if (!workersPerCity.isEmpty()) return
        for (unit in civInfo.units.getCivUnits().filter { it.cache.hasUniqueToBuildImprovements }) 
            workersPerCity.add(unit.closestCity(), 1)
    }

    private fun tryHeadTowardsUndevelopedCity(
        unitView: MapUnitView,
        currentTile: Tile
    ): Boolean = timeThis("WorkerAutomation.tryHeadTowardsUndevelopedCity") {
        val unit = unitView.getUnit()
        if (unit.civ.cities.isEmpty()) return false
        
        synchronized(workersPerCity) {
            fillWorkersPerCity()
            // Note, however, that the closest city to a tile isn't necessarily the owning city
            // Only move to cities with fewer workers nearby.
            val closestUndevelopedCity = unit.civ.cities
                .sortedBy { it.getCenterTile().aerialDistanceTo(currentTile) }
                .firstOrNull {
                    it != unit.currentTile.owningCity
                    && workersPerCity[it] < workersPerCity[unit.closestCity()]
                    && it.getTiles().any { tile -> tile.isLand
                        && tile.getUnits() .none { unit -> unit.cache.hasUniqueToBuildImprovements }
                        && (tile.isPillaged() || tileHasWorkToDo(tile, unitView)) }
                    && unit.movement.canReach(it.getCenterTile()) } //goto closest undeveloped city

            if (closestUndevelopedCity != null) {
                debug("WorkerAutomation: %s -> head towards undeveloped city %s", unit, closestUndevelopedCity.name)
                workersPerCity.add(unit.closestCity(), -1)
                workersPerCity.add(closestUndevelopedCity, +1)
                val reachedTile = unit.movement.headTowards(closestUndevelopedCity.getCenterTile())
                if (reachedTile != currentTile) unit.doAction() // since we've moved, maybe we can do something here - automate
                return true
            }
            return false
        }
    }

    private fun startWorkOnCurrentTile(unitView: MapUnitView) {
        val unit = unitView.getUnit()
        val currentTile = unit.currentTile
        val tileRanking = tileRankings[unitView.viewOf(currentTile)]!!
        if (tileRanking.repairImprovment == true) {
            debug("WorkerAutomation: $unit -> repairs $currentTile")
            unitView.tryInvokeUnitAction(UnitActionType.Repair)
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
        unitView: MapUnitView,
        tileToWork: Tile,
    ) {
        val unit = unitView.getUnit()
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
        if (reachedTile == tileToWork && reachedTile.improvementInProgress == Constants.fort) {
            debug("Replacing fort in progress with new improvement")
            reachedTile.stopWorkingOnImprovement()
        }

        if (!unit.hasMovement() || reachedTile != tileToWork) return
        
        // If there's move still left, and this is even a tile we want, perform action
        // Unit may stop due to Enemy Unit within walking range during doAction() call

        // tileRankings is updated in getBasePriority, which is only called if isAutomationWorkableTile is true
        // Meaning, there are tiles we can't/shouldn't work, and they won't even be in tileRankings
        if (tileHasWorkToDo(unit.currentTile, unitView))
            startWorkOnCurrentTile(unitView)
    }


    /**
     * Looks for a worthwhile tile to improve
     * @return Null if no tile to work was found
     */
    @Readonly
    private fun findTileToWork(unitView: MapUnitView, tilesToAvoid: Set<TileView>): Tile? = timeThis("findTileToWork") {
        val unit = unitView.getUnit()
        val currentTile = unit.getTile()
        
        if (isAutomationWorkableTile(currentTile, tilesToAvoid, currentTile, unitView)
            && getBasePriority(unitView.getTile(), unitView) >= 3
            && (currentTile.isPillaged() || currentTile.hasFalloutEquivalent() || tileHasWorkToDo(currentTile, unitView)))
            return currentTile
        
        val workableTilesCenterFirst = currentTile.getTilesInDistance(3)
            .filter {
                (it.getOwner() == null || it.getOwner() == unit.civ || it.getOwner()!!.isCityState)
                    && isAutomationWorkableTile(it, tilesToAvoid, currentTile, unitView) 
                    && getTileSelectionPriority(it, unitView) >= 0
            }

        val workableTilesPrioritized = workableTilesCenterFirst.groupBy { getTileSelectionPriority(it, unitView) }
            .asSequence().sortedByDescending { it.key }

        // Search through each group by priority
        // If we can find an eligible best tile in the group lets return that
        // under the assumption that best tile is better than tiles in all lower groups
        for (tilePriorityGroup in workableTilesPrioritized) {
            var bestTile: Tile? = null
            for (tileInGroup in tilePriorityGroup.value.sortedBy { unit.getTile().aerialDistanceTo(it) }) {
                // These are the expensive calculations (tileCanBeImproved, canReach), so we only apply these filters after everything else it done.
                if (!tileHasWorkToDo(tileInGroup, unitView)) continue
                if (unit.getTile() == tileInGroup) return unit.getTile()
                if (!unit.movement.canReach(tileInGroup)) continue
                if (bestTile == null || getFullPriority(tileInGroup, unitView) > getFullPriority(bestTile, unitView)) {
                    bestTile = tileInGroup
                }
            }
            if (bestTile != null) {
                return bestTile
            }
        }
        return null
    }

    /**
     * Road tiles may be outside our borders, so their normal tile priority can be lower than nearby
     * improvements even though [getImprovementRanking] gives the planned road a positive value.
     * Use the road plan priority when ordering tile groups so those strategic tiles get considered.
     */
    @Readonly
    private fun getTileSelectionPriority(tile: Tile, unitView: MapUnitView): Float {
        val unit = unitView.getUnit()
        val basePriority = getBasePriority(unitView.viewOf(tile), unitView)
        val roadPlan = roadBetweenCitiesAutomation.tilesOfRoadsMap[tile] ?: return basePriority
        val bestRoad = roadBetweenCitiesAutomation.bestRoadAvailable
        val roadImprovement = bestRoad.improvement(ruleSet) ?: return basePriority

        if (tile.getUnpillagedRoad() >= bestRoad || !unit.canBuildImprovement(roadImprovement, tile))
            return basePriority

        return maxOf(basePriority, roadPlan.priority)
    }

    @Readonly
    private fun isAutomationWorkableTile(
        tile: Tile,
        tilesToAvoid: Set<TileView>,
        currentTile: Tile,
        unitView: MapUnitView
    ): Boolean {
        val unit = unitView.getUnit()
        if (unitView.getTile().getTileMap().getTile(tile) in tilesToAvoid) return false
        if (tile.isMarkedForCreatesOneImprovement()) return false
        if (!(tile == currentTile
                    || (unit.isCivilian() && (tile.civilianUnit == null || !tile.civilianUnit!!.cache.hasUniqueToBuildImprovements))
                    || (unit.isMilitary() && (tile.militaryUnit == null || !tile.militaryUnit!!.cache.hasUniqueToBuildImprovements))))
            return false
        if (tile.owningCity != null && tile.getOwner() != civInfo) return false
        if (tile.isCityCenter()) return false
        if (tile in roadBetweenCitiesAutomation.tilesOfRoadsMap) return true
        // Don't try to improve tiles we can't benefit from at all
        if (!civInfo.canSeeResource(tile.tileResource) && tile.getTilesInDistance(civInfo.gameInfo.ruleset.modOptions.constants.cityWorkRange)
                .none { it.isCityCenter() && it.getCity()?.civ == civInfo }
        ) return false
        if (tile.tileImprovement?.hasUnique(UniqueType.AutomatedUnitsWillNotReplace) == true && !tile.isPillaged()) return false
        return true
    }

    /**
     * Calculate a priority for the tile without accounting for the improvement it'self
     * This is a cheap guess on how helpful it might be to do work on this tile
     */
    @Readonly
    fun getBasePriority(tile: TileView, unitView: MapUnitView): Float {
        val civ = unitView.civ()
        val unitSpecificPriority = 2 - (tile.aerialDistanceTo(unitView.getTile()) / 2.0f).coerceIn(0f, 2f) * 3
        if (tileRankings.containsKey(tile))
            return tileRankings[tile]!!.tilePriority + unitSpecificPriority

        var priority = 0f
        if (tile.getOwner() == civ) {
            priority += 1f // the fact it's inside our borders means we can do something
            if (tile.providesYield()) priority += 2
            if (hasFalloutEquivalent(tile)) priority += 1
            if (tile.terrainFeatures.isNotEmpty()) {
                val lastTerrain = tile.getAllTerrains().last()
                if (lastTerrain.hasUnique(UniqueType.ProductionBonusWhenRemoved)) priority += 0.5f 
                else if (lastTerrain.hasUnique(UniqueType.Vegetation)) priority -= 0.3f
            } // Removing our forests is good for tempo, jungles not as much
            if (tile.getAllTerrains().any { it.hasUnique(UniqueType.FreshWater) }) priority += 1
            // we want our farms up when unlocking Civil Service
        }

        val resource = tile.getViewableResource(civ)
        if (resource != null) {
            priority += 1
            if (resource.resourceType == ResourceType.Luxury) priority += 5
            //luxuries are more important than other types of resources
        }
            
        tileRankings[tile] = TileImprovementRank(priority)
        return priority + unitSpecificPriority
    }

    /**
     * Calculates the priority building the improvement on the tile
     */
    @Readonly
    private fun getImprovementPriority(tile: Tile, unitView: MapUnitView): Float = timeThis("getImprovementPriority") {
        val unit = unitView.getUnit()
        val tileView = unitView.viewOf(tile)
        getBasePriority(tileView, unitView)
        @LocalState val rank = tileRankings[tileView]
        if (rank!!.improvementPriority == null) {
            // All values of rank have to be initialized
            rank.improvementPriority = -100f
            rank.bestImprovement = null
            rank.repairImprovment = false

            val bestImprovement = chooseImprovement(unitView, tile)
            if (bestImprovement != null) {
                rank.bestImprovement = bestImprovement
                // Increased priority if the improvement has been worked on longer
                val timeSpentPriority = if (tile.improvementInProgress == bestImprovement.name)
                    bestImprovement.getTurnsToBuild(unit.civ,unit) - tile.turnsToImprovement else 0

                rank.improvementPriority = getImprovementRanking(tile, unitView, rank.bestImprovement!!) + timeSpentPriority
            }

            if (tile.tileImprovement != null && tile.isPillaged() && tile.owningCity != null) {
                // Value repairing higher when it is quicker and is in progress
                var repairBonusPriority = tile.getImprovementToRepair()!!.getTurnsToBuild(unit.civ,unit) - UnitActionsFromUniques.getRepairTurns(unit)
                if (tile.improvementInProgress == Constants.repair) repairBonusPriority += UnitActionsFromUniques.getRepairTurns(unit) - tile.turnsToImprovement

                val repairPriority = repairBonusPriority + Automation.rankStatsValue(tile.stats.getStatDiffForImprovement(tile.tileImprovement!!, unit.civ, tile.owningCity), GameView(unit.civ.gameInfo, unit.civ).civView)
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
    @Readonly
    private fun getFullPriority(tile: Tile, unitView: MapUnitView): Float {
        return getBasePriority(unitView.viewOf(tile), unitView) + getImprovementPriority(tile, unitView)
    }

    /**
     * Returns the best improvement
     */
    @Readonly
    private fun tileHasWorkToDo(tile: Tile, unitView: MapUnitView): Boolean = timeThis("tileHasWorkToDo") {
        if (getImprovementPriority(tile, unitView) <= 0) return false
        val tileRanking = tileRankings[unitView.viewOf(tile)]!!
        if (!(tileRanking.bestImprovement != null || tileRanking.repairImprovment!!))
            throw IllegalStateException("There was an improvementPriority > 0 and nothing to do")
        return true
    }

    /**
     * Determine the improvement appropriate to a given tile and worker
     * Returns null if none is worth it
     * */
    @Readonly
    private fun chooseImprovement(unitView: MapUnitView, 
          tile: Tile, 
          ignoreImprovements: Sequence<TileImprovement> = NO_IGNORED_IMPROVEMENTS
    ): TileImprovement? = timeThis("chooseImprovement") {
        val unit = unitView.getUnit()
        if (tile.isMarkedForCreatesOneImprovement()) return null
        // You can keep working on half-built improvements, even if they're unique to another civ
        if (tile.improvementInProgress != null) return ruleSet.tileImprovements[tile.improvementInProgress]

        val gameContext = GameContext(civInfo = unit.civ, unit = unit, tile = tile)
        val currentImprovement = tile.tileImprovement
        val potentialTileImprovements = ruleSet.tileImprovements.filter {
            !ignoreImprovements.contains(it.value)
                    && (it.value.uniqueTo == null || unit.civ.matchesFilter(it.value.uniqueTo!!, gameContext))
                    && unit.canBuildImprovement(it.value, tile)
                    && tile.improvementFunctions.canBuildImprovement(it.value, gameContext)
                // Properly exclude removing forest and jungle tiles from potentialTileImprovements.
                    && !(UncivGame.Current.settings.stopAutomatedWorkersRemoveVegetation &&
                        tile.terrainHasUnique(UniqueType.Vegetation) &&
                        it.value.name.startsWith(Constants.remove) &&
                        civInfo.isHuman()) // Make sure to only apply this to player automated workers.
        }
        if (potentialTileImprovements.isEmpty()) return null

        val currentTileStats = tile.stats.getTileStats(tile.getCity(), civInfo)
        val allBuildableImprovements = potentialTileImprovements.values.asSequence()
            .map { Pair(it, getImprovementRanking(tile, unitView, it, currentTileStats, ignoreImprovements + potentialTileImprovements.values)) }
        var bestBuildableImprovement = allBuildableImprovements
            .filter { it.second > 0f }
            .maxByOrNull { it.second }?.first

        if (currentImprovement != null && civInfo.isHuman() && !UncivGame.Current.settings.automatedWorkersReplaceImprovements
            && UncivGame.Current.worldScreen?.autoPlay?.isAutoPlayingAndFullAutoPlayAI() == false) {
            // Note that we might still want to build roads or remove fallout, so we can't exit the function immedietly
            bestBuildableImprovement = null
        }

        val lastTerrain = tile.lastTerrain
        
        @Readonly fun isRemovable(terrain: Terrain): Boolean =
            potentialTileImprovements.containsKey(Constants.remove + terrain.name)

        val resource = tile.tileResource
        val improvementForResource: TileImprovement? = when {
            !civInfo.canSeeResource(resource) -> null
            
            tile.terrainFeatures.isNotEmpty() &&
                lastTerrain.unbuildable &&
                isRemovable(lastTerrain) &&
                !tile.providesResources(civInfo) &&
                !isResourceImprovementAllowedOnFeature(tile, potentialTileImprovements)
                    -> potentialTileImprovements[Constants.remove + lastTerrain.name]
            
            else -> resource.getImprovements().asSequence()
                .mapNotNull { potentialTileImprovements[it] }
                .filter { it.name in potentialTileImprovements || it == currentImprovement }
                .maxByOrNull { getImprovementRanking(tile, unitView, it, null, ignoreImprovements + potentialTileImprovements.values) }
        }

        // After gathering all the data, we conduct the hierarchy in one place
        val finalImprovement = when {
            bestBuildableImprovement != null && bestBuildableImprovement.isRoad() -> bestBuildableImprovement
            
            // For bonus resources we just want the highest-yield improvement, not necessarily the resource-yielding improvement
            improvementForResource != null && resource!!.resourceType != ResourceType.Bonus ->
                if (improvementForResource == currentImprovement) null else improvementForResource
            
            // If this is a resource that HAS an improvement that we can see, but this unit can't build it, don't waste your time
            resource.let {
                civInfo.canSeeResource(it) &&
                    it.resourceType != ResourceType.Bonus &&
                    it.getImprovements().any()
            } -> return null
            
            bestBuildableImprovement == null -> null

            tile.improvement != null &&
                    getImprovementRanking(tile, unitView, tile.tileImprovement!!, null, ignoreImprovements + potentialTileImprovements.values)
                    > getImprovementRanking(tile, unitView, bestBuildableImprovement, null, ignoreImprovements + potentialTileImprovements.values)
                -> null // What we have is better, even if it's pillaged we should repair it

            lastTerrain.let {
                isRemovable(it) &&
                    (Automation.rankStatsValue(it, GameView(civInfo.gameInfo, civInfo).civView) < 0 || it.hasUnique(UniqueType.NullifyYields))
            } -> potentialTileImprovements[Constants.remove + lastTerrain.name]

            else -> bestBuildableImprovement
        }
        return finalImprovement
    }

    @Readonly
    private fun getImprovementRanking(
        tile: Tile,
        unitView: MapUnitView,
        improvement: TileImprovement,
        /** Provide for performance */ currentTileStats: Stats? = null,
        ignoreImprovements: Sequence<TileImprovement> = NO_IGNORED_IMPROVEMENTS
    ): Float {
        val unit = unitView.getUnit()

        // Add the value of roads if we want to build it here
        if (improvement.isRoad() && roadBetweenCitiesAutomation.bestRoadAvailable.improvement(ruleSet) == improvement
            && tile in roadBetweenCitiesAutomation.tilesOfRoadsMap) {
            val roadPlan = roadBetweenCitiesAutomation.tilesOfRoadsMap[tile]!!
            // We still want good farm/mine building to be able to outrank a low-value road (e.g. an
            // upgrade-only Road -> Railroad on an already-connected route), but connecting a genuinely
            // disconnected city (see #15417) shouldn't need a near-maximum roadPlan.priority (previously -9)
            // to ever get built - that left roads languishing for dozens of turns behind minor tile upgrades.
            val value = (roadPlan.priority - WorkerAutomationConst.connectRoadPriorityOffset)
            return value
        }

        // If this tile is not in our territory or neighboring it, it has no value
        if (tile.getOwner() != unit.civ &&
            // Check if it is not an unowned neighboring tile that can be in city range
            (!improvement.hasUnique(UniqueType.CanBuildOutsideBorders) ||
                tile.neighbors.none {
                    it.getOwner() == unit.civ && it.owningCity != null && 
                        tile.aerialDistanceTo(it.owningCity!!.getCenterTile()) <= civInfo.modConstants.cityWorkRange
                })
        )
            return 0f
        

        @LocalState val stats = tile.stats.getStatDiffForImprovement(improvement, civInfo, tile.getCity(), currentTileStats)

        improvement.forEachMatchingUnique(UniqueType.ImprovementStatsForAdjacencies, GameContext.EmptyState) { unique ->
            if (unique.params[1] == improvement.name) //for Moai-like improvements, add the yields of future self-adjacencies
                stats.add(unique.stats * tile.neighbors.count { (it.tileImprovement != improvement && it.improvementFunctions.canBuildImprovement(improvement, gameContext = civInfo.state)) })
        }

        var isResourceImprovedByNewImprovement = tile.tileResource.let { civInfo.canSeeResource(it) && it.isImprovedBy(improvement.name) }

        if (improvement.name.startsWith(Constants.remove)) {
            // We need to look beyond what we are doing right now and at the final improvement that will be on this tile
            val removalObject = improvement.name.replace(Constants.remove, "")
            val removedFeature = tile.terrainFeatures.firstOrNull { it == removalObject }
            val removalImprovement = if (removalObject == tile.improvement) removalObject else null

            if (removedFeature != null || removalImprovement != null) {
                @LocalState val newTile = tile.clone(addUnits = false)
                newTile.setTerrainTransients()
                if (removedFeature != null)
                    newTile.removeTerrainFeature(removedFeature)
                if (removalImprovement != null)
                    newTile.removeImprovement()
                val wantedFinalImprovement = chooseImprovement(unitView, newTile, ignoreImprovements = ignoreImprovements)
                if (wantedFinalImprovement != null){
                    val statDiff = newTile.stats.getStatDiffForImprovement(wantedFinalImprovement, civInfo, newTile.getCity())
                    stats.add(statDiff)
                    // Take into account that the resource might be improved by the *final* improvement
                    isResourceImprovedByNewImprovement = newTile.tileResource?.isImprovedBy(wantedFinalImprovement.name) == true
                    if (tile.terrainFeatures.isNotEmpty() && tile.lastTerrain.hasUnique(UniqueType.ProductionBonusWhenRemoved))
                        stats.add(Stat.Production, 0.5f) //We're gaining tempo by chopping the forest, adding an imaginary yield per turn is a way to correct for this
                }

            }
        }

        // If the tile is a neighboring tile it has a lower value
        if (tile.getOwner() != unit.civ)
            stats.div(3f)

        var value = Automation.rankStatsValue(stats, GameView(unit.civ.gameInfo, unit.civ).civView)
        // Calculate the bonus from gaining the resources, this isn't included in the stats above
        val resource = tile.tileResource
        val currentImprovement = tile.tileImprovement
        if (resource != null) {
            // A better resource ranking system might be required, we don't want the improvement
            // ranking for resources to be too high
            if (resource.resourceType != ResourceType.Bonus) {
                // Always build the resource-specific improvement for luxuries and strategics,
                // as it's nontrivial to tell when we won't need them
                if (currentImprovement != null && resource.isImprovedBy(currentImprovement)) {
                    value -= 2f
                }
                if (isResourceImprovedByNewImprovement) {
                    value += 2f
                }
            } else {
                // For bonus resources, we want to build just enough resource-specific improvements
                // to unlock stone works and stables, otherwise farms are better
                if (currentImprovement != null && tile.tileResource!!.isImprovedBy(currentImprovement)) {
                    value -= 0.3f // enough to offset the 0.2f food vs production value difference
                }
                if (isResourceImprovedByNewImprovement && tile.getTilesInDistance(4).none { it.tileImprovement == improvement }) {
                    value += 0.3f
                }
            }
        }
        if (currentImprovement != null && isImprovementProbablyAFort(currentImprovement)) {
            // Replace/build improvements on other tiles before this one
            // the old fort building logic was found to be detrimental (PR #14309), but let's keep the forts we got around for a bit
            value /= 2
        }
        return value
    }

    @Readonly
    private fun getImprovementRanking(
        tile: Tile,
        unitView: MapUnitView,
        improvementName: String,
        
        /** Provide for performance */ currentTileStats: Stats? = null
    ): Float = timeThis("getImprovementRanking") {
        val unit = unitView.getUnit()
        val improvement = ruleSet.tileImprovements[improvementName]!!
        return getImprovementRanking(tile, unitView, improvement, currentTileStats)
    }

    /**
     * Checks whether the improvement matching the tile resource requires any terrain feature to be removed first.
     *
     * Assumes the caller ensured that terrainFeature and resource are both present!
     */
    @Readonly
    private fun isResourceImprovementAllowedOnFeature(
        tile: Tile,
        potentialTileImprovements: Map<String, TileImprovement>
    ): Boolean {
        return tile.tileResource!!.getImprovements().any { resourceImprovementName ->
            if (resourceImprovementName !in potentialTileImprovements) return@any false
            val resourceImprovement = potentialTileImprovements[resourceImprovementName]!!
            tile.terrainFeatureObjects.any { resourceImprovement.isAllowedOnFeature(it) }
        }
    }

    /** Fallout or an equivalent terrain feature - same check as [Tile.hasFalloutEquivalent] */
    @Readonly fun hasFalloutEquivalent(tile: TileView): Boolean =
        tile.terrainFeatures.any { tile.getRuleset().terrains[it]!!.hasUnique(UniqueType.NullifyYields) }

    @Readonly fun isImprovementProbablyAFort(improvement: TileImprovement): Boolean = improvement.hasUnique(UniqueType.DefensiveBonus)


    /** Try improving a Water Resource
     *
     *  todo: No logic to avoid capture by enemies yet!
     *
     *  @return Whether any progress was made (improved a tile or at least moved towards an opportunity)
     */
    fun automateWorkBoats(unitView: MapUnitView): Boolean {
        val civ = unitView.civ()
        val unitTile = unitView.getTile()
        val closestReachableResource = civ.cities().asSequence()
            .flatMap { city -> city.getTiles() }
            .filter {
                hasWorkableSeaResource(it, civ)
                    && (unitTile == it || unitView.canMoveTo(it))
            }
            .sortedBy { it.aerialDistanceTo(unitTile) }
            .firstOrNull { unitView.canReach(it) && isNotBonusResourceOrWorkable(it, civ) }
            ?: return false

        unitView.tryHeadTowards(closestReachableResource)
        if (unitView.getTile() != closestReachableResource) return true // moving counts as progress

        return unitView.tryInvokeUnitAction(UnitActionType.CreateImprovement)
    }

    companion object {
        val NO_IGNORED_IMPROVEMENTS: Sequence<TileImprovement> = sequenceOf()
        
        // Static methods so they can be reused in ConstructionAutomation
        /** Checks whether [tile] is water and has a resource [civInfo] can improve
         *
         *  Does check whether a matching improvement can currently be built (e.g. Oil before Refrigeration).
         *  Can return `true` if there is an improvement that does not match the resource (for future modding abilities).
         *  Does not check tile ownership - caller [automateWorkBoats] already did, other callers need to ensure this explicitly.
         */
        @Readonly
        fun hasWorkableSeaResource(tile: Tile, civInfo: Civilization): Boolean {
            val resource = tile.tileResource
            return when {
                !tile.isWater -> false
                !civInfo.canSeeResource(resource) -> false
                tile.improvement != null && resource.isImprovedBy(tile.improvement!!) -> false
                else -> resource.getImprovements().any {
                    val improvement = civInfo.gameInfo.ruleset.tileImprovements[it]!!
                    tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
                }
            }
        }

        /** View version of [hasWorkableSeaResource] for automation use */
        @Readonly
        fun hasWorkableSeaResource(tile: TileView, civ: CivView): Boolean {
            val resource = tile.getViewableResource(civ)
            return when {
                resource == null || !tile.isWater -> false
                tile.improvement != null && resource.isImprovedBy(tile.improvement!!) -> false
                else -> resource.getImprovements().any {
                    tile.canBuildImprovement(tile.getRuleset().tileImprovements[it]!!)
                }
            }
        }

        /** View version of [isNotBonusResourceOrWorkable] for automation use.
         *  @throws NullPointerException on tiles without a resource */
        @Readonly
        fun isNotBonusResourceOrWorkable(tile: TileView, civ: CivView): Boolean =
            tile.getViewableResource(civ)!!.resourceType != ResourceType.Bonus
                || civ.cities().any { it.isInRange(tile) }

        /** Test whether improving the resource on [tile] benefits [civInfo] (yields or strategic or luxury)
         *
         *  Only tests resource type and city range, not any improvement requirements.
         *  @throws NullPointerException on tiles without a resource
         */
        @Readonly
        fun isNotBonusResourceOrWorkable(tile: Tile, civInfo: Civilization): Boolean =
            tile.tileResource!!.resourceType != ResourceType.Bonus // Improve Oil even if no City reaps the yields
                || civInfo.cities.any { it.tilesInRange.contains(tile) } // Improve Fish only if any of our Cities reaps the yields
    }
}
