package com.unciv.logic.map

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.FixedPointMovement.Companion.FPM_ONE
import com.unciv.logic.map.FixedPointMovement.Companion.FPM_POINT_FIVE
import com.unciv.logic.map.FixedPointMovement.Companion.FPM_ZERO
import com.unciv.logic.map.FixedPointMovement.Companion.fpmFromMovement
import com.unciv.logic.map.MapPathing.roadPreferredMovementCost
import com.unciv.logic.map.RouteNode.Companion.MAX_MOVE_THIS_TURN
import com.unciv.logic.map.RouteNode.Companion.MAX_TURNS
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.movement.MovementCost
import com.unciv.logic.map.mapunit.movement.PathsToTilesWithinTurn
import com.unciv.logic.map.mapunit.movement.UnitMovement.ParentTileAndTotalMovement
import com.unciv.logic.map.tile.Tile
import com.unciv.utils.Log
import com.unciv.utils.LongPriorityQueue
import com.unciv.utils.forEachSetBit
import org.jetbrains.annotations.VisibleForTesting
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.InternalState
import yairm210.purity.annotations.Readonly
import java.util.BitSet
import java.util.Formatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * PathingMap is a class that coordinates the pathing caches and calculations.
 *
 * For the most part, all this class really does is manage the PathingMapCache automatically.
 * When pathing is requested, it builds an AStarPathfinder, which does the actual pathfinding, and
 * then the PathingMap merges the results back into its PathingMapCache. 
 * 
 * To calculate with different moveThroughPredicate, cost, or other conditions would invalidate the
 * cached "Node"s, so require a separate PathingMap.
 * 
 * The AStarPathfinder algorithm is uesd here because each tile is calculated exactly once, and the
 * resulting "Node" data can be reused for all subsequent pathfinding, even when pathfinding to
 * different targets.  This introduces a *very* minor bias in later paths toward earlier paths, but
 * only among routes of otherwise equal priority.
 * 
 * This completely replaces UnitMovement#getMovementToTilesAtPosition, UnitMovement#getShortestPath,
 * UnitMovement#getDistanceToTiles, AStar, MapPathing#getPath, MapPathing#getConnection, and 
 * MapPathing#MapPathing.getRoadPath.
 * 
 * Future plans:
 * - createCityExpansionPathing
 *   - Useful for UseGoldAutomation.maybeBuyCityTiles
 * - Multiple start points.
 *   - Today, MotivationToAttackAutomation#getAttackPathsModifier does a separate BFS from each
 *     city. Merging them would give a performance boost.
 * - Multiple target points.
 *   - Today, MotivationToAttackAutomation#getAttackPathsModifier does a separate BFS to each
 *     city. Merging them would give a performance boost.
 *   - RoadBetweenCitiesAutomation#getRoadToConnectCityToCapital could just path from the capital
 *     to each city, and tiles already connected would simply have a "cost" of zero.
 * - Add #getAttackableEnemies.
 *   - Right now TargetHelper#getAttackableEnemies is rediculously inefficient. For each tile we can
 *     possibly attack from, for each tile in attack range, does it contain an enemy.  For long
 *     range units like Rocket Artillery, surrounded by roads, this causes most tiles to be analyzed
 *     37+ times. Instead, this class could ~BFS to (movement-1) + (range tiles), do a linear scan
 *     for enemies, and return the path to each enemy. Or similar.    
 */
@InternalState
class PathingMap(
    private val tileMap: TileMap,
    private val debugId: Any,
    private val debugMapType: String,
    private val getCurrentCacheKey: () -> PathingMapCacheKey,
    private val moveThroughPredicate: MoveThroughPredicate,
    private val endTurnDamage: EndTurnDamageLookup,
    private val cost: TileMovementCost,
    private val tileRoadCost: TileRoadCost,
    private val relationshipLevel: (Tile) -> RelationshipLevel,
) {
    @Cache
    private val cacheRef: AtomicReference<PathingMapCache?> = AtomicReference<PathingMapCache?>(null)

    /**
     * This is the only method that is NOT thread-safe.
     */
    @Suppress("purity")
    fun clear() {
        val cache = cacheRef.get()
        if (cache != null && VERBOSE_PATHFINDING_LOGS == cache.key.startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
            Log.debug("#clear explicitly dumping caches for $debugMapType $debugId")
        cacheRef.set(null)
    }
    
    @Suppress("purity")
    private fun fetchCache(): PathingMapCache {
        val latestKey = getCurrentCacheKey()
        val oldCache = cacheRef.get()
        if (oldCache?.isCacheValid(latestKey) == true) {
            if (VERBOSE_PATHFINDING_LOGS == oldCache.key.startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#fetchCache() reusing existing $debugMapType $debugId cache $oldCache because keys are unchanged")
            return oldCache  // if the cache is still valid, keep using it
        } else if (oldCache != null) {
            if (VERBOSE_PATHFINDING_LOGS == oldCache.key.startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#fetchCache() dumping cache $debugMapType $debugId $oldCache because $latestKey does not match")
            cacheRef.set(null) // if the cache is invalid, dump it
        }
        val newCache = PathingMapCache(latestKey, tileMap) // otherwise, make a new cache
        val movementUsedThisTurn = (latestKey.fullMove - latestKey.moveRemaining).coerceIn(FPM_ZERO, MAX_MOVE_THIS_TURN)
        val tile = tileMap[latestKey.startingPoint]
        val root = RouteNode.rootNode(tile, movementUsedThisTurn)
        newCache.routeNodes[tile.zeroBasedIndex] = root.bits
        newCache.nodesNeedingNeighbors[tile.zeroBasedIndex] = true
        // compareAndSet, in case another thread tried to initialize in parallel
        // get the value that the other thread set
        return if (cacheRef.compareAndSet(null, newCache)) {
            if (VERBOSE_PATHFINDING_LOGS == latestKey.startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#fetchCache() initialized new cache $debugMapType $debugId $newCache")
            newCache
        } else {
            if (VERBOSE_PATHFINDING_LOGS == latestKey.startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#fetchCache() retrying cache fetch due to data race initializing cache $newCache")
            fetchCache()
        }        
    }


    /**
     * Constructs a sequence representing the path from the given destination tile back to the starting point.
     * If the destination has not been reached, the sequence will be empty.
     * 
     * WARNING: If the path being analyzed exceeds maxTurns, then this returns a path of length 
     * maxTurns towards the destination, but the path will not actually reach the destination.
     *
     * @param destination The destination tile to trace the path to.
     * @return A sequence of tiles representing the path from the destination to the starting point.
     */
    @Readonly
    @Suppress("purity")
    fun getShortestPath(destination: Tile, maxTurns: Int = MAX_VALID_TURNS): List<Tile>? {
        val cache = fetchCache()
        if (destination.position == cache.key.startingPoint) {
            if (VERBOSE_PATHFINDING_LOGS == cache.key.startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#getShortestPath returning startingPoint cache.key.startingPoint for $debugMapType $debugId")
            return listOf(destination)
        } else if (cache.key.moveRemaining <= 0) {
            if (VERBOSE_PATHFINDING_LOGS == cache.key.startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#getShortestPath emulating no-movement-pathing bug to $destination for $debugMapType $debugId")
            return listOf()
            
        }
        // if we don't already know the shortest path, and might yet find it, search
        var targetNode = RouteNode(cache.routeNodes[destination.zeroBasedIndex])
        if (!targetNode.initialized  && !cache.nodesNeedingNeighbors.isEmpty) {
            if (VERBOSE_PATHFINDING_LOGS == cache.key.startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#getShortestPath(${destination.position}) calculcating for $debugMapType $debugId")
            stepUntilDestination(cache, destination, maxTurns)
        }
        val bestTarget =  RouteNode(cache.routeNodes[destination.zeroBasedIndex])
        // if the target is not reachable within maxTurns, return nothing
        if (!bestTarget.initialized || bestTarget.isNoPathingNode) {
            if (VERBOSE_PATHFINDING_LOGS == cache.key.startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#getShortestPath returning no path to $destination for $debugMapType $debugId")
            return null
        }
        val result = pathAsList(bestTarget, cache)
        if (VERBOSE_PATHFINDING_LOGS == cache.key.startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
            Log.debug("#getShortestPath returning ${result.map{it.position}} to $destination for $debugMapType $debugId")
        return result
    }

    private fun pathAsList(targetNode: RouteNode, cache: PathingMapCache): MutableList<Tile> {
        // Now routeNodes has the shortest route, so we extract it into a list and return
        var currentNode = targetNode
        val result = mutableListOf(currentNode.tile(tileMap))
        var turns = currentNode.turns
        while (true) {
            val parentTile = currentNode.parentTile(tileMap)
            val parentNode = RouteNode(cache.routeNodes[parentTile.zeroBasedIndex])
            if (parentTile.position == cache.key.startingPoint) break
            if (parentNode.turns < turns && parentNode.endTurnWithoutMoreDamage) {
                result.add(parentTile)
                turns = parentNode.turns
            }
            currentNode = parentNode
        }
        return result.asReversed()
    }


    /**
     * Gets the tiles the unit could move to with remaining movement this turn.
     * Does not consider if tiles can actually be entered, use canMoveTo for that.
     * If a tile can be reached within the turn, but it cannot be passed through, the total distance to it is set to unitMovement
     */
    @Readonly
    @Suppress("purity")
    fun getMovementToTilesAtPosition(): PathsToTilesWithinTurn {
        val cache = fetchCache()
        val tilesSameTurn = cache.tilesSameTurn
        if (tilesSameTurn.isNotEmpty()) {
            if (VERBOSE_PATHFINDING_LOGS == cache.key.startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#getMovementToTilesAtPosition returning cached tilesSameTurn[len=${tilesSameTurn.size}] for $debugMapType $debugId")
            return tilesSameTurn
        } 
        // if we've already calculated the results, return that
        // otherwise, if there's uncalculated nodes, step until maxTurns is reached
        if (!cache.nodesNeedingNeighbors.isEmpty) {
            if (VERBOSE_PATHFINDING_LOGS == cache.key.startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                Log.debug("#getMovementToTilesAtPosition calculcating for $debugMapType $debugId")
            stepUntilDestination(cache, null, 1)
        }
        getTilesSameTurn(cache)
        return tilesSameTurn
    }

    private fun getTilesSameTurn(cache: PathingMapCache) {
        val tilesSameTurn = cache.tilesSameTurn
        // accumulate all the results
        synchronized(tilesSameTurn) {
            if (tilesSameTurn.isNotEmpty()) {
                if (VERBOSE_PATHFINDING_LOGS == cache.key.startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
                    Log.debug("#getMovementToTilesAtPosition returning cached tilesSameTurn[len=${tilesSameTurn.size}] which was calculated by another thread for $debugMapType $debugId")
                return
            } // if we've already calculated the results, return that
            cache.addedNeighborNodes.forEachSetBit {
                val node = RouteNode(cache.routeNodes[it])
                if (node.initialized && node.turns == 0) {
                    val tile = node.tile(tileMap)
                    tilesSameTurn[tile] =
                        ParentTileAndTotalMovement(
                            tile,
                            node.parentTile(tileMap), node.moveUsedThisTurn.toFloat())
                }
            }
            cache.nodesNeedingNeighbors.forEachSetBit {
                val node = RouteNode(cache.routeNodes[it])
                if (node.initialized && node.turns == 0) {
                    val tile = node.tile(tileMap)
                    tilesSameTurn[tile] =
                        ParentTileAndTotalMovement(tile, node.parentTile(tileMap), node.moveUsedThisTurn.toFloat())
                }
            }
        }
        if (VERBOSE_PATHFINDING_LOGS == cache.key.startingPoint || VERBOSE_PATHFINDING_LOGS == ALWAYS_LOG)
            Log.debug("#getMovementToTilesAtPosition calculcated tilesSameTurn=${tilesSameTurn.map {it.key.position}} for $debugMapType $debugId")
    }

    @VisibleForTesting
    @Readonly
    fun getCachedNode(tile: Tile): RouteNode {
        return RouteNode(cacheRef.get()?.routeNodes[tile.zeroBasedIndex] ?: 0L)
    }

    /**
     * Use a AStarPathfinder instance to calculate the route, with thread-safe way
     **/
    private fun stepUntilDestination(cache: PathingMapCache, destination: Tile?, timeLimitTurns: Int) {
        val finder = AStarPathfinder(
            debugId,
            debugMapType,
            destination,
            moveThroughPredicate,
            endTurnDamage,
            cost,
            tileRoadCost,
            relationshipLevel,
            cache.forkForPathfinding(),
            timeLimitTurns.coerceAtMost(MAX_VALID_TURNS),
            tileMap,
        )
        finder.stepUntilDestination()

        // now merge the pathfinder's tilesChecked and tilesToCheck back into the shared PathingData
        // again using a synchronized block not just for thread-safety, but also to ensure atomicity
        cache.mergePathfindingFork(finder.cache)
    }
    
    override fun toString(): String {
        val cache = cacheRef.get()
        return "${javaClass.simpleName}[debugMapType=$debugMapType debugId=$debugId key=${cache?.key}]"
    }

    fun toDebugString(destination:Tile?=null) = fetchCache().toDebugString(tileMap, destination)

    companion object {
        const val MAX_VALID_TURNS = RouteNode.MAX_VALID_TURNS
        
        // Functional interfaces used here to prevent Kotlin from Boxing the return values
        @FunctionalInterface
        fun interface MoveThroughPredicate {
            @Readonly
            operator fun invoke(it: Tile): Boolean
        }
        @FunctionalInterface
        fun interface EndTurnDamageLookup {
            @Readonly
            operator fun invoke(it: Tile): Int
        }
        @FunctionalInterface
        fun interface TileMovementCost {
            @Readonly
            operator fun invoke(from: Tile, to: Tile): FixedPointMovement
        }
        @FunctionalInterface
        fun interface TileRoadCost {
            @Readonly
            operator fun invoke(it: Tile): FixedPointMovement
        }

        @Suppress("unused")
        internal val ALWAYS_LOG: HexCoord = HexCoord(0xFFFF,0xFFFE)
        @Suppress("unused")
        @VisibleForTesting
        val NEVER_LOG: HexCoord = HexCoord(0xFFFF,0xFFFF)
        // can temporarily set this to a unit, civ, or tile position, to enable verbose logging for that thing's pathfinding
        @VisibleForTesting
        val VERBOSE_PATHFINDING_LOGS: HexCoord = NEVER_LOG
        
        @Readonly
        fun createUnitPathingMap(unit: MapUnit, considerZoneOfControl: Boolean = true, includeEscortUnit: Boolean = true): PathingMap {
            val name = if (!considerZoneOfControl) "createUnitPathingMapNoZoc"
                else if (!includeEscortUnit) "createUnitPathingMapNoEscort"
                else "createUnitPathingMap"
            // These two precalculated because for some reason they're rediculously slow
            val selfFullMove = unit.getMaxMovement()
            val otherUntilFullMove = if (includeEscortUnit) unit.getOtherEscortUnit()?.getMaxMovement() ?: MAX_TURNS else MAX_TURNS
            val getCurrentCacheKey = {
                val escort = if (includeEscortUnit && unit.isEscorting()) unit.getOtherEscortUnit() else null
                PathingMapCacheKey(
                    unit.currentTile.position, 
                    fpmFromMovement(unit.currentMovement).coerceAtMost(escort?.currentMovement?.toFixedPointMove() ?: MAX_MOVE_THIS_TURN),
                    selfFullMove.coerceAtMost(if (escort != null) otherUntilFullMove else MAX_VALID_TURNS),
                    )
            }
            return PathingMap(
                unit.currentTile.tileMap,
                unit,
                name,
                getCurrentCacheKey,
                { unit.movement.canPassThrough(it)  },
                { unit.getDamageFromTerrain(it) },
                { from, to -> fpmFromMovement(MovementCost.getMovementCostBetweenAdjacentTilesEscort(unit, from, to, considerZoneOfControl, includeEscortUnit)) },
                { fpmFromMovement(it.getConnectionStatus(unit.civ).movement) },
                { tile -> tile.getOwner()?.getDiplomacyManager(unit.civ)?.relationshipIgnoreAfraid() ?: RelationshipLevel.Favorable }
            )
        }

        @Readonly
        fun createLandAttackPathingMap(civ: Civilization, startingPoint: Tile, targetCiv: Civilization): PathingMap {
            return PathingMap(
                civ.gameInfo.tileMap,
                civ,
                "createLandAttackPathingMap",
                { civPathingCacheKey(startingPoint.position)},
                { isLandTileCanAttackThrough(civ, it, targetCiv) },
                { 0 },
                { from, to -> fpmFromMovement(roadPreferredMovementCost(civ, from, to)) },
                { fpmFromMovement(it.getConnectionStatus(civ).movement) },
                { tile -> tile.getOwner()?.getDiplomacyManager(civ)?.relationshipIgnoreAfraid() ?: RelationshipLevel.Favorable }
            )
        }

        @Readonly
        fun createAmphibiousAttackPathingMap(civ: Civilization, startingPoint: Tile, targetCiv: Civilization): PathingMap {
            return PathingMap(
                civ.gameInfo.tileMap,
                civ,
                "createAmphibiousAttackPathingMap",
                { civPathingCacheKey(startingPoint.position)},
                { isTileCanAttackThrough(civ, it, targetCiv) },
                { 0 },
                { from, to -> fpmFromMovement(roadPreferredMovementCost(civ, from, to)) },
                { fpmFromMovement(it.getConnectionStatus(civ).movement) },
                { tile -> tile.getOwner()?.getDiplomacyManager(civ)?.relationshipIgnoreAfraid() ?: RelationshipLevel.Favorable }
            )
        }

        @Readonly
        fun createRoadPathingMap(civ: Civilization, startingPoint: Tile): PathingMap {
            return PathingMap(
                civ.gameInfo.tileMap,
                civ,
                "createRoadPathingMap",
                { civPathingCacheKey(startingPoint.position)},
                {MapPathing.isValidRoadPathTile(civ, it) },
                { 0 },
                { _, to -> if ((to.hasRoadConnection(civ, false) || to.hasRailroadConnection(false))) FPM_POINT_FIVE else FPM_ONE },
                { FPM_ONE },
                { tile -> tile.getOwner()?.getDiplomacyManager(civ)?.relationshipIgnoreAfraid() ?: RelationshipLevel.Favorable }
            )
        }
        
        @Readonly
        private fun civPathingCacheKey(startingPoint: HexCoord) = PathingMapCacheKey(startingPoint, MAX_MOVE_THIS_TURN, MAX_VALID_TURNS)

        @Readonly
        private fun isTileCanAttackThrough(civInfo: Civilization, tile: Tile, targetCiv: Civilization): Boolean {
            val owner = tile.getOwner()
            return !tile.isImpassible()
                && (owner == targetCiv || owner == null || civInfo.diplomacyFunctions.canPassThroughTiles(owner))
        }

        @Readonly
        private fun isLandTileCanAttackThrough(civInfo: Civilization, tile: Tile, targetCiv: Civilization): Boolean {
            return tile.isLand && isTileCanAttackThrough(civInfo, tile, targetCiv)
        }
    }
}
