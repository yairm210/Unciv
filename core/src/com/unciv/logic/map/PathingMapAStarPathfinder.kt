package com.unciv.logic.map

import com.badlogic.gdx.utils.IntIntMap
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.PathingMap.Companion.ALWAYS_LOG
import com.unciv.logic.map.PathingMap.Companion.EndTurnDamageLookup
import com.unciv.logic.map.PathingMap.Companion.MoveThroughPredicate
import com.unciv.logic.map.PathingMap.Companion.TileMovementCost
import com.unciv.logic.map.PathingMap.Companion.TileRoadCost
import com.unciv.logic.map.PathingMap.Companion.VERBOSE_PATHFINDING_LOGS
import com.unciv.logic.map.tile.Tile
import com.unciv.utils.Log
import com.unciv.utils.forEachSetBit
import org.jetbrains.annotations.VisibleForTesting
import yairm210.purity.annotations.InternalState
import yairm210.purity.annotations.Readonly
import java.util.PriorityQueue


class PrioritizedNode(
    val node: RouteNode,
    val underestimatedTotal: Float) {
    @Readonly
    override fun toString() = "$node underestimatedTotal=$underestimatedTotal"
    
    companion object {
        val COMPARATOR: Comparator<PrioritizedNode> = Comparator<PrioritizedNode> { a, b ->
            if (a.node.damageTaken != b.node.damageTaken)
                return@Comparator a.node.damageTaken.compareTo(b.node.damageTaken)
            if (a.underestimatedTotal != b.underestimatedTotal)
                return@Comparator a.underestimatedTotal.compareTo(b.underestimatedTotal)
            if (a.node.turns != b.node.turns)
                return@Comparator a.node.turns.compareTo(b.node.turns)
            if (a.node.pauseBeforeMountainMoveThisTurn != b.node.pauseBeforeMountainMoveThisTurn)
                return@Comparator a.node.pauseBeforeMountainMoveThisTurn.compareTo(b.node.pauseBeforeMountainMoveThisTurn)
            if (a.node.moveUsedThisTurn != b.node.moveUsedThisTurn)
                return@Comparator a.node.moveUsedThisTurn.compareTo(b.node.moveUsedThisTurn)
            // b.compareTo a, because "higher" relationshipLevels, like Ally, should come first
            if (a.node.relationshipLevel != b.node.relationshipLevel)
                return@Comparator b.node.relationshipLevel.ordinal.compareTo(a.node.relationshipLevel.ordinal)
            // All other things being equal, use tiles closest to the center as a completely 
            // arbitrary tiebreaker. This ensures that all priorities are unique, removing
            // "randomness" from the results.
            return@Comparator a.node.tile.zeroBasedIndex.compareTo(b.node.tile.zeroBasedIndex)
        }
    }
}

@InternalState
internal class AStarPathfinder(
    private val debugId: Any,
    private val debugMapType: String,
    private val destination: Tile?,
    private val moveThroughPredicate: MoveThroughPredicate,
    private val endTurnDamage: EndTurnDamageLookup,
    private val cost: TileMovementCost,
    private val tileRoadCost: TileRoadCost,
    private val relationshipLevel: (Tile) -> RelationshipLevel,
    internal val cache: PathingMapCache,
    private val timeLimitTurns: Int,
    private val tileMap: TileMap,
) {
    internal val routeNodes: Array<RouteNode?> = cache.routeNodes
    private val initialBufferSize = tileMap.tileMatrix.size + tileMap.tileMatrix[0].size
    internal val tilesInTodo: IntIntMap = IntIntMap(initialBufferSize)

    /**
     * Frontier priority queue for managing the tiles to be checked.
     * Tiles are ordered based on their priority, determined by the cumulative cost so far and the
     * heuristic estimate to the goal.
     */
    internal val todo = PriorityQueue(initialBufferSize, PrioritizedNode.COMPARATOR)
    

    /*
     * Separate init function so that this work can occur outside 
     */
    init {
        // Add all the initial tiles to check to the priority queue
        cache.nodesNeedingNeighbors.forEachSetBit {
            val node = routeNodes[it]
            if (node != null && node.turns <= timeLimitTurns) {
                todo.add(PrioritizedNode(node, calculateUnderestimatedMovement(node)))
                tilesInTodo.put(it, node.damageTaken)
            }
        }
    }

    // Heuristics for not-yet-calculated tiles here based on distance to target        
    @Readonly
    private fun calculateUnderestimatedMovement(node: RouteNode): Float {
        val movementSoFar = node.turns * cache.key.fullMove + node.moveUsedThisTurn
        val minRemainingTiles = destination?.let { node.tile.aerialDistanceTo(it) } ?: 1
        val minRemainingCost = tileRoadCost(node.tile) + (minRemainingTiles - 1) * (FASTEST_ROAD_COST)
        val underestimatedTotal = movementSoFar + minRemainingCost
        return underestimatedTotal
    }
    
    private fun neighborNeedsCalcuating(currentNode: RouteNode, neighborTile: Tile): Boolean {
        val startingPoint = cache.key.startingPoint
        val alreadyCalculatedNode = routeNodes[neighborTile.zeroBasedIndex]
        if (cache.addedNeighborNodes.get(neighborTile.zeroBasedIndex) && alreadyCalculatedNode!!.damageTaken <= currentNode.damageTaken) {
            // Note this only checks if THIS thread calculated it
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#calculateAndQueue ${currentNode.tile.position} ignoring ${alreadyCalculatedNode.tile.position} because we already calculated it, for $debugMapType $debugId")
            return false
        }
        val todoWithDamage = tilesInTodo.get(neighborTile.zeroBasedIndex, Integer.MAX_VALUE)
        if (todoWithDamage <= currentNode.damageTaken) {
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#calculateAndQueue ${currentNode.tile.position} ignoring ${neighborTile.position} because it's already queued, for $debugMapType $debugId")
            return false// another tile already queued a route to that neighbor. skip it.
        }
        // If another thread already calculated the best route, then we can queue it and move on
        if (alreadyCalculatedNode != null && alreadyCalculatedNode.damageTaken <= currentNode.damageTaken) {
            if (alreadyCalculatedNode.turns < timeLimitTurns)
                todo.add(PrioritizedNode( alreadyCalculatedNode,calculateUnderestimatedMovement(alreadyCalculatedNode)))
            tilesInTodo.put(neighborTile.zeroBasedIndex, alreadyCalculatedNode.damageTaken)
            cache.nodesNeedingNeighbors.set(neighborTile.zeroBasedIndex)
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#calculateAndQueue ${currentNode.tile.position} queueing ${alreadyCalculatedNode.tile.position} because another thread calculated, for $debugMapType $debugId")
            return false
        }
        if (!moveThroughPredicate(neighborTile)) { // can't move here.
            val noPathingNode = RouteNode.noPathingNode(neighborTile)
            routeNodes[neighborTile.zeroBasedIndex] = noPathingNode
            cache.addedNeighborNodes.set(neighborTile.zeroBasedIndex)
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#calculateAndQueue ${currentNode.tile.position} set ${noPathingNode.tile.position} as noPathingNode because cannot move there, for $debugMapType $debugId")
            return false
        }
        return true
    }

    private fun calculateNeighborNode(currentNode: RouteNode, neighborTile: Tile): RouteNode {
        val startingPoint = cache.key.startingPoint
        val damageTaken = currentNode.damageTaken
        val roundedFullMovement = cache.key.fullMove - Constants.minimumMovementEpsilon
        val cost = cost(currentNode.tile, neighborTile)
        val newUsedMovement = currentNode.moveUsedThisTurn + cost
        val endTurnThereDamage = endTurnDamage(neighborTile)
        val newMountainMovement = 
            if (currentNode.endTurnWithoutMoreDamage && endTurnThereDamage > 0) cost // first entering mountains
            else if (!currentNode.endTurnWithoutMoreDamage && endTurnThereDamage > 0) currentNode.pauseBeforeMountainMoveThisTurn + cost
            else 0f // ignored in this case
        val canEndTurnOrProbablyMoveAway = endTurnThereDamage == 0 || (newUsedMovement < roundedFullMovement)
        val relationship = relationshipLevel(neighborTile)
        
        val newNode: RouteNode
        // This can use more than the remaining movement, but that's correct behavior.
        // https://yairm210.medium.com/multi-turn-pathfinding-7136bd0bdaf0
        if (currentNode.moveUsedThisTurn < roundedFullMovement  && canEndTurnOrProbablyMoveAway) {
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#calculateAndQueue ${currentNode.tile.position} queing ${neighborTile.position} for same turn, for $debugMapType $debugId")
            return RouteNode(neighborTile, relationship, newMountainMovement, newUsedMovement, currentNode.turns, currentNode.tile, damageTaken)
        } else if (currentNode.endTurnWithoutMoreDamage && canEndTurnOrProbablyMoveAway) {
            // We have to move there next turn.
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#calculateAndQueue ${currentNode.tile.position} queing ${neighborTile.position} for next turn, for $debugMapType $debugId")
            return RouteNode( neighborTile,  relationship, newMountainMovement,  cost, currentNode.turns + 1, currentNode.tile, damageTaken)
        } else if (currentNode.pauseBeforeMountainMoveThisTurn < roundedFullMovement) {
            // If we could have moved here if we'd paused before entering mountains, then
            // pretend we paused before entering the mountains.
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#calculateAndQueue ${currentNode.tile.position} queing ${neighborTile.position} with retroactive pause before mountains, for $debugMapType $debugId")
            return RouteNode(neighborTile, relationship, newMountainMovement, newMountainMovement, currentNode.turns + 1, currentNode.tile, damageTaken)
        } else {
            // Ending our turn here takes damage. We'll add the neighbor tile, but the damage
            // means it's neighbors will be calculated at a super low priority.
            // In the meantime, another tile might find a route here that doesn't require taking damage,
            // which is the ONLY scenario where a tile can get recalculated.
            if (VERBOSE_PATHFINDING_LOGS == startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#calculateAndQueue ${currentNode.tile.position} queing ${neighborTile.position} with taking damage, for $debugMapType $debugId")
            return RouteNode(neighborTile, relationship, cost, cost, currentNode.turns + 1, currentNode.tile, damageTaken + endTurnThereDamage)
        }
    }

    // returns target node. If timed out 
    internal fun stepUntilDestination() {
        while (true) {
            val currentPriority = todo.poll() ?: return
            val currentNode = currentPriority.node
            for (neighborTile in currentNode.tile.neighbors) {
                if (!neighborNeedsCalcuating(currentNode, neighborTile)) continue
                val newNode = calculateNeighborNode(currentNode, neighborTile) // calculate each neighbor
                routeNodes[neighborTile.zeroBasedIndex] = newNode
                if (newNode.turns < timeLimitTurns)
                    todo.add(PrioritizedNode(newNode, calculateUnderestimatedMovement(newNode)))
                tilesInTodo.put(neighborTile.zeroBasedIndex, newNode.damageTaken)
                cache.nodesNeedingNeighbors.set(neighborTile.zeroBasedIndex)
            }
            // mark this tile as having its neighbors added
            tilesInTodo.remove(currentNode.tile.zeroBasedIndex, 0)
            cache.nodesNeedingNeighbors.clear(currentNode.tile.zeroBasedIndex)
            cache.addedNeighborNodes.set(currentNode.tile.zeroBasedIndex)
            // if we reached the destination, (or if another thread did), then we stop
            if (destination != null && routeNodes[destination.zeroBasedIndex] != null) 
                return
        }
    }

    override fun toString() = "${javaClass.simpleName}[debugMapType=$debugMapType debugId=$debugId]"
    
    @VisibleForTesting
    @Suppress("unused")
    fun toDebugString() = cache.toDebugString(UncivGame.Current.gameInfo!!.tileMap, destination)

    companion object {
        // Setting this higher than the fastest speed (railroads at 0.1f) will cause the pathfinding
        // to execute significantly faster, but it may miss optimal paths that use railroads way off
        // to the side. Additionally, it will cause subsequent pathfinding to bias *very* strongly
        // towards the earlier pathfinding, potentially causing it to miss even obvious railroads
        // for later paths.  If we eliminate the caching, then it would be safe to set this higher.
        const val FASTEST_ROAD_COST = 0.1f
    }
}
