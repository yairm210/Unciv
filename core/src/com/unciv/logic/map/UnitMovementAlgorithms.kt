package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2

class UnitMovementAlgorithms(val unit:MapUnit) {
    val tileMap = unit.getTile().tileMap

    private fun getMovementCostBetweenAdjacentTiles(from: TileInfo, to: TileInfo): Float {
        if (from.roadStatus === RoadStatus.Railroad && to.roadStatus === RoadStatus.Railroad)
            return 1 / 10f

        if (from.roadStatus !== RoadStatus.None && to.roadStatus !== RoadStatus.None) //Road
        {
            if (unit.civInfo.tech.isResearched("Machinery")) return 1 / 3f
            else return 1 / 2f
        }
        if (unit.hasUnique("Ignores terrain cost")) return 1f

        if (unit.hasUnique("Rough terrain penalty")
                && (to.baseTerrain == "Hill" || to.terrainFeature == "Forest" || to.terrainFeature == "Jungle"))
            return 4f

        return to.lastTerrain.movementCost.toFloat() // no road
    }


    fun getDistanceToTilesWithinTurn(origin: Vector2, unitMovement: Float): HashMap<TileInfo, Float> {
        if(unitMovement==0f) return hashMapOf()
        val distanceToTiles = LinkedHashMap<TileInfo, Float>()
        val unitTile = tileMap[origin]
        distanceToTiles[unitTile] = 0f
        var tilesToCheck = listOf(unitTile)

        while (!tilesToCheck.isEmpty()) {
            val updatedTiles = ArrayList<TileInfo>()
            for (tileToCheck in tilesToCheck)
                for (neighbor in tileToCheck.neighbors) {
                    var totalDistanceToTile:Float
                    if (!unit.canPassThrough(neighbor))
                        totalDistanceToTile = unitMovement // Can't go here.
                    // The reason that we don't just "return" is so that when calculating how to reach an enemy,
                    // You need to assume his tile is reachable, otherwise all movement algs on reaching enemy
                    // cities and units goes kaput.

                    else {
                        val distanceBetweenTiles = getMovementCostBetweenAdjacentTiles(tileToCheck, neighbor)
                        totalDistanceToTile = distanceToTiles[tileToCheck]!! + distanceBetweenTiles
                    }

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

    fun getShortestPath(destination: TileInfo): List<TileInfo> {
        val currentTile = unit.getTile()
        if (currentTile.position.equals(destination)) return listOf(currentTile) // edge case that's needed, so that workers will know that they can reach their own tile. *sig

        var tilesToCheck: List<TileInfo> = listOf(currentTile)
        val movementTreeParents = HashMap<TileInfo, TileInfo?>() // contains a map of "you can get from X to Y in that turn"
        movementTreeParents[currentTile] = null

        var distance = 1
        while (true) {
            val newTilesToCheck = ArrayList<TileInfo>()
            val distanceToDestination = HashMap<TileInfo, Float>()
            val movementThisTurn = if (distance == 1) unit.currentMovement else unit.getMaxMovement().toFloat()
            for (tileToCheck in tilesToCheck) {
                val distanceToTilesThisTurn = getDistanceToTilesWithinTurn(tileToCheck.position, movementThisTurn)
                for (reachableTile in distanceToTilesThisTurn.keys) {
                    if (reachableTile == destination)
                        distanceToDestination[tileToCheck] = distanceToTilesThisTurn[reachableTile]!!
                    else {
                        if (movementTreeParents.containsKey(reachableTile)) continue // We cannot be faster than anything existing...
                        if (!unit.canMoveTo(reachableTile)) continue // This is a tile that we can''t actually enter - either an intermediary tile containing our unit, or an enemy unit/city
                        movementTreeParents[reachableTile] = tileToCheck
                        newTilesToCheck.add(reachableTile)
                    }
                }
            }

            if (distanceToDestination.isNotEmpty()) {
                val path = mutableListOf(destination) // Traverse the tree upwards to get the list of tiles leading to the destination,
                // Get the tile from which the distance to the final tile in least -
                // this is so that when we finally get there, we'll have as many movement points as possible
                var intermediateTile = distanceToDestination.minBy { it.value }!!.key
                while (intermediateTile != currentTile) {
                    path.add(intermediateTile)
                    intermediateTile = movementTreeParents[intermediateTile]!!
                }
                return path.reversed() // and reverse in order to get the list in chronological order
            }

            if (newTilesToCheck.isEmpty()) return emptyList() // there is NO PATH (eg blocked by enemy units)

            // no need to check tiles that are surrounded by reachable tiles, only need to check the edgemost tiles.
            // Because anything we can reach from intermediate tiles, can be more easily reached by the edgemost tiles,
            // since we'll have to pass through an edgemost tile in order to reach the diestination anyway
            tilesToCheck = newTilesToCheck.filterNot { tile -> tile.neighbors.all { newTilesToCheck.contains(it) || tilesToCheck.contains(it) } }

            distance++
        }
    }

    /**
     * @return The tile that we reached this turn
     */
    fun headTowards(destination: TileInfo): TileInfo {
        val currentTile = unit.getTile()
        if(currentTile==destination) return currentTile

        val distanceToTiles = unit.getDistanceToTiles()

        val destinationTileThisTurn: TileInfo
        if (distanceToTiles.containsKey(destination)) { // we can get there this turn
            if (unit.canMoveTo(destination))
                destinationTileThisTurn = destination
            else   // Someone is blocking to the path to the final tile...
            {
                val destinationNeighbors = destination.neighbors
                if (destinationNeighbors.contains(currentTile)) // We're right nearby anyway, no need to move
                    return currentTile

                val reachableDestinationNeighbors = destinationNeighbors
                        .filter { distanceToTiles.containsKey(it) && unit.canMoveTo(it)}
                if (reachableDestinationNeighbors.isEmpty()) // We can't get closer...
                    return currentTile

                destinationTileThisTurn = reachableDestinationNeighbors.minBy { distanceToTiles[it]!! }!!
            }
        } else { // If the tile is far away, we need to build a path how to get there, and then take the first step
            val path = getShortestPath(destination)
            class UnreachableDestinationException:Exception()
            if(path.isEmpty()) throw UnreachableDestinationException()
            destinationTileThisTurn = path.first()
        }

        unit.moveToTile(destinationTileThisTurn)
        return destinationTileThisTurn
    }

    fun canReach(destination: TileInfo): Boolean {
        return getShortestPath(destination).isNotEmpty()
    }

    fun getFullPathToCloseTile(destination: TileInfo): List<TileInfo> {
        val currentUnitTile = unit.getTile()
        val distanceToTiles = unit.getDistanceToTiles()
        val reversedList = ArrayList<TileInfo>()
        var currentTile = destination
        while(currentTile != currentUnitTile){
            reversedList.add(currentTile)
            val distanceToCurrentTile = distanceToTiles[currentTile]!!
            if(currentUnitTile in currentTile.neighbors
                    && getMovementCostBetweenAdjacentTiles(currentUnitTile,currentTile) == distanceToCurrentTile)
                return reversedList.reversed()

            for(tile in currentTile.neighbors)
                currentTile = currentTile.neighbors.first{it in distanceToTiles
                    && getMovementCostBetweenAdjacentTiles(it,currentTile) == distanceToCurrentTile - distanceToTiles[it]!!}
        }
        throw Exception("We couldn't get the path between the two tiles")
    }

    fun teleportToClosestMoveableTile(){
        var allowedTile:TileInfo? = null
        var distance=0
        while(allowedTile==null){
            distance++
            allowedTile = unit.getTile().getTilesAtDistance(distance)
                    .firstOrNull{unit.canMoveTo(it)}
        }
        unit.removeFromTile() // we "teleport" them away
        unit.putInTile(allowedTile)
    }

}