

package com.unciv.logic.map.mapunit.movement

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.BFS
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.UnitMovementMemoryType
import com.unciv.utils.getOrPut
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly
import java.util.BitSet


class UnitMovement(val unit: MapUnit) {

    @Cache private val pathfindingCache = PathfindingCache(unit)

    class ParentTileAndTotalMovement(val tile: Tile, val parentTile: Tile, val totalMovement: Float)

    @Readonly fun isUnknownTileWeShouldAssumeToBePassable(tile: Tile) = !unit.civ.hasExplored(tile)
    
    
    /**
     * Gets the tiles the unit could move to at [position] with [unitMovement].
     * Does not consider if tiles can actually be entered, use canMoveTo for that.
     * If a tile can be reached within the turn, but it cannot be passed through, the total distance to it is set to unitMovement
     */
    @Readonly @Suppress("purity") // mutates passed parameter
    fun getMovementToTilesAtPosition(
        position: Vector2,
        unitMovement: Float,
        considerZoneOfControl: Boolean = true,
        tilesToIgnoreBitset: BitSet? = null,
        canPassThroughCache: ArrayList<Boolean?> = ArrayList(),
        movementCostCache: HashMap<Pair<Tile, Tile>, Float> = HashMap(),
        includeOtherEscortUnit: Boolean = true
    ): PathsToTilesWithinTurn {
        @LocalState val distanceToTiles = PathsToTilesWithinTurn()

        val currentUnitTile = unit.currentTile
        // This is for performance, because this is called all the time
        val unitTile = if (position == currentUnitTile.position) currentUnitTile else currentUnitTile.tileMap[position]
        distanceToTiles[unitTile] = ParentTileAndTotalMovement(unitTile, unitTile, 0f)

        // If I can't move my only option is to stay...
        if (unitMovement == 0f || unit.cache.cannotMove) return distanceToTiles
        // If our escort can't move, ditto
        if (includeOtherEscortUnit && unit.isEscorting()
            && unit.getOtherEscortUnit()?.currentMovement == 0f) return distanceToTiles

        var tilesToCheck = listOf(unitTile)
        
        while (tilesToCheck.isNotEmpty()) {
            val updatedTiles = ArrayList<Tile>()
            for (tileToCheck in tilesToCheck)
                for (neighbor in tileToCheck.neighbors) {
                    // ignore this tile
                    if (tilesToIgnoreBitset != null && tilesToIgnoreBitset.get(neighbor.zeroBasedIndex)) continue // ignore this tile
                    var totalDistanceToTile: Float = when {
                        !neighbor.isExplored(unit.civ) ->
                            distanceToTiles[tileToCheck]!!.totalMovement + 1f  // If we don't know then we just guess it to be 1.
                        
                        !canPassThroughCache.getOrPut(neighbor.zeroBasedIndex){
                            canPassThrough(neighbor)
                        } -> unitMovement // Can't go here.
                        // The reason that we don't just "return" is so that when calculating how to reach an enemy,
                        // You need to assume his tile is reachable, otherwise all movement algorithms on reaching enemy
                        // cities and units goes kaput.
                        else -> {
                            val key = Pair(tileToCheck, neighbor)
                            val movementCost = movementCostCache.getOrPut(key) {
                                MovementCost.getMovementCostBetweenAdjacentTilesEscort(unit, tileToCheck, neighbor, considerZoneOfControl, includeOtherEscortUnit)
                            }
                            distanceToTiles[tileToCheck]!!.totalMovement + movementCost
                        }
                    }

                    val currentBestPath = distanceToTiles[neighbor]
                    if (currentBestPath == null || currentBestPath.totalMovement > totalDistanceToTile) { // this is the new best path
                        val usableMovement = if (includeOtherEscortUnit && unit.isEscorting())
                            minOf(unitMovement, unit.getOtherEscortUnit()!!.currentMovement)
                        else unitMovement

                        if (totalDistanceToTile < usableMovement - Constants.minimumMovementEpsilon)  // We can still keep moving from here!
                            updatedTiles += neighbor
                        else
                            totalDistanceToTile = usableMovement
                        // In Civ V, you can always travel between adjacent tiles, even if you don't technically
                        // have enough movement points - it simply depletes what you have

                        distanceToTiles[neighbor] = ParentTileAndTotalMovement(neighbor, tileToCheck, totalDistanceToTile)
                    }
                }

            tilesToCheck = updatedTiles
        }

        return distanceToTiles
    }

    /**
     * Does not consider if the [destination] tile can actually be entered, use [canMoveTo] for that.
     * Returns an empty list if there's no way to get to the destination.
     */
    @Readonly
    fun getShortestPath(destination: Tile, avoidDamagingTerrain: Boolean = false): List<Tile> {
        if (unit.cache.cannotMove) return listOf()

        // First try and find a path without damaging terrain
        if (!avoidDamagingTerrain && unit.civ.passThroughImpassableUnlocked && unit.baseUnit.isLandUnit) {
            val damageFreePath = getShortestPath(destination, true)
            if (damageFreePath.isNotEmpty()) return damageFreePath
        }
        
        if (destination.neighbors.none { isUnknownTileWeShouldAssumeToBePassable(it) || canPassThrough(it) }) {
            // edge case where this all of the tiles around the destination are
            // explored and known the unit can't pass through any of thoes tiles so we know a priori that no path exists
            pathfindingCache.setShortestPathCache(destination, listOf())
            return listOf()
        }
        val cachedPath = pathfindingCache.getShortestPathCache(destination)
        if (cachedPath.isNotEmpty())
            return cachedPath

        val currentTile = unit.getTile()
        if (currentTile.position == destination.position) {
            // edge case that's needed, so that workers will know that they can reach their own tile. *sigh*
            pathfindingCache.setShortestPathCache(destination, listOf(currentTile))
            return listOf(currentTile)
        }

        var tilesToCheck = listOf(currentTile)
        val movementTreeParents = HashMap<Tile, Tile?>() // contains a map of "you can get from X to Y in that turn"
        movementTreeParents[currentTile] = null

        var distance = 1
        val unitMaxMovement = unit.getMaxMovement().toFloat()
        val newTilesToCheck = ArrayList<Tile>()
        val visitedTilesBitset = BitSet()
        visitedTilesBitset.set(currentTile.zeroBasedIndex)
        val civilization = unit.civ

        val passThroughCacheNew = ArrayList<Boolean?>()
        val movementCostCache = HashMap<Pair<Tile, Tile>, Float>()
        val canMoveToCache = HashMap<Tile, Boolean>()

        while (true) {
            newTilesToCheck.clear()
            fun isUnfriendlyCityState(tile:Tile): Boolean = tile.getOwner().let { it != null && it.isCityState
                    && it.getDiplomacyManager(unit.civ)?.isRelationshipLevelLT(RelationshipLevel.Friend) == true }

            // When comparing booleans, we get false first, so we need to negate the isLand / isCityState checks
            // By order of preference: 1. Land tiles 2. Aerial distance 3. Not city states
            val comparison: Comparator<Tile> = if (unit.type.isLandUnit())
                compareBy({!it.isLand}, {it.aerialDistanceTo(destination)}, ::isUnfriendlyCityState)
            else compareBy({it.aerialDistanceTo(destination)}, ::isUnfriendlyCityState)
            
            val tilesByPreference = tilesToCheck.sortedWith(comparison)

            for (tileToCheck in tilesByPreference) {
                val distanceToTilesThisTurn = if (distance == 1) {
                    getDistanceToTiles(true, passThroughCacheNew, movementCostCache) // check cache
                }
                else {
                    getMovementToTilesAtPosition(
                        tileToCheck.position,
                        unitMaxMovement,
                        false,
                        visitedTilesBitset,
                        passThroughCacheNew,
                        movementCostCache
                    )
                }
                for (reachableTile in distanceToTilesThisTurn.keys) {
                    // Avoid damaging terrain on first pass
                    if (avoidDamagingTerrain && unit.getDamageFromTerrain(reachableTile) > 0)
                        continue
                    // Avoid Enemy Territory if Civilian and Automated. For multi-turn pathing
                    if (unit.isCivilian() && unit.isAutomated() && reachableTile.isEnemyTerritory(civilization))
                        continue
                    if (reachableTile == destination) {
                        val path = mutableListOf(destination)
                        // Traverse the tree upwards to get the list of tiles leading to the destination
                        var intermediateTile = tileToCheck
                        while (intermediateTile != currentTile) {
                            path.add(intermediateTile)
                            intermediateTile = movementTreeParents[intermediateTile]!!
                        }
                        path.reverse() // and reverse in order to get the list in chronological order
                        pathfindingCache.setShortestPathCache(destination, path)

                        return path
                    }
                    
                    if (movementTreeParents.containsKey(reachableTile)) continue // We cannot be faster than anything existing...
                    if (!isUnknownTileWeShouldAssumeToBePassable(reachableTile) &&
                        !canMoveToCache.getOrPut(reachableTile) { canMoveTo(reachableTile) })
                    // This is a tile that we can't actually enter - either an intermediary tile containing our unit, or an enemy unit/city
                        continue
                    movementTreeParents[reachableTile] = tileToCheck
                    newTilesToCheck.add(reachableTile)
                }
            }

            if (newTilesToCheck.isEmpty()) {
                // there is NO PATH (eg blocked by enemy units)
                pathfindingCache.setShortestPathCache(destination, emptyList())
                return emptyList()
            }

            // add newTilesToCheck to visitedTiles so we do not path over these tiles in a later iteration
            for (tile in newTilesToCheck) visitedTilesBitset.set(tile.zeroBasedIndex)
            // no need to check tiles that are surrounded by reachable tiles, only need to check the edgemost tiles.
            // Because anything we can reach from intermediate tiles, can be more easily reached by the edgemost tiles,
            // since we'll have to pass through an edgemost tile in order to reach the destination anyway
            tilesToCheck = newTilesToCheck.filterNot { tile -> tile.neighbors.all { visitedTilesBitset.get(it.zeroBasedIndex) } }

            distance++
        }
    }

    class UnreachableDestinationException(msg: String) : Exception(msg)

    @Readonly
    fun getTileToMoveToThisTurn(finalDestination: Tile): Tile {

        val currentTile = unit.getTile()
        if (currentTile == finalDestination) return currentTile

        // If we can fly, head there directly
        if ((unit.baseUnit.movesLikeAirUnits || unit.isPreparingParadrop()) && canMoveTo(finalDestination)) return finalDestination

        val distanceToTiles = getDistanceToTiles()

        // If the tile is far away, we need to build a path how to get there, and then take the first step
        if (!distanceToTiles.containsKey(finalDestination)) {
            val shortestDestination = getShortestPath(finalDestination).firstOrNull()
                ?: throw UnreachableDestinationException("$unit ${unit.currentTile} cannot reach $finalDestination")
            if (shortestDestination !in distanceToTiles)
                return distanceToTiles.keys.minBy { it.aerialDistanceTo(finalDestination) }
            return shortestDestination
        }

        // we should be able to get there this turn
        if (canMoveTo(finalDestination))
            return finalDestination

        // Someone is blocking to the path to the final tile...
        val destinationNeighbors = finalDestination.neighbors
        return when (currentTile) {
            in destinationNeighbors -> currentTile // We're right nearby anyway, no need to move
            else -> destinationNeighbors
                .filter { distanceToTiles.containsKey(it) && canMoveTo(it) }
                .minByOrNull { distanceToTiles.getValue(it).totalMovement } // we can get a little closer
                ?: currentTile // We can't get closer...
        }
    }

    /**
     * @return The tile that we reached this turn
     */
    fun headTowards(destination: Tile): Tile {
        val destinationTileThisTurn = getTileToMoveToThisTurn(destination)
        moveToTile(destinationTileThisTurn)
        return unit.currentTile
    }

    /** This is performance-heavy - use as last resort, only after checking everything else!
     *  Also note that REACHABLE tiles are not necessarily tiles that the unit CAN ENTER
     *  @see canReachInCurrentTurn
     */
    @Readonly
    fun canReach(destination: Tile) = canReachCommon(destination) {
        getShortestPath(it).any()
    }

    /** Cached and thus not as performance-heavy as [canReach] */
    @Readonly
    fun canReachInCurrentTurn(destination: Tile) = canReachCommon(destination) {
        getDistanceToTiles().containsKey(it)
    }

    @Readonly
    private inline fun canReachCommon(destination: Tile, @Readonly specificFunction: (Tile) -> Boolean) = when {
        unit.cache.cannotMove ->
            destination == unit.getTile()
        unit.baseUnit.movesLikeAirUnits ->
            unit.currentTile.aerialDistanceTo(destination) <= unit.getMaxMovementForAirUnits()
        unit.isPreparingParadrop() ->
            canParadropOn(destination, unit.currentTile.aerialDistanceTo(destination))
        else ->
            specificFunction(destination)  // Note: Could pass destination as implicit closure from outer fun to lambda, but explicit is clearer
    }

    /**
     * @param includeOtherEscortUnit determines whether or not this method will also check its the other escort unit if it has one
     * Leave it as default unless you know what [getReachableTilesInCurrentTurn] does.
     */
    @Readonly
    fun getReachableTilesInCurrentTurn(includeOtherEscortUnit: Boolean = true): Sequence<Tile> {
        return when {
            unit.cache.cannotMove -> sequenceOf(unit.getTile())
            unit.baseUnit.movesLikeAirUnits ->
                unit.getTile().getTilesInDistanceRange(IntRange(1, unit.getMaxMovementForAirUnits()))
            unit.isPreparingParadrop() -> {
                unit.getTile().getTilesInDistance(unit.cache.paradropDestinationTileFilters.maxOf { it.value } )
                    .filter { unit.movement.canParadropOn(it, it.aerialDistanceTo(unit.getTile())) }
            }
            includeOtherEscortUnit && unit.isEscorting() -> {
                    val otherUnitTiles = unit.getOtherEscortUnit()!!.movement.getReachableTilesInCurrentTurn(false).toSet()
                    unit.movement.getDistanceToTiles().filter { otherUnitTiles.contains(it.key) }.keys.asSequence()
                }
            else -> unit.movement.getDistanceToTiles().keys.asSequence()
        }
    }

    /** Returns whether we can perform a swap move to the specified tile */
    @Readonly
    fun canUnitSwapTo(destination: Tile): Boolean {
        return canReachInCurrentTurn(destination) && canUnitSwapToReachableTile(destination)
    }

    /** Returns the tiles to which we can perform a swap move */
    @Readonly
    fun getUnitSwappableTiles(): Sequence<Tile> {
        return getReachableTilesInCurrentTurn().filter { canUnitSwapToReachableTile(it) }
    }

    /**
     * Returns whether we can perform a unit swap move to the specified tile, given that it is
     * reachable in the current turn
     */
    @Readonly
    private fun canUnitSwapToReachableTile(reachableTile: Tile): Boolean {
        // Air units cannot swap
        if (unit.baseUnit.movesLikeAirUnits) return false
        // We can't swap with ourself
        if (reachableTile == unit.getTile()) return false
        if (unit.cache.cannotMove) return false
        // Check whether the tile contains a unit of the same type as us that we own and that can
        // also reach our tile in its current turn.
        val otherUnit = (
            if (unit.isCivilian())
                reachableTile.civilianUnit
            else
                reachableTile.militaryUnit
            ) ?: return false
        val ourPosition = unit.getTile()
        if (otherUnit.owner != unit.owner
            || otherUnit.cache.cannotMove  // redundant, line below would cover it too
            || !otherUnit.movement.canReachInCurrentTurn(ourPosition)) return false

        if (!canMoveTo(reachableTile, allowSwap = true)) return false
        if (!otherUnit.movement.canMoveTo(ourPosition, allowSwap = true)) return false
        // All clear!
        return true
    }

    /**
     * Displace a unit - choose a viable tile close by if possible and 'teleport' the unit there.
     * This will not use movement points or check for a possible route.
     * It is used e.g. if an enemy city expands its borders, or trades or diplomacy change a unit's
     * allowed position. Does not teleport transported units on their own, these are teleported when
     * the transporting unit is moved.
     * CAN DESTROY THE UNIT.
     */
    fun teleportToClosestMoveableTile() {
        unit.stopEscorting()
        if (unit.isTransported) return // handled when carrying unit is teleported
        var allowedTile: Tile? = null
        var distance = 0
        // When we didn't limit the allowed distance the game would sometimes spend a whole minute looking for a suitable tile.

        if (canPassThrough(unit.getTile())
            && !isCityCenterCannotEnter(unit.getTile()))
            return // This unit can stay here - e.g. it has "May enter foreign tiles without open borders"
        while (allowedTile == null && distance < 5) {
            distance++
            allowedTile = unit.getTile().getTilesAtDistance(distance)
                // can the unit be placed safely there? Is tile either unowned or friendly?
                .filter { canMoveTo(it) && it.getOwner()?.isAtWarWith(unit.civ) != true }
                // out of those where it can be placed, can it reach them in any meaningful way?
                .firstOrNull { getPathBetweenTiles(unit.currentTile, it).contains(it) }
        }

        // No tile within 4 spaces? move him to a city.
        val origin = unit.getTile()
        if (allowedTile == null)
            allowedTile = unit.civ.cities.flatMap { it.getTiles() }
                .sortedBy { it.aerialDistanceTo(origin) }.firstOrNull{ canMoveTo(it) }

        if (allowedTile != null) {
            unit.removeFromTile() // we "teleport" them away
            unit.putInTile(allowedTile)
            // Cancel sleep or fortification if forcibly displaced - for now, leave movement / auto / explore orders
            if (unit.isSleeping() || unit.isFortified() || unit.isGuarding())
                unit.action = null
            unit.mostRecentMoveType = UnitMovementMemoryType.UnitTeleported

            // bring along the payloads
            val payloadUnits = origin.getUnits().filter { it.isTransported && unit.canTransport(it) }.toList()
            for (payload in payloadUnits) {
                payload.removeFromTile()
                payload.putInTile(allowedTile)
                payload.isTransported = true // restore the flag to not leave the payload in the city
                payload.mostRecentMoveType = UnitMovementMemoryType.UnitTeleported
            }
        }
        // it's possible that there is no close tile, and all the guy's cities are full.
        // Nothing we can do.
        else unit.destroy()
    }

    fun moveToTile(destination: Tile, considerZoneOfControl: Boolean = true) {
        if (destination == unit.getTile() || unit.isDestroyed) return // already here (or dead)!
        // Reset closestEnemy chache
        val escortUnit = if (unit.isEscorting()) unit.getOtherEscortUnit()!! else null

        if (unit.baseUnit.movesLikeAirUnits) { // air units move differently from all other units
            if (unit.action != UnitActionType.Automate.value) unit.action = null
            unit.removeFromTile()
            unit.isTransported = false // it has left the carrier by own means
            unit.putInTile(destination)
            unit.currentMovement = 0f
            unit.mostRecentMoveType = UnitMovementMemoryType.UnitTeleported
            return
        }

        if (unit.isPreparingParadrop()) { // paradropping units move differently
            unit.action = null
            unit.removeFromTile()
            unit.putInTile(destination)
            unit.mostRecentMoveType = UnitMovementMemoryType.UnitTeleported
            unit.useMovementPoints(1f)
            unit.attacksThisTurn += 1
            // Check if unit maintenance changed
            // Is also done for other units, but because we skip everything else, we have to manually check it
            // The reason we skip everything, is that otherwise `getPathToTile()` throws an exception
            // As we can not reach our destination in a single turn
            if (unit.canGarrison()
                && (unit.getTile().isCityCenter() || destination.isCityCenter())
                && unit.civ.hasUnique(UniqueType.UnitsInCitiesNoMaintenance)
            ) unit.civ.updateStatsForNextTurn()
            return
        }

        val distanceToTiles = getDistanceToTiles(considerZoneOfControl)
        val pathToDestination = distanceToTiles.getPathToTile(destination)
        val movableTiles = pathToDestination.takeWhile { canPassThrough(it) }
        val lastReachableTile = movableTiles.lastOrNull { canMoveTo(it) }
            ?: return  // no tiles can pass though/can move to
        unit.mostRecentMoveType = UnitMovementMemoryType.UnitMoved
        val pathToLastReachableTile = distanceToTiles.getPathToTile(lastReachableTile)

        if (unit.isFortified() || unit.isGuarding() || unit.isSetUpForSiege() || unit.isSleeping())
            unit.action = null // un-fortify/un-setup/un-sleep after moving

        // If this unit is a carrier, keep record of its air payload whereabouts.
        val origin = unit.getTile()
        var needToFindNewRoute = false
        // Cache this in case something goes wrong

        var lastReachedEnterableTile = unit.getTile()
        var previousTile = unit.getTile()
        var passingMovementSpent = 0f // Movement points spent since last tile we could end our turn on


        for (tile in pathToLastReachableTile) {
            if (!unit.movement.canPassThrough(tile)) {
                // AAAH something happened making our previous path invalid
                // Maybe we spawned a unit using ancient ruins, or our old route went through
                // fog of war, and we found an obstacle halfway?
                // Anyway: PANIC!! We stop this route, and after leaving the game in a valid state,
                // we try again.
                needToFindNewRoute = true
                break // If you ever remove this break, remove the `assumeCanPassThrough` param below
            }

            // This fixes a bug where tiles in the fog of war would always only cost 1 mp
            if (!unit.civ.gameInfo.gameParameters.godMode)
                passingMovementSpent += MovementCost.getMovementCostBetweenAdjacentTiles(unit, previousTile, tile)

            // In case something goes wrong, cache the last tile we were able to end on
            // We can assume we can pass through this tile, as we would have broken earlier
            if (unit.movement.canMoveTo(tile, assumeCanPassThrough = true)) {
                lastReachedEnterableTile = tile
                unit.useMovementPoints(passingMovementSpent)
                unit.removeFromTile()
                unit.putInTile(tile) // Required for ruins,

                if (escortUnit != null) {
                    escortUnit.movement.moveToTile(tile)
                    unit.startEscorting() // Need to re-apply this
                }

                passingMovementSpent = 0f
            }

            previousTile = tile

            // We can't continue, stop here.
            if (unit.isDestroyed || unit.currentMovement - passingMovementSpent < Constants.minimumMovementEpsilon) {
                break
            }
        }

        val finalTileReached = lastReachedEnterableTile

        // Silly floats which are almost zero
        if (unit.currentMovement < Constants.minimumMovementEpsilon)
            unit.currentMovement = 0f


        // The .toList() here is because we have a sequence that's running on the units in the tile,
        // then if we move one of the units we'll get a ConcurrentModificationException, se we save them all to a list
        val payloadUnits = origin.getUnits().filter { it.isTransported && unit.canTransport(it) }.toList()
        // bring along the payloads
        for (payload in payloadUnits) {
            payload.removeFromTile()
            for (tile in pathToLastReachableTile) {
                payload.moveThroughTile(tile)
                if (tile == finalTileReached) break // this is the final tile the transport reached
            }
            payload.putInTile(finalTileReached)
            payload.isTransported = true // restore the flag to not leave the payload in the city
            payload.mostRecentMoveType = UnitMovementMemoryType.UnitMoved
        }

        // Unit maintenance changed
        if (unit.canGarrison()
            && (origin.isCityCenter() || finalTileReached.isCityCenter())
            && unit.civ.hasUnique(UniqueType.UnitsInCitiesNoMaintenance)
        ) unit.civ.updateStatsForNextTurn()

        // Under rare cases (see #8044), we can be headed to a tile and *the entire path* is blocked by other units, so we can't "enter" that tile.
        // If, in such conditions, the *destination tile* is unenterable, needToFindNewRoute will trigger, so we need to catch this situation to avoid infinite loop
        if (needToFindNewRoute && unit.currentTile != origin) {
            moveToTile(destination, considerZoneOfControl)
        }

        unit.updateUniques()
    }

    /**
     * Swaps this unit with the unit on the given tile
     * Precondition: this unit can swap-move to the given tile, as determined by canUnitSwapTo
     */
    fun swapMoveToTile(destination: Tile) {
        unit.stopEscorting()
        val otherUnit = (
            if (unit.isCivilian())
                destination.civilianUnit
            else
                destination.militaryUnit
            )?: return // The precondition guarantees that there is an eligible same-type unit at the destination
        otherUnit.stopEscorting()
        val ourOldPosition = unit.getTile()
        val theirOldPosition = otherUnit.getTile()

        val ourPayload = ourOldPosition.getUnits().filter { it.isTransported && unit.isTransportTypeOf(it) }.toList()
        val theirPayload = theirOldPosition.getUnits().filter { it.isTransported && otherUnit.isTransportTypeOf(it) }.toList()

        // Swap the units
        // Step 1: Release the destination tile
        otherUnit.removeFromTile()
        for (payload in theirPayload)
            payload.removeFromTile()
        // Step 2: Perform the movement
        unit.movement.moveToTile(destination)
        // Step 3: Release the newly taken tile
        unit.removeFromTile()
        for (payload in ourPayload)
            payload.removeFromTile()
        // Step 4: Restore the initial position after step 1
        otherUnit.putInTile(theirOldPosition)
        for (payload in theirPayload) {
            payload.putInTile(theirOldPosition)
            payload.isTransported = true // restore the flag to not leave the payload in the city
        }
        // Step 5: Perform the another movement
        otherUnit.movement.moveToTile(ourOldPosition)
        // Step 6: Restore the position in the new tile after step 3
        unit.putInTile(theirOldPosition)
        for (payload in ourPayload) {
            payload.putInTile(theirOldPosition)
            payload.isTransported = true // restore the flag to not leave the payload in the city
        }
        // Step 6: Update states
        otherUnit.mostRecentMoveType = UnitMovementMemoryType.UnitMoved
        unit.mostRecentMoveType = UnitMovementMemoryType.UnitMoved
    }

    @Readonly
    private fun isCityCenterCannotEnter(tile: Tile) = tile.isCityCenter()
        && tile.getOwner() != unit.civ
        && !tile.getCity()!!.hasJustBeenConquered

    /**
     * Designates whether we can enter the tile - without attacking
     * DOES NOT designate whether we can reach that tile in the current turn
     * @param includeOtherEscortUnit determines whether or not this method will also check if the other escort unit [canMoveTo] if it has one.
     * Leave it as default unless you know what [canMoveTo] does.
     */
    @Readonly
    fun canMoveTo(tile: Tile, assumeCanPassThrough: Boolean = false, allowSwap: Boolean = false, includeOtherEscortUnit: Boolean = true): Boolean {
        if (unit.baseUnit.movesLikeAirUnits)
            return canAirUnitMoveTo(tile, unit)

        if (!assumeCanPassThrough && !canPassThrough(tile))
            return false

        // even if they'll let us pass through, we can't enter their city - unless we just captured it
        if (isCityCenterCannotEnter(tile))
            return false

        if (includeOtherEscortUnit && unit.isEscorting()
            && !unit.getOtherEscortUnit()!!.movement.canMoveTo(tile, assumeCanPassThrough, allowSwap, includeOtherEscortUnit = false))
            return false

        return if (unit.isCivilian())
            (tile.civilianUnit == null || (allowSwap && tile.civilianUnit!!.owner == unit.owner))
                && (tile.militaryUnit == null || tile.militaryUnit!!.owner == unit.owner)
        else
        // can skip checking for airUnit since not a city
            (tile.militaryUnit == null || (allowSwap && tile.militaryUnit!!.owner == unit.owner))
                && (tile.civilianUnit == null || tile.civilianUnit!!.owner == unit.owner || unit.civ.isAtWarWith(tile.civilianUnit!!.civ))
    }

    @Readonly
    private fun canAirUnitMoveTo(tile: Tile, unit: MapUnit): Boolean {
        // landing in the city
        if (tile.isCityCenter()) {
            if (tile.airUnits.filter { !it.isTransported }.size < tile.getCity()!!.getMaxAirUnits() && tile.getCity()?.civ == unit.civ)
                return true // if city is free - no problem, get in
        } // let's check whether it enters city on carrier now...

        if (tile.militaryUnit != null) {
            val unitAtDestination = tile.militaryUnit!!
            return unitAtDestination.canTransport(unit)
        }
        return false
    }

    // Can a paratrooper land at this tile?
    @Readonly
    private fun canParadropOn(destination: Tile, distance: Int): Boolean {
        if (unit.cache.cannotMove) return false

        // Can only move to tiles within range that are visible and not impassible
        // Based on some testing done in the base game
        if (destination.isImpassible() || !unit.civ.viewableTiles.contains(destination)) return false

        // The destination is valid if any of the `tileFilters` match, and is within range
        for ((tileFilter, distanceAllowed) in unit.cache.paradropDestinationTileFilters) {
            if (distance <= distanceAllowed && destination.matchesFilter(tileFilter, unit.civ)) return true
        }

        return false
    }

    /**
     * @returns whether this unit can pass through [tile].
     * Note that sometimes, a tile can be passed through but not entered. Use [canMoveTo] to
     * determine whether a unit can enter a tile.
     *
     * This is the most called function in the entire game,
     * so multiple callees of this function have been optimized,
     * because optimization on this function results in massive benefits!
     * @param includeOtherEscortUnit determines whether or not this method will also check if the other escort unit [canPassThrough] if it has one.
     * Leave it as default unless you know what [canPassThrough] does.
     */
    @Readonly
    fun canPassThrough(tile: Tile, includeOtherEscortUnit: Boolean = true): Boolean {
        if (tile.isImpassible()) {
            // special exception - ice tiles are technically impassible, but some units can move through them anyway
            // helicopters can pass through impassable tiles like mountains
            if (!unit.cache.canPassThroughImpassableTiles && !(unit.cache.canEnterIceTiles && tile.terrainFeatures.contains(Constants.ice))
                // carthage-like uniques sometimes allow passage through impassible tiles
                && !(unit.civ.passThroughImpassableUnlocked && unit.civ.passableImpassables.contains(tile.lastTerrain.name)))
                return false
        }
        if (tile.isLand
            && unit.baseUnit.isWaterUnit
            && !tile.isCityCenter())
            return false

        val unitSpecificAllowOcean: Boolean by lazy {
            unit.civ.tech.specificUnitsCanEnterOcean &&
                unit.civ.getMatchingUniques(UniqueType.UnitsMayEnterOcean)
                    .any { unit.matchesFilter(it.params[0]) }
        }
        if (tile.isWater && unit.baseUnit.isLandUnit && !unit.cache.canMoveOnWater) {
            if (!unit.civ.tech.unitsCanEmbark) return false
            if (unit.cache.cannotEmbark) return false
            if (tile.isOcean && !unit.civ.tech.embarkedUnitsCanEnterOcean && !unitSpecificAllowOcean)
                return false
        }
        if (tile.isOcean && !unit.civ.tech.allUnitsCanEnterOcean) { // Apparently all Polynesian naval units can enter oceans
            if (!unitSpecificAllowOcean && unit.cache.cannotEnterOceanTiles) return false
        }

        if (unit.cache.canEnterCityStates && tile.getOwner()?.isCityState == true)
            return true
        if (!unit.cache.canEnterForeignTerrain && !tile.canCivPassThrough(unit.civ)) return false

        // The first unit is:
        //   1. Either military unit
        //   2. or unprotected civilian
        //   3. or unprotected air unit while no civilians on tile
        val firstUnit = tile.getFirstUnit()
        // Moving to non-empty tile
        if (firstUnit != null && unit.civ != firstUnit.civ) {
            // Allow movement through unguarded, at-war Civilian Unit. Capture on the way
            // But not for Embarked Units capturing on Water
            if (!(unit.baseUnit.isLandUnit && tile.isWater && !unit.cache.canMoveOnWater)
                && firstUnit.isCivilian() && unit.civ.isAtWarWith(firstUnit.civ))
                return true
            // Cannot enter hostile tile with any unit in there
            if (unit.civ.isAtWarWith(firstUnit.civ))
                return false
        }
        if (includeOtherEscortUnit && unit.isEscorting() && !unit.getOtherEscortUnit()!!.movement.canPassThrough(tile,false))
            return false
        return true
    }


    /**
     * @param includeOtherEscortUnit determines whether or not this method will also check if the other escort units [getDistanceToTiles] if it has one.
     * Leave it as default unless you know what [getDistanceToTiles] does.
     */
    @Readonly
    fun getDistanceToTiles(
        considerZoneOfControl: Boolean = true,
        passThroughCacheNew: ArrayList<Boolean?> = ArrayList(),
        movementCostCache: HashMap<Pair<Tile, Tile>, Float> = HashMap(),
        includeOtherEscortUnit: Boolean = true
    ): PathsToTilesWithinTurn {
        val distanceToTiles = getMovementToTilesAtPosition(
            unit.currentTile.position,
            unit.currentMovement,
            considerZoneOfControl,
            null,
            passThroughCacheNew,
            movementCostCache,
            includeOtherEscortUnit
        )

        return distanceToTiles
    }

    fun getAerialPathsToCities(): HashMap<Tile, ArrayList<Tile>> {
        var tilesToCheck = ArrayList<Tile>()
        /** each tile reached points to its parent tile, where we got to it from */
        val tilesReached = HashMap<Tile, Tile>()

        val startingTile = unit.currentTile
        tilesToCheck.add(startingTile)
        tilesReached[startingTile] = startingTile


        while (tilesToCheck.isNotEmpty()) {
            val newTilesToCheck = ArrayList<Tile>()
            for (currentTileToCheck in tilesToCheck) {
                val reachableTiles = currentTileToCheck.getTilesInDistance(unit.getRange())
                    .filter { unit.movement.canMoveTo(it) }
                for (reachableTile in reachableTiles) {
                    if (tilesReached.containsKey(reachableTile)) continue
                    tilesReached[reachableTile] = currentTileToCheck
                    newTilesToCheck.add(reachableTile)
                }
            }
            tilesToCheck = newTilesToCheck
        }

        val pathsToCities = HashMap<Tile, ArrayList<Tile>>()

        for (city in tilesReached.keys) {
            val path = ArrayList<Tile>()
            var currentCity = city
            while (currentCity != startingTile) { // we don't add the "starting tile" to the arraylist
                path.add(currentCity)
                currentCity = tilesReached[currentCity]!! // go to parent
            }
            path.reverse()
            pathsToCities[city] = path
        }

        pathsToCities.remove(startingTile)
        return pathsToCities
    }

    /**
     * @returns the set of [Tile] between [from] and [to] tiles.
     * It takes into account the terrain and units possibilities of entering the terrain,
     * however ignores the diplomatic aspects of such movement like crossing closed borders.
     */
    private fun getPathBetweenTiles(from: Tile, to: Tile): MutableSet<Tile> {
        val tmp = unit.cache.canEnterForeignTerrain
        unit.cache.canEnterForeignTerrain = true // the trick to ignore tiles owners
        val bfs = BFS(from) { canPassThrough(it) }
        bfs.stepUntilDestination(to)
        unit.cache.canEnterForeignTerrain = tmp
        return bfs.getReachedTiles()
    }

    fun clearPathfindingCache() = pathfindingCache.clear()

}

/**
 * Cache for the results of [UnitMovement.getDistanceToTiles] accounting for zone of control.
 * [UnitMovement.getDistanceToTiles] is called in numerous places for AI pathfinding so
 * being able to skip redundant calculations helps out over a long game (especially with high level
 * AI or a big map). Same thing with [UnitMovement.getShortestPath] which is called in
 * [UnitMovement.canReach] and in [UnitMovement.headTowards]. Often, the AI will
 * see if it can reach a tile using canReach then if it can, it will headTowards it. We can cache
 * the result since otherwise this is a redundant calculation that will find the same path.
 */
class PathfindingCache(private val unit: MapUnit) {
    private var shortestPathCache = listOf<Tile>()
    private var destination: Tile? = null
    private var movement = -1f
    private var currentTile: Tile? = null

    /** Check if the caches are valid (only checking if the unit has moved or consumed movement points;
     * the isPlayerCivilization check is performed in the functions because we want isValid() == false
     * to have a specific behavior) */
    @Readonly private fun isValid(): Boolean = (movement == unit.currentMovement) && (unit.getTile() == currentTile)

    fun getShortestPathCache(destination: Tile): List<Tile> {
        if (unit.civ.isHuman()) return listOf()
        if (isValid() && this.destination == destination) {
            return shortestPathCache
        }
        return listOf()
    }

    fun setShortestPathCache(destination: Tile, newShortestPath: List<Tile>) {
        if (unit.civ.isHuman()) return
        if (isValid()) {
            shortestPathCache = newShortestPath
            this.destination = destination
        }
    }

    fun clear() {
        movement = unit.currentMovement
        currentTile = unit.getTile()
        destination = null
        shortestPathCache = listOf()
    }
}

class PathsToTilesWithinTurn : LinkedHashMap<Tile, UnitMovement.ParentTileAndTotalMovement>() {
    fun getPathToTile(tile: Tile): List<Tile> {
        if (!containsKey(tile))
            throw Exception("Can't reach this tile!")
        val reversePathList = ArrayList<Tile>()
        var currentTile = tile
        while (get(currentTile)!!.parentTile != currentTile) {
            reversePathList.add(currentTile)
            currentTile = get(currentTile)!!.parentTile
        }
        return reversePathList.reversed()
    }
}
