package com.unciv.logic.automation.unit

import com.badlogic.gdx.math.Vector2
import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.BFS
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.MapPathing
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.utils.Log
import com.unciv.utils.debug


private object WorkerAutomationConst {
    /** BFS max size is determined by the aerial distance of two cities to connect, padded with this */
    // two tiles longer than the distance to the nearest connected city should be enough as the 'reach' of a BFS is increased by blocked tiles
    const val maxBfsReachPadding = 2
}

class RoadAutomation(val civInfo: Civilization, cachedForTurn:Int, cloningSource: RoadAutomation? = null) {

    //region Cache
    private val ruleSet = civInfo.gameInfo.ruleset

    /** Caches BFS by city locations (cities needing connecting).
     *
     *  key: The city to connect from as [hex position][Vector2].
     *
     *  value: The [BFS] searching from that city, whether successful or not.
     */
    //todo: If BFS were to deal in vectors instead of Tiles, we could copy this on cloning
    private val bfsCache = HashMap<Vector2, BFS>()

    /** Caches road to build for connecting cities unless option is off or ruleset removed all roads */
    internal val bestRoadAvailable: RoadStatus =
        cloningSource?.bestRoadAvailable ?:
        //Player can choose not to auto-build roads & railroads.
        if (civInfo.isHuman() && (!UncivGame.Current.settings.autoBuildingRoads
                || UncivGame.Current.settings.autoPlay.isAutoPlayingAndFullAI()))
            RoadStatus.None
        else civInfo.tech.getBestRoadAvailable()

    /** Same as above, but ignores the option */
    private val actualBestRoadAvailable: RoadStatus = civInfo.tech.getBestRoadAvailable()

    /** Civ-wide list of unconnected Cities, sorted by closest to capital first */
    private val citiesThatNeedConnecting: List<City> by lazy {
        val result = civInfo.cities.asSequence()
            .filter {
                civInfo.getCapital() != null
                    && it.population.population > 3
                    && !it.isCapital() && !it.isBeingRazed // Cities being razed should not be connected.
                    && !it.cityStats.isConnectedToCapital(bestRoadAvailable)
            }.sortedBy {
                it.getCenterTile().aerialDistanceTo(civInfo.getCapital()!!.getCenterTile())
            }.toList()
        if (Log.shouldLog()) {
            debug("WorkerAutomation citiesThatNeedConnecting for ${civInfo.civName} turn $cachedForTurn:")
            if (result.isEmpty())
                debug("\tempty")
            else result.forEach {
                debug("\t${it.name}")
            }
        }
        result
    }

    /** Civ-wide list of _connected_ Cities, unsorted */
    private val tilesOfConnectedCities: List<Tile> by lazy {
        val result = civInfo.cities.asSequence()
            .filter { it.isCapital() || it.cityStats.isConnectedToCapital(bestRoadAvailable) }
            .map { it.getCenterTile() }
            .toList()
        if (Log.shouldLog()) {
            debug("WorkerAutomation tilesOfConnectedCities for ${civInfo.civName} turn $cachedForTurn:")
            if (result.isEmpty())
                debug("\tempty")
            else result.forEach {
                debug("\t$it")
            }
        }
        result
    }

    /** Cache of roads to connect cities each turn */
    internal val roadsToConnectCitiesCache: HashMap<City, List<Tile>> = HashMap()

    /** Hashmap of all cached tiles in each list in [roadsToConnectCitiesCache] */
    internal val tilesOfRoadsToConnectCities: HashMap<Tile, City> = HashMap()

    //endregion

    //region Functions
    /**
     * Automate the process of connecting a road between two points.
     * Current thoughts:
     * Will be a special case of MapUnit.automated property
     * Unit has new attributes startTile endTile
     * - We will progress towards the end path sequentially, taking absolute least distance w/o regard for movement cost
     * - Cancel upon risk of capture
     * - Cancel upon blocked
     * - End automation upon finish
     */
    // TODO: Caching
    // TODO: Hide the automate road button if road is not unlocked
    fun automateConnectRoad(unit: MapUnit, tilesWhereWeWillBeCaptured: Set<Tile>){
        if (actualBestRoadAvailable == RoadStatus.None) return

        var currentTile = unit.getTile()

        /** Reset side effects from automation, return worker to non-automated state*/
        fun stopAndCleanAutomation(){
            unit.automated = false
            unit.action = null
            unit.automatedRoadConnectionDestination = null
            unit.automatedRoadConnectionPath = null
            currentTile.stopWorkingOnImprovement()
        }

        if (unit.automatedRoadConnectionDestination == null){
            stopAndCleanAutomation()
            return
        }

        /** Conditions for whether it is acceptable to build a road on this tile */
        fun shouldBuildRoadOnTile(tile: Tile): Boolean {
            return !tile.isCityCenter() // Can't build road on city tiles
                // Special case for civs that treat forest/jungles as roads (inside their territory). We shouldn't build if railroads aren't unlocked.
                && !(tile.hasConnection(unit.civ) && actualBestRoadAvailable == RoadStatus.Road)
                // Build (upgrade) if possible
                && tile.roadStatus != actualBestRoadAvailable
                // Build if the road is pillaged
                || tile.roadIsPillaged
        }

        val destinationTile = unit.civ.gameInfo.tileMap[unit.automatedRoadConnectionDestination!!]

        var pathToDest: List<Vector2>? = unit.automatedRoadConnectionPath

        // The path does not exist, create it
        if (pathToDest == null) {
            val foundPath: List<Tile>? = MapPathing.getRoadPath(unit, currentTile, destinationTile)
            if (foundPath == null) {
                Log.debug("WorkerAutomation: $unit -> connect road failed")
                stopAndCleanAutomation()
                unit.civ.addNotification("Connect road failed!", currentTile.position, NotificationCategory.Units, NotificationIcon.Construction)
                return
            }

            pathToDest = foundPath // Convert to a list of positions for serialization
                .map { it.position }

            unit.automatedRoadConnectionPath = pathToDest
            debug("WorkerAutomation: $unit -> found connect road path to destination tile: %s, %s", destinationTile, pathToDest)
        }

        val currTileIndex = pathToDest.indexOf(currentTile.position)

        // The worker was somehow moved off its path, cancel the action
        if (currTileIndex == -1) {
            Log.debug("$unit -> was moved off its connect road path. Operation cancelled.")
            stopAndCleanAutomation()
            unit.civ.addNotification("Connect road cancelled!", currentTile.position, NotificationCategory.Units, unit.name)
            return
        }

        /* Can not build a road on this tile, try to move on.
        * The worker should search for the next furthest tile in the path that:
        * - It can move to
        * - Can be improved/upgraded
        * */
        if (unit.currentMovement > 0 && !shouldBuildRoadOnTile(currentTile)) {
            if (currTileIndex == pathToDest.size - 1) { // The last tile in the path is unbuildable or has a road.
                stopAndCleanAutomation()
                unit.civ.addNotification("Connect road completed!", currentTile.position, NotificationCategory.Units, unit.name)
                return
            }

            if (currTileIndex < pathToDest.size - 1) { // Try to move to the next tile in the path
                val tileMap = unit.civ.gameInfo.tileMap
                var nextTile: Tile = currentTile

                // Create a new list with tiles where the index is greater than currTileIndex
                val futureTiles = pathToDest.asSequence()
                    .dropWhile { it != unit.currentTile.position }
                    .drop(1)
                    .map { tileMap[it] }



                for (futureTile in futureTiles) { // Find the furthest tile we can reach in this turn, move to, and does not have a road
                    if (unit.movement.canReachInCurrentTurn(futureTile) && unit.movement.canMoveTo(futureTile)) { // We can at least move to this tile
                        nextTile = futureTile
                        if (shouldBuildRoadOnTile(futureTile)) {
                            break // Stop on this tile
                        }
                    }
                }

                unit.movement.moveToTile(nextTile)
                currentTile = unit.getTile()
            }
        }

        // We need to check current movement again after we've (potentially) moved
        if (unit.currentMovement > 0) {
            // Repair pillaged roads first
            if (currentTile.roadStatus != RoadStatus.None && currentTile.roadIsPillaged){
                currentTile.setRepaired()
                return
            }
            if (shouldBuildRoadOnTile(currentTile) && currentTile.improvementInProgress != actualBestRoadAvailable.name) {
                val improvement = actualBestRoadAvailable.improvement(ruleSet)!!
                currentTile.startWorkingOnImprovement(improvement, civInfo, unit)
                return
            }
        }
    }



    /**
     * Uses a cache to find and return the connection to make that is associated with a city.
     * May not work if the unit that originally created this cache is different from the next.
     * (Due to the difference in [UnitMovement.canPassThrough()])
     */
    private fun getRoadConnectionBetweenCities(unit: MapUnit, city: City): List<Tile> {
        if (city in roadsToConnectCitiesCache) return roadsToConnectCitiesCache[city]!!

        val isCandidateTilePredicate: (Tile) -> Boolean = { it.isLand && unit.movement.canPassThrough(it) }
        val toConnectTile = city.getCenterTile()
        val bfs: BFS = bfsCache[toConnectTile.position] ?:
        BFS(toConnectTile, isCandidateTilePredicate).apply {
            maxSize = HexMath.getNumberOfTilesInHexagon(
                WorkerAutomationConst.maxBfsReachPadding +
                    tilesOfConnectedCities.minOf { it.aerialDistanceTo(toConnectTile) }
            )
            bfsCache[toConnectTile.position] = this@apply
        }
        val cityTilesToSeek = HashSet(tilesOfConnectedCities)

        var nextTile = bfs.nextStep()
        while (nextTile != null) {
            if (nextTile in cityTilesToSeek) {
                // We have a winner!
                val cityTile = nextTile
                val pathToCity = bfs.getPathTo(cityTile)
                roadsToConnectCitiesCache[city] = pathToCity.toList().filter { it.roadStatus != bestRoadAvailable }
                for (tile in pathToCity) {
                    if (tile !in tilesOfRoadsToConnectCities)
                        tilesOfRoadsToConnectCities[tile] = city
                }
                return roadsToConnectCitiesCache[city]!!
            }
            nextTile = bfs.nextStep()
        }

        roadsToConnectCitiesCache[city] = listOf()
        return roadsToConnectCitiesCache[city]!!
    }


    /**
     * Most importantly builds the cache so that [chooseImprovement] knows later what tiles a road should be built on
     * Returns a list of all the cities close by that this worker may want to connect
     */
    internal fun getNearbyCitiesToConnect(unit: MapUnit): List<City> {
        if (bestRoadAvailable == RoadStatus.None || citiesThatNeedConnecting.isEmpty()) return listOf()
        val candidateCities = citiesThatNeedConnecting.filter {
            // Cities that are too far away make the canReach() calculations devastatingly long
            it.getCenterTile().aerialDistanceTo(unit.getTile()) < 20
        }
        if (candidateCities.none()) return listOf() // do nothing.

        // Search through ALL candidate cities to build the cache
        for (toConnectCity in candidateCities) {
            getRoadConnectionBetweenCities(unit, toConnectCity).filter { it.getUnpillagedRoad() < bestRoadAvailable }
        }
        return candidateCities
    }

    /**
     * Looks for work connecting cities. Used to search for far away roads to build.
     * @return whether we actually did anything
     */
    internal fun tryConnectingCities(unit: MapUnit, candidateCities: List<City>): Boolean {
        if (bestRoadAvailable == RoadStatus.None || citiesThatNeedConnecting.isEmpty()) return false

        if (candidateCities.none()) return false // do nothing.
        val currentTile = unit.getTile()
        var bestTileToConstructRoadOn: Tile? = null
        var bestTileToConstructRoadOnDist: Int = Int.MAX_VALUE

        // Search through ALL candidate cities for the closest tile to build a road on
        for (toConnectCity in candidateCities) {
            val roadableTiles = getRoadConnectionBetweenCities(unit, toConnectCity).filter { it.getUnpillagedRoad() < bestRoadAvailable }
            val reachableTile = roadableTiles.map { Pair(it, it.aerialDistanceTo(unit.getTile())) }
                .filter { it.second < bestTileToConstructRoadOnDist }
                .sortedBy { it.second }
                .firstOrNull {
                    unit.movement.canMoveTo(it.first) && unit.movement.canReach(it.first)
                } ?: continue // Apparently we can't reach any of these tiles at all
            bestTileToConstructRoadOn = reachableTile.first
            bestTileToConstructRoadOnDist = reachableTile.second
        }

        if (bestTileToConstructRoadOn == null) return false

        if (bestTileToConstructRoadOn != currentTile && unit.currentMovement > 0)
            unit.movement.headTowards(bestTileToConstructRoadOn)
        if (unit.currentMovement > 0 && bestTileToConstructRoadOn == currentTile
            && currentTile.improvementInProgress != bestRoadAvailable.name) {
            val improvement = bestRoadAvailable.improvement(ruleSet)!!
            bestTileToConstructRoadOn.startWorkingOnImprovement(improvement, civInfo, unit)
        }
        return true
    }
    //endregion
}
