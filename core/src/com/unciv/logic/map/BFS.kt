package com.unciv.logic.map

/**
 * Defines intermediate steps of a breadth-first search, for use in either get shortest path or get connected tiles.
 */
class BFS(val startingPoint: TileInfo, val predicate : (TileInfo) -> Boolean){
    var tilesToCheck = ArrayList<TileInfo>()
    /** each tile reached points to its parent tile, where we got to it from */
    val tilesReached = HashMap<TileInfo, TileInfo>()

    init {
        reset()
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

    fun nextStep(): BFS {
        val newTilesToCheck = ArrayList<TileInfo>()
        for (tileInfo in tilesToCheck)
            for (neighbor in tileInfo.neighbors)
                if (!tilesReached.containsKey(neighbor) && predicate(neighbor)) {
                    tilesReached[neighbor] = tileInfo
                    newTilesToCheck.add(neighbor)
                }
        tilesToCheck = newTilesToCheck
        return this
    }

    fun getPathTo(destination: TileInfo, reverse: Boolean = false): List<TileInfo> {
        val path = ArrayList<TileInfo>()
        var currentNode = destination
        while (currentNode != startingPoint) {
            path.add(currentNode)
            val parent = tilesReached[currentNode] ?: return emptyList()
            // destination is not in our path
            currentNode = parent
        }
        if (reverse)
            path.reverse()
        return path
    }

    fun reset(): BFS {
        tilesToCheck.clear()
        tilesReached.clear()
        tilesToCheck.add(startingPoint)
        tilesReached[startingPoint] = startingPoint
        return this
    }
}