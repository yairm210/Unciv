package com.unciv.logic.automation

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.UnCivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.BFS
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.TileImprovement

class WorkerAutomation(val unit: MapUnit) {

    fun automateWorkerAction() {
        val enemyUnitsInWalkingDistance = unit.movement.getDistanceToTiles().keys
                .filter {
                    it.militaryUnit != null && it.militaryUnit!!.civInfo != unit.civInfo
                            && unit.civInfo.isAtWarWith(it.militaryUnit!!.civInfo)
                }

        if (enemyUnitsInWalkingDistance.isNotEmpty()) return  // Don't you dare move.

        val currentTile = unit.getTile()
        val tileToWork = findTileToWork()

        if (getPriority(tileToWork, unit.civInfo) < 3) { // building roads is more important
            if (tryConnectingCities(unit)) return
        }

        if (tileToWork != currentTile) {
            val reachedTile = unit.movement.headTowards(tileToWork)
            if (reachedTile != currentTile) unit.doPreTurnAction() // otherwise, we get a situation where the worker is automated, so it tries to move but doesn't, then tries to automate, then move, etc, forever. Stack overflow exception!
            return
        }
        if (currentTile.improvementInProgress == null && currentTile.isLand) {
            val improvement = chooseImprovement(currentTile, unit.civInfo)
            if (improvement != null && currentTile.canBuildImprovement(improvement, unit.civInfo)) {
                // What if we're stuck on this tile but can't build there?
                currentTile.startWorkingOnImprovement(improvement, unit.civInfo)
                return
            }
        }
        if (currentTile.improvementInProgress != null) return // we're working!
        if (tryConnectingCities(unit)) return //nothing to do, try again to connect cities

        val citiesToNumberOfUnimprovedTiles = HashMap<String, Int>()
        for (city in unit.civInfo.cities) {
            citiesToNumberOfUnimprovedTiles[city.name] =
                    city.getTiles().count { it.isLand && it.civilianUnit == null
                            && tileCanBeImproved(it, unit.civInfo) }
        }

        val mostUndevelopedCity = unit.civInfo.cities
                .filter { citiesToNumberOfUnimprovedTiles[it.name]!! > 0 }
                .sortedByDescending { citiesToNumberOfUnimprovedTiles[it.name] }
                .firstOrNull { unit.movement.canReach(it.ccenterTile) } //goto most undeveloped city
        if (mostUndevelopedCity != null && mostUndevelopedCity != unit.currentTile.owningCity) {
            val reachedTile = unit.movement.headTowards(mostUndevelopedCity.ccenterTile)
            if (reachedTile != currentTile) unit.doPreTurnAction() // since we've moved, maybe we can do something here - automate
            return
        }

        unit.civInfo.addNotification("[${unit.name}] has no work to do.", unit.currentTile.position, Color.GRAY)
    }



    private fun tryConnectingCities(unit: MapUnit):Boolean { // returns whether we actually did anything
        //Player can choose not to auto-build roads & railroads.
        if (unit.civInfo.isPlayerCivilization() && !UnCivGame.Current.settings.autoBuildingRoads)
            return false

        val targetRoad = unit.civInfo.tech.getBestRoadAvailable()

        val citiesThatNeedConnecting = unit.civInfo.cities
                .filter { it.population.population>3 && !it.isCapital() && !it.isBeingRazed //City being razed should not be connected.
                    && !it.cityStats.isConnectedToCapital(targetRoad) }
        if(citiesThatNeedConnecting.isEmpty()) return false // do nothing.

        val citiesThatNeedConnectingBfs = citiesThatNeedConnecting
                .map { city -> BFS(city.getCenterTile()){it.isLand && unit.movement.canPassThrough(it)} }
                .toMutableList()

        val connectedCities = unit.civInfo.cities.filter { it.isCapital() || it.cityStats.isConnectedToCapital(targetRoad) }
                .map { it.getCenterTile() }

        while(citiesThatNeedConnectingBfs.any()){
            for(bfs in citiesThatNeedConnectingBfs.toList()){
                bfs.nextStep()
                if(bfs.tilesToCheck.isEmpty()){ // can't get to any connected city from here
                    citiesThatNeedConnectingBfs.remove(bfs)
                    continue
                }
                for(city in connectedCities)
                    if(bfs.tilesToCheck.contains(city)) { // we have a winner!
                        val pathToCity = bfs.getPathTo(city)
                        val roadableTiles = pathToCity.filter { it.roadStatus < targetRoad }
                        val tileToConstructRoadOn :TileInfo
                        if(unit.currentTile in roadableTiles) tileToConstructRoadOn = unit.currentTile
                        else{
                            val reachableTiles = roadableTiles
                                    .filter {  unit.movement.canMoveTo(it)&& unit.movement.canReach(it)}
                            if(reachableTiles.isEmpty()) continue
                            tileToConstructRoadOn = reachableTiles.minBy { unit.movement.getShortestPath(it).size }!!
                            unit.movement.headTowards(tileToConstructRoadOn)
                        }
                        if(unit.currentMovement>0 && unit.currentTile==tileToConstructRoadOn
                                && unit.currentTile.improvementInProgress!=targetRoad.name)
                            tileToConstructRoadOn.startWorkingOnImprovement(targetRoad.improvement()!!,unit.civInfo)
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
        val currentTile=unit.getTile()
        val workableTiles = currentTile.getTilesInDistance(4)
                .filter {
                    (it.civilianUnit== null || it == currentTile)
                            && tileCanBeImproved(it, unit.civInfo) }
                .sortedByDescending { getPriority(it, unit.civInfo) }.toMutableList()

        // the tile needs to be actually reachable - more difficult than it seems,
        // which is why we DON'T calculate this for every possible tile in the radius,
        // but only for the tile that's about to be chosen.
        val selectedTile = workableTiles.firstOrNull{unit.movement.canReach(it) }

        if (selectedTile != null
                && getPriority(selectedTile, unit.civInfo)>1
                && (!workableTiles.contains(currentTile)
                        || getPriority(selectedTile, unit.civInfo) > getPriority(currentTile, unit.civInfo)))
            return selectedTile
        else return currentTile
    }

    private fun tileCanBeImproved(tile: TileInfo, civInfo: CivilizationInfo): Boolean {
        if (!tile.isLand || tile.getBaseTerrain().impassable || tile.isCityCenter())
            return false
        val city=tile.getCity()
        if (city == null || city.civInfo != civInfo)
            return false

        if(tile.improvement==null){
            if(tile.improvementInProgress!=null) return true
            val chosenImprovement = chooseImprovement(tile, civInfo)
            if(chosenImprovement!=null && tile.canBuildImprovement(chosenImprovement, civInfo)) return true
        }
        else{
            if(!tile.containsGreatImprovement() && tile.hasViewableResource(civInfo)
                    && tile.getTileResource().improvement != tile.improvement
                    && tile.canBuildImprovement(chooseImprovement(tile, civInfo)!!, civInfo))
                return true
        }

        return false // cou;dn't find anything to construct here
    }

    private fun getPriority(tileInfo: TileInfo, civInfo: CivilizationInfo): Int {
        var priority = 0
        if (tileInfo.getOwner() == civInfo){
            priority += 2
            if (tileInfo.isWorked()) priority += 3
        }
        else if (tileInfo.neighbors.any { it.getOwner() != null }) priority += 1
        if (tileInfo.hasViewableResource(civInfo)) priority += 1
        return priority
    }

    private fun chooseImprovement(tile: TileInfo, civInfo: CivilizationInfo): TileImprovement? {
        val improvementStringForResource : String ?= when {
            tile.resource == null || !tile.hasViewableResource(civInfo) -> null
            tile.terrainFeature == "Marsh" -> "Remove Marsh"
            tile.terrainFeature == "Fallout" -> "Remove Fallout"
            tile.terrainFeature == Constants.jungle -> "Remove Jungle"
            tile.terrainFeature == Constants.forest && tile.getTileResource().improvement!="Camp" -> "Remove Forest"
            else -> tile.getTileResource().improvement
        }

        val improvementString = when {
            tile.improvementInProgress != null -> tile.improvementInProgress
            improvementStringForResource != null -> improvementStringForResource
            tile.containsGreatImprovement() -> null
            tile.containsUnfinishedGreatImprovement() -> null
            tile.terrainFeature == Constants.jungle -> "Trading post"
            tile.terrainFeature == "Marsh" -> "Remove Marsh"
            tile.terrainFeature == "Oasis" -> null
            tile.terrainFeature == Constants.forest -> "Lumber mill"
            tile.baseTerrain == Constants.hill -> "Mine"
            tile.baseTerrain in listOf(Constants.grassland,Constants.desert,Constants.plains) -> "Farm"
            tile.baseTerrain == Constants.tundra -> "Trading post"
            else -> throw Exception("No improvement found for "+tile.baseTerrain)
        }
        if (improvementString == null) return null
        return GameBasics.TileImprovements[improvementString]!!
    }

}