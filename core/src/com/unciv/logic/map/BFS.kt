package com.unciv.logic.map

/**
 * Defines intermediate steps of a breadth-first search, for use in either get shortest path or get onnected tiles.
 */
class BFS(val startingPoint: TileInfo, val predicate : (TileInfo) -> Boolean){
    var tilesToCheck = ArrayList<TileInfo>()
    /** each tile reached points to its parent tile, where we got to it from */
    val tilesReached = HashMap<TileInfo, TileInfo>()

    init{
        tilesToCheck.add(startingPoint)
        tilesReached[startingPoint] = startingPoint
    }

    fun stepToEnd(){
        while(tilesToCheck.isNotEmpty())
            nextStep()
    }

    fun stepUntilDestination(destination: TileInfo): BFS {
        while(!tilesReached.containsKey(destination) && tilesToCheck.isNotEmpty())
            nextStep()
        return this
    }

    fun nextStep(){
        val newTilesToCheck = ArrayList<TileInfo>()
        for(tileInfo in tilesToCheck){
            val fitNeighbors = tileInfo.neighbors.asSequence()
                    .filter(predicate)
                    .filter{!tilesReached.containsKey(it)}
            fitNeighbors.forEach { tilesReached[it] = tileInfo; newTilesToCheck.add(it) }
        }
        tilesToCheck = newTilesToCheck
    }

    fun getPathTo(destination: TileInfo): ArrayList<TileInfo> {
        val path = ArrayList<TileInfo>()
        path.add(destination)
        var currentNode = destination
        while(currentNode != startingPoint){
            tilesReached[currentNode]?.let {
                currentNode = it
                path.add(currentNode)
            } ?: return ArrayList() // destination is not in our path
        }
        return path
    }
}