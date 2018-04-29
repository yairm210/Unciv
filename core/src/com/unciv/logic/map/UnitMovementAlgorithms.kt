package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.CivilizationInfo

class UnitMovementAlgorithms(val tileMap: TileMap){

   fun getDistanceToTilesWithinTurn(origin: Vector2, unitMovement:Float, civInfo:CivilizationInfo): HashMap<TileInfo, Float> {
       val distanceToTiles = HashMap<TileInfo, Float>()
       val unitTile = tileMap[origin]
       distanceToTiles[unitTile] = 0f
       var tilesToCheck = listOf(unitTile)
       val isMachineryResearched = civInfo.tech.isResearched("Machinery")

       while (!tilesToCheck.isEmpty()) {
           val updatedTiles = ArrayList<TileInfo>()
           for (tileToCheck in tilesToCheck)
               for (maybeUpdatedTile in tileToCheck.neighbors) {
                   if(maybeUpdatedTile.getOwner() != null && maybeUpdatedTile.getOwner() != civInfo && maybeUpdatedTile.isCityCenter())
                       continue // Enemy city, can't move through it!

                   var distanceBetweenTiles = maybeUpdatedTile.lastTerrain.movementCost.toFloat() // no road
                   if (tileToCheck.roadStatus !== RoadStatus.None && maybeUpdatedTile.roadStatus !== RoadStatus.None) //Road
                       distanceBetweenTiles = if (isMachineryResearched) 1 / 3f else 1 / 2f

                   if (tileToCheck.roadStatus === RoadStatus.Railroad && maybeUpdatedTile.roadStatus === RoadStatus.Railroad) // Railroad
                       distanceBetweenTiles = 1 / 10f

                   var totalDistanceToTile = distanceToTiles[tileToCheck]!! + distanceBetweenTiles
                   if (!distanceToTiles.containsKey(maybeUpdatedTile) || distanceToTiles[maybeUpdatedTile]!! > totalDistanceToTile) {
                       if (totalDistanceToTile < unitMovement)
                           updatedTiles += maybeUpdatedTile
                       else
                           totalDistanceToTile = unitMovement
                       distanceToTiles[maybeUpdatedTile] = totalDistanceToTile
                   }
               }

           tilesToCheck = updatedTiles
       }

       return distanceToTiles
   }


   fun getShortestPath(origin: Vector2, destination: Vector2, currentMovement: Float, maxMovement: Int, civInfo: CivilizationInfo): List<TileInfo> {
       if(origin.equals(destination)) return listOf(tileMap[origin]) // edge case that's needed, so that workers will know that they can reach their own tile. *sig

       var tilesToCheck: List<TileInfo> = listOf(tileMap[origin])
       val movementTreeParents = HashMap<TileInfo, TileInfo?>() // contains a map of "you can get from X to Y in that turn"
       movementTreeParents[tileMap[origin]] = null

       var distance = 1
       while (true) {
           val newTilesToCheck = ArrayList<TileInfo>()
           val distanceToDestination = HashMap<TileInfo, Float>()
           val movementThisTurn = if (distance == 1) currentMovement else maxMovement.toFloat()
           for (tileToCheck in tilesToCheck) {
               val distanceToTilesThisTurn = getDistanceToTilesWithinTurn(tileToCheck.position, movementThisTurn, civInfo)
               for (reachableTile in distanceToTilesThisTurn.keys) {
                   if(reachableTile.position == destination)
                       distanceToDestination[tileToCheck] = distanceToTilesThisTurn[reachableTile]!!
                   else {
                       if (movementTreeParents.containsKey(reachableTile)) continue // We cannot be faster than anything existing...
                       if (reachableTile.position != destination && reachableTile.unit != null) continue // This is an intermediary tile that contains a unit - we can't go there!
                       movementTreeParents[reachableTile] = tileToCheck
                       newTilesToCheck.add(reachableTile)
                   }
               }
           }

           if (distanceToDestination.isNotEmpty()) {
               val path = mutableListOf(tileMap[destination]) // Traverse the tree upwards to get the list of tiles leading to the destination,
               var currentTile = distanceToDestination.minBy { it.value }!!.key
               while (currentTile.position != origin) {
                   path.add(currentTile)
                   currentTile = movementTreeParents[currentTile]!!
               }
               return path.reversed() // and reverse in order to get the list in chronological order
           }

           if(newTilesToCheck.isEmpty()) return emptyList() // there is NO PATH (eg blocked by enemy units)

           tilesToCheck = newTilesToCheck.filterNot {tile -> tile.neighbors.all{newTilesToCheck.contains(it) || tilesToCheck.contains(it) } }
           distance++
       }
   }

}
