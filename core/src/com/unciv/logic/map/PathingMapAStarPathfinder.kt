package com.unciv.logic.map

import com.badlogic.gdx.utils.IntIntMap
import com.unciv.UncivGame
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.FixedPointMovement.Companion.FPM_ZERO
import com.unciv.logic.map.PathingMap.Companion.ALWAYS_LOG
import com.unciv.logic.map.PathingMap.Companion.VERBOSE_PATHFINDING_LOGS
import com.unciv.logic.map.PathingMap.Companion.EndTurnDamageLookup
import com.unciv.logic.map.PathingMap.Companion.EndSearchPredicate
import com.unciv.logic.map.PathingMap.Companion.TilePredicate
import com.unciv.logic.map.PathingMap.Companion.TileMovementCost
import com.unciv.logic.map.PathingMap.Companion.TileRoadCost
import com.unciv.logic.map.RouteNode.Companion.MAX_DAMAGING_TILES
import com.unciv.logic.map.RouteNode.Companion.MAX_TURNS
import com.unciv.logic.map.RouteNode.Companion.MAX_UNDERESTIMATED_TOTAL
import com.unciv.logic.map.RouteNode.Companion.TILE_IDX_LO_MASK
import com.unciv.logic.map.RouteNode.Companion.TILE_IDX_OFFSET
import com.unciv.logic.map.RouteNode.Companion.UNDERESTIMATED_TOTAL_HI_MASK
import com.unciv.logic.map.RouteNode.Companion.UNDERESTIMATED_TOTAL_LO_MASK
import com.unciv.logic.map.RouteNode.Companion.UNDERESTIMATED_TOTAL_OFFSET
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.utils.Log
import com.unciv.utils.LongPriorityQueue
import com.unciv.utils.forEachSetBit
import org.jetbrains.annotations.VisibleForTesting
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.InternalState
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

// This crams all the information we need about prioritizing a node into a single Long, avoiding allocations
@JvmInline
@VisibleForTesting
value class PrioritizedNode(val bits: Long) {
    constructor(node: RouteNode, underestimatedTotal: FixedPointMovement)
        : this(
        (node.bits and UNDERESTIMATED_TOTAL_HI_MASK.inv()) or
            toUnderestimatedTotalbits(underestimatedTotal)
    ) {
        require(underestimatedTotal > 0) { "underestimatedTotal $underestimatedTotal must be positive" }
        require(underestimatedTotal <= MAX_UNDERESTIMATED_TOTAL) { "underestimatedTotal $underestimatedTotal exceeds max $MAX_UNDERESTIMATED_TOTAL" }
    }

    val tileIdx: Int get() { require(initialized); return ((bits shr TILE_IDX_OFFSET) and TILE_IDX_LO_MASK).toInt() }

    val underestimatedTotal: FixedPointMovement get() {
        val b = ((bits shr UNDERESTIMATED_TOTAL_OFFSET) and UNDERESTIMATED_TOTAL_LO_MASK)
        return FixedPointMovement.fpmFromFixedPointBits(b.toInt())
    }

    val initialized: Boolean get() = bits > 0 
    
    @Readonly
    override fun toString(): String = "PrioritizedNode[underestimatedTotal=$underestimatedTotal ${RouteNode(bits)}]"

    companion object {
        @Pure
        private fun toUnderestimatedTotalbits(priority: FixedPointMovement): Long
            = priority.bits.toLong() shl UNDERESTIMATED_TOTAL_OFFSET
    }
}

@InternalState
internal class AStarPathfinder(
    private val debugId: Any,
    private val debugMapType: String,
    private val destination: Tile?,
    private val passThroughPredicate: TilePredicate,
    private val moveToPredicate: TilePredicate,
    private val endTurnDamage: EndTurnDamageLookup,
    private val cost: TileMovementCost,
    private val tileRoadCost: TileRoadCost,
    private val relationshipLevel: (Tile) -> RelationshipLevel,
    private val endSearchPredicate: EndSearchPredicate,
    internal val cache: PathingMapCache,
    private val timeLimitTurns: Int,
    private val tileMap: TileMap,
) {
    internal val routeNodes = cache.routeNodes // Actually Array<RouteNode>
    private val initialBufferSize = tileMap.tileMatrix.size + tileMap.tileMatrix[0].size
    internal val tilesInTodo: IntIntMap = IntIntMap(initialBufferSize)
    private val fpmFullMovement = cache.key.fullMove
    @Cache private var damageFreeAnchorCost = FPM_ZERO
    /**
     * Frontier priority queue for managing the tiles to be checked.
     * Tiles are ordered based on their priority, determined by the cumulative cost so far and the
     * heuristic estimate to the goal.
     */
    internal val todo = LongPriorityQueue(initialBufferSize)

    /*
     * Separate init function so that this work can occur outside 
     */
    init {
        require(timeLimitTurns > 0)
        require(timeLimitTurns < MAX_TURNS)
        // Add all the initial tiles to check to the priority queue
        cache.nodesNeedingNeighbors.forEachSetBit {
            val node = RouteNode(routeNodes[it])
            if (node.initialized && moveFromLessThanTimeLimit(node)) {
                todo.add(PrioritizedNode(node, calculateUnderestimatedMovement(node)).bits)
                tilesInTodo.put(it, node.damagingTiles)
            }
        }
    }
    
    private fun moveFromLessThanTimeLimit(node: RouteNode) = 
        node.turns < timeLimitTurns-1 || (node.turns == timeLimitTurns-1 && node.moveUsedThisTurn < fpmFullMovement)

    // Heuristics for not-yet-calculated tiles here based on distance to target        
    @Readonly
    private fun calculateUnderestimatedMovement(node: RouteNode): FixedPointMovement {
        val tile = node.tile(tileMap)
        val movementSoFar = fpmFullMovement * node.turns + node.moveUsedThisTurn.coerceAtMost(fpmFullMovement)
        val minRemainingTiles = destination?.let { tile.aerialDistanceTo(it) } ?: 1
        val minRemainingCost = tileRoadCost(tile) + (minRemainingTiles - 1) * (FASTEST_ROAD_COST)
        val underestimatedTotal = movementSoFar + minRemainingCost
        return underestimatedTotal
    }
    private fun neighborNeedsQueueing(currentNode: RouteNode, neighborTile: Tile): Boolean {
        val alreadyCalculatedNode = RouteNode(routeNodes[neighborTile.zeroBasedIndex])
        if (cache.addedNeighborNodes.get(neighborTile.zeroBasedIndex) && alreadyCalculatedNode.damagingTiles <= currentNode.damagingTiles) {
            // Note this only checks if THIS thread calculated it
            //if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
            //    Log.debug("#calculateAndQueue ${currentTile.position} ignoring ${alreadyCalculatedNode.tile(tileMap).position} because we already calculated it, for $debugMapType $debugId")
            return false
        }
        val todoWithDamage = tilesInTodo.get(neighborTile.zeroBasedIndex, Integer.MAX_VALUE)
        if (todoWithDamage <= currentNode.damagingTiles) {
            //if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
            //    Log.debug("#calculateAndQueue ${currentTile.position} ignoring ${neighborTile.position} because it's already queued, for $debugMapType $debugId")
            return false// another tile already queued a route to that neighbor. skip it.
        }
        return true        
    }
    
    private fun neighborNeedsCalcuating(currentNode: RouteNode, neighborTile: Tile): Boolean {
        val currentTile = currentNode.tile(tileMap)
        val startingPoint = cache.key.startingPoint
        val alreadyCalculatedNode = RouteNode(routeNodes[neighborTile.zeroBasedIndex])
        // If another thread already calculated the best route, then we can queue it and move on
        if (alreadyCalculatedNode.initialized && alreadyCalculatedNode.damagingTiles <= currentNode.damagingTiles) {
            if (moveFromLessThanTimeLimit(alreadyCalculatedNode))
                todo.add(PrioritizedNode(alreadyCalculatedNode, calculateUnderestimatedMovement(alreadyCalculatedNode)).bits)
            tilesInTodo.put(neighborTile.zeroBasedIndex, alreadyCalculatedNode.damagingTiles)
            cache.nodesNeedingNeighbors.set(neighborTile.zeroBasedIndex)
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#calculateAndQueue ${currentTile.position} queueing ${alreadyCalculatedNode.tile(tileMap).position} because another thread calculated, for $debugMapType $debugId")
            return false
        }
        if (!passThroughPredicate(neighborTile)) { // can't pass through.
            val noPathingNode = RouteNode.noPathingNode(neighborTile, currentNode.turns)
            routeNodes[neighborTile.zeroBasedIndex] = noPathingNode.bits
            cache.addedNeighborNodes.set(neighborTile.zeroBasedIndex)
            cache.nodesNeedingNeighbors.clear(neighborTile.zeroBasedIndex)
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#calculateAndQueue ${currentTile.position} set ${neighborTile.position} as noPathingNode because cannot move there, for $debugMapType $debugId")
            return false
        }
        return true
    }

    /**
     * Find the previous tile where stopping does not incur damage
     * 
     * Tiles that incur damage are assumed to be rare, so we save bits in the node by not storing where the previous
     * non-damaging parent is. So in the rare cases we'd end up stopping on a damaging tile, we transitively check parents
     * to find the previous tile where stopping does not incur damage.
     */
    @Readonly
    private fun findDamageFreeAnchor(startNode: RouteNode, neighborTile: Tile, firstHopCost: FixedPointMovement): RouteNode {
        val startingPoint = cache.key.startingPoint
        var ancestor = startNode
        var totalCost = firstHopCost
        while (true) {
            if (totalCost >= fpmFullMovement) {
                if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                    Log.debug("#findDamageFreeAnchor gave up short of ${neighborTile.position}: ${ancestor.tile(tileMap).position} is already $totalCost away, for $debugMapType $debugId")
                return RouteNode() // this ancestor is already too far; anything earlier is even farther
            }
            if (ancestor.canStopOn && ancestor.endTurnWithoutMoreDamage) {
                damageFreeAnchorCost = totalCost
                return ancestor
            }
            val ancestorTile = ancestor.tile(tileMap)
            val parentTile = ancestor.parentTile(tileMap)
            if (parentTile == ancestorTile) {
                if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                    Log.debug("#findDamageFreeAnchor reached the search's root looking for ${neighborTile.position} with nothing damage-free usable, for $debugMapType $debugId")
                return RouteNode() // reached the search's root with nothing usable
            }
            ancestor = RouteNode(routeNodes[parentTile.zeroBasedIndex])
            totalCost = (totalCost + cost(ancestor.tile(tileMap), ancestorTile)).coerceAtMost(RouteNode.MAX_MOVE_THIS_TURN)
        }
    }

    // This can use more than the remaining movement, but that's correct behavior.
    // https://yairm210.medium.com/multi-turn-pathfinding-7136bd0bdaf0
    private fun calculateNeighborNode(currentNode: RouteNode, neighborTile: Tile): RouteNode {
        val currentTile = currentNode.tile(tileMap)
        val startingPoint = cache.key.startingPoint
        val damagingTiles = currentNode.damagingTiles
        val cost = cost(currentTile, neighborTile).coerceAtMost(fpmFullMovement)
        val canMoveTo = currentNode.turns > 0 || moveToPredicate(neighborTile)
        val endTurnThereDamage = endTurnDamage(neighborTile).coerceAtMost(1)
        val neighborDamaging = endTurnThereDamage != 0
        val trueSafe = canMoveTo && !neighborDamaging
        val moveSinceStoppable = (currentNode.moveSinceStoppable + cost).coerceAtMost(RouteNode.MAX_MOVE_THIS_TURN)
        val newMoveSinceStoppable = if (canMoveTo) FPM_ZERO else moveSinceStoppable
        val midTurn = currentNode.moveUsedThisTurn < fpmFullMovement
        val usedSoFar = if (midTurn) currentNode.moveUsedThisTurn else FPM_ZERO
        val newUsedMovement = (usedSoFar + cost).coerceAtMost(fpmFullMovement)
        val thisTurnPassThroughOrSafeEndTurn = newUsedMovement < fpmFullMovement || trueSafe
        val relationship = relationshipLevel(neighborTile)
        if ((midTurn || currentNode.canStopOn) && thisTurnPassThroughOrSafeEndTurn) {
            // if we can move to the next tile, and then either end our turn safely or move away, then we do so.
            val turns = if (midTurn) currentNode.turns else currentNode.turns + 1
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#calculateAndQueue ${currentTile.position} queing ${neighborTile.position} for turn $turns, for $debugMapType $debugId")
            return RouteNode(neighborTile, relationship, newMoveSinceStoppable, newUsedMovement, turns, currentTile, damagingTiles, neighborDamaging)
        } else if (!canMoveTo && moveSinceStoppable < fpmFullMovement) {
            // If we could have moved here if we'd paused before entering unstoppable tiles
            // (usually allied units), pretend we paused before entering the mountains.
            // TODO: Eliminate endTurnDamage call.
            val retreatDamagingTiles = if (currentNode.canStopOn)
                    (damagingTiles + endTurnDamage(currentTile).coerceAtMost(1)).coerceAtMost(MAX_DAMAGING_TILES)
                else damagingTiles
            val retreatTurns = if (currentNode.canStopOn) currentNode.turns + 1 else currentNode.turns
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#calculateAndQueue ${currentTile.position} queing ${neighborTile.position} with retroactive pause, for $debugMapType $debugId")
            return RouteNode(neighborTile, relationship, moveSinceStoppable, moveSinceStoppable, retreatTurns, currentTile, retreatDamagingTiles, neighborDamaging)
        } else if (!canMoveTo) {
            // Even pausing as early as possible wasn't enough: it's simply not reachable this way,
            // and hopefully another tile finds a route to it later.
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#calculateAndQueue ${currentTile.position} skipping ${neighborTile.position} as unreachable, for $debugMapType $debugId")
            return RouteNode() // uninitialized -- see this function's return-type docs
        }

        // canMoveTo is true, so we CAN stop here -- but it's damaging, and we've run out of budget
        // to push past it. Before accepting the damage, look backward for a nearer damage-free
        // anchor to retroactively pause at instead, taking no damage at all.
        val damageFreeAnchor = findDamageFreeAnchor(currentNode, neighborTile, cost)
        if (damageFreeAnchor.initialized) {
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#calculateAndQueue ${currentTile.position} queing ${neighborTile.position} with retroactive pause before mountains, for $debugMapType $debugId")
            return RouteNode(neighborTile, relationship, FPM_ZERO, damageFreeAnchorCost, damageFreeAnchor.turns + 1, currentTile, damageFreeAnchor.damagingTiles, neighborDamaging)
        } else {
            // Ending our turn here takes damage. We'll add the neighbor tile, but the damage
            // means its neighbors will be calculated at a super low priority. In the meantime, another
            // tile might find a route here that doesn't require taking damage, which is the ONLY
            // scenario where a tile can get recalculated.
            val newDamageTiles = (damagingTiles + endTurnThereDamage).coerceAtMost(MAX_DAMAGING_TILES)
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#calculateAndQueue ${currentTile.position} queing ${neighborTile.position} with taking damage, for $debugMapType $debugId ($canMoveTo)")
            return RouteNode(neighborTile, relationship, FPM_ZERO, cost, currentNode.turns + 1, currentTile, newDamageTiles, neighborDamaging)
        }
    }

    private fun considerNeighbor(
        currentNode: RouteNode,
        neighborTile: Tile
    ): Tile? {
        val alreadyCalculatedNode = RouteNode(routeNodes[neighborTile.zeroBasedIndex])
        if (!neighborNeedsQueueing(currentNode, neighborTile)) return null
        val neighborNode =
            if (!neighborNeedsCalcuating(currentNode, neighborTile)) {
                RouteNode(routeNodes[neighborTile.zeroBasedIndex])
            } else {
                val newNode = calculateNeighborNode(currentNode, neighborTile)
                if (!newNode.initialized) return null
                routeNodes[neighborTile.zeroBasedIndex] = newNode.bits
                if (moveFromLessThanTimeLimit(newNode))
                    todo.add(PrioritizedNode(newNode, calculateUnderestimatedMovement(newNode)).bits)
                tilesInTodo.put(neighborTile.zeroBasedIndex, newNode.damagingTiles)
                cache.nodesNeedingNeighbors.set(neighborTile.zeroBasedIndex)
                newNode
            }
        if (!alreadyCalculatedNode.initialized && endSearchPredicate(neighborTile, neighborNode))
            return neighborTile
        return null
    }

    internal fun stepUntilDestination(): Tile? {
        val startTile = tileMap[cache.key.startingPoint]
        if (endSearchPredicate(startTile, RouteNode(routeNodes[startTile.zeroBasedIndex]))) return startTile
        while (todo.isNotEmpty()) {
            val currentPrioritizedNode = PrioritizedNode(todo.poll())
            val currentNode = RouteNode(routeNodes[currentPrioritizedNode.tileIdx])
            val currentTile = currentNode.tile(tileMap)
            for (neighborTile in currentTile.neighbors) { // calculate each neighbor       
                val foundTargetTile = considerNeighbor(currentNode, neighborTile)
                if (foundTargetTile != null) return foundTargetTile
            }
            // mark this tile as having its neighbors added
            tilesInTodo.remove(currentTile.zeroBasedIndex, 0)
            cache.nodesNeedingNeighbors.clear(currentTile.zeroBasedIndex)
            cache.addedNeighborNodes.set(currentTile.zeroBasedIndex)
            // if we reached the destination, (or if another thread did), then we stop
            if (destination != null && RouteNode(routeNodes[destination.zeroBasedIndex]).initialized)
                return destination
        }
        return null
    }

    override fun toString() = "${javaClass.simpleName}[debugMapType=$debugMapType debugId=$debugId]"
    
    @VisibleForTesting
    @Suppress("unused")
    fun cacheToDebugString() = cache.toDebugString(UncivGame.Current.gameInfo!!.tileMap, destination)

    @VisibleForTesting
    @Suppress("unused")
    fun queueToDebugString() = buildString { todo.forEach { append(PrioritizedNode(it)).append('\n') } }

    companion object {
        // Setting this higher than the fastest speed (railroads at 0.1f) will cause the pathfinding
        // to execute significantly faster, but it may miss optimal paths that use railroads way off
        // to the side. Additionally, it will cause subsequent pathfinding to bias *very* strongly
        // towards the earlier pathfinding, potentially causing it to miss even obvious railroads
        // for later paths.  If we eliminate the caching, then it would be safe to set this higher.
        const val FASTEST_ROAD_COST = 0.1f
        
        init {
            require(FASTEST_ROAD_COST == RoadStatus.Railroad.movementImproved)
        }
    }
}
