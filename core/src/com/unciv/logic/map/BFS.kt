package com.unciv.logic.map

/**
 * Defines intermediate steps of a breadth-first search, for use in either get shortest path or get onnected tiles.
 */
class BFS(val startingPoint: TileInfo, val predicate : (TileInfo) -> Boolean){
    var tilesToCheck = ArrayList<TileInfo>()
    val tilesReached = HashMap<TileInfo, TileInfo>() // each tile reached points to its parent tile, where we got to it from

    init{
        tilesToCheck.add(startingPoint)
        tilesReached[startingPoint] = startingPoint
    }

    fun stepToEnd(){
        while(tilesToCheck.isNotEmpty())
            nextStep()
    }

    fun stepUntilDestination(destination: TileInfo){
        while(!tilesReached.containsKey(destination) && tilesToCheck.isNotEmpty())
            nextStep()
    }

    fun nextStep(){
        val newTilesToCheck = ArrayList<TileInfo>()
        for(tileInfo in tilesToCheck){
            val fitNeighbors = tileInfo.neighbors.asSequence()
                    .filter(predicate)
                    .filter{!tilesReached.containsKey(it)}.toList()
            fitNeighbors.forEach { tilesReached[it] = tileInfo }
            newTilesToCheck.addAll(fitNeighbors)
        }
        tilesToCheck = newTilesToCheck
    }

    fun getPathTo(destination: TileInfo): ArrayList<TileInfo> {
        val path = ArrayList<TileInfo>()
        path.add(destination)
        var currentNode = destination
        while(currentNode != startingPoint){
            currentNode = tilesReached[currentNode]!!
            path.add(currentNode)
        }
        return path
    }
}