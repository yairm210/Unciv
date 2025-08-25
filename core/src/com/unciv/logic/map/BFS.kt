package com.unciv.logic.map

import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.InternalState
import yairm210.purity.annotations.Readonly
import kotlin.collections.ArrayDeque

/**
 * Defines intermediate steps of a breadth-first search, for use in either get shortest path or get connected tiles.
 * 
 * @param startingPoint Starting [Tile] from which to start the search
 * @param predicate A condition for subsequent neighboring tiles to be considered in search
 */
@InternalState
class BFS(
    val startingPoint: Tile,
    private val predicate : (Tile) -> Boolean
) {
    /** Maximum number of tiles to search */
    var maxSize = Int.MAX_VALUE

    /** remaining tiles to check */
    private val tilesToCheck = ArrayDeque<Tile>(37)  // needs resize at distance 4

    /** each tile reached points to its parent tile, where we got to it from */
    private val tilesReached = HashMap<Tile, Tile>()

    init {
        tilesToCheck.add(startingPoint)
        tilesReached[startingPoint] = startingPoint
    }

    /** Process fully until there's nowhere left to check */
    fun stepToEnd() {
        while (!hasEnded())
            nextStep()
    }

    /**
     * Process until either [destination] is reached or there's nowhere left to check
     * @return `this` instance for chaining
     */
    fun stepUntilDestination(destination: Tile): BFS {
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
     *
     * @return The Tile that was checked, or `null` if there was nothing to do
     */
    fun nextStep(): Tile? {
        if (tilesReached.size >= maxSize) { tilesToCheck.clear(); return null }
        val current = tilesToCheck.removeFirstOrNull() ?: return null
        for (neighbor in current.neighbors) {
            if (neighbor !in tilesReached && predicate(neighbor)) {
                tilesReached[neighbor] = current
                tilesToCheck.add(neighbor)
            }
        }
        return current
    }

    /**
     * @return a Sequence from the [destination] back to the [startingPoint], including both, or empty if [destination] has not been reached
     */
    @Readonly
    fun getPathTo(destination: Tile): Sequence<Tile> = sequence {
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
    fun hasReachedTile(tile: Tile) = tilesReached.containsKey(tile)

    /** @return all tiles reached so far */
    fun getReachedTiles(): MutableSet<Tile> = tilesReached.keys

    /** @return number of tiles reached so far */
    fun size() = tilesReached.size
}
