package com.unciv.logic.automation

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.BFS
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.tile.TileImprovement

class WorkerAutomation(val unit: MapUnit) {


    fun automateWorkerAction() {
        val enemyUnitsInWalkingDistance = unit.movement.getDistanceToTiles().keys
                .filter { UnitAutomation.containsEnemyMilitaryUnit(unit, it) }

        if (enemyUnitsInWalkingDistance.isNotEmpty() && !unit.baseUnit.isMilitary()) return UnitAutomation.runAway(unit)

        val currentTile = unit.getTile()
        val tileToWork = findTileToWork()
        
        if (getPriority(tileToWork, unit.civInfo) < 3) { // building roads is more important
            if (tryConnectingCities(unit)) return
        }

        if (tileToWork != currentTile) {
            val reachedTile = unit.movement.headTowards(tileToWork)
            if (reachedTile != currentTile) unit.doAction() // otherwise, we get a situation where the worker is automated, so it tries to move but doesn't, then tries to automate, then move, etc, forever. Stack overflow exception!
            return
        }

        if (currentTile.improvementInProgress == null && currentTile.isLand
                && tileCanBeImproved(currentTile, unit.civInfo)) {
            return currentTile.startWorkingOnImprovement(chooseImprovement(currentTile, unit.civInfo)!!, unit.civInfo)
        }

        if (currentTile.improvementInProgress != null) return // we're working!
        if (tryConnectingCities(unit)) return //nothing to do, try again to connect cities

        val citiesToNumberOfUnimprovedTiles = HashMap<String, Int>()
        for (city in unit.civInfo.cities) {
            citiesToNumberOfUnimprovedTiles[city.id] = city.getTiles()
                    .count { it.isLand && it.civilianUnit == null && tileCanBeImproved(it, unit.civInfo) }
        }

        val mostUndevelopedCity = unit.civInfo.cities.asSequence()
                .filter { citiesToNumberOfUnimprovedTiles[it.id]!! > 0 }
                .sortedByDescending { citiesToNumberOfUnimprovedTiles[it.id] }
                .firstOrNull { unit.movement.canReach(it.getCenterTile()) } //goto most undeveloped city

        if (mostUndevelopedCity != null && mostUndevelopedCity != unit.currentTile.owningCity) {
            val reachedTile = unit.movement.headTowards(mostUndevelopedCity.getCenterTile())
            if (reachedTile != currentTile) unit.doAction() // since we've moved, maybe we can do something here - automate
            return
        }

        unit.civInfo.addNotification("[${unit.displayName()}] has no work to do.", unit.currentTile.position, unit.name, "OtherIcons/Sleep")
    }


    private fun tryConnectingCities(unit: MapUnit): Boolean { // returns whether we actually did anything
        //Player can choose not to auto-build roads & railroads.
        if (unit.civInfo.isPlayerCivilization() && !UncivGame.Current.settings.autoBuildingRoads)
            return false
        val targetRoad = unit.civInfo.tech.getBestRoadAvailable()

        val citiesThatNeedConnecting = unit.civInfo.cities.asSequence()
                .filter {
                    it.population.population > 3 && !it.isCapital() && !it.isBeingRazed //City being razed should not be connected.
                            && !it.cityStats.isConnectedToCapital(targetRoad)
                            // Cities that are too far away make the caReach() calculations devastatingly long
                            && it.getCenterTile().aerialDistanceTo(unit.getTile()) < 20
                }
        if (citiesThatNeedConnecting.none()) return false // do nothing.

        val citiesThatNeedConnectingBfs = citiesThatNeedConnecting
                .sortedBy { it.getCenterTile().aerialDistanceTo(unit.civInfo.getCapital().getCenterTile()) }
                .map { city -> BFS(city.getCenterTile()) { it.isLand && unit.movement.canPassThrough(it) } }

        val connectedCities = unit.civInfo.cities
                .filter { it.isCapital() || it.cityStats.isConnectedToCapital(targetRoad) }.map { it.getCenterTile() }

        // Since further away cities take longer to get to and - most importantly - the canReach() to them is very long,
        // we order cities by their closeness to the worker first, and then check for each one whether there's a viable path
        // it can take to an existing connected city.
        for (bfs in citiesThatNeedConnectingBfs) {
            while (bfs.tilesToCheck.isNotEmpty()) {
                bfs.nextStep()
                for (city in connectedCities)
                    if (bfs.tilesToCheck.contains(city)) { // we have a winner!
                        val pathToCity = bfs.getPathTo(city).asSequence()
                        val roadableTiles = pathToCity.filter { it.roadStatus < targetRoad }
                        val tileToConstructRoadOn: TileInfo
                        if (unit.currentTile in roadableTiles) tileToConstructRoadOn = unit.currentTile
                        else {
                            val reachableTile = roadableTiles
                                    .sortedBy { it.aerialDistanceTo(unit.getTile()) }
                                    .firstOrNull { unit.movement.canMoveTo(it) && unit.movement.canReach(it) }
                            if (reachableTile == null) continue
                            tileToConstructRoadOn = reachableTile
                            unit.movement.headTowards(tileToConstructRoadOn)
                        }
                        if (unit.currentMovement > 0 && unit.currentTile == tileToConstructRoadOn
                                && unit.currentTile.improvementInProgress != targetRoad.name) {
                            val improvement = targetRoad.improvement(unit.civInfo.gameInfo.ruleSet)!!
                            tileToConstructRoadOn.startWorkingOnImprovement(improvement, unit.civInfo)
                        }
                        return true
                    }
            }
        }

        return false
    }

    /**
     * Returns the current tile if no tile to work was found
     */
    private fun findTileToWork(): TileInfo {
        val currentTile = unit.getTile()
        val workableTiles = currentTile.getTilesInDistance(4)
                .filter {
                    (it.civilianUnit == null || it == currentTile)
                            && tileCanBeImproved(it, unit.civInfo)
                            && it.getTilesInDistance(2)
                            .none { it.isCityCenter() && it.getCity()!!.civInfo.isAtWarWith(unit.civInfo) }
                }
                .sortedByDescending { getPriority(it, unit.civInfo) }

        // the tile needs to be actually reachable - more difficult than it seems,
        // which is why we DON'T calculate this for every possible tile in the radius,
        // but only for the tile that's about to be chosen.
        val selectedTile = workableTiles.firstOrNull { unit.movement.canReach(it) }

        return if (selectedTile != null
                && getPriority(selectedTile, unit.civInfo) > 1
                && (!workableTiles.contains(currentTile)
                    || getPriority(selectedTile, unit.civInfo) > getPriority(currentTile, unit.civInfo)))
            selectedTile
        else currentTile
    }

    private fun tileCanBeImproved(tile: TileInfo, civInfo: CivilizationInfo): Boolean {
        if (!tile.isLand || tile.isImpassible() || tile.isCityCenter())
            return false
        val city = tile.getCity()
        if (city == null || city.civInfo != civInfo)
            return false
        if (tile.improvement != null && !UncivGame.Current.settings.automatedWorkersReplaceImprovements)
            return false

        if (tile.improvement == null) {
            if (tile.improvementInProgress != null && unit.canBuildImprovement(tile.getTileImprovementInProgress()!!, tile)) return true
            val chosenImprovement = chooseImprovement(tile, civInfo)
            if (chosenImprovement != null && tile.canBuildImprovement(chosenImprovement, civInfo) && unit.canBuildImprovement(chosenImprovement, tile)) return true
        } else if (!tile.containsGreatImprovement() && tile.hasViewableResource(civInfo)
                && tile.getTileResource().improvement != tile.improvement
                && chooseImprovement(tile, civInfo) // if the chosen improvement is not null and buildable
                    .let { it != null && tile.canBuildImprovement(it, civInfo) && unit.canBuildImprovement(it, tile)})
            return true
        return false // couldn't find anything to construct here
    }

    fun getPriority(tileInfo: TileInfo, civInfo: CivilizationInfo): Int {
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

    private fun chooseImprovement(tile: TileInfo, civInfo: CivilizationInfo): TileImprovement? {
        val improvementStringForResource: String? = when {
            tile.resource == null || !tile.hasViewableResource(civInfo) -> null
            tile.terrainFeatures.contains(Constants.marsh) && !isImprovementOnFeatureAllowed(tile, civInfo) -> "Remove Marsh"
            tile.terrainFeatures.contains("Fallout") && !isImprovementOnFeatureAllowed(tile, civInfo) -> "Remove Fallout"    // for really mad modders
            tile.terrainFeatures.contains(Constants.jungle) && !isImprovementOnFeatureAllowed(tile, civInfo) -> "Remove Jungle"
            tile.terrainFeatures.contains(Constants.forest) && !isImprovementOnFeatureAllowed(tile, civInfo) -> "Remove Forest"
            else -> tile.getTileResource().improvement
        }

        // turnsToBuild is what defines them as buildable
        val tileImprovements = civInfo.gameInfo.ruleSet.tileImprovements.filter { it.value.turnsToBuild != 0 }
        val uniqueImprovement = tileImprovements.values
                .firstOrNull { it.uniqueTo == civInfo.civName }

        val improvementString = when {
            tile.improvementInProgress != null -> tile.improvementInProgress
            improvementStringForResource != null && tileImprovements.containsKey(improvementStringForResource)
                    && tileImprovements[improvementStringForResource]!!.turnsToBuild != 0 -> improvementStringForResource
            tile.containsGreatImprovement() -> null
            tile.containsUnfinishedGreatImprovement() -> null

            // Defence is more important that civilian improvements
            // While AI sucks in strategical placement of forts, allow a human does it manually
            !civInfo.isPlayerCivilization() && evaluateFortPlacement(tile, civInfo, false) -> Constants.fort
            // I think we can assume that the unique improvement is better
            uniqueImprovement != null && tile.canBuildImprovement(uniqueImprovement, civInfo) 
                && unit.canBuildImprovement(uniqueImprovement, tile) -> 
                    uniqueImprovement.name

            tile.terrainFeatures.contains("Fallout") -> "Remove Fallout"
            tile.terrainFeatures.contains(Constants.marsh) -> "Remove Marsh"
            tile.terrainFeatures.contains(Constants.jungle) -> Constants.tradingPost
            tile.terrainFeatures.contains("Oasis") -> null
            tile.terrainFeatures.contains(Constants.forest) -> "Lumber mill"
            tile.isHill() -> "Mine"
            tile.baseTerrain in listOf(Constants.grassland, Constants.desert, Constants.plains) -> "Farm"
            tile.isAdjacentToFreshwater -> "Farm"
            tile.baseTerrain in listOf(Constants.tundra, Constants.snow) -> Constants.tradingPost
            else -> null
        }
        if (improvementString == null) return null
        return unit.civInfo.gameInfo.ruleSet.tileImprovements[improvementString] // For mods, the tile improvement may not exist, so don't assume.
    }

    private fun isImprovementOnFeatureAllowed(tile: TileInfo, civInfo: CivilizationInfo): Boolean {
        // routine assumes the caller ensured that terrainFeature and resource are both present
        val resourceImprovementName = tile.getTileResource().improvement
                ?: return false
        val resourceImprovement = civInfo.gameInfo.ruleSet.tileImprovements[resourceImprovementName]
                ?: return false
        return tile.terrainFeatures.any { resourceImprovement.isAllowedOnFeature(it) }
    }

    private fun isAcceptableTileForFort(tile: TileInfo, civInfo: CivilizationInfo): Boolean {
        if (tile.isCityCenter() // don't build fort in the city
                || !tile.isLand // don't build fort in the water
                || tile.improvement == Constants.fort // don't build fort if it is already here
                || tile.hasViewableResource(civInfo) // don't build on resource tiles
                || tile.containsGreatImprovement() // don't build on great improvements (including citadel)
                || tile.containsUnfinishedGreatImprovement()) return false

        return true
    }

    fun evaluateFortPlacement(tile: TileInfo, civInfo: CivilizationInfo, isCitadel: Boolean): Boolean {
        // build on our land only
        if ((tile.owningCity?.civInfo != civInfo &&
                        // except citadel which can be built near-by
                        (!isCitadel || tile.neighbors.all { it.getOwner() != civInfo })) ||
                !isAcceptableTileForFort(tile, civInfo)) return false

        // if this place is not perfect, let's see if there is a better one
        val nearestTiles = tile.getTilesInDistance(2).filter { it.owningCity?.civInfo == civInfo }.toList()
        for (closeTile in nearestTiles) {
            // don't build forts too close to the cities
            if (closeTile.isCityCenter()) return false
            // don't build forts too close to other forts
            if (closeTile.improvement != null
                    && closeTile.getTileImprovement()!!.uniqueObjects.any { it.placeholderText == "Gives a defensive bonus of []%" }
                    || closeTile.improvementInProgress != Constants.fort) return false
            // there is another better tile for the fort
            if (!tile.isHill() && closeTile.isHill() &&
                    isAcceptableTileForFort(closeTile, civInfo)) return false
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