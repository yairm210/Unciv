package com.unciv.logic.map

import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.InternalState
import java.util.PriorityQueue


data class TilePriority(val tile: Tile, val priority: Float)

/**
 * AStar is an implementation of the A* search algorithm, commonly used for finding the shortest path
 * in a weighted graph.
 *
 * The algorithm maintains a priority queue of paths while exploring the graph, expanding paths in
 * order of their estimated total cost from the start node to the goal node, factoring in both the
 * cost so far and an estimated cost (heuristic) to the goal.
 *
 * @param startingPoint The initial tile where the search begins.
 * @param predicate A function that determines if a tile should be considered for further exploration.
 *                  For instance, it might return `true` for passable tiles and `false` for obstacles.
 * @param cost A function that takes two tiles (fromTile, toTile) as input and returns the cost
 *                     of moving from 'fromTile' to 'toTile' as a Float. This allows for flexible cost
 *                     calculations based on different criteria, such as distance, terrain, or other
 *                     custom logic defined by the user.
 * @param heuristic A function that estimates the cost from a given tile to the goal. For the A*
 *                  algorithm to guarantee the shortest path, this heuristic must be admissible,
 *                  meaning it should never overestimate the actual cost to reach the goal.
 *                  You can set this to `{ tile -> 0 }` for Djikstra's algorithm.
 *
 * Usage Example:
 * ```
 * val unit: MapUnit = ...
 * val aStarSearch = AStar(startTile,
 *                        { tile -> tile.isPassable },
 *                        { from: Tile, to: Tile -> MovementCost.getMovementCostBetweenAdjacentTiles(unit, from, to)},
 *                        { tile -> <custom heuristic> })
 *
 * val path = aStarSearch.findPath(goalTile)
 * ```
 */
@InternalState
class AStar(
    val startingPoint: Tile,
    private val predicate : (Tile) -> Boolean,
    private val cost: (Tile, Tile) -> Float,
    private val heuristic : (Tile, Tile) -> Float,
) {
    /** Maximum number of tiles to search */
    var maxSize = Int.MAX_VALUE

    /** Cache for storing the costs */
    private val costCache = mutableMapOf<Pair<Tile,Tile>, Float>()

    /**
     * Retrieves the cost of moving to a given tile, utilizing a cache to improve efficiency.
     * If the cost for a tile is not already cached, it computes the cost using the provided cost function and stores it in the cache.
     *
     * @param from The source tile.
     * @param to The destination tile.
     * @return The cost of moving between the tiles.
     */
    private fun getCost(from: Tile, to: Tile): Float {
        return costCache.getOrPut(Pair(from, to)) { cost(from, to) }
    }

    /**
     * Comparator for the priority queue used in the A* algorithm.
     * It compares two `TilePriority` objects based on their priority value,
     * ensuring that tiles with lower estimated total costs are given precedence in the queue.
     */
    private val tilePriorityComparator = Comparator<TilePriority> { tp1, tp2 ->
        tp1.priority.compareTo(tp2.priority)
    }

    /**
     * Frontier priority queue for managing the tiles to be checked.
     * Tiles are ordered based on their priority, determined by the cumulative cost so far and the heuristic estimate to the goal.
     */
    private val tilesToCheck = PriorityQueue(27, tilePriorityComparator)

    /**
     * A map where each tile reached during the search points to its parent tile.
     * This map is used to reconstruct the path once the destination is reached.
     */
    private val tilesReached = HashMap<Tile, Tile>()

    /**
     * A map holding the cumulative cost to reach each tile.
     * This is used to calculate the most efficient path to a tile during the search process.
     */
    private val cumulativeTileCost = HashMap<Tile, Float>()

    init {
        tilesToCheck.add(TilePriority(startingPoint, 0f))
        tilesReached[startingPoint] = startingPoint
        cumulativeTileCost[startingPoint] = 0f
    }

    /**
     * Continues the search process until there are no more tiles left to check.
     */
    fun stepToEnd() {
        while (!hasEnded())
            nextStep()
    }

    /**
     * Continues the search process until either the specified destination is reached or there are no more tiles left to check.
     *
     * @param destination The destination tile to reach.
     * @return This AStar instance, allowing for method chaining.
     */
    fun stepUntilDestination(destination: Tile): AStar {
        while (!tilesReached.containsKey(destination) && !hasEnded())
            nextStep()
        return this
    }

    /**
     * Processes one step in the A* algorithm, expanding the search from the current tile to its neighbors.
     * It updates the search structures accordingly, considering both the cost so far and the heuristic estimate.
     *
     * If the maximum size is reached or no more tiles are available, this method will do nothing.
     */
    fun nextStep() {
        if (tilesReached.size >= maxSize) { tilesToCheck.clear(); return }
        val currentTile = tilesToCheck.poll()?.tile ?: return
        for (neighbor in currentTile.neighbors) {
            val newCost: Float = cumulativeTileCost[currentTile]!! + getCost(currentTile, neighbor)
            if (predicate(neighbor) &&
                (!cumulativeTileCost.containsKey(neighbor)
                || newCost < (cumulativeTileCost[neighbor] ?: Float.MAX_VALUE))
            ){
                cumulativeTileCost[neighbor] = newCost
                val priority: Float = newCost + heuristic(currentTile, neighbor)
                tilesToCheck.add(TilePriority(neighbor, priority))
                tilesReached[neighbor] = currentTile
            }
        }
    }

    /**
     * Constructs a sequence representing the path from the given destination tile back to the starting point.
     * If the destination has not been reached, the sequence will be empty.
     *
     * @param destination The destination tile to trace the path to.
     * @return A sequence of tiles representing the path from the destination to the starting point.
     */
    fun getPathTo(destination: Tile): Sequence<Tile> = sequence {
        var currentNode = destination
        while (true) {
            val parent = tilesReached[currentNode] ?: break  // destination is not in our path
            yield(currentNode)
            if (currentNode == startingPoint) break
            currentNode = parent
        }
    }

    /**
     * Checks if there are no more tiles to be checked in the search.
     *
     * @return True if the search has ended, otherwise false.
     */
    fun hasEnded() = tilesToCheck.isEmpty()

    /**
     * Determines if a specific tile has been reached during the search.
     *
     * @param tile The tile to check.
     * @return True if the tile has been reached, otherwise false.
     */
    fun hasReachedTile(tile: Tile) = tilesReached.containsKey(tile)

    /**
     * Retrieves all tiles that have been reached so far in the search.
     *
     * @return A set of tiles that have been reached.
     */
    fun getReachedTiles(): MutableSet<Tile> = tilesReached.keys

    /**
     * Provides the number of tiles that have been reached so far in the search.
     *
     * @return The count of tiles reached.
     */
    fun size() = tilesReached.size
}
