package com.unciv.logic.automation

import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.RoadStatus
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
        if (tile.improvementInProgress == null) {
            val improvement = chooseImprovement(tile)
            if (tile.canBuildImprovement(improvement, unit.civInfo)) {
                // What if we're stuck on this tile but can't build there?
                tile.startWorkingOnImprovement(improvement, unit.civInfo)
                return
            }
        }
        if(tile.improvementInProgress!=null) return // we're working!
    }

    fun tryConnectingCities():Boolean{ // returns whether we actually did anything
        val cityThatNeedsConnecting = unit.civInfo.cities.filter { it.population.population>3 && !it.isCapital()
                &&  !it.cityStats.isConnectedToCapital(RoadStatus.Road) }
                .minBy { HexMath().getDistance(it.location, unit.getTile().position) }
        if(cityThatNeedsConnecting==null) return false// do nothing.

        val closestConnectedCity = unit.civInfo.cities.filter { it.isCapital() || it.cityStats.isConnectedToCapital(RoadStatus.Road) }
                .minBy { HexMath().getDistance(cityThatNeedsConnecting.location,it.location) }!!

        val pathToClosestCity = unit.civInfo.gameInfo.tileMap
                .getShortestPathBetweenTwoTiles(cityThatNeedsConnecting.getCenterTile(),
                        closestConnectedCity.getCenterTile())
                .filter { it.roadStatus==RoadStatus.None}

        val unitTile = unit.getTile()
        if(unitTile in pathToClosestCity){
            if(unitTile.improvementInProgress==null)
                unitTile.startWorkingOnImprovement(GameBasics.TileImprovements["Road"]!!,unit.civInfo)
            return true
        }

        val closestTileInPathWithNoRoad = pathToClosestCity
                .filter { unit.canMoveTo(it)}
                .sortedByDescending { HexMath().getDistance(unit.getTile().position, it.position) }
                .firstOrNull { unit.movementAlgs().canReach(it) }

        if(closestTileInPathWithNoRoad==null) return false
        unit.movementAlgs().headTowards(closestTileInPathWithNoRoad)
        if(unit.currentMovement>0 && unit.getTile()==closestTileInPathWithNoRoad)
            closestTileInPathWithNoRoad.startWorkingOnImprovement(GameBasics.TileImprovements["Road"]!!,unit.civInfo)
        return true
    }

    private fun findTileToWork(): TileInfo {
        val currentTile=unit.getTile()
        val workableTiles = currentTile.getTilesInDistance(4)
                .filter {
                    (it.civilianUnit== null || it == currentTile)
                            && it.improvement == null
                            && it.canBuildImprovement(chooseImprovement(it), unit.civInfo)
                            && {val city=it.getCity();  city==null || it.getCity()?.civInfo == unit.civInfo}() // don't work tiles belonging to another civ
                }.sortedByDescending { getPriority(it, unit.civInfo) }.toMutableList()

        // the tile needs to be actually reachable - more difficult than it seems,
        // which is why we DON'T calculate this for every possible tile in the radius,
        // but only for the tile that's about to be chosen.
        val selectedTile = workableTiles.firstOrNull{
            unit.movementAlgs()
                .getShortestPath(workableTiles.first())
                .isNotEmpty()}

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

    private fun chooseImprovement(tile: TileInfo): TileImprovement {
        val improvementString = when {
            tile.improvementInProgress != null -> tile.improvementInProgress
            tile.terrainFeature == "Jungle" -> "Trading post"
            tile.terrainFeature == "Marsh" -> "Remove Marsh"
            tile.terrainFeature == "Forest" &&
                    (tile.resource == null || tile.getTileResource().improvement!="Camp") -> "Lumber mill"
            tile.resource != null -> tile.getTileResource().improvement
            tile.baseTerrain == "Hill" -> "Mine"
            tile.baseTerrain in listOf("Grassland","Desert","Plains") -> "Farm"
            tile.baseTerrain == "Tundra" -> "Trading post"
            else -> null
        }
        return GameBasics.TileImprovements[improvementString]!!
    }


    fun constructRoadTo(destination:TileInfo) {
        val currentTile = unit.getTile()
        if (currentTile.roadStatus == RoadStatus.None) {
            currentTile.startWorkingOnImprovement(GameBasics.TileImprovements["Road"]!!, unit.civInfo)
            return
        }
        val pathToDestination = unit.movementAlgs().getShortestPath(destination)
        val destinationThisTurn = pathToDestination.first()
        val fullPathToCurrentDestination = unit.movementAlgs().getFullPathToCloseTile(destinationThisTurn)
        val firstTileWithoutRoad = fullPathToCurrentDestination.firstOrNull { it.roadStatus == RoadStatus.None && unit.canMoveTo(it) }
        if (firstTileWithoutRoad == null) {
            unit.moveToTile(destinationThisTurn)
            return
        }
        unit.moveToTile(firstTileWithoutRoad)
        if (unit.currentMovement > 0)
            firstTileWithoutRoad.startWorkingOnImprovement(GameBasics.TileImprovements["Road"]!!, unit.civInfo)
    }


}