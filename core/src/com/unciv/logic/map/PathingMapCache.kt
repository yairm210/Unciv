package com.unciv.logic.map

import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.FixedPointMovement.Companion.FPM_ZERO
import com.unciv.logic.map.FixedPointMovement.Companion.fpmFromMovement
import com.unciv.logic.map.mapunit.movement.PathsToTilesWithinTurn
import com.unciv.logic.map.tile.Tile
import com.unciv.utils.forEachSetBit
import org.jetbrains.annotations.VisibleForTesting
import yairm210.purity.annotations.InternalState
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly
import java.util.BitSet
import java.util.Formatter
import java.util.Locale


/*
 * All the information we need about a route node, crammed into a single Long
 * 
 * If we avoid passing this to any methods that are erased, such as generics, then we can eliminate
 * allocations, leading to huge performance improvements.  
 * API oddities: 
 * - Due to the bitfield cramming, extrating the Tile, or ParentTile, requires passing in a TileMap,
 *   so we can look up the tile from that.
 * 
 * Squeezing it into a Long requires a lot of careful considerations.  Not only avoiding passing it 
 * to methods with type erasure, but also ensuring that we squeeze everything into 63 bits, and also
 * fast compatability with PrioritizedNode, and also PrioritizedNode's Comparitor. 
 * - If we pack the bits just right, then the PrioritizedNode comparator can simply compare the 
 *   entire Long directly.  So we want to store the highest priority values in the most significant
 *   bits, and the lowest priority values into the least significant bits. 
 * - Packing everything into bits requires careful consideration of ranges, and then also for
 *   floats, consideration of precision and accuracy.
 * - PrioritizedNode needs almost all of the values, so we need to make sure everything that needs
 *   fits in 63 bits.
 * 
 * Movement needs to be stored in fixed-point, in a base that can represent increments of 0.1
 *   move for railroads, and also increments of 1/3 for normal roads, uniquely. Base 30 (10x3) is
 *   the obvious choice, as it can store both road fractions with no precision loss whatsoever. So
 *   movement of 3.666 is stored as 110 (3.666*30), and restored as 3.666 (73/30). This can actually
 *   make the code simpler, as the rounding during conversion eliminates all floating point 
 *   rounding, so we no longer need to worry about minimumMovementEpsilon. The fastest unit in the
 *   base game is the Missile Cruiser with 7 movement. The fastest LAND units in the base game have
 *   6 movement.  There are a few techs which can give double movement under specific conditions, so
 *   we want to allow ~14 movement. Thus, the biggest value we need to store is 420 (14*30), which
 *   requires 9 bits. (This actually allows movements up to 17, which is nice for mod support).
 * 
 * Fields, in order from least to highest priority:
 * - tileIdx: the zeroBasedIndex, which is trivial to convert to/from Tile instances. Maximum map 
 *   size is radius 500, or 748501 tiles, so this takes 20 bits. Not strictly needed by RouteNode, 
 *   but handy. 
 * - relationshipLevel: Used as a tiebreaker when pathing through tiles owned by different civs.
 *   Unowned tiles are considered Allies. Stored as (7-ordinal), so that Ally=0, and is the highest
 *   priority. In the future, this can be reduced to 2, or even 1 bits, if needed.
 * - pbmMoveThisTurn: (pbm=PauseBeforeMountains) How much movement we would have used this turn, if
 *   we had ended a turn right before entering damaging terrain.  0 if the terrain is not damaging.
 *   This is used to retroactively calculate how far we could enter mountains if we had already
 *   moved some before entering damaging terrain. This is stored as fixed-point, base 30, in 9 bits.
 * - moveUsedThisTurn: How much movement we have used this turn so far.  This is stored as 
 *   fixed-point, base 30, in 9 bits.
 * - turns: The number of turns used so far. Tiles reachable on the current turn have turns=0. We
 *   assume a maximum of 64 turns for pathing. If the AStar calculation hits this limit when
 *   calculating a tile, it simply returns the route to this tile. Which is a surprisingly
 *   reasonable approximation, in an unreasonable scenario. This requires 6 bits.
 * - underestimatedTotal: How much movement it would take to reach the target from the current tile,
 *   if all remaining tiles to the target are railroads.  We store this as as base-30 fixed point, 
 *   with a maximum value of 819.15 movement used (25.55*64). This takes 14 bits. This is ONLY
 *   used by PrioritizedNode. This is never zero, which guarantees that an initialized 
 *   PrioritizedNode is never zero... except for the start node in one edge case.
 * - parentClockDir: The clock-direction index (2-12) of the parent tile, relative to this tile. We
 *   use 14 to represent "no parent tile" (such as for the root node). Since the values are always
 *   even, we do not store the last bit, so this only takes 3 bits. This is never zero, which 
 *   guarantees that an initialized RouteNode is never zero.
 * - padding: In a RouteNode, the other remaining 12 bits of underestimatedTotal just hold zeroes, 
 *   for now.
 * - damagingTiles: How many tiles that cause end-turn damage have been crossed to reach this tile.
 *   This is the absolute highest priority field, so it goes in the most significant bits. So very
 *   long routes that do not take damage are prioritized over shorter routes that take damage,
 *   emulating prior behavior, while also allowing cache reuse.  We only store up to 3 damaging
 *   tiles, so this takes 2 bits.
 * - sign bit: Zero.  We *could* use it for values, but then we'd have to handle negative values
 *   in the comparison, which might involve negation and other complexity when reading other
 *   fields. Far safer and eaiser and faster to just keep it zero.
 * 
 * Tiles that cannot be pathed to at all store the maximum value in all fields except
 * zeroBasedIndex.
 */
@JvmInline
@VisibleForTesting
value class RouteNode(val bits: Long=0L) {
    constructor(
        tile: Tile,
        relationshipLevel: RelationshipLevel,
        pauseBeforeMountainMove: FixedPointMovement,
        moveThisTurn: FixedPointMovement,
        turns: Int,
        parentTile: Tile,
        damagingTiles: Int,
    ):
        this(
            toTileIdxBits(tile) or
                toRelationshipLevelBits(relationshipLevel) or
                toPbmMoveThisTurnBits(pauseBeforeMountainMove) or
                toMoveThisTurnBits(moveThisTurn) or
                toTurnsBits(turns) or
                toParentClockDirBits(tile, parentTile) or
                toDamagingTilesBits(damagingTiles)
        ) {
        require(tile.zeroBasedIndex < tile.tileMap.tileList.size) { "tileList ${tile.zeroBasedIndex} exceeds max ${tile.tileMap.tileList.size}" }
        require(tile.tileMap.tileList.size <= TILE_IDX_LO_MASK) { "tileList ${tile.tileMap.tileList.size} exceeds max $TILE_IDX_LO_MASK" }
        require(pauseBeforeMountainMove >= 0) { "pauseBeforeMountainMoveThisTurn $pbmMoveThisTurn must be positive" }
        require(pauseBeforeMountainMove <= MAX_MOVE_THIS_TURN) { "pauseBeforeMountainMoveThisTurn $pbmMoveThisTurn exceeds max $MAX_MOVE_THIS_TURN" }
        require(moveThisTurn >= 0) { "moveThisTurn $moveThisTurn must be positive" }
        require(moveThisTurn <= MAX_MOVE_THIS_TURN) { "moveThisTurn $moveThisTurn exceeds max $MAX_MOVE_THIS_TURN" }
        require(turns >= 0) { "turns $turns must be positive" }
        require(turns <= MAX_TURNS) { "turns $turns exceeds max $MAX_TURNS" }
        require(toParentClockDirBits(tile, parentTile) > 0) {"parentClockDir $parentClockDir must be positive"}
        require(damagingTiles >= 0) { "damagingTiles $damagingTiles must be positive" }
        require(damagingTiles <= DAMAGE_TILES_LO_MASK) { "damagingTiles $moveThisTurn exceeds max $DAMAGE_TILES_LO_MASK" }
    }

    val tileIdx: Int get() { require(initialized); return ((bits shr TILE_IDX_OFFSET) and TILE_IDX_LO_MASK).toInt() }
    @Readonly
    fun tile(tileMap: TileMap): Tile = tileMap.tileList[tileIdx]

    val relationshipLevelBits: Long get() {require(initialized); return ((bits shr RELATIONSHIP_LEVEL_OFFSET) and RELATIONSHIP_LEVEL_LO_MASK) }
    val relationshipLevel: RelationshipLevel get() = RelationshipLevel.entries[MAX_RELATIONSHIP_LEVEL - relationshipLevelBits.toInt()]

    val pbmMoveThisTurn: FixedPointMovement get() {
        require(initialized)
        val bits = ((bits shr PBM_MOVE_THIS_TURN_OFFSET) and PBM_MOVE_THIS_TURN_LO_MASK)
        return FixedPointMovement.fpmFromFixedPointBits(bits.toInt())
    }
    val endTurnWithoutMoreDamage: Boolean get() = bits and PBM_MOVE_THIS_TURN_HI_MASK == 0L

    val moveUsedThisTurn: FixedPointMovement get() {
        require(initialized)
        val bits = ((bits shr MOVE_THIS_TURN_OFFSET) and MOVE_THIS_TURN_LO_MASK)
        return FixedPointMovement.fpmFromFixedPointBits(bits.toInt())
    }

    val turns: Int get() { require(initialized); return ((bits shr TURNS_OFFSET) and TURNS_LO_MASK).toInt() }

    val parentClockDir: Int get() { require(initialized); return (((bits shr PARENT_TILE_OFFSET) and PARENT_TILE_LO_MASK)*2).toInt() }
    @Readonly
    fun parentTile(tileMap: TileMap): Tile {
        val idx = parentClockDir
        if (idx == NO_PARENT_TILE_VALUE) return tile(tileMap)
        return tileMap.getClockPositionNeighborTile(tile(tileMap), idx)!!
    }

    val damagingTiles: Int get() { require(initialized); return ((bits shr DAMAGE_TILES_OFFSET) and DAMAGE_TILES_LO_MASK).toInt() }

    // parentClockDir can never be 0, so all zeroes means uninitialized
    val initialized: Boolean get() = bits != 0L

    val isNoPathingNode: Boolean get() = pbmMoveThisTurn.bits == PBM_MOVE_THIS_TURN_LO_MASK.toInt()

    @Readonly
    override fun toString() = "RouteNode[tile=$tileIdx, turns=$turns, moveUsedThisTurn=$moveUsedThisTurn]"
    @Readonly
    fun toString(tileMap: TileMap) = "RouteNode[tile=${tile(tileMap)} turns=$turns, moveThisTurn=$moveUsedThisTurn]"

    companion object {
        // bits 0-19 (20b = 1048576tiles) are the zeroBasedIndex of this tile (radius 500, or approx 1170x896) 
        internal const val TILE_IDX_OFFSET = 0
        internal const val TILE_IDX_BIT_COUNT = 20
        internal const val TILE_IDX_LO_MASK = (0x1L shl TILE_IDX_BIT_COUNT) - 1L
        // bits 20-22 (3b = 8values) are our relationship with the owning civ
        private const val RELATIONSHIP_LEVEL_OFFSET = TILE_IDX_OFFSET + TILE_IDX_BIT_COUNT
        private const val RELATIONSHIP_LEVEL_BIT_COUNT = 3
        private const val RELATIONSHIP_LEVEL_LO_MASK = (0x1L shl RELATIONSHIP_LEVEL_BIT_COUNT) - 1L
        private const val MAX_RELATIONSHIP_LEVEL = 7
        @Suppress("unused")
        private val relationshipReq = require(RelationshipLevel.entries.size == MAX_RELATIONSHIP_LEVEL + 1)
        // bits 23-31 (9b = 512values = 25.55move) are the base-30 movement used since entering
        // damaging terrain, if any.  Zero if not in damaging terrain.
        private const val PBM_MOVE_THIS_TURN_OFFSET = RELATIONSHIP_LEVEL_OFFSET + RELATIONSHIP_LEVEL_BIT_COUNT
        private const val PBM_MOVE_THIS_TURN_BIT_COUNT = 9
        private const val PBM_MOVE_THIS_TURN_LO_MASK = (0x1L shl PBM_MOVE_THIS_TURN_BIT_COUNT) - 1L
        private const val PBM_MOVE_THIS_TURN_HI_MASK = PBM_MOVE_THIS_TURN_LO_MASK shl PBM_MOVE_THIS_TURN_OFFSET
        // bits 32-40 (9b = 512values = 25.55move) are the base-30 movement used on this turn.
        private const val MOVE_THIS_TURN_OFFSET = PBM_MOVE_THIS_TURN_OFFSET + PBM_MOVE_THIS_TURN_BIT_COUNT
        private const val MOVE_THIS_TURN_BIT_COUNT = 9
        private const val MOVE_THIS_TURN_LO_MASK = (0x1L shl MOVE_THIS_TURN_BIT_COUNT) - 1L
        val MAX_MOVE_THIS_TURN = FixedPointMovement.fpmFromFixedPointBits(MOVE_THIS_TURN_LO_MASK.toInt())
        // bits 41-46 (6b = 63turns) are the number of turns to get to this tile. 0=This turn.
        private const val TURNS_OFFSET = MOVE_THIS_TURN_OFFSET + MOVE_THIS_TURN_BIT_COUNT
        private const val TURNS_BIT_COUNT = 6
        private const val TURNS_LO_MASK = (0x1L shl TURNS_BIT_COUNT) - 1L
        const val MAX_TURNS = TURNS_LO_MASK.toInt()
        const val MAX_VALID_TURNS = MAX_TURNS - 1
        // [PrioritizedNode] bits 47-60 (14b) are the underestimated total movement from the initial tile toward the target. 
        internal const val UNDERESTIMATED_TOTAL_OFFSET = TURNS_OFFSET + TURNS_BIT_COUNT
        internal const val UNDERESTIMATED_TOTAL_BIT_COUNT = 14
        internal const val UNDERESTIMATED_TOTAL_LO_MASK = (0x1L shl  UNDERESTIMATED_TOTAL_BIT_COUNT) - 1L
        internal const val UNDERESTIMATED_TOTAL_HI_MASK = UNDERESTIMATED_TOTAL_LO_MASK shl UNDERESTIMATED_TOTAL_OFFSET
        internal val MAX_UNDERESTIMATED_TOTAL = FixedPointMovement.fpmFromFixedPointBits(UNDERESTIMATED_TOTAL_LO_MASK.toInt())
        // [RouteNode] bits 47-49 (3b = 8values > 6neighbors +1self) are the parent tile clock direction/2+1.  no-Parent is "7", and "0" is never valid
        private const val PARENT_TILE_OFFSET = UNDERESTIMATED_TOTAL_OFFSET
        private const val PARENT_TILE_BIT_COUNT = 3
        private const val PARENT_TILE_LO_MASK = (0x1L shl PARENT_TILE_BIT_COUNT) - 1L
        private const val NO_PARENT_TILE_BITS = 7L
        private const val NO_PARENT_TILE_VALUE = 14
        // [RouteNode] bits 50-60 (11b) are padding bits, only used by PrioritizedNode's underestimatedTotal field
        // bits 61-62 (2b = 4turns) are the number of turns ended in damaging tiles.
        private const val DAMAGE_TILES_OFFSET = UNDERESTIMATED_TOTAL_OFFSET + UNDERESTIMATED_TOTAL_BIT_COUNT
        private const val DAMAGE_TILES_BIT_COUNT = 2
        private const val DAMAGE_TILES_LO_MASK = (0x1L shl DAMAGE_TILES_BIT_COUNT) -1L
        internal const val MAX_DAMAGING_TILES = DAMAGE_TILES_LO_MASK.toInt()


        @Readonly
        private fun toParentClockDirBits(tile: Tile, parentTile: Tile): Long
            = (if (tile == parentTile) NO_PARENT_TILE_BITS else tile.tileMap.getNeighborTileClockPosition(tile, parentTile)/2L) shl PARENT_TILE_OFFSET
        @Pure
        private fun toMoveThisTurnBits(moveThisTurn: FixedPointMovement): Long
            = moveThisTurn.bits.toLong() shl MOVE_THIS_TURN_OFFSET
        @Pure
        private fun toPbmMoveThisTurnBits(pbmMoveThisTurn: FixedPointMovement): Long
            = pbmMoveThisTurn.bits.toLong() shl PBM_MOVE_THIS_TURN_OFFSET
        @Readonly
        private fun toTileIdxBits(tile: Tile): Long
            = (tile.zeroBasedIndex.toLong() shl TILE_IDX_OFFSET)
        @Pure
        private fun toTurnsBits(turns: Int): Long
            = (turns.toLong() shl TURNS_OFFSET)
        @Pure
        private fun toDamagingTilesBits(damagingTiles: Int): Long
            = (damagingTiles.toLong() shl DAMAGE_TILES_OFFSET)
        @Pure
        private fun toRelationshipLevelBits(relationshipLevel: RelationshipLevel): Long
            = (MAX_RELATIONSHIP_LEVEL - relationshipLevel.ordinal).toLong() shl RELATIONSHIP_LEVEL_OFFSET

        @Pure
        fun noPathingNode(tile: Tile) = RouteNode(
            tile,
            RelationshipLevel.Unforgivable,
            MAX_MOVE_THIS_TURN,
            MAX_MOVE_THIS_TURN,
            MAX_TURNS,
            tile,
            MAX_DAMAGING_TILES,
        )
        @Pure
        fun rootNode(tile: Tile, moveThisTurn: FixedPointMovement) = RouteNode(
            tile,
            RelationshipLevel.Favorable, // irrelevant since we start here
            FPM_ZERO,
            moveThisTurn,
            0,

            tile,
            0,
        )
    }
}

@JvmInline
value class FixedPointMovement private constructor(val bits: Int) {
    @Pure operator fun plus(other: FixedPointMovement) = FixedPointMovement(bits + other.bits)
    @Pure operator fun minus(other: FixedPointMovement) = FixedPointMovement(bits - other.bits)
    @Pure operator fun plus(other: Float) = FixedPointMovement(bits + (other * MOVE_SPEED_BASE).toInt())
    @Pure operator fun minus(other: Float) = FixedPointMovement(bits - (other * MOVE_SPEED_BASE).toInt())
    @Pure operator fun compareTo(other: FixedPointMovement) = bits.compareTo(other.bits)
    @Pure operator fun compareTo(other: Int) = bits.compareTo((other * MOVE_SPEED_BASE))

    @Pure fun toFloat() = bits / 30f
    @Pure fun coerceAtMost(max: FixedPointMovement) = FixedPointMovement(bits.coerceAtMost(max.bits))
    @Pure fun coerceAtLeast(min: FixedPointMovement) = FixedPointMovement(bits.coerceAtLeast(min.bits))
    @Pure fun coerceIn(min: FixedPointMovement, max: FixedPointMovement) = FixedPointMovement(bits.coerceIn(min.bits, max.bits))

    override fun toString() = toFloat().toString()

    companion object {
        const val MOVE_SPEED_BASE = 30
        val FPM_ZERO = FixedPointMovement(0)
        val FPM_POINT_FIVE = FixedPointMovement(MOVE_SPEED_BASE/2)
        val FPM_ONE = FixedPointMovement(MOVE_SPEED_BASE)

        @Pure fun fpmFromFixedPointBits(bits: Int) = FixedPointMovement(bits)
        @Pure fun fpmFromMovement(move: Int) = FixedPointMovement((move * MOVE_SPEED_BASE))
        @Pure fun fpmFromMovement(move: Float): FixedPointMovement { // rounding HALF_UP 
            val plusOneBit = (move * (MOVE_SPEED_BASE * 2)).toInt()
            return FixedPointMovement((plusOneBit shr 1) + (plusOneBit and 1))
        }
    }
}
@Pure fun Float.toFixedPointMove() = fpmFromMovement(this)
@Pure operator fun Int.minus(other: FixedPointMovement) = fpmFromMovement(this) - other


data class PathingMapCacheKey(
    val startingPoint: HexCoord,
    val moveRemaining: FixedPointMovement,
    val fullMove: Int,
)

@InternalState
internal class PathingMapCache private constructor(
    /**
     * The key for this cache. If the key no longer matches, then the cache is invalid
     */
    internal val key: PathingMapCacheKey,

    /**
     * Frontier list of the tiles to be checked.
     *
     * In exceptional cases, a node already calculated may be left here, and recalculated again
     * later.
     *
     * Bitset used to minimize memory allocations
     */
    internal val nodesNeedingNeighbors: BitSet,
    /**
     * A BitSet to track which tiles have already been checked.
     * This helps avoid redundant calculations and ensures each tile is processed only once.
     *
     * Bitset used to minimize memory allocations
     */
    internal val addedNeighborNodes: BitSet,
    /**
     * A map where each tile reached during the search points to its parent tile.
     * This map is used to reconstruct the path once the destination is reached.
     *
     * Theoretically, this can be replaced with three separate arrays for each field, eliminiating
     * the separate allocations per-node, but it's unclear if the performance is worth the
     * complexity.
     */
    internal val routeNodes: LongArray,  // Actually Array<RouteNode>
) {
    internal val tilesSameTurn: PathsToTilesWithinTurn = PathsToTilesWithinTurn()
        
    constructor(key: PathingMapCacheKey, tileMap: TileMap) : this(
        key,
        BitSet(tileMap.tileList.size),
        BitSet(tileMap.tileList.size),
        LongArray(tileMap.tileList.size)
    )
    
    fun isCacheValid(latestKey: PathingMapCacheKey) = key == latestKey
    
    /*
     * Returns a mutable fork for pathfinding
     * 
     * This uses the same routeNodes, but copies of the frontier bitsets. It does not set tilesSameTurn.
     */
    fun forkForPathfinding(): PathingMapCache {
        synchronized(addedNeighborNodes) {
            val codesNeedingNeighborsCopy: BitSet = nodesNeedingNeighbors.clone() as BitSet
            val addedNeighborNodesCopy: BitSet = addedNeighborNodes.clone() as BitSet
            return PathingMapCache(key, codesNeedingNeighborsCopy, addedNeighborNodesCopy, routeNodes)
        }
    }
    
    fun mergePathfindingFork(update: PathingMapCache) {
        require(key == update.key && routeNodes === routeNodes)
        // now merge the pathfinder's tilesChecked and tilesToCheck back into the shared PathingData
        // again using a synchronized block not just for thread-safety, but also to ensure atomicity
        synchronized(addedNeighborNodes) {
            // For each tile who had its neighbors queued by this thread, move them to the right
            // data structure. Since these are BitSets, this is rediculously fast.
            addedNeighborNodes.or(update.addedNeighborNodes)
            nodesNeedingNeighbors.andNot(update.addedNeighborNodes)

            // For tiles that were queued but not yet calculated, add them to nodesNeedingNeighbors.
            // When a tile incurs taking damage, a later tile can replace it's data with a less
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
                if (node.turns == 0 && node.moveUsedThisTurn == FPM_ZERO) 'S'
                else if (tile == destination) 'D'
                else if (!node.endTurnWithoutMoreDamage) '*'
                else ' '
            if (node.turns == Int.MAX_VALUE) // cannot move to this tile
                format(" -/---%s", tag)
            else // we've found a minimum path to this tile
                format("%2d/%1.1f%s", node.turns, node.moveUsedThisTurn.toFloat(), tag)
        }
    }

    override fun toString() = "${javaClass.simpleName}[key=${key.startingPoint}/${key.moveRemaining}/${key.fullMove} nodesNeedingNeighbors=${nodesNeedingNeighbors.cardinality()} addedNeighborNodes=${addedNeighborNodes.cardinality()} routeNodes=${routeNodes.size} tilesSameTurn=${tilesSameTurn.size}"
}
