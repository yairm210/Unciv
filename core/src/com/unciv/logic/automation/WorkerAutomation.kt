package com.unciv.logic.automation

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.BFS
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.TileImprovement

class WorkerAutomation(val unit: MapUnit) {

    fun automateWorkerAction() {
        val enemyUnitsInWalkingDistance = unit.getDistanceToTiles().keys
                .filter { it.militaryUnit!=null && it.militaryUnit!!.civInfo!=unit.civInfo
                        && unit.civInfo.isAtWarWith(it.militaryUnit!!.civInfo) }

        if(enemyUnitsInWalkingDistance.isNotEmpty()) return  // Don't you dare move.

        val tile = unit.getTile()
        val tileToWork = findTileToWork()

        if(getPriority(tileToWork,unit.civInfo) < 3){ // building roads is more important
            if(tryConnectingCities()) return
        }

        if (tileToWork != tile) {
            val reachedTile = unit.movementAlgs().headTowards(tileToWork)
            if(reachedTile!=tile) unit.doPreTurnAction() // otherwise, we get a situation where the worker is automated, so it tries to move but doesn't, then tries to automate, then move, etc, forever. Stack overflow exception!
            return
        }
        if (tile.improvementInProgress == null && tile.isLand) {
            val improvement = chooseImprovement(tile, unit.civInfo)
            if (improvement != null && tile.canBuildImprovement(improvement, unit.civInfo)) {
                // What if we're stuck on this tile but can't build there?
                tile.startWorkingOnImprovement(improvement, unit.civInfo)
                return
            }
        }
        if(tile.improvementInProgress!=null) return // we're working!
        if(tryConnectingCities()) return //nothing to do, try again to connect cities

        unit.civInfo.addNotification("[${unit.name}] has no work to do.", unit.currentTile.position, Color.GRAY)

    }



    fun tryConnectingCities():Boolean { // returns whether we actually did anything

        val targetRoad = unit.civInfo.tech.getBestRoadAvailable()

        val citiesThatNeedConnecting = unit.civInfo.cities
                .filter { it.population.population>3 && !it.isCapital()
                    && !it.cityStats.isConnectedToCapital(targetRoad) }
        if(citiesThatNeedConnecting.isEmpty()) return false // do nothing.

        val citiesThatNeedConnectingBfs = citiesThatNeedConnecting
                .map { city -> BFS(city.getCenterTile()){it.isLand && unit.canPassThrough(it)} }
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
                            val reachableTiles = roadableTiles.filter {  unit.canMoveTo(it)&& unit.movementAlgs().canReach(it)}
                            if(reachableTiles.isEmpty()) continue
                            tileToConstructRoadOn = reachableTiles.minBy { unit.movementAlgs().getShortestPath(it).size }!!
                            unit.movementAlgs().headTowards(tileToConstructRoadOn)
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
                            && (it.improvement == null || (it.hasViewableResource(unit.civInfo) && !it.containsGreatImprovement() && it.getTileResource().improvement != it.improvement))
                            && it.isLand
                            && !it.getBaseTerrain().impassable
                            && (it.containsUnfinishedGreatImprovement() || it.canBuildImprovement(chooseImprovement(it, unit.civInfo), unit.civInfo))
                            && {val city=it.getCity();  city==null || it.getCity()?.civInfo == unit.civInfo}() // don't work tiles belonging to another civ
                }.sortedByDescending { getPriority(it, unit.civInfo) }.toMutableList()

        // the tile needs to be actually reachable - more difficult than it seems,
        // which is why we DON'T calculate this for every possible tile in the radius,
        // but only for the tile that's about to be chosen.
        val selectedTile = workableTiles.firstOrNull{
            unit.movementAlgs().canReach(it) }

        if (selectedTile != null
                && getPriority(selectedTile, unit.civInfo)>1
                && (!workableTiles.contains(currentTile)
                        || getPriority(selectedTile, unit.civInfo) > getPriority(currentTile, unit.civInfo)))
            return selectedTile
        else return currentTile
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
            tile.terrainFeature == Constants.forest -> "Lumber mill"
            tile.baseTerrain == Constants.hill -> "Mine"
            tile.baseTerrain in listOf("Grassland","Desert","Plains") -> "Farm"
            tile.baseTerrain == "Tundra" -> "Trading post"
            else -> throw Exception("No improvement found for "+tile.baseTerrain)
        }
        if (improvementString == null) return null
        return GameBasics.TileImprovements[improvementString]!!
    }

}