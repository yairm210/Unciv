package com.unciv.logic.automation.unit

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.ThreatLevel
import com.unciv.logic.automation.civilization.NextTurnAutomation
import com.unciv.logic.automation.unit.UnitAutomation.wander
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.map.BFS
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsFromUniques
import com.unciv.utils.Log
import com.unciv.utils.debug

private object WorkerAutomationConst {
    /** BFS max size is determined by the aerial distance of two cities to connect, padded with this */
    // two tiles longer than the distance to the nearest connected city should be enough as the 'reach' of a BFS is increased by blocked tiles
    const val maxBfsReachPadding = 2
}

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

    private val ruleSet = civInfo.gameInfo.ruleset

    /** Caches road to build for connecting cities unless option is off or ruleset removed all roads */
    private val bestRoadAvailable: RoadStatus =
        cloningSource?.bestRoadAvailable ?:
        //Player can choose not to auto-build roads & railroads.
        if (civInfo.isHuman() && (!UncivGame.Current.settings.autoBuildingRoads
                || UncivGame.Current.settings.autoPlay.isAutoPlayingAndFullAI()))
            RoadStatus.None
        else civInfo.tech.getBestRoadAvailable()

    /** Civ-wide list of unconnected Cities, sorted by closest to capital first */
    private val citiesThatNeedConnecting: List<City> by lazy {
        val result = civInfo.cities.asSequence()
            .filter {
                civInfo.getCapital() != null
                    && it.population.population > 3
                        && !it.isCapital() && !it.isBeingRazed // Cities being razed should not be connected.
                        && !it.cityStats.isConnectedToCapital(bestRoadAvailable)
            }.sortedBy {
                it.getCenterTile().aerialDistanceTo(civInfo.getCapital()!!.getCenterTile())
            }.toList()
        if (Log.shouldLog()) {
            debug("WorkerAutomation citiesThatNeedConnecting for ${civInfo.civName} turn $cachedForTurn:")
            if (result.isEmpty())
                debug("\tempty")
            else result.forEach {
                debug("\t${it.name}")
            }
        }
        result
    }

    /** Civ-wide list of _connected_ Cities, unsorted */
    private val tilesOfConnectedCities: List<Tile> by lazy {
        val result = civInfo.cities.asSequence()
            .filter { it.isCapital() || it.cityStats.isConnectedToCapital(bestRoadAvailable) }
            .map { it.getCenterTile() }
            .toList()
        if (Log.shouldLog()) {
            debug("WorkerAutomation tilesOfConnectedCities for ${civInfo.civName} turn $cachedForTurn:")
            if (result.isEmpty())
                debug("\tempty")
            else result.forEach {
                debug("\t$it")
            }
        }
        result
    }

    /** Caches BFS by city locations (cities needing connecting).
     *
     *  key: The city to connect from as [hex position][Vector2].
     *
     *  value: The [BFS] searching from that city, whether successful or not.
     */
    //todo: If BFS were to deal in vectors instead of Tiles, we could copy this on cloning
    private val bfsCache = HashMap<Vector2, BFS>()

    //todo: UnitMovementAlgorithms.canReach still very expensive and could benefit from caching, it's not using BFS


    ///////////////////////////////////////// Helpers /////////////////////////////////////////

    companion object {
        /** For console logging only */
        private fun MapUnit.label() = toString() + " " + getTile().position.toString()
    }


    ///////////////////////////////////////// Methods /////////////////////////////////////////
    /**
     * Automate one Worker - decide what to do and where, move, start or continue work.
     */
    fun automateWorkerAction(unit: MapUnit, tilesWhereWeWillBeCaptured: Set<Tile>) {
        val currentTile = unit.getTile()
        val tileToWork = findTileToWork(unit, tilesWhereWeWillBeCaptured)

        if (getPriority(tileToWork) < 3) { // building roads is more important
            if (tryConnectingCities(unit)) return
        }

        if (tileToWork != currentTile) {
            debug("WorkerAutomation: %s -> head towards %s", unit.label(), tileToWork)
            val reachedTile = unit.movement.headTowards(tileToWork)
            if (reachedTile != currentTile) unit.doAction() // otherwise, we get a situation where the worker is automated, so it tries to move but doesn't, then tries to automate, then move, etc, forever. Stack overflow exception!

            //If we have reached a fort tile that is in progress and shouldn't be there, cancel it.
            if (reachedTile == tileToWork && reachedTile.improvementInProgress == Constants.fort && !evaluateFortSuroundings(currentTile, false)) {
                debug("Replacing fort in progress with new improvement")
                reachedTile.stopWorkingOnImprovement()
            }

            // If there's move still left, perform action
            // Unit may stop due to Enemy Unit within walking range during doAction() call
            if (unit.currentMovement > 0 && reachedTile == tileToWork) {
                if (reachedTile.isPillaged()) {
                    debug("WorkerAutomation: ${unit.label()} -> repairs $reachedTile")
                    UnitActionsFromUniques.getRepairAction(unit)?.action?.invoke()
                    return
                }
                if (reachedTile.improvementInProgress == null && reachedTile.isLand
                        && tileCanBeImproved(unit, reachedTile)
                ) {
                    debug("WorkerAutomation: ${unit.label()} -> start improving $reachedTile")
                    return reachedTile.startWorkingOnImprovement(
                        chooseImprovement(unit, reachedTile)!!, civInfo, unit
                    )
                }
            }
            return
        }

        if (currentTile.isPillaged()) {
            debug("WorkerAutomation: ${unit.label()} -> repairs $currentTile")
            UnitActionsFromUniques.getRepairAction(unit)?.action?.invoke()
            return
        }

        if (currentTile.improvementInProgress == null) {
            val newImprovement = getImprovementToImprove(unit, currentTile)
            if (newImprovement != null) {
                debug("WorkerAutomation: ${unit.label()} -> start improving $currentTile")
                return currentTile.startWorkingOnImprovement(newImprovement, civInfo, unit)
            }
        }

        if (currentTile.improvementInProgress != null) return // we're working!

        if (unit.cache.hasUniqueToCreateWaterImprovements) {
            // Support Alpha Frontier-Style Workers that _also_ have the "May create improvements on water resources" unique
            if (automateWorkBoats(unit)) return
        }

        if (tryConnectingCities(unit)) return //nothing to do, try again to connect cities

        val citiesToNumberOfUnimprovedTiles = HashMap<String, Int>()
        for (city in unit.civ.cities) {
            citiesToNumberOfUnimprovedTiles[city.id] = city.getTiles()
                .count { it.isLand && it.civilianUnit == null && (it.isPillaged() || tileCanBeImproved(unit, it)) }
        }

        val mostUndevelopedCity = unit.civ.cities.asSequence()
            .filter { citiesToNumberOfUnimprovedTiles[it.id]!! > 0 }
            .sortedByDescending { citiesToNumberOfUnimprovedTiles[it.id] }
            .firstOrNull { unit.movement.canReach(it.getCenterTile()) } //goto most undeveloped city

        if (mostUndevelopedCity != null && mostUndevelopedCity != currentTile.owningCity) {
            debug("WorkerAutomation: %s -> head towards undeveloped city %s", unit.label(), mostUndevelopedCity.name)
            val reachedTile = unit.movement.headTowards(mostUndevelopedCity.getCenterTile())
            if (reachedTile != currentTile) unit.doAction() // since we've moved, maybe we can do something here - automate
            return
        }

        debug("WorkerAutomation: %s -> nothing to do", unit.label())
        unit.civ.addNotification("${unit.shortDisplayName()} has no work to do.", currentTile.position, NotificationCategory.Units, unit.name, "OtherIcons/Sleep")

        // Idle CS units should wander so they don't obstruct players so much
        if (unit.civ.isCityState())
            wander(unit, stayInTerritory = true, tilesToAvoid = tilesWhereWeWillBeCaptured)
    }

    /**
     * Looks for work connecting cities
     * @return whether we actually did anything
     */
    private fun tryConnectingCities(unit: MapUnit): Boolean {
        if (bestRoadAvailable == RoadStatus.None || citiesThatNeedConnecting.isEmpty()) return false

        // Since further away cities take longer to get to and - most importantly - the canReach() to them is very long,
        // we order cities by their closeness to the worker first, and then check for each one whether there's a viable path
        // it can take to an existing connected city.
        val candidateCities = citiesThatNeedConnecting.asSequence().filter {
            // Cities that are too far away make the canReach() calculations devastatingly long
            it.getCenterTile().aerialDistanceTo(unit.getTile()) < 20
        }
        if (candidateCities.none()) return false // do nothing.

        val isCandidateTilePredicate: (Tile) -> Boolean = { it.isLand && unit.movement.canPassThrough(it) }
        val currentTile = unit.getTile()
        val cityTilesToSeek = ArrayList(tilesOfConnectedCities.sortedBy { it.aerialDistanceTo(currentTile) })

        for (toConnectCity in candidateCities) {
            val toConnectTile = toConnectCity.getCenterTile()
            val bfs: BFS = bfsCache[toConnectTile.position] ?:
                BFS(toConnectTile, isCandidateTilePredicate).apply {
                    maxSize = HexMath.getNumberOfTilesInHexagon(
                        WorkerAutomationConst.maxBfsReachPadding +
                            tilesOfConnectedCities.minOf { it.aerialDistanceTo(toConnectTile) }
                    )
                    bfsCache[toConnectTile.position] = this@apply
                }

            while (true) {
                for (cityTile in cityTilesToSeek.toList()) { // copy since we change while running
                    if (!bfs.hasReachedTile(cityTile)) continue
                    // we have a winner!
                    val pathToCity = bfs.getPathTo(cityTile)
                    val roadableTiles = pathToCity.filter { it.getUnpillagedRoad() < bestRoadAvailable }
                    val tileToConstructRoadOn: Tile
                    if (currentTile in roadableTiles) tileToConstructRoadOn =
                        currentTile
                    else {
                        val reachableTile = roadableTiles
                            .sortedBy { it.aerialDistanceTo(unit.getTile()) }
                            .firstOrNull {
                                unit.movement.canMoveTo(it) && unit.movement.canReach(it)
                            }
                        if (reachableTile == null) {
                            cityTilesToSeek.remove(cityTile) // Apparently we can't reach any of these tiles at all
                            continue
                        }
                        tileToConstructRoadOn = reachableTile
                        unit.movement.headTowards(tileToConstructRoadOn)
                    }
                    if (unit.currentMovement > 0 && currentTile == tileToConstructRoadOn
                        && currentTile.improvementInProgress != bestRoadAvailable.name) {
                        val improvement = bestRoadAvailable.improvement(ruleSet)!!
                        tileToConstructRoadOn.startWorkingOnImprovement(improvement, civInfo, unit)
                    }
                    debug("WorkerAutomation: %s -> connect city %s to %s on %s",
                        unit.label(), bfs.startingPoint.getCity()?.name, cityTile.getCity()!!.name, tileToConstructRoadOn)
                    return true
                }
                if (bfs.hasEnded()) break // We've found another city that this one can connect to
                bfs.nextStep()
            }
            debug("WorkerAutomation: ${unit.label()} -> connect city ${bfs.startingPoint.getCity()?.name} failed at BFS size ${bfs.size()}")
        }

        return false
    }

    /**
     * Looks for a worthwhile tile to improve
     * @return The current tile if no tile to work was found
     */
    private fun findTileToWork(unit: MapUnit, tilesToAvoid: Set<Tile>): Tile {
        val currentTile = unit.getTile()
        val workableTilesCenterFirst = currentTile.getTilesInDistance(4)
                .filter {
                    it !in tilesToAvoid
                    && (it.civilianUnit == null || it == currentTile)
                    && (it.owningCity == null || it.getOwner() == civInfo)
                    && getPriority(it) > 1
                    && it.getTilesInDistance(2)  // don't work in range of enemy cities
                        .none { tile -> tile.isCityCenter() && tile.getCity()!!.civ.isAtWarWith(civInfo) }
                    && it.getTilesInDistance(3)  // don't work in range of enemy units
                        .none { tile -> tile.militaryUnit != null && tile.militaryUnit!!.civ.isAtWarWith(civInfo)}
                }

        // Carthage can move through mountains, special case
        // If there are non-damage dealing tiles available, move to the best of those, otherwise move to the best damage dealing tile
        val workableTilesPrioritized = workableTilesCenterFirst
                .sortedWith(
                    compareBy<Tile> { unit.getDamageFromTerrain(it) > 0 } // Sort on Boolean puts false first
                        .thenByDescending { getPriority(it) }
                )

        // These are the expensive calculations (tileCanBeImproved, canReach), so we only apply these filters after everything else it done.
        val selectedTile = workableTilesPrioritized
            .firstOrNull { unit.movement.canReach(it) && (it.isPillaged() || tileCanBeImproved(unit, it)) }
            ?: return currentTile

        // Note: workableTiles is a Sequence, and we oiginally used workableTiles.contains for the second
        // test, which looks like a second potentially deep iteration of it, after just being iterated
        // for selectedTile. But TileMap.getTilesInDistanceRange iterates its range forward, meaning
        // currentTile is always the very first entry of the _unsorted_ Sequence - if it is still
        // contained at all and not dropped by the filters - which is the point here.
        return if ( currentTile == selectedTile  // No choice
                || (!currentTile.isPillaged() && !tileCanBeImproved(unit, currentTile)) // current tile is unimprovable
                || workableTilesCenterFirst.firstOrNull() != currentTile  // current tile is unworkable by city
                || getPriority(selectedTile) > getPriority(currentTile))  // current tile is less important
            selectedTile
        else currentTile
    }

    /**
     * Tests if tile can be improved by a specific unit, or if no unit is passed, any unit at all
     * (but does not check whether the ruleset contains any unit capable of it)
     */
    private fun tileCanBeImproved(unit: MapUnit, tile: Tile): Boolean {
        return getImprovementToImprove(unit, tile) != null
    }

    private fun getImprovementToImprove(unit: MapUnit, tile: Tile): TileImprovement? {
        //todo This is wrong but works for Alpha Frontier, because the unit has both:
        // It should test for the build over time ability, but this tests the create and die ability
        if (!tile.isLand && !unit.cache.hasUniqueToCreateWaterImprovements) return null
        // Allow outlandish mods having non-road improvements on Mountains
        if (tile.isImpassible() && !unit.cache.canPassThroughImpassableTiles) return null
        if (tile.isCityCenter()) return null

        val city = tile.getCity()
        if (city == null || city.civ != civInfo)
            return null

        if (!city.tilesInRange.contains(tile)
            && !tile.hasViewableResource(civInfo)
            && civInfo.cities.none { it.getCenterTile().aerialDistanceTo(tile) <= 3 }
        )
            return null // unworkable tile

        //If the tile is a junk improvement or a fort placed in a bad location.
        val junkImprovement = tile.getTileImprovement()?.hasUnique(UniqueType.AutomatedWorkersWillReplace) == true
            || (tile.improvement == Constants.fort && !evaluateFortSuroundings(tile, false)
                && (!civInfo.isHuman() || UncivGame.Current.settings.autoPlay.isAutoPlayingAndFullAI()))

        if (tile.improvement != null && !junkImprovement
            && !UncivGame.Current.settings.automatedWorkersReplaceImprovements
            && unit.civ.isHuman() && !UncivGame.Current.settings.autoPlay.isAutoPlayingAndFullAI()
        )
            return null

        val anyImprovementIsOk = tile.improvement == null || junkImprovement

        if (anyImprovementIsOk
                && tile.improvementInProgress != null
                && unit.canBuildImprovement(tile.getTileImprovementInProgress()!!, tile))
            return tile.getTileImprovementInProgress()!!

        val isResourceTileWithoutResourceProvider = !tile.containsGreatImprovement()
            && tile.hasViewableResource(civInfo)
            && (tile.improvement == null || !tile.tileResource.isImprovedBy(tile.improvement!!))

        if (anyImprovementIsOk || isResourceTileWithoutResourceProvider) {
            val chosenImprovement = chooseImprovement(unit, tile)
            if (chosenImprovement != null
                && tile.improvementFunctions.canBuildImprovement(chosenImprovement, civInfo)
                && unit.canBuildImprovement(chosenImprovement, tile)
            )
                return chosenImprovement
        }

        return null // couldn't find anything to construct here
    }


    /**
     * Calculate a priority for improving a tile
     */
    fun getPriority(tile: Tile): Int {
        var priority = 0
        if (tile.getOwner() == civInfo) {
            priority += 2
            if (tile.providesYield()) priority += 3
            if (tile.isPillaged()) priority += 1
        }
        // give a minor priority to tiles that we could expand onto
        else if (tile.getOwner() == null && tile.neighbors.any { it.getOwner() == civInfo })
            priority += 1

        if (priority != 0 && tile.hasViewableResource(civInfo)) priority += 1
        return priority
    }

    /**
     * Determine the improvement appropriate to a given tile and worker
     */
    private fun chooseImprovement(unit: MapUnit, tile: Tile): TileImprovement? {
        // You can keep working on half-built improvements, even if they're unique to another civ
        if (tile.improvementInProgress != null) return ruleSet.tileImprovements[tile.improvementInProgress!!]

        val potentialTileImprovements = ruleSet.tileImprovements.filter {
            unit.canBuildImprovement(it.value, tile)
                    && tile.improvementFunctions.canBuildImprovement(it.value, civInfo)
                    && (it.value.uniqueTo == null || it.value.uniqueTo == unit.civ.civName)
        }
        if (potentialTileImprovements.isEmpty()) return null

        val localUniqueCache = LocalUniqueCache()
        fun getImprovementRanking(improvementName: String): Float {
            val improvement = ruleSet.tileImprovements[improvementName]!!
            val stats = tile.stats.getStatDiffForImprovement(improvement, civInfo, tile.getCity(), localUniqueCache)
            return Automation.rankStatsValue(stats, unit.civ)
        }

        val bestBuildableImprovement = potentialTileImprovements.values.asSequence()
            .map { Pair(it, getImprovementRanking(it.name)) }
            .filter { it.second > 0f }
            .maxByOrNull { it.second }?.first

        val lastTerrain = tile.lastTerrain

        fun isRemovable(terrain: Terrain): Boolean = ruleSet.tileImprovements.containsKey(Constants.remove + terrain.name)

        val improvementStringForResource: String? = when {
            tile.resource == null || !tile.hasViewableResource(civInfo) -> null
            tile.terrainFeatures.isNotEmpty()
                && lastTerrain.unbuildable
                && isRemovable(lastTerrain)
                && !tile.providesResources(civInfo)
                && !isResourceImprovementAllowedOnFeature(tile, potentialTileImprovements) -> Constants.remove + lastTerrain.name
            else -> tile.tileResource.getImprovements().filter { it in potentialTileImprovements || it==tile.improvement }
                .maxByOrNull { getImprovementRanking(it) }
        }

        // After gathering all the data, we conduct the hierarchy in one place
        val improvementString = when {
            improvementStringForResource != null -> if (improvementStringForResource==tile.improvement) null else improvementStringForResource
            // if this is a resource that HAS an improvement, but this unit can't build it, don't waste your time
            tile.resource != null && tile.tileResource.getImprovements().any() -> return null
            bestBuildableImprovement == null -> null

            tile.improvement != null &&
                    getImprovementRanking(tile.improvement!!) > getImprovementRanking(bestBuildableImprovement.name)
                -> null // What we have is better, even if it's pillaged we should repair it

            lastTerrain.let {
                isRemovable(it) &&
                    (Automation.rankStatsValue(it, civInfo) < 0
                        || it.hasUnique(UniqueType.NullifyYields))
            } -> Constants.remove + lastTerrain.name

            else -> bestBuildableImprovement.name
        }
        return ruleSet.tileImprovements[improvementString] // For mods, the tile improvement may not exist, so don't assume.
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
            tile.terrainFeatures.any { resourceImprovement.isAllowedOnFeature(it) }
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
            || tile.hasViewableResource(civInfo) // don't build on resource tiles
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
    private fun evaluateFortSuroundings(tile: Tile, isCitadel: Boolean): Boolean {
        //todo Is the Citadel code dead anyway? If not - why does the nearestTiles check not respect the param?

        // build on our land only
        if (tile.owningCity?.civ != civInfo &&
                    // except citadel which can be built near-by
                    (!isCitadel || tile.neighbors.all { it.getOwner() != civInfo }) ||
            !isAcceptableTileForFort(tile)) return false

        // if this place is not perfect, let's see if there is a better one
        val nearestTiles = tile.getTilesInDistance(2).filter { it.owningCity?.civ == civInfo }.toList()
        for (closeTile in nearestTiles) {
            // don't build forts too close to the cities
            if (closeTile.isCityCenter()) return false
            // don't build forts too close to other forts
            if (closeTile.improvement != null
                && closeTile.getTileImprovement()!!.hasUnique("Gives a defensive bonus of []%")
                || closeTile.improvementInProgress != Constants.fort) return false
            // there is another better tile for the fort
            if (!tile.isHill() && closeTile.isHill() &&
                isAcceptableTileForFort(closeTile)) return false
        }

        val enemyCivs = civInfo.getKnownCivs()
            .filterNot { it == civInfo || it.cities.isEmpty() || !civInfo.getDiplomacyManager(it).canAttack() }
        // no potential enemies
        if (enemyCivs.none()) return false

        val threatMapping: (Civilization) -> Int = {
            // the war is already a good nudge to build forts
            (if (civInfo.isAtWarWith(it)) 20 else 0) +
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
            civInfo,
            it) <= threatMapping(it) }
        // no threat, let's not build fort
        if (enemyCivsIsCloseEnough.none()) return false

        // make list of enemy cities as sources of threat
        val enemyCities = mutableListOf<Tile>()
        enemyCivsIsCloseEnough.forEach { enemyCities.addAll(it.cities.map { city -> city.getCenterTile() }) }

        // find closest enemy city
        val closestEnemyCity = enemyCities.minByOrNull { it.aerialDistanceTo(tile) }!!
        val distanceToEnemy = tile.aerialDistanceTo(closestEnemyCity)

        // find closest our city to defend from this enemy city
        val closestOurCity = civInfo.cities.minByOrNull { it.getCenterTile().aerialDistanceTo(tile) }!!.getCenterTile()
        val distanceToOurCity = tile.aerialDistanceTo(closestOurCity)

        val distanceBetweenCities = closestEnemyCity.aerialDistanceTo(closestOurCity)

        // let's build fort on the front line, not behind the city
        // +2 is a acceptable deviation from the straight line between cities
        return distanceBetweenCities + 2 > distanceToEnemy + distanceToOurCity
    }

    /**
     * Do we want to build a Fort [here][tile] considering surroundings?
     *
     * @param  isCitadel Controls within borders check - true also allows 1 tile outside borders
     * @return Yes the location is good for a Fort here
     */
    fun evaluateFortPlacement(tile: Tile, isCitadel: Boolean): Boolean {
        return tile.improvement != Constants.fort // don't build fort if it is already here
            && evaluateFortSuroundings(tile,isCitadel)
    }

    private fun hasWorkableSeaResource(tile: Tile, civInfo: Civilization): Boolean =
        tile.isWater && tile.improvement == null && tile.hasViewableResource(civInfo)

    /** Try improving a Water Resource
     *
     *  No logic to avoid capture by enemies yet!
     *
     *  @return Whether any progress was made (improved a tile or at least moved towards an opportunity)
     */
    fun automateWorkBoats(unit: MapUnit): Boolean {
        val closestReachableResource = unit.civ.cities.asSequence()
            .flatMap { city -> city.getWorkableTiles() }
            .filter {
                hasWorkableSeaResource(it, unit.civ)
                    && (unit.currentTile == it || unit.movement.canMoveTo(it))
            }
            .sortedBy { it.aerialDistanceTo(unit.currentTile) }
            .firstOrNull { unit.movement.canReach(it) }
            ?: return false

        // could be either fishing boats or oil well
        val isImprovable = closestReachableResource.tileResource.getImprovements().any()
        if (!isImprovable) return false

        unit.movement.headTowards(closestReachableResource)
        if (unit.currentTile != closestReachableResource) return true // moving counts as progress

        // We know we have the CreateWaterImprovements, but not whether
        // all conditionals succeed with a current StateForConditionals(civ, unit)
        // todo: Not necessarily the optimal flow: Be optimistic and head towards,
        //       then when arrived and the conditionals say "no" do something else instead?
        val action = UnitActionsFromUniques.getWaterImprovementAction(unit)
            ?: return false

        // If action.action is null that means only transient reasons prevent the improvement -
        // report progress and hope next run it will work.
        action.action?.invoke()
        return true
    }
}
