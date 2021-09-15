package com.unciv.logic.map

import kotlin.collections.ArrayDeque

/**
 * Defines intermediate steps of a breadth-first search, for use in either get shortest path or get connected tiles.
 */
class BFS(
    val startingPoint: TileInfo,
    private val predicate : (TileInfo) -> Boolean
) {
    /** Maximum number of tiles to search */
    var maxSize = Int.MAX_VALUE
    
    /** remaining tiles to check */
    private val tilesToCheck = ArrayDeque<TileInfo>(37)  // needs resize at distance 4

    /** each tile reached points to its parent tile, where we got to it from */
    private val tilesReached = HashMap<TileInfo, TileInfo>()

    init {
        tilesToCheck.add(startingPoint)
        tilesReached[startingPoint] = startingPoint
    }

    /** Process fully until there's nowhere left to check
     *  Optionally assigns a continent ID as it goes */
    fun stepToEnd(continent: Int? = null) {
        if (continent != null)
            startingPoint.setContinent(continent)
        while (!hasEnded())
            nextStep(continent)
    }

    /**
     * Process until either [destination] is reached or there's nowhere left to check
     * @return `this` instance for chaining
     */
    fun stepUntilDestination(destination: TileInfo): BFS {
        while (!tilesReached.containsKey(destination) && !hasEnded())
            nextStep()
        return this
    }

    /**
     * Process one tile-to-search, fetching all neighbors not yet touched
     * and adding those that fulfill the [predicate] to the reached set
     * and to the yet-to-be-processed set.
     * 
     * Will do nothing when [hasEnded] returns `true`
     */
    fun nextStep(continent: Int? = null) {
        if (tilesReached.size >= maxSize) { tilesToCheck.clear(); return }
        val current = tilesToCheck.removeFirstOrNull() ?: return
        for (neighbor in current.neighbors) {
            if (neighbor !in tilesReached && predicate(neighbor)) {
                tilesReached[neighbor] = current
                tilesToCheck.add(neighbor)
                if (continent != null)
                    neighbor.setContinent(continent)
            }
        }
    }

    /**
     * @return a Sequence from the [destination] back to the [startingPoint], including both, or empty if [destination] has not been reached
     */
    fun getPathTo(destination: TileInfo): Sequence<TileInfo> = sequence {
        var currentNode = destination
        while (true) {
            val parent = tilesReached[currentNode] ?: break  // destination is not in our path
            yield(currentNode)
            if (currentNode == startingPoint) break
            currentNode = parent
        }
    }

    /** @return true if there are no more tiles to check */
    fun hasEnded() = tilesToCheck.isEmpty()

    /** @return true if the [tile] has been reached */
    fun hasReachedTile(tile: TileInfo) = tilesReached.containsKey(tile)

    /** @return all tiles reached so far */
    fun getReachedTiles(): MutableSet<TileInfo> = tilesReached.keys

    /** @return number of tiles reached so far */
    fun size() = tilesReached.size
}
