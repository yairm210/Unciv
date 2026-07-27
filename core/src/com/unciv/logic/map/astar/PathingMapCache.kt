package com.unciv.logic.map.astar

import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapunit.movement.PathsToTilesWithinTurn
import com.unciv.logic.map.tile.Tile
import com.unciv.utils.forEachSetBit
import yairm210.purity.annotations.InternalState
import yairm210.purity.annotations.Readonly
import java.util.BitSet
import java.util.Formatter
import java.util.Locale


/**
 *  @property key The key for this cache. If the key no longer matches, then the cache is invalid
 *  @property nodesNeedingNeighbors Frontier list of the tiles to be checked.
 *            In exceptional cases, a node already calculated may be left here, and recalculated again later.
 *            Bitset used to minimize memory allocations
 *  @property addedNeighborNodes A BitSet to track which tiles have already been checked.
 *            This helps avoid redundant calculations and ensures each tile is processed only once.
 *            Bitset used to minimize memory allocations
 *  @property routeNodes A map where each tile reached during the search points to its parent tile.
 *            This map is used to reconstruct the path once the destination is reached.
 *            Theoretically, this can be replaced with three separate arrays for each field, eliminating
 *            the separate allocations per-node, but it's unclear if the performance is worth the complexity.
 */
@InternalState
internal class PathingMapCache private constructor(
    internal val key: Key,
    internal val nodesNeedingNeighbors: BitSet,
    internal val addedNeighborNodes: BitSet,
    internal val routeNodes: LongArray,  // Actually Array<RouteNode>
) {
    data class Key(
        val startingPoint: HexCoord,
        val moveRemaining: FixedPointMovement,
        val fullMove: FixedPointMovement,
    ) {
        override fun toString() = "$startingPoint/$moveRemaining/$fullMove"
    }

    internal val tilesSameTurn: PathsToTilesWithinTurn = PathsToTilesWithinTurn()

    /** Creates an empty [PathingMapCache], using [tileMap] only for allocating the arrays to the proper size */
    constructor(key: Key, tileMap: TileMap) : this(
        key,
        BitSet(tileMap.tileList.size),
        BitSet(tileMap.tileList.size),
        LongArray(tileMap.tileList.size)
    )

    @Readonly
    fun isCacheValid(latestKey: Key) = key == latestKey

    /**
     * Returns a mutable fork for pathfinding
     *
     * This uses the same [routeNodes], but copies of the frontier bitsets. It does not set [tilesSameTurn].
     * @see mergePathfindingFork
     */
    @Readonly
    fun forkForPathfinding(): PathingMapCache {
        synchronized(addedNeighborNodes) {
            val codesNeedingNeighborsCopy: BitSet = nodesNeedingNeighbors.clone() as BitSet
            val addedNeighborNodesCopy: BitSet = addedNeighborNodes.clone() as BitSet
            return PathingMapCache(key, codesNeedingNeighborsCopy, addedNeighborNodesCopy, routeNodes)
        }
    }

    /**
     * Merges a fork created with [forkForPathfinding], modifying [addedNeighborNodes]
     * and [nodesNeedingNeighbors]. Also mutates `update.nodesNeedingNeighbors`, meaning the fork
     * should be discarded.
     */
    fun mergePathfindingFork(update: PathingMapCache) {
        require(key == update.key && routeNodes === update.routeNodes)
        // now merge the pathfinder's tilesChecked and tilesToCheck back into the shared PathingData
        // again using a synchronized block not just for thread-safety, but also to ensure atomicity
        synchronized(addedNeighborNodes) {
            // For each tile who had its neighbors queued by this thread, move them to the right
            // data structure. Since these are BitSets, this is rediculously fast.
            addedNeighborNodes.or(update.addedNeighborNodes)
            nodesNeedingNeighbors.andNot(update.addedNeighborNodes)

            // For tiles that were queued but not yet calculated, add them to nodesNeedingNeighbors.
            // When a tile incurs taking damage, a later tile can replace its data with a less
            // damaging route. In edge cases, this can cause a tile to be queued a second time.
            // Since tiles can be queued multiple times, they can be both queued (at higher damage)
            // and already calculated (at lower damage). We have to skip over tiles already calculated
            update.nodesNeedingNeighbors.andNot(update.addedNeighborNodes)
            nodesNeedingNeighbors.or(update.nodesNeedingNeighbors)
        }
    }

    fun clear() {
        nodesNeedingNeighbors.clear()
        addedNeighborNodes.clear()
        routeNodes.fill(0L)
        tilesSameTurn.clear()
    }

    fun toDebugString(tileMap: TileMap, destination: Tile? = null): String {
        val routeTiles = tileMap.tileList.filterIndexed { idx, _ -> RouteNode(routeNodes[idx]).initialized }
        if (routeTiles.isEmpty()) return "{}"
        // first determine which rows and columns need printing
        val height = -tileMap.bottomY * 2
        val width = tileMap.tileMatrix.size
        val xs = BitSet(width)
        val ys = BitSet(height)
        routeTiles.forEach {
            xs.set(it.position.x - tileMap.leftX)
            ys.set(height -it.position.y + tileMap.bottomY) // invert the ys to easily iterate from positive to negative
        }
        val stringBuilder = StringBuilder("\n")
        Formatter(stringBuilder, Locale.US).use {
            // format the column headers
            it.format("       ")
            xs.forEachSetBit {xIndex: Int ->
                val x = xIndex + tileMap.leftX
                it.format(" %+-4d  ", x)
            }
            it.format("\n")
            // for each row
            ys.forEachSetBit {yIndex: Int ->
                // print row header
                val y = -yIndex + height + tileMap.bottomY
                // display the row header
                it.format("  %+-4d ", y)
                // display each cell for the row
                xs.forEachSetBit {xIndex: Int ->
                    val x = xIndex + tileMap.leftX
                    val tile = tileMap.getOrNull(x, y)
                    it.toDebugStringFormatNode(tile, destination)
                }
                it.format("\n")
            }
        }
        return stringBuilder.toString()
    }

    private fun Formatter.toDebugStringFormatNode(tile: Tile?, destination: Tile?) {
        if (tile == null) { // out of map bounds
            format("       ")
            return
        }
        val node = RouteNode(routeNodes[tile.zeroBasedIndex])
        if (!node.initialized) {// pathing has not explored this tile yet
            format("  /    ")
        } else {
            val tag =
                if (node.turns == 0 && node.moveUsedThisTurn == FixedPointMovement.FPM_ZERO) 'S'
                else if (tile == destination) 'D'
                else if (!node.endTurnWithoutMoreDamage) '*'
                else ' '
            if (node.turns == Int.MAX_VALUE) // cannot move to this tile
                format(" -/---%s", tag)
            else // we've found a minimum path to this tile
                format("%2d/%1.1f%s", node.turns, node.moveUsedThisTurn.toFloat(), tag)
        }
    }

    override fun toString() = "${javaClass.simpleName}[key=$key nodesNeedingNeighbors=${nodesNeedingNeighbors.cardinality()} addedNeighborNodes=${addedNeighborNodes.cardinality()} routeNodes=${routeNodes.size} tilesSameTurn=${tilesSameTurn.size}]"
}
