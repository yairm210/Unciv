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

    // This can use more than the remaining movement, but that's correct behavior.
    // https://yairm210.medium.com/multi-turn-pathfinding-7136bd0bdaf0
    private fun calculateNeighborNode(currentNode: RouteNode, neighborTile: Tile): RouteNode? {
        val currentTile = currentNode.tile(tileMap)
        val startingPoint = cache.key.startingPoint
        val damagingTiles = currentNode.damagingTiles
        val cost = cost(currentTile, neighborTile).coerceAtMost(fpmFullMovement)
        val newUsedMovement = (currentNode.moveUsedThisTurn + cost).coerceAtMost(fpmFullMovement)
        val canMoveTo = currentNode.turns > 0 || moveToPredicate(neighborTile)
        val endTurnThereDamage = endTurnDamage(neighborTile).coerceAtMost(1)
        val newMountainMovement = when {
                currentNode.endTurnWithoutMoreDamage && endTurnThereDamage > 0 -> cost // first entering mountains
                !currentNode.endTurnWithoutMoreDamage && endTurnThereDamage > 0 -> currentNode.pbmMoveThisTurn + cost
                else ->FPM_ZERO // ignored in this case
            }.coerceAtMost(fpmFullMovement)
        val thisTurnPassThroughOrSafeEndTurn = (newUsedMovement < fpmFullMovement) || (canMoveTo && endTurnThereDamage == 0)
        val nextTurnPassThroughOrEndTurn = (newUsedMovement < fpmFullMovement) || canMoveTo
        val relationship = relationshipLevel(neighborTile)

        fun log(verb: String, context: String) {
            if (VERBOSE_PATHFINDING_LOGS != startingPoint && VERBOSE_PATHFINDING_LOGS != ALWAYS_LOG) return
            // Use printf style formatting, not kotlin templates, late vs. early, potentially wasted evaluation
            Log.debug(
                "#calculateAndQueue %s %s %s %s, for %s %s (%s)",
                currentTile.position, verb, neighborTile.position, context,
                debugMapType, debugId, canMoveTo
            )
        }
        fun newNode(
            pauseBeforeMountainMove: FixedPointMovement, moveThisTurn: FixedPointMovement,
            turnDelta: Int, newDamagingTiles: Int = damagingTiles
        ) = RouteNode(
            neighborTile, relationship, pauseBeforeMountainMove, moveThisTurn,
            currentNode.turns + turnDelta,  currentTile, canMoveTo, newDamagingTiles
        )
        if (currentNode.moveUsedThisTurn < fpmFullMovement  && thisTurnPassThroughOrSafeEndTurn) {
            // if we can move to the next tile, and then either end our turn safely or move away, then we do so.
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                log("queuing", "for same turn")
            return newNode(newMountainMovement, newUsedMovement, 0)
        } else if (currentNode.endTurnWithoutMoreDamage && currentNode.canMoveTo && nextTurnPassThroughOrEndTurn) {
            // If we can safely end our turn on the current tile, and then either end our turn or move away, then we do so.
            log("queuing", "for next turn")
            return newNode(newMountainMovement, cost, 1)
        } else if (currentNode.endTurnWithoutMoreDamage && currentNode.canMoveTo && !canMoveTo) {
            // Cannot end our turn on the next tile, nor pass through it. Possibly because it's occupied by a unit.
            // Classic #getDistanceToTiles requires we populate the node, so populate it, but do not return it.
            log("stubbing", "as occupied")
            val occupiedNode = newNode(newMountainMovement, cost, 1)
            routeNodes[neighborTile.zeroBasedIndex] = occupiedNode.bits
            cache.addedNeighborNodes.clear(neighborTile.zeroBasedIndex)
            return null
        } else if (currentNode.pbmMoveThisTurn < fpmFullMovement) {
            // If we could have moved here if we'd paused before entering mountains, then
            // pretend we paused before entering the mountains.
            log("queuing", "with retroactive pause before mountains")
            return newNode(newMountainMovement, newMountainMovement, 1)
        } else {
            // Ending our turn here takes damage. We'll add the neighbor tile, but the damage
            // means its neighbors will be calculated at a super low priority.
            // In the meantime, another tile might find a route here that doesn't require taking damage,
            // which is the ONLY scenario where a tile can get recalculated.
            val newDamageTiles = (damagingTiles + endTurnThereDamage).coerceAtMost(RouteNode.MAX_DAMAGING_TILES)
            log("queuing", "with taking damage")
            return newNode(cost, cost, 1, newDamageTiles)
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
                val newNode = calculateNeighborNode(currentNode, neighborTile) ?: return null // calculate each neighbor
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
