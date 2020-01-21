package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.civilization.CivilizationInfo

class UnitMovementAlgorithms(val unit:MapUnit) {

    // This function is called ALL THE TIME and should be as time-optimal as possible!
     fun getMovementCostBetweenAdjacentTiles(from: TileInfo, to: TileInfo, civInfo: CivilizationInfo): Float {

        if ((from.isLand != to.isLand) && unit.type.isLandUnit())
            return 100f // this is embarkment or disembarkment, and will take the entire turn

        var extraCost = 0f

        val toOwner = to.getOwner()
        if (toOwner != null && to.isLand && toOwner.hasActiveGreatWall && civInfo.isAtWarWith(toOwner))
            extraCost += 1

        if (from.roadStatus === RoadStatus.Railroad && to.roadStatus === RoadStatus.Railroad)
            return 1 / 10f + extraCost

        if (from.hasRoad(civInfo) && to.hasRoad(civInfo))
        {
            if (unit.civInfo.tech.movementSpeedOnRoadsImproved) return 1 / 3f + extraCost
            else return 1 / 2f + extraCost
        }
        if (unit.ignoresTerrainCost) return 1f + extraCost
        if (unit.doubleMovementInForestAndJungle && (to.baseTerrain == Constants.forest || to.baseTerrain == Constants.jungle))
            return 1f + extraCost

        if (unit.roughTerrainPenalty
                && (to.baseTerrain == Constants.hill || to.terrainFeature == Constants.forest || to.terrainFeature == Constants.jungle))
            return 4f + extraCost

        if (unit.doubleMovementInCoast && to.baseTerrain == Constants.coast)
            return 1 / 2f + extraCost

        return to.getLastTerrain().movementCost.toFloat() + extraCost // no road
    }

    class ParentTileAndTotalDistance(val parentTile:TileInfo, val totalDistance: Float)

    fun getDistanceToTilesWithinTurn(origin: Vector2, unitMovement: Float): PathsToTilesWithinTurn {
        if(unitMovement==0f) return PathsToTilesWithinTurn()

        val distanceToTiles = PathsToTilesWithinTurn()
        val unitTile = unit.getTile().tileMap[origin]
        distanceToTiles[unitTile] = ParentTileAndTotalDistance(unitTile,0f)
        var tilesToCheck = listOf(unitTile)

        while (tilesToCheck.isNotEmpty()) {
            val updatedTiles = ArrayList<TileInfo>()
            for (tileToCheck in tilesToCheck)
                for (neighbor in tileToCheck.neighbors) {
                    var totalDistanceToTile:Float

                    if (!canPassThrough(neighbor))
                        totalDistanceToTile = unitMovement // Can't go here.
                    // The reason that we don't just "return" is so that when calculating how to reach an enemy,
                    // You need to assume his tile is reachable, otherwise all movement algs on reaching enemy
                    // cities and units goes kaput.

                    else {
                        val distanceBetweenTiles = getMovementCostBetweenAdjacentTiles(tileToCheck, neighbor, unit.civInfo)
                        totalDistanceToTile = distanceToTiles[tileToCheck]!!.totalDistance + distanceBetweenTiles
                    }

                    if (!distanceToTiles.containsKey(neighbor) || distanceToTiles[neighbor]!!.totalDistance > totalDistanceToTile) { // this is the new best path
                        if (totalDistanceToTile < unitMovement)  // We can still keep moving from here!
                            updatedTiles += neighbor
                        else
                            totalDistanceToTile = unitMovement
                        // In Civ V, you can always travel between adjacent tiles, even if you don't technically
                        // have enough movement points - it simple depletes what you have

                        distanceToTiles[neighbor] = ParentTileAndTotalDistance(tileToCheck,totalDistanceToTile)
                    }
                }

            tilesToCheck = updatedTiles
        }

        return distanceToTiles
    }

    fun getShortestPath(destination: TileInfo): List<TileInfo> {
        val currentTile = unit.getTile()
        if (currentTile.position == destination) return listOf(currentTile) // edge case that's needed, so that workers will know that they can reach their own tile. *sigh*

        var tilesToCheck: List<TileInfo> = listOf(currentTile)
        val movementTreeParents = HashMap<TileInfo, TileInfo?>() // contains a map of "you can get from X to Y in that turn"
        movementTreeParents[currentTile] = null

        var distance = 1
        while (true) {
            val newTilesToCheck = ArrayList<TileInfo>()
            val distanceToDestination = HashMap<TileInfo, Float>()
            val movementThisTurn = if (distance == 1) unit.currentMovement else unit.getMaxMovement().toFloat()
            for (tileToCheck in tilesToCheck) {
                val distanceToTilesThisTurn = getDistanceToTilesWithinTurn(tileToCheck.position, movementThisTurn)
                for (reachableTile in distanceToTilesThisTurn.keys) {
                    if (reachableTile == destination)
                        distanceToDestination[tileToCheck] = distanceToTilesThisTurn[reachableTile]!!.totalDistance
                    else {
                        if (movementTreeParents.containsKey(reachableTile)) continue // We cannot be faster than anything existing...
                        if (!canMoveTo(reachableTile)) continue // This is a tile that we can''t actually enter - either an intermediary tile containing our unit, or an enemy unit/city
                        movementTreeParents[reachableTile] = tileToCheck
                        newTilesToCheck.add(reachableTile)
                    }
                }
            }

            if (distanceToDestination.isNotEmpty()) {
                val path = mutableListOf(destination) // Traverse the tree upwards to get the list of tiles leading to the destination,
                // Get the tile from which the distance to the final tile in least -
                // this is so that when we finally get there, we'll have as many movement points as possible
                var intermediateTile = distanceToDestination.minBy { it.value }!!.key
                while (intermediateTile != currentTile) {
                    path.add(intermediateTile)
                    intermediateTile = movementTreeParents[intermediateTile]!!
                }
                return path.reversed() // and reverse in order to get the list in chronological order
            }

            if (newTilesToCheck.isEmpty()) return emptyList() // there is NO PATH (eg blocked by enemy units)

            // no need to check tiles that are surrounded by reachable tiles, only need to check the edgemost tiles.
            // Because anything we can reach from intermediate tiles, can be more easily reached by the edgemost tiles,
            // since we'll have to pass through an edgemost tile in order to reach the diestination anyway
            tilesToCheck = newTilesToCheck.filterNot { tile -> tile.neighbors.all { newTilesToCheck.contains(it) || tilesToCheck.contains(it) } }

            distance++
        }
    }

    fun getTileToMoveToThisTurn(finalDestination:TileInfo): TileInfo {

        val currentTile = unit.getTile()
        if (currentTile == finalDestination) return currentTile

        if(unit.type.isAirUnit()){
            return finalDestination // head there directly
        }


        val distanceToTiles = getDistanceToTiles()

        if (distanceToTiles.containsKey(finalDestination)) { // we should be able to get there this turn
            if (canMoveTo(finalDestination))
                return finalDestination

            // Someone is blocking to the path to the final tile...
            val destinationNeighbors = finalDestination.neighbors
            if (destinationNeighbors.contains(currentTile)) // We're right nearby anyway, no need to move
                return currentTile

            val reachableDestinationNeighbors = destinationNeighbors
                    .filter { distanceToTiles.containsKey(it) && canMoveTo(it) }
            if (reachableDestinationNeighbors.isEmpty()) // We can't get closer...
                return currentTile

            return reachableDestinationNeighbors.minBy { distanceToTiles[it]!!.totalDistance }!! // we can get a little closer
        }   // If the tile is far away, we need to build a path how to get there, and then take the first step
        val path = getShortestPath(finalDestination)
        class UnreachableDestinationException : Exception()
        if (path.isEmpty()) throw UnreachableDestinationException()
        return path.first()
    }

    /**
     * @return The tile that we reached this turn
     */
    fun headTowards(destination: TileInfo): TileInfo {
        val destinationTileThisTurn = getTileToMoveToThisTurn(destination)
        moveToTile(destinationTileThisTurn)
        return destinationTileThisTurn
    }

    fun canReach(destination: TileInfo): Boolean {
        if(unit.type.isAirUnit())
            return unit.currentTile.arialDistanceTo(destination) <= unit.getRange()
        return getShortestPath(destination).isNotEmpty()
    }


    fun teleportToClosestMoveableTile(){
        var allowedTile:TileInfo? = null
        var distance=0
        // When we didn't limit the allowed distance the game would sometimes spend a whole minute looking for a suitable tile.
        while(allowedTile==null && distance<5){
            distance++
            allowedTile = unit.getTile().getTilesAtDistance(distance)
                    .firstOrNull{canMoveTo(it)}
        }

        // No tile within 4 spaces? move him to a city.
        if(allowedTile==null){
            for(city in unit.civInfo.cities){
                allowedTile = city.getTiles()
                        .firstOrNull { canMoveTo(it) }
                if(allowedTile!=null) break
            }
        }
        unit.removeFromTile() // we "teleport" them away
        if(allowedTile!=null) // it's possible that there is no close tile, and all the guy's cities are full. Screw him then.
            unit.putInTile(allowedTile)
    }


    fun moveToTile(destination: TileInfo) {
        if(destination==unit.getTile()) return // already here!

        class CantEnterThisTileException(msg: String) : Exception(msg)
        if(!canMoveTo(destination))
            throw CantEnterThisTileException("$unit can't enter $destination")

        if(unit.type.isAirUnit()){ // they move differently from all other units
            unit.action=null
            unit.removeFromTile()
            unit.putInTile(destination)
            unit.currentMovement=0f
            return
        }

        val distanceToTiles = getDistanceToTiles()
        class YouCantGetThereFromHereException(msg: String) : Exception(msg)
        if (!distanceToTiles.containsKey(destination))
            throw YouCantGetThereFromHereException("$unit can't get from ${unit.currentTile.position} to ${destination.position}.")

        if(destination.isCityCenter() && destination.getOwner()!=unit.civInfo && !destination.getCity()!!.hasJustBeenConquered)
            throw Exception("This is an enemy city, you can't go here!")

        unit.currentMovement -= distanceToTiles[destination]!!.totalDistance
        if (unit.currentMovement < 0.1) unit.currentMovement = 0f // silly floats which are "almost zero"
        if(unit.isFortified() || unit.action==Constants.unitActionSetUp || unit.action==Constants.unitActionSleep)
            unit.action=null // unfortify/setup after moving

        // If this unit is a carrier, keep record of its air payload whereabouts.
        var origin = unit.getTile()

        unit.removeFromTile()
        unit.putInTile(destination)

        // Unit maintenance changed
        if (unit.canGarrison()
            && (origin.isCityCenter() || destination.isCityCenter())
            && unit.civInfo.policies.isAdopted("Oligarchy")
        ) unit.civInfo.updateStatsForNextTurn()

        if(unit.type.isAircraftCarrierUnit() || unit.type.isMissileCarrierUnit()){ // bring along the payloads
            for(airUnit in origin.airUnits.filter { !it.isUnitInCity }){
                airUnit.removeFromTile()
                airUnit.putInTile(destination)
                airUnit.isUnitInCity = false // don't leave behind payloads in the city if carrier happens to dock
            }
        }

        // Move through all intermediate tiles to get ancient ruins, barb encampments
        // and to view tiles along the way
        // We only activate the moveThroughTile AFTER the putInTile because of a really weird bug -
        // If you're going to (or past) a ruin, and you activate the ruin bonus, and A UNIT spawns.
        // That unit could now be blocking your entrance to the destination, so the putInTile would fail! =0
        // Instead, we move you to the destination directly, and only afterwards activate the various tiles on the way.
        val pathToFinalTile = distanceToTiles.getPathToTile(destination)
        for(tile in pathToFinalTile){
            unit.moveThroughTile(tile)
        }

    }


    /**
     * Designates whether we can enter the tile - without attacking
     * DOES NOT designate whether we can reach that tile in the current turn
     */
    fun canMoveTo(tile: TileInfo): Boolean {
        if(unit.type.isAirUnit())
            if(tile.isCityCenter())
                return tile.airUnits.size<6 && tile.getCity()?.civInfo==unit.civInfo
            else if(tile.militaryUnit!=null) {
                val unitAtDestination = tile.militaryUnit!!

                var unitCapacity = if (unitAtDestination.getUniques().contains("Can carry 2 aircraft")) 2 else 0
                // unitCapacity += unitAtDestination.getUniques().count { it == "Can carry 1 extra air unit" }

                return ((unitAtDestination.type.isAircraftCarrierUnit() && !unit.type.isMissileUnit()) ||
                        (unitAtDestination.type.isMissileCarrierUnit() && unit.type.isMissileUnit()))
                        && unitAtDestination.owner==unit.owner && tile.airUnits.size < unitCapacity
            } else
                return false

        if(!canPassThrough(tile))
            return false

        if (unit.type.isCivilian())
            return tile.civilianUnit==null && (tile.militaryUnit==null || tile.militaryUnit!!.owner==unit.owner)
        else return tile.militaryUnit==null && (tile.civilianUnit==null || tile.civilianUnit!!.owner==unit.owner)
    }


    // This is the most called function in the entire game,
    // so multiple callees of this function have been optimized,
    // because optimization on this function results in massive benefits!
    fun canPassThrough(tile: TileInfo):Boolean{
        if(tile.getBaseTerrain().impassable) return false
        if (tile.isLand
                && unit.type.isWaterUnit()
                // Check that the tile is not a coastal city's center
                && !(tile.isCityCenter() && tile.isCoastalTile()))
            return false

        if(tile.isWater && unit.type.isLandUnit()){
            if(!unit.civInfo.tech.unitsCanEmbark) return false
            if(tile.isOcean && !unit.civInfo.tech.embarkedUnitsCanEnterOcean)
                return false
        }
        if (tile.isOcean && unit.civInfo.nation.unique != "Can embark and move over Coasts and Oceans immediately. +1 Sight when embarked. +10% Combat Strength bonus if within 2 tiles of a Moai.") {
            if (unit.baseUnit.uniques.contains("Cannot enter ocean tiles")) return false
            if (unit.baseUnit.uniques.contains("Cannot enter ocean tiles until Astronomy")
                && !unit.civInfo.tech.isResearched("Astronomy"))
                return false
        }
        if (tile.naturalWonder != null) return false

        val tileOwner = tile.getOwner()
        if(tileOwner!=null && tileOwner.civName!=unit.owner) {
            if (tile.isCityCenter() && !tile.getCity()!!.hasJustBeenConquered) return false
            if (!unit.civInfo.canEnterTiles(tileOwner)
                    && !(unit.civInfo.isPlayerCivilization() && tileOwner.isCityState())) return false
            // AIs won't enter city-state's border.
        }

        val unitsInTile = tile.getUnits()
        if(unitsInTile.isNotEmpty()){
            val firstUnit = unitsInTile.first()
            if(firstUnit.civInfo != unit.civInfo && unit.civInfo.isAtWarWith(firstUnit.civInfo))
                return false
        }

        return true
    }

    fun getDistanceToTiles() = getDistanceToTilesWithinTurn(unit.currentTile.position,unit.currentMovement)

    fun getArialPathsToCities(): HashMap<TileInfo, ArrayList<TileInfo>> {
        var tilesToCheck = ArrayList<TileInfo>()
        /** each tile reached points to its parent tile, where we got to it from */
        val tilesReached = HashMap<TileInfo, TileInfo>()

        val startingTile = unit.currentTile
        tilesToCheck.add(startingTile)
        tilesReached[startingTile] = startingTile


        while(tilesToCheck.isNotEmpty()) {
            val newTilesToCheck = ArrayList<TileInfo>()
            for(currentTileToCheck in tilesToCheck){
                val reachableTiles = currentTileToCheck.getTilesInDistance(unit.getRange())
                        .filter { unit.movement.canMoveTo(it) }
                for(reachableTile in reachableTiles) {
                    if (tilesReached.containsKey(reachableTile)) continue
                    tilesReached[reachableTile] = currentTileToCheck
                    newTilesToCheck.add(reachableTile)
                }
            }
            tilesToCheck=newTilesToCheck
        }

        val pathsToCities = HashMap<TileInfo, ArrayList<TileInfo>>()

        for(city in tilesReached.keys){
            val path = ArrayList<TileInfo>()
            var currentCity = city
            while(currentCity!=startingTile){ // we don't add the "starting tile" to the arraylist
                path.add(currentCity)
                currentCity = tilesReached[currentCity]!! // go to parent
            }
            path.reverse()
            pathsToCities[city] = path
        }

        pathsToCities.remove(startingTile)
        return pathsToCities
    }
}

class PathsToTilesWithinTurn : LinkedHashMap<TileInfo, UnitMovementAlgorithms.ParentTileAndTotalDistance>(){
    fun getPathToTile(tile: TileInfo): List<TileInfo> {
        if(!containsKey(tile)) throw Exception("Can't reach this tile!")
        val reversePathList = ArrayList<TileInfo>()
        var currentTile = tile
        while(get(currentTile)!!.parentTile!=currentTile){
            reversePathList.add(currentTile)
            currentTile = get(currentTile)!!.parentTile
        }
        return reversePathList.reversed()
    }
}