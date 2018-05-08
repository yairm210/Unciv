package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2

class UnitMovementAlgorithms(val tileMap: TileMap){
   private fun getMovementCostBetweenAdjacentTiles(from:TileInfo, to:TileInfo, unit:MapUnit): Float {
        if (from.roadStatus === RoadStatus.Railroad && to.roadStatus === RoadStatus.Railroad)
            return 1 / 10f

        if (from.roadStatus !== RoadStatus.None && to.roadStatus !== RoadStatus.None) //Road
        {
            if(unit.civInfo.tech.isResearched("Machinery")) return 1/3f
            else return 1/2f
        }
        if(unit.getBaseUnit().hasUnique("Ignores terrain cost")) return 1f;

        if(unit.getBaseUnit().hasUnique("Rough terrain penalty")
                && (to.baseTerrain=="Hill" || to.terrainFeature=="Forest" || to.terrainFeature=="Jungle"))
            return 4f

        return to.lastTerrain.movementCost.toFloat() // no road
    }

   fun getDistanceToTilesWithinTurn(origin: Vector2, unitMovement:Float, unit: MapUnit): HashMap<TileInfo, Float> {
       val distanceToTiles = HashMap<TileInfo, Float>()
       val unitTile = tileMap[origin]
       distanceToTiles[unitTile] = 0f
       var tilesToCheck = listOf(unitTile)

       while (!tilesToCheck.isEmpty()) {
           val updatedTiles = ArrayList<TileInfo>()
           for (tileToCheck in tilesToCheck)
               for (neighbor in tileToCheck.neighbors) {
                   if(neighbor.getOwner() != null && neighbor.getOwner() != unit.civInfo
                           && neighbor.isCityCenter())
                       continue // Enemy city, can't move through it!

                   var distanceBetweenTiles = getMovementCostBetweenAdjacentTiles(tileToCheck,neighbor,unit)

                   var totalDistanceToTile = distanceToTiles[tileToCheck]!! + distanceBetweenTiles
                   if (!distanceToTiles.containsKey(neighbor) || distanceToTiles[neighbor]!! > totalDistanceToTile) {
                       if (totalDistanceToTile < unitMovement)
                           updatedTiles += neighbor
                       else
                           totalDistanceToTile = unitMovement
                       distanceToTiles[neighbor] = totalDistanceToTile
                   }
               }

           tilesToCheck = updatedTiles
       }

       return distanceToTiles
   }

   fun getShortestPath(origin: Vector2, destination: Vector2, unit:MapUnit): List<TileInfo> {
       if(origin.equals(destination)) return listOf(tileMap[origin]) // edge case that's needed, so that workers will know that they can reach their own tile. *sig

       var tilesToCheck: List<TileInfo> = listOf(tileMap[origin])
       val movementTreeParents = HashMap<TileInfo, TileInfo?>() // contains a map of "you can get from X to Y in that turn"
       movementTreeParents[tileMap[origin]] = null

       var distance = 1
       while (true) {
           val newTilesToCheck = ArrayList<TileInfo>()
           val distanceToDestination = HashMap<TileInfo, Float>()
           val movementThisTurn = if (distance == 1) unit.currentMovement else unit.maxMovement.toFloat()
           for (tileToCheck in tilesToCheck) {
               val distanceToTilesThisTurn = getDistanceToTilesWithinTurn(tileToCheck.position, movementThisTurn, unit)
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

           // no need to check tiles that are surrounded by reachable tiles, only need to check the edgemost tiles.
           // Because anything we can reach from intermediate tiles, can be more easily reached by the edgemost tiles,
           // since we'll have to pass through an edgemost tile in order to reach the diestination anyway
           tilesToCheck = newTilesToCheck.filterNot {tile -> tile.neighbors.all{newTilesToCheck.contains(it) || tilesToCheck.contains(it) } }

           distance++
       }
   }

    /**
     * @param origin
     * @param destination
     * @return The tile that we reached this turn
     */
    fun headTowards(unit:MapUnit,destination: Vector2): TileInfo {
        val currentTile = unit.getTile()
        val tileMap = currentTile.tileMap

        val finalDestinationTile = tileMap[destination]
        val distanceToTiles = unit.getDistanceToTiles()

        val destinationTileThisTurn:TileInfo
        if (distanceToTiles.containsKey(finalDestinationTile)) { // we can get there this turn
            if (finalDestinationTile.unit == null)
                destinationTileThisTurn = finalDestinationTile
            else   // Someone is blocking to the path to the final tile...
            {
                val destinationNeighbors = tileMap[destination].neighbors
                if(destinationNeighbors.contains(currentTile)) // We're right nearby anyway, no need to move
                    return currentTile

                val reachableDestinationNeighbors = destinationNeighbors.filter { distanceToTiles.containsKey(it) && it.unit==null }
                if(reachableDestinationNeighbors.isEmpty()) // We can't get closer...
                    return currentTile

                destinationTileThisTurn = reachableDestinationNeighbors.minBy { distanceToTiles[it]!! }!!
            }
        }

        else { // If the tile is far away, we need to build a path how to get there, and then take the first step
            val path = getShortestPath(currentTile.position, destination, unit)
            destinationTileThisTurn = path.first()
        }

        unit.moveToTile(destinationTileThisTurn)
        return destinationTileThisTurn
    }




}
