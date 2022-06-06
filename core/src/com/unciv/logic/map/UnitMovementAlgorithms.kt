package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.HexMath.getDistance
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.helpers.UnitMovementMemoryType
import com.unciv.models.ruleset.unique.UniqueType

class UnitMovementAlgorithms(val unit: MapUnit) {

    // This function is called ALL THE TIME and should be as time-optimal as possible!
    private fun getMovementCostBetweenAdjacentTiles(
        from: TileInfo,
        to: TileInfo,
        civInfo: CivilizationInfo,
        considerZoneOfControl: Boolean = true
    ): Float {

        if (from.isLand != to.isLand && unit.baseUnit.isLandUnit())
            return if (from.isWater && to.isLand) unit.costToDisembark ?: 100f
            else unit.costToEmbark ?: 100f

        // If the movement is affected by a Zone of Control, all movement points are expended
        if (considerZoneOfControl && isMovementAffectedByZoneOfControl(from, to, civInfo))
            return 100f

        // land units will still spend all movement points to embark even with this unique
        if (unit.allTilesCosts1)
            return 1f

        val toOwner = to.getOwner()
        val extraCost = if (
            toOwner != null &&
            to.isLand &&
            toOwner.hasActiveGreatWall &&
            civInfo.isAtWarWith(toOwner)
        ) 1f else 0f

        if (from.roadStatus == RoadStatus.Railroad && to.roadStatus == RoadStatus.Railroad)
            return RoadStatus.Railroad.movement + extraCost

        // Each of these two function calls `hasUnique(UniqueType.CityStateTerritoryAlwaysFriendly)`
        // when entering territory of a city state
        val areConnectedByRoad = from.hasConnection(civInfo) && to.hasConnection(civInfo)

        val areConnectedByRiver = from.isAdjacentToRiver() && to.isAdjacentToRiver() && from.isConnectedByRiver(to)

        if (areConnectedByRoad && (!areConnectedByRiver || civInfo.tech.roadsConnectAcrossRivers))
            return unit.civInfo.tech.movementSpeedOnRoads + extraCost

        if (unit.ignoresTerrainCost) return 1f + extraCost
        if (areConnectedByRiver) return 100f  // Rivers take the entire turn to cross

        val terrainCost = to.getLastTerrain().movementCost.toFloat()

        if (unit.noTerrainMovementUniques)
            return terrainCost + extraCost

        if (to.terrainFeatures.any { unit.doubleMovementInTerrain[it] == MapUnit.DoubleMovementTerrainTarget.Feature })
            return terrainCost * 0.5f + extraCost

        if (unit.roughTerrainPenalty && to.isRoughTerrain())
            return 100f // units that have to spend all movement in rough terrain, have to spend all movement in rough terrain
        // Placement of this 'if' based on testing, see #4232

        if (civInfo.nation.ignoreHillMovementCost && to.isHill())
            return 1f + extraCost // usually hills take 2 movements, so here it is 1

        if (unit.noBaseTerrainOrHillDoubleMovementUniques)
            return terrainCost + extraCost

        if (unit.doubleMovementInTerrain[to.baseTerrain] == MapUnit.DoubleMovementTerrainTarget.Base)
            return terrainCost * 0.5f + extraCost
        if (unit.doubleMovementInTerrain[Constants.hill] == MapUnit.DoubleMovementTerrainTarget.Hill && to.isHill())
            return terrainCost * 0.5f + extraCost

        if (unit.noFilteredDoubleMovementUniques)
            return terrainCost + extraCost
        if (unit.doubleMovementInTerrain.any {
            it.value == MapUnit.DoubleMovementTerrainTarget.Filter &&
                to.matchesFilter(it.key)
        })
            return terrainCost * 0.5f + extraCost

        return terrainCost + extraCost // no road or other movement cost reduction
    }

    private fun getTilesExertingZoneOfControl(tileInfo: TileInfo, civInfo: CivilizationInfo) = sequence {
        for (tile in tileInfo.neighbors) {
            if (tile.isCityCenter() && civInfo.isAtWarWith(tile.getOwner()!!)) {
                yield(tile)
            }
            else if (tile.militaryUnit != null && civInfo.isAtWarWith(tile.militaryUnit!!.civInfo)) {
                if (tile.militaryUnit!!.type.isWaterUnit() || (unit.type.isLandUnit() && !tile.militaryUnit!!.isEmbarked()))
                    yield(tile)
            }
        }
    }

    /** Returns whether the movement between the adjacent tiles [from] and [to] is affected by Zone of Control */
    private fun isMovementAffectedByZoneOfControl(from: TileInfo, to: TileInfo, civInfo: CivilizationInfo): Boolean {
        // Sources:
        // - https://civilization.fandom.com/wiki/Zone_of_control_(Civ5)
        // - https://forums.civfanatics.com/resources/understanding-the-zone-of-control-vanilla.25582/
        //
        // Enemy military units exert a Zone of Control over the tiles surrounding them. Moving from
        // one tile in the ZoC of an enemy unit to another tile in the same unit's ZoC expends all
        // movement points. Land units only exert a ZoC against land units. Sea units exert a ZoC
        // against both land and sea units. Cities exert a ZoC as well, and it also affects both
        // land and sea units. Embarked land units do not exert a ZoC. Finally, units that can move
        // after attacking are not affected by zone of control if the movement is caused by killing
        // a unit. This last case is handled in the movement-after-attacking code instead of here.

        // We only need to check the two shared neighbors of [from] and [to]: the way of getting
        // these two tiles can perhaps be optimized. Using a hex-math-based "commonAdjacentTiles"
        // function is surprisingly less efficient than the current neighbor-intersection approach.
        // See #4085 for more details.
        val tilesExertingZoneOfControl = getTilesExertingZoneOfControl(from, civInfo)
        if (tilesExertingZoneOfControl.none { to.neighbors.contains(it)})
            return false

        // Even though this is a very fast check, we perform it last. This is because very few units
        // ignore zone of control, so the previous check has a much higher chance of yielding an
        // early "false". If this function is going to return "true", the order doesn't matter
        // anyway.
        if (unit.ignoresZoneOfControl)
            return false
        return true
    }

    class ParentTileAndTotalDistance(val parentTile: TileInfo, val totalDistance: Float)

    fun isUnknownTileWeShouldAssumeToBePassable(tileInfo: TileInfo) = !unit.civInfo.exploredTiles.contains(tileInfo.position)

    /**
     * Does not consider if tiles can actually be entered, use canMoveTo for that.
     * If a tile can be reached within the turn, but it cannot be passed through, the total distance to it is set to unitMovement
     */
    fun getDistanceToTilesWithinTurn(origin: Vector2, unitMovement: Float, considerZoneOfControl: Boolean = true): PathsToTilesWithinTurn {
        val distanceToTiles = PathsToTilesWithinTurn()
        if (unitMovement == 0f) return distanceToTiles

        val currentUnitTile = unit.currentTile
        // This is for performance, because this is called all the time
        val unitTile = if (origin == currentUnitTile.position) currentUnitTile else currentUnitTile.tileMap[origin]
        distanceToTiles[unitTile] = ParentTileAndTotalDistance(unitTile, 0f)
        var tilesToCheck = listOf(unitTile)

        while (tilesToCheck.isNotEmpty()) {
            val updatedTiles = ArrayList<TileInfo>()
            for (tileToCheck in tilesToCheck)
                for (neighbor in tileToCheck.neighbors) {
                    var totalDistanceToTile: Float = when {
                        !unit.civInfo.exploredTiles.contains(neighbor.position) ->
                            distanceToTiles[tileToCheck]!!.totalDistance + 1f  // If we don't know then we just guess it to be 1.
                        !canPassThrough(neighbor) -> unitMovement // Can't go here.
                        // The reason that we don't just "return" is so that when calculating how to reach an enemy,
                        // You need to assume his tile is reachable, otherwise all movement algorithms on reaching enemy
                        // cities and units goes kaput.
                        else -> {
                            val distanceBetweenTiles = getMovementCostBetweenAdjacentTiles(tileToCheck, neighbor, unit.civInfo, considerZoneOfControl)
                            distanceToTiles[tileToCheck]!!.totalDistance + distanceBetweenTiles
                        }
                    }

                    if (!distanceToTiles.containsKey(neighbor) || distanceToTiles[neighbor]!!.totalDistance > totalDistanceToTile) { // this is the new best path
                        if (totalDistanceToTile < unitMovement)  // We can still keep moving from here!
                            updatedTiles += neighbor
                        else
                            totalDistanceToTile = unitMovement
                        // In Civ V, you can always travel between adjacent tiles, even if you don't technically
                        // have enough movement points - it simply depletes what you have

                        distanceToTiles[neighbor] = ParentTileAndTotalDistance(tileToCheck, totalDistanceToTile)
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
    fun getShortestPath(destination: TileInfo, avoidDamagingTerrain: Boolean = false): List<TileInfo> {
        // First try and find a path without damaging terrain
        if (!avoidDamagingTerrain && unit.civInfo.passThroughImpassableUnlocked && unit.baseUnit.isLandUnit()) {
            val damageFreePath = getShortestPath(destination, true)
            if (damageFreePath.isNotEmpty()) return damageFreePath
        }

        val currentTile = unit.getTile()
        if (currentTile.position == destination) return listOf(currentTile) // edge case that's needed, so that workers will know that they can reach their own tile. *sigh*

        var tilesToCheck = listOf(currentTile)
        val movementTreeParents = HashMap<TileInfo, TileInfo?>() // contains a map of "you can get from X to Y in that turn"
        movementTreeParents[currentTile] = null

        var movementThisTurn = unit.currentMovement
        var distance = 1
        val newTilesToCheck = ArrayList<TileInfo>()
        val distanceToDestination = HashMap<TileInfo, Float>()
        var considerZoneOfControl = true // only for first distance!
        while (true) {
            if (distance == 2) { // only set this once after distance > 1
                movementThisTurn = unit.getMaxMovement().toFloat()
                considerZoneOfControl = false  // by then units would have moved around, we don't need to consider untenable futures when it harms performance!
            }
            newTilesToCheck.clear()
            distanceToDestination.clear()
            for (tileToCheck in tilesToCheck) {
                val distanceToTilesThisTurn = getDistanceToTilesWithinTurn(tileToCheck.position, movementThisTurn, considerZoneOfControl)
                for (reachableTile in distanceToTilesThisTurn.keys) {
                    // Avoid damaging terrain on first pass
                    if (avoidDamagingTerrain && unit.getDamageFromTerrain(reachableTile) > 0)
                        continue
                    if (reachableTile == destination)
                        distanceToDestination[tileToCheck] = distanceToTilesThisTurn[reachableTile]!!.totalDistance
                    else {
                        if (movementTreeParents.containsKey(reachableTile)) continue // We cannot be faster than anything existing...
                        if (!isUnknownTileWeShouldAssumeToBePassable(reachableTile) &&
                                 !canMoveTo(reachableTile)) continue // This is a tile that we can't actually enter - either an intermediary tile containing our unit, or an enemy unit/city
                        movementTreeParents[reachableTile] = tileToCheck
                        newTilesToCheck.add(reachableTile)
                    }
                }
            }

            if (distanceToDestination.isNotEmpty()) {
                val path = mutableListOf(destination) // Traverse the tree upwards to get the list of tiles leading to the destination,
                // Get the tile from which the distance to the final tile in least -
                // this is so that when we finally get there, we'll have as many movement points as possible
                var intermediateTile = distanceToDestination.minByOrNull { it.value }!!.key
                while (intermediateTile != currentTile) {
                    path.add(intermediateTile)
                    intermediateTile = movementTreeParents[intermediateTile]!!
                }
                path.reverse() // and reverse in order to get the list in chronological order
                return path
            }

            if (newTilesToCheck.isEmpty()) return emptyList() // there is NO PATH (eg blocked by enemy units)

            // no need to check tiles that are surrounded by reachable tiles, only need to check the edgemost tiles.
            // Because anything we can reach from intermediate tiles, can be more easily reached by the edgemost tiles,
            // since we'll have to pass through an edgemost tile in order to reach the destination anyway
            tilesToCheck = newTilesToCheck.filterNot { tile -> tile.neighbors.all { it in newTilesToCheck || it in tilesToCheck } }

            distance++
        }
    }

    fun getTileToMoveToThisTurn(finalDestination: TileInfo): TileInfo {

        val currentTile = unit.getTile()
        if (currentTile == finalDestination) return currentTile

        // If we can fly, head there directly
        if ((unit.baseUnit.movesLikeAirUnits() || unit.isPreparingParadrop()) && canMoveTo(finalDestination)) return finalDestination

        val distanceToTiles = getDistanceToTiles()

        class UnreachableDestinationException(msg: String) : Exception(msg)

        // If the tile is far away, we need to build a path how to get there, and then take the first step
        if (!distanceToTiles.containsKey(finalDestination))
            return getShortestPath(finalDestination).firstOrNull()
                    ?: throw UnreachableDestinationException("$unit ${unit.currentTile.position} cannot reach $finalDestination")

        // we should be able to get there this turn
        if (canMoveTo(finalDestination))
            return finalDestination

        // Someone is blocking to the path to the final tile...
        val destinationNeighbors = finalDestination.neighbors
        return when (currentTile) {
            in destinationNeighbors -> currentTile // We're right nearby anyway, no need to move
            else -> destinationNeighbors.asSequence()
                .filter { distanceToTiles.containsKey(it) && canMoveTo(it) }
                .minByOrNull { distanceToTiles.getValue(it).totalDistance } // we can get a little closer
                    ?: currentTile // We can't get closer...
        }
    }

    /**
     * @return The tile that we reached this turn
     */
    fun headTowards(destination: TileInfo): TileInfo {
        val destinationTileThisTurn = getTileToMoveToThisTurn(destination)
        moveToTile(destinationTileThisTurn)
        return unit.currentTile
    }

    /** This is performance-heavy - use as last resort, only after checking everything else!
     * Also note that REACHABLE tiles are not necessarily tiles that the unit CAN ENTER */
    fun canReach(destination: TileInfo): Boolean {
        if (unit.baseUnit.movesLikeAirUnits() || unit.isPreparingParadrop())
            return canReachInCurrentTurn(destination)
        return getShortestPath(destination).any()
    }

    private fun canReachInCurrentTurn(destination: TileInfo): Boolean {
        if (unit.baseUnit.movesLikeAirUnits())
            return unit.currentTile.aerialDistanceTo(destination) <= unit.getMaxMovementForAirUnits()
        if (unit.isPreparingParadrop())
            return getDistance(unit.currentTile.position, destination.position) <= unit.paradropRange && canParadropOn(destination)
        return getDistanceToTiles().containsKey(destination)
    }

    fun getReachableTilesInCurrentTurn(): Sequence<TileInfo> {
        return when {
            unit.baseUnit.movesLikeAirUnits() ->
                unit.getTile().getTilesInDistanceRange(IntRange(1, unit.getMaxMovementForAirUnits()))
            unit.isPreparingParadrop() ->
                unit.getTile().getTilesInDistance(unit.paradropRange)
                    .filter { unit.movement.canParadropOn(it) }
            else ->
                unit.movement.getDistanceToTiles().keys.asSequence()
        }
    }

    /** Returns whether we can perform a swap move to the specified tile */
    fun canUnitSwapTo(destination: TileInfo): Boolean {
        return canReachInCurrentTurn(destination) && canUnitSwapToReachableTile(destination)
    }

    /** Returns the tiles to which we can perform a swap move */
    fun getUnitSwappableTiles(): Sequence<TileInfo> {
        return getReachableTilesInCurrentTurn().filter { canUnitSwapToReachableTile(it) }
    }

    /**
     * Returns whether we can perform a unit swap move to the specified tile, given that it is
     * reachable in the current turn
     */
    private fun canUnitSwapToReachableTile(reachableTile: TileInfo): Boolean {
        // Air units cannot swap
        if (unit.baseUnit.movesLikeAirUnits()) return false
        // We can't swap with ourself
        if (reachableTile == unit.getTile()) return false
        // Check whether the tile contains a unit of the same type as us that we own and that can
        // also reach our tile in its current turn.
        val otherUnit = (
            if (unit.isCivilian())
                reachableTile.civilianUnit
            else
                reachableTile.militaryUnit
        ) ?: return false
        val ourPosition = unit.getTile()
        if (otherUnit.owner != unit.owner || !otherUnit.movement.canReachInCurrentTurn(ourPosition)) return false
        // Check if we could enter their tile if they wouldn't be there
        otherUnit.removeFromTile()
        val weCanEnterTheirTile = canMoveTo(reachableTile)
        otherUnit.putInTile(reachableTile)
        if (!weCanEnterTheirTile) return false
        // Check if they could enter our tile if we wouldn't be here
        unit.removeFromTile()
        val theyCanEnterOurTile = otherUnit.movement.canMoveTo(ourPosition)
        unit.putInTile(ourPosition)
        if (!theyCanEnterOurTile) return false
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
        if (unit.isTransported) return // handled when carrying unit is teleported
        var allowedTile: TileInfo? = null
        var distance = 0
        // When we didn't limit the allowed distance the game would sometimes spend a whole minute looking for a suitable tile.
        while (allowedTile == null && distance < 5) {
            distance++
            allowedTile = unit.getTile().getTilesAtDistance(distance)
                // can the unit be placed safely there?
                .filter { canMoveTo(it) }
                // out of those where it can be placed, can it reach them in any meaningful way?
                .firstOrNull { getPathBetweenTiles(unit.currentTile, it).contains(it) }
        }

        // No tile within 4 spaces? move him to a city.
        val origin = unit.getTile()
        if (allowedTile == null)
            allowedTile = unit.civInfo.cities.flatMap { it.getTiles() }
                .sortedBy { it.aerialDistanceTo(origin) }.firstOrNull{ canMoveTo(it) }

        if (allowedTile != null) {
            unit.removeFromTile() // we "teleport" them away
            unit.putInTile(allowedTile)
            // Cancel sleep or fortification if forcibly displaced - for now, leave movement / auto / explore orders
            if (unit.isSleeping() || unit.isFortified())
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

    fun moveToTile(destination: TileInfo, considerZoneOfControl: Boolean = true) {
        if (destination == unit.getTile() || unit.isDestroyed) return // already here (or dead)!


        if (unit.baseUnit.movesLikeAirUnits()) { // air units move differently from all other units
            unit.action = null
            unit.removeFromTile()
            unit.isTransported = false // it has left the carrier by own means
            unit.putInTile(destination)
            unit.currentMovement = 0f
            unit.mostRecentMoveType = UnitMovementMemoryType.UnitTeleported
            return
        } else if (unit.isPreparingParadrop()) { // paradropping units move differently
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
                && unit.civInfo.hasUnique(UniqueType.UnitsInCitiesNoMaintenance)
            ) unit.civInfo.updateStatsForNextTurn()
            return
        }

        val distanceToTiles = getDistanceToTiles(considerZoneOfControl)
        val pathToDestination = distanceToTiles.getPathToTile(destination)
        val movableTiles = pathToDestination.takeWhile { canPassThrough(it) }
        val lastReachableTile = movableTiles.lastOrNull { canMoveTo(it) }
            ?: return  // no tiles can pass though/can move to
        unit.mostRecentMoveType = UnitMovementMemoryType.UnitMoved
        val pathToLastReachableTile = distanceToTiles.getPathToTile(lastReachableTile)

        if (unit.isFortified() || unit.isSetUpForSiege() || unit.isSleeping())
            unit.action = null // un-fortify/un-setup/un-sleep after moving

        // If this unit is a carrier, keep record of its air payload whereabouts.
        val origin = unit.getTile()
        var needToFindNewRoute = false
        // Cache this in case something goes wrong

        var lastReachedEnterableTile = unit.getTile()
        var previousTile = unit.getTile()
        var passingMovementSpent = 0f // Movement points spent since last tile we could end our turn on

        unit.removeFromTile()

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
            unit.moveThroughTile(tile)

            // This fixes a bug where tiles in the fog of war would always only cost 1 mp
            if (!unit.civInfo.gameInfo.gameParameters.godMode)
                passingMovementSpent += getMovementCostBetweenAdjacentTiles(previousTile, tile, unit.civInfo)

            // In case something goes wrong, cache the last tile we were able to end on
            // We can assume we can pass through this tile, as we would have broken earlier
            if (unit.movement.canMoveTo(tile, assumeCanPassThrough = true)) {
                lastReachedEnterableTile = tile
                unit.useMovementPoints(passingMovementSpent)
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


        if (!unit.isDestroyed)
            unit.putInTile(finalTileReached)

        // The .toList() here is because we have a sequence that's running on the units in the tile,
        // then if we move one of the units we'll get a ConcurrentModificationException, se we save them all to a list
        val payloadUnits = origin.getUnits().filter { it.isTransported && unit.canTransport(it) }.toList()
        // bring along the payloads
        for (payload in payloadUnits) {
            payload.removeFromTile()
            for (tile in pathToLastReachableTile){
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
            && unit.civInfo.hasUnique(UniqueType.UnitsInCitiesNoMaintenance)
        ) unit.civInfo.updateStatsForNextTurn()
        if (needToFindNewRoute) moveToTile(destination, considerZoneOfControl)
    }

    /**
     * Swaps this unit with the unit on the given tile
     * Precondition: this unit can swap-move to the given tile, as determined by canUnitSwapTo
     */
    fun swapMoveToTile(destination: TileInfo) {
        val otherUnit = (
            if (unit.isCivilian())
                destination.civilianUnit
            else
                destination.militaryUnit
        )?: return // The precondition guarantees that there is an eligible same-type unit at the destination

        val ourOldPosition = unit.getTile()
        val theirOldPosition = otherUnit.getTile()

        val ourPayload = ourOldPosition.getUnits().filter { it.isTransported }.toList()
        val theirPayload = theirOldPosition.getUnits().filter { it.isTransported }.toList()

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

    /**
     * Designates whether we can enter the tile - without attacking
     * DOES NOT designate whether we can reach that tile in the current turn
     */
    fun canMoveTo(tile: TileInfo, assumeCanPassThrough: Boolean = false): Boolean {
        if (unit.baseUnit.movesLikeAirUnits())
            return canAirUnitMoveTo(tile, unit)

        if (!assumeCanPassThrough && !canPassThrough(tile))
            return false

        // even if they'll let us pass through, we can't enter their city - unless we just captured it
        if (tile.isCityCenter() && tile.getOwner() != unit.civInfo && !tile.getCity()!!.hasJustBeenConquered)
            return false

        return if (unit.isCivilian())
            tile.civilianUnit == null && (tile.militaryUnit == null || tile.militaryUnit!!.owner == unit.owner)
        else
            // can skip checking for airUnit since not a city
            tile.militaryUnit == null && (tile.civilianUnit == null || tile.civilianUnit!!.owner == unit.owner || unit.civInfo.isAtWarWith(tile.civilianUnit!!.civInfo))
    }

    private fun canAirUnitMoveTo(tile: TileInfo, unit: MapUnit): Boolean {
        // landing in the city
        if (tile.isCityCenter()) {
            if (tile.airUnits.filter { !it.isTransported }.size < 6 && tile.getCity()?.civInfo == unit.civInfo)
                return true // if city is free - no problem, get in
        } // let's check whether it enters city on carrier now...

        if (tile.militaryUnit != null) {
            val unitAtDestination = tile.militaryUnit!!
            return unitAtDestination.canTransport(unit)
        }
        return false
    }

    // Can a paratrooper land at this tile?
    fun canParadropOn(destination: TileInfo): Boolean {
        // Can only move to land tiles within range that are visible and not impassible
        // Based on some testing done in the base game
        if (!destination.isLand || destination.isImpassible() || !unit.civInfo.viewableTiles.contains(destination)) return false
        return true
    }

    /**
     * @returns whether this unit can pass through [tile].
     * Note that sometimes, a tile can be passed through but not entered. Use [canMoveTo] to
     * determine whether a unit can enter a tile.
     *
     * This is the most called function in the entire game,
     * so multiple callees of this function have been optimized,
     * because optimization on this function results in massive benefits!
     */
    fun canPassThrough(tile: TileInfo): Boolean {
        if (tile.isImpassible()) {
            // special exception - ice tiles are technically impassible, but some units can move through them anyway
            // helicopters can pass through impassable tiles like mountains
            if (!unit.canPassThroughImpassableTiles && !(unit.canEnterIceTiles && tile.terrainFeatures.contains(Constants.ice))
                // carthage-like uniques sometimes allow passage through impassible tiles
                && !(unit.civInfo.passThroughImpassableUnlocked && unit.civInfo.passableImpassables.contains(tile.getLastTerrain().name)))
                return false
        }
        if (tile.isLand
                && unit.baseUnit.isWaterUnit()
                && !tile.isCityCenter())
            return false

        val unitSpecificAllowOcean: Boolean by lazy {
            unit.civInfo.tech.specificUnitsCanEnterOcean &&
                    unit.civInfo.getMatchingUniques(UniqueType.UnitsMayEnterOcean)
                        .any { unit.matchesFilter(it.params[0]) }
        }
        if (tile.isWater && unit.baseUnit.isLandUnit()) {
            if (!unit.civInfo.tech.unitsCanEmbark) return false
            if (tile.isOcean && !unit.civInfo.tech.embarkedUnitsCanEnterOcean && !unitSpecificAllowOcean)
                return false
        }
        if (tile.isOcean && !unit.civInfo.tech.allUnitsCanEnterOcean) { // Apparently all Polynesian naval units can enter oceans
            if (!unitSpecificAllowOcean && unit.cannotEnterOceanTiles) return false
        }
        if (tile.naturalWonder != null) return false

        if (!unit.canEnterForeignTerrain && !tile.canCivPassThrough(unit.civInfo)) return false

        // The first unit is:
        //   1. Either military unit
        //   2. or unprotected civilian
        //   3. or unprotected air unit while no civilians on tile
        val firstUnit = tile.getFirstUnit()
        // Moving to non-empty tile
        if (firstUnit != null && unit.civInfo != firstUnit.civInfo) {
            // Allow movement through unguarded, at-war Civilian Unit. Capture on the way
            // But not for Embarked Units capturing on Water
            if (!(unit.isEmbarked() && tile.isWater)
                    && firstUnit.isCivilian() && unit.civInfo.isAtWarWith(firstUnit.civInfo))
                return true
            // Cannot enter hostile tile with any unit in there
            if (unit.civInfo.isAtWarWith(firstUnit.civInfo))
                return false
        }

        return true
    }


    fun getDistanceToTiles(considerZoneOfControl: Boolean = true): PathsToTilesWithinTurn = getDistanceToTilesWithinTurn(unit.currentTile.position, unit.currentMovement, considerZoneOfControl)

    fun getAerialPathsToCities(): HashMap<TileInfo, ArrayList<TileInfo>> {
        var tilesToCheck = ArrayList<TileInfo>()
        /** each tile reached points to its parent tile, where we got to it from */
        val tilesReached = HashMap<TileInfo, TileInfo>()

        val startingTile = unit.currentTile
        tilesToCheck.add(startingTile)
        tilesReached[startingTile] = startingTile


        while (tilesToCheck.isNotEmpty()) {
            val newTilesToCheck = ArrayList<TileInfo>()
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

        val pathsToCities = HashMap<TileInfo, ArrayList<TileInfo>>()

        for (city in tilesReached.keys) {
            val path = ArrayList<TileInfo>()
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
     * @returns the set of [TileInfo] between [from] and [to] tiles.
     * It takes into account the terrain and units possibilities of entering the terrain,
     * however ignores the diplomatic aspects of such movement like crossing closed borders.
     */
    private fun getPathBetweenTiles(from: TileInfo, to: TileInfo): MutableSet<TileInfo> {
        val tmp = unit.canEnterForeignTerrain
        unit.canEnterForeignTerrain = true // the trick to ignore tiles owners
        val bfs = BFS(from) { canPassThrough(it) }
        bfs.stepUntilDestination(to)
        unit.canEnterForeignTerrain = tmp
        return bfs.getReachedTiles()
    }

}

class PathsToTilesWithinTurn : LinkedHashMap<TileInfo, UnitMovementAlgorithms.ParentTileAndTotalDistance>() {
    fun getPathToTile(tile: TileInfo): List<TileInfo> {
        if (!containsKey(tile))
            throw Exception("Can't reach this tile!")
        val reversePathList = ArrayList<TileInfo>()
        var currentTile = tile
        while (get(currentTile)!!.parentTile != currentTile) {
            reversePathList.add(currentTile)
            currentTile = get(currentTile)!!.parentTile
        }
        return reversePathList.reversed()
    }
}
