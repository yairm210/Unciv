package com.unciv.logic.automation

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.automation.UnitAutomation.wander
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.BFS
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.UniqueType
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
 * This is instantiated from [CivilizationInfo.getWorkerAutomation] and cached there.
 *
 * @param civInfo       The Civilization - data common to all automated workers is cached once per Civ
 * @param cachedForTurn The turn number this was created for - a recreation of the instance is forced on different turn numbers
 */
class WorkerAutomation(
    val civInfo: CivilizationInfo,
    val cachedForTurn: Int,
    cloningSource: WorkerAutomation? = null
) {
    ///////////////////////////////////////// Cached data /////////////////////////////////////////

    private val ruleSet = civInfo.gameInfo.ruleSet

    /** Caches road to build for connecting cities unless option is off or ruleset removed all roads */
    private val bestRoadAvailable: RoadStatus =
        cloningSource?.bestRoadAvailable ?:
        //Player can choose not to auto-build roads & railroads.
        if (civInfo.isPlayerCivilization() && !UncivGame.Current.settings.autoBuildingRoads)
            RoadStatus.None
        else civInfo.tech.getBestRoadAvailable()

    /** Civ-wide list of unconnected Cities, sorted by closest to capital first */
    private val citiesThatNeedConnecting: List<CityInfo> by lazy {
        val result = civInfo.cities.asSequence()
            .filter {
                it.population.population > 3
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
    private val tilesOfConnectedCities: List<TileInfo> by lazy {
        val result = civInfo.cities.asSequence()
            .filter { it.isCapital() || it.cityStats.isConnectedToCapital(bestRoadAvailable) }
            .map { it.getCenterTile() }
            .toList()
        if (Log.shouldLog()) {
            debug("WorkerAutomation tilesOfConnectedCities for ${civInfo.civName} turn $cachedForTurn:")
            if (result.isEmpty())
                debug("\tempty")
            else result.forEach {
                debug("\t$it")    //  ${it.getCity()?.name} included in TileInfo toString()
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
    //todo: If BFS were to deal in vectors instead of TileInfos, we could copy this on cloning
    private val bfsCache = HashMap<Vector2, BFS>()

    //todo: UnitMovementAlgorithms.canReach still very expensive and could benefit from caching, it's not using BFS


    ///////////////////////////////////////// Helpers /////////////////////////////////////////

    companion object {
        /** Maps to instance [WorkerAutomation.automateWorkerAction] knowing only the MapUnit */
        fun automateWorkerAction(unit: MapUnit) {
            unit.civInfo.getWorkerAutomation().automateWorkerAction(unit)
        }

        /** Convenience shortcut supports old calling syntax for [WorkerAutomation.getPriority] */
        fun getPriority(tileInfo: TileInfo, civInfo: CivilizationInfo): Int {
            return civInfo.getWorkerAutomation().getPriority(tileInfo)
        }

        /** Convenience shortcut supports old calling syntax for [WorkerAutomation.evaluateFortPlacement] */
        fun evaluateFortPlacement(tile: TileInfo, civInfo: CivilizationInfo, isCitadel: Boolean): Boolean {
            return civInfo.getWorkerAutomation().evaluateFortPlacement(tile, isCitadel)
        }

        /** For console logging only */
        private fun MapUnit.label() = toString() + " " + getTile().position.toString()
    }


    ///////////////////////////////////////// Methods /////////////////////////////////////////
    /**
     * Automate one Worker - decide what to do and where, move, start or continue work.
     */
    fun automateWorkerAction(unit: MapUnit) {
        val currentTile = unit.getTile()
        val tileToWork = findTileToWork(unit)

        if (getPriority(tileToWork, civInfo) < 3) { // building roads is more important
            if (tryConnectingCities(unit)) return
        }

        if (tileToWork != currentTile) {
            debug("WorkerAutomation: %s -> head towards %s", unit.label(), tileToWork)
            val reachedTile = unit.movement.headTowards(tileToWork)
            if (reachedTile != currentTile) unit.doAction() // otherwise, we get a situation where the worker is automated, so it tries to move but doesn't, then tries to automate, then move, etc, forever. Stack overflow exception!
            return
        }

        if (currentTile.improvementInProgress == null && currentTile.isLand
            && tileCanBeImproved(unit, currentTile)) {
            debug("WorkerAutomation: ${unit.label()} -> start improving $currentTile")
            return currentTile.startWorkingOnImprovement(chooseImprovement(unit, currentTile)!!, civInfo, unit)
        }

        if (currentTile.improvementInProgress != null) return // we're working!
        if (tryConnectingCities(unit)) return //nothing to do, try again to connect cities

        val citiesToNumberOfUnimprovedTiles = HashMap<String, Int>()
        for (city in unit.civInfo.cities) {
            citiesToNumberOfUnimprovedTiles[city.id] = city.getTiles()
                .count { it.isLand && it.civilianUnit == null && tileCanBeImproved(unit, it) }
        }

        val mostUndevelopedCity = unit.civInfo.cities.asSequence()
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
        unit.civInfo.addNotification("${unit.shortDisplayName()} has no work to do.", currentTile.position, unit.name, "OtherIcons/Sleep")

        // Idle CS units should wander so they don't obstruct players so much
        if (unit.civInfo.isCityState())
            wander(unit, stayInTerritory = true)
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

        val isCandidateTilePredicate = { it: TileInfo -> it.isLand && unit.movement.canPassThrough(it) }
        val currentTile = unit.getTile()
        val cityTilesToSeek = tilesOfConnectedCities.sortedBy { it.aerialDistanceTo(currentTile) }

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
                for (cityTile in cityTilesToSeek) {
                    if (!bfs.hasReachedTile(cityTile)) continue
                    // we have a winner!
                    val pathToCity = bfs.getPathTo(cityTile)
                    val roadableTiles = pathToCity.filter { it.roadStatus < bestRoadAvailable }
                    val tileToConstructRoadOn: TileInfo
                    if (currentTile in roadableTiles) tileToConstructRoadOn =
                        currentTile
                    else {
                        val reachableTile = roadableTiles
                            .sortedBy { it.aerialDistanceTo(unit.getTile()) }
                            .firstOrNull {
                                unit.movement.canMoveTo(it) && unit.movement.canReach(
                                    it
                                )
                            }
                            ?: continue
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
                if (bfs.hasEnded()) break
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
    private fun findTileToWork(unit: MapUnit): TileInfo {
        val currentTile = unit.getTile()
        val workableTiles = currentTile.getTilesInDistance(4)
                .filter {
                    (it.civilianUnit == null || it == currentTile)
                            && tileCanBeImproved(unit, it)
                            && it.getTilesInDistance(2)
                            .none { tile -> tile.isCityCenter() && tile.getCity()!!.civInfo.isAtWarWith(civInfo) }
                }
                .sortedByDescending { getPriority(it) }

        // the tile needs to be actually reachable - more difficult than it seems,
        // which is why we DON'T calculate this for every possible tile in the radius,
        // but only for the tile that's about to be chosen.
        val selectedTile = workableTiles.firstOrNull { unit.movement.canReach(it) }

        return if (selectedTile != null
                && getPriority(selectedTile) > 1
                && (!workableTiles.contains(currentTile)
                    || getPriority(selectedTile) > getPriority(currentTile)))
            selectedTile
        else currentTile
    }

    /**
     * Tests if tile can be improved by a specific unit, or if no unit is passed, any unit at all
     * (but does not check whether the ruleset contains any unit capable of it)
     */
    private fun tileCanBeImproved(unit: MapUnit, tile: TileInfo): Boolean {
        if (!tile.isLand || tile.isImpassible() || tile.isCityCenter())
            return false
        val city = tile.getCity()
        if (city == null || city.civInfo != civInfo)
            return false
        if (tile.improvement != null && !UncivGame.Current.settings.automatedWorkersReplaceImprovements) {
            if (unit.civInfo.isPlayerCivilization())
                return false
        }

        if (tile.improvement == null) {
            if (tile.improvementInProgress != null && unit.canBuildImprovement(tile.getTileImprovementInProgress()!!, tile)) return true
            val chosenImprovement = chooseImprovement(unit, tile)
            if (chosenImprovement != null && tile.canBuildImprovement(chosenImprovement, civInfo) && unit.canBuildImprovement(chosenImprovement, tile)) return true

        } else if (!tile.containsGreatImprovement() && tile.hasViewableResource(civInfo)
            && tile.tileResource.isImprovedBy(tile.improvement!!)
            && (chooseImprovement(unit, tile) // if the chosen improvement is not null and buildable
                .let { it != null && tile.canBuildImprovement(it, civInfo) && unit.canBuildImprovement(it, tile)}))
            return true
        return false // couldn't find anything to construct here
    }

    /**
     * Calculate a priority for improving a tile
     */
    private fun getPriority(tileInfo: TileInfo): Int {
        var priority = 0
        if (tileInfo.getOwner() == civInfo) {
            priority += 2
            if (tileInfo.providesYield()) priority += 3
        }
        // give a minor priority to tiles that we could expand onto
        else if (tileInfo.getOwner() == null && tileInfo.neighbors.any { it.getOwner() == civInfo })
            priority += 1

        if (priority != 0 && tileInfo.hasViewableResource(civInfo)) priority += 1
        return priority
    }

    /**
     * Determine the improvement appropriate to a given tile and worker
     */
    private fun chooseImprovement(unit: MapUnit, tile: TileInfo): TileImprovement? {

        val potentialTileImprovements = ruleSet.tileImprovements.filter {
            unit.canBuildImprovement(it.value, tile)
                    && tile.canBuildImprovement(it.value, civInfo)
                    && (it.value.uniqueTo == null || it.value.uniqueTo == unit.civInfo.civName)
        }
        if (potentialTileImprovements.isEmpty()) return null

        val uniqueImprovement = potentialTileImprovements.values.asSequence()
            .filter { it.uniqueTo == civInfo.civName }
            .maxByOrNull { Automation.rankStatsValue(it, unit.civInfo) }

        val bestBuildableImprovement = potentialTileImprovements.values.asSequence()
            .map { Pair(it, Automation.rankStatsValue(it, civInfo)) }
            .filter { it.second > 0f }
            .maxByOrNull { it.second }?.first

        val lastTerrain = tile.getLastTerrain()

        fun isUnbuildableAndRemovable(terrain: Terrain): Boolean = terrain.unbuildable
                && ruleSet.tileImprovements.containsKey(Constants.remove + terrain.name)

        val improvementStringForResource: String? = when {
            tile.resource == null || !tile.hasViewableResource(civInfo) -> null
            tile.terrainFeatures.isNotEmpty()
                    && isUnbuildableAndRemovable(lastTerrain)
                    && !isResourceImprovementAllowedOnFeature(tile) -> Constants.remove + lastTerrain.name
            else -> tile.tileResource.getImprovements().firstOrNull { it in potentialTileImprovements }
        }

        val improvementString = when {
            tile.improvementInProgress != null -> tile.improvementInProgress!!
            improvementStringForResource != null -> improvementStringForResource
            // if this is a resource that HAS an improvement, but this unit can't build it, don't waste your time
            tile.resource != null && tile.tileResource.getImprovements().any() -> return null
            tile.containsGreatImprovement() -> return null
            tile.containsUnfinishedGreatImprovement() -> return null

            // Defence is more important that civilian improvements
            // While AI sucks in strategical placement of forts, allow a human does it manually
            !civInfo.isPlayerCivilization() && evaluateFortPlacement(tile, civInfo,false) -> Constants.fort
            // I think we can assume that the unique improvement is better
            uniqueImprovement != null && tile.canBuildImprovement(uniqueImprovement, civInfo)
                -> uniqueImprovement.name

            lastTerrain.let {
                isUnbuildableAndRemovable(it) &&
                        (Automation.rankStatsValue(it, civInfo) < 0 || it.hasUnique(UniqueType.NullifyYields) )
            } -> Constants.remove + lastTerrain.name

            bestBuildableImprovement != null -> bestBuildableImprovement.name
            else -> return null
        }
        return ruleSet.tileImprovements[improvementString] // For mods, the tile improvement may not exist, so don't assume.
    }

    /**
     * Checks whether the improvement matching the tile resource requires any terrain feature to be removed first.
     *
     * Assumes the caller ensured that terrainFeature and resource are both present!
     */
    private fun isResourceImprovementAllowedOnFeature(tile: TileInfo): Boolean {
        return tile.tileResource.getImprovements().any { resourceImprovementName ->
            val resourceImprovement = ruleSet.tileImprovements[resourceImprovementName] ?: return false
            tile.terrainFeatures.any { resourceImprovement.isAllowedOnFeature(it) }
        }
    }

    /**
     * Checks whether a given tile allows a Fort and whether a Fort may be undesirable (without checking surroundings).
     *
     * -> Checks: city, already built, resource, great improvements.
     * Used only in [evaluateFortPlacement].
     */
    private fun isAcceptableTileForFort(tile: TileInfo): Boolean {
        //todo Should this not also check impassable and the fort improvement's terrainsCanBeBuiltOn/uniques?
        if (tile.isCityCenter() // don't build fort in the city
            || !tile.isLand // don't build fort in the water
            || tile.improvement == Constants.fort // don't build fort if it is already here
            || tile.hasViewableResource(civInfo) // don't build on resource tiles
            || tile.containsGreatImprovement() // don't build on great improvements (including citadel)
            || tile.containsUnfinishedGreatImprovement()) return false

        return true
    }

    /**
     * Do we want a Fort [here][tile] considering surroundings?
     * @param  isCitadel Controls within borders check - true also allows 1 tile outside borders
     * @return Yes please build a Fort here
     */
    private fun evaluateFortPlacement(tile: TileInfo, isCitadel: Boolean): Boolean {
        //todo Is the Citadel code dead anyway? If not - why does the nearestTiles check not respect the param?

        // build on our land only
        if ((tile.owningCity?.civInfo != civInfo &&
                    // except citadel which can be built near-by
                    (!isCitadel || tile.neighbors.all { it.getOwner() != civInfo })) ||
            !isAcceptableTileForFort(tile)) return false

        // if this place is not perfect, let's see if there is a better one
        val nearestTiles = tile.getTilesInDistance(2).filter { it.owningCity?.civInfo == civInfo }.toList()
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
        if (enemyCivs.isEmpty()) return false

        val threatMapping: (CivilizationInfo) -> Int = {
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
        val enemyCivsIsCloseEnough = enemyCivs.filter { NextTurnAutomation.getMinDistanceBetweenCities(civInfo, it) <= threatMapping(it) }
        // no threat, let's not build fort
        if (enemyCivsIsCloseEnough.isEmpty()) return false

        // make list of enemy cities as sources of threat
        val enemyCities = mutableListOf<TileInfo>()
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

}
