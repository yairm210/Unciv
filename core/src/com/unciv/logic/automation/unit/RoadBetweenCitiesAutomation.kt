package com.unciv.logic.automation.unit

import com.badlogic.gdx.math.Vector2
import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.BFS
import com.unciv.logic.map.HexMath
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

/** Responsible for the "connect cities" automation as part of worker automation */
class RoadBetweenCitiesAutomation(val civInfo: Civilization, cachedForTurn: Int, cloningSource: RoadBetweenCitiesAutomation? = null) {

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
        if (civInfo.isHuman() && !UncivGame.Current.settings.autoBuildingRoads
            && UncivGame.Current.worldScreen?.autoPlay?.isAutoPlayingAndFullAutoPlayAI() == false)
            RoadStatus.None
        else civInfo.tech.getBestRoadAvailable()

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
            if (result.isEmpty()) debug("\tempty")
            else result.forEach { debug("\t${it.name}") }
        }
        result
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
            val improvement = bestRoadAvailable.improvement(civInfo.gameInfo.ruleset)!!
            bestTileToConstructRoadOn.startWorkingOnImprovement(improvement, civInfo, unit)
        }
        return true
    }
}
