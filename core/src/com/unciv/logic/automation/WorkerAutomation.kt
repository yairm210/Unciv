package com.unciv.logic.automation

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.TileImprovement

class WorkerAutomation {

    fun automateWorkerAction(unit: MapUnit) {
        val enemyUnitsInWalkingDistance = unit.getDistanceToTiles().keys
                .filter { it.unit!=null && it.unit!!.civInfo!=unit.civInfo }

        if(enemyUnitsInWalkingDistance.isNotEmpty()) return  // Don't you dare move.

        val tile = unit.getTile()
        val tileToWork = findTileToWork(unit)
        if (tileToWork != tile) {
            unit.movementAlgs().headTowards(tileToWork)
            unit.doPreTurnAction()
            return
        }
        if (tile.improvementInProgress == null) {
            val improvement = chooseImprovement(tile)
            if (tile.canBuildImprovement(improvement, unit.civInfo))
            // What if we're stuck on this tile but can't build there?
                tile.startWorkingOnImprovement(improvement, unit.civInfo)
        }
    }

    private fun findTileToWork(worker:MapUnit): TileInfo {
        val currentTile=worker.getTile()
        val workableTiles = currentTile.getTilesInDistance(4)
                .filter {
                    (it.unit == null || it == currentTile)
                            && it.improvement == null
                            && it.canBuildImprovement(chooseImprovement(it), worker.civInfo)
                            && {val city=it.getCity();  city==null || it.getCity()?.civInfo == worker.civInfo}() // don't work tiles belonging to another civ
                }.sortedByDescending { getPriority(it, worker.civInfo) }.toMutableList()

        // the tile needs to be actually reachable - more difficult than it seems,
        // which is why we DON'T calculate this for every possible tile in the radius,
        // but only for the tile that's about to be chosen.
        val selectedTile = workableTiles.firstOrNull{
            worker.movementAlgs()
                .getShortestPath(workableTiles.first())
                .isNotEmpty()}

        if (selectedTile != null
                && getPriority(selectedTile, worker.civInfo)>1
                && (!workableTiles.contains(currentTile)
                        || getPriority(selectedTile, worker.civInfo) > getPriority(currentTile,worker.civInfo)))
            return selectedTile
        else return currentTile
    }


    private fun getPriority(tileInfo: TileInfo, civInfo: CivilizationInfo): Int {
        var priority = 0
        if (tileInfo.isWorked()) priority += 3
        if (tileInfo.getOwner() == civInfo) priority += 2
        if (tileInfo.hasViewableResource(civInfo)) priority += 1
        else if (tileInfo.neighbors.any { it.getOwner() != null }) priority += 1
        return priority
    }

    private fun chooseImprovement(tile: TileInfo): TileImprovement {
        val improvementString = when {
            tile.improvementInProgress != null -> tile.improvementInProgress
            tile.terrainFeature == "Forest" -> "Lumber mill"
            tile.terrainFeature == "Jungle" -> "Trading post"
            tile.terrainFeature == "Marsh" -> "Remove Marsh"
            tile.resource != null -> tile.tileResource.improvement
            tile.baseTerrain == "Hill" -> "Mine"
            tile.baseTerrain == "Grassland" || tile.baseTerrain == "Desert" || tile.baseTerrain == "Plains" -> "Farm"
            tile.baseTerrain == "Tundra" -> "Trading post"
            else -> null
        }
        return GameBasics.TileImprovements[improvementString]!!
    }

}