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
            for(neighbor in tileInfo.neighbors){
                if(predicate(neighbor) && !tilesReached.containsKey(neighbor)){
                    tilesReached[neighbor] = tileInfo
                    newTilesToCheck.add(neighbor)
                }
            }
        }
        tilesToCheck = newTilesToCheck
    }

    fun getPathTo(destination: TileInfo): ArrayList<TileInfo> {
        val path = ArrayList<TileInfo>()
        path.add(destination)
        var currentNode = destination
        while(currentNode != startingPoint) {
            val parent = tilesReached[currentNode]
            if (parent == null) return ArrayList()// destination is not in our path
            currentNode = parent
            path.add(currentNode)
        }
        return path
    }
}