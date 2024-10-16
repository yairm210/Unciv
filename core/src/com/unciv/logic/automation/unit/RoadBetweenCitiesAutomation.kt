package com.unciv.logic.automation.unit

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.BFS
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.MapPathing
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.utils.Log
import com.unciv.utils.debug
import kotlin.math.max

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

    /** Cache of roads to connect cities each turn. Call [getRoadsToBuildFromCity] instead of using this */
    private val roadsToBuildByCitiesCache: HashMap<City, List<RoadPlan>> = HashMap()

    /** Hashmap of all cached tiles in each list in [roadsToBuildByCitiesCache] */
    internal val tilesOfRoadsMap: HashMap<Tile, RoadPlan> = HashMap()

    inner class RoadPlan(val tiles: List<Tile>, val priority: Float, val fromCity: City, val toCity: City) {
        val numberOfRoadsToBuild: Int by lazy { tiles.count { it.getUnpillagedRoad() != bestRoadAvailable } }
    }


    /**
     * Tries to return a list of road plans to connect this city to the surrounding cities.
     * If there are no surrounding cities to connect to and this city is still unconnected to the capital it will try and build a special road to the capital.
     *
     * @return every road that we want to try and connect assosiated with this city.
     */
    fun getRoadsToBuildFromCity(city: City): List<RoadPlan> {
        if (roadsToBuildByCitiesCache.containsKey(city)) {
            return roadsToBuildByCitiesCache[city]!!
        }
        // TODO: some better worker representative needs to be used here
        val workerUnit = civInfo.gameInfo.ruleset.units.map { it.value }.firstOrNull { it.hasUnique(UniqueType.BuildImprovements) }
            // This is a temporary unit only for AI purposes so it doesn't get a unique ID
            ?.getMapUnit(civInfo, Constants.NO_ID) ?: return listOf()
        val roadToCapitalStatus = city.cityStats.getRoadTypeOfConnectionToCapital()

        fun rankRoadCapitalPriority(roadStatus: RoadStatus): Float {
            return when(roadStatus) {
                RoadStatus.None -> if (bestRoadAvailable != RoadStatus.None) 2f else 0f
                RoadStatus.Road -> if (bestRoadAvailable != RoadStatus.Road) 1f else 0f
                else -> 0f
            }
        }

        val basePriority = rankRoadCapitalPriority(roadToCapitalStatus)

        val roadsToBuild: MutableList<RoadPlan> = mutableListOf()
        for (closeCity in city.neighboringCities.filter { it.civ == civInfo && it.getCenterTile().aerialDistanceTo(city.getCenterTile()) <= 8 }) {

            // Try to find if the other city has planned to build a road to this city
            if (roadsToBuildByCitiesCache.containsKey(closeCity)) {
                // There should only ever be one or zero possible connections from their city to this city
                val roadToBuild = roadsToBuildByCitiesCache[closeCity]!!.firstOrNull { it.fromCity == city || it.toCity == city}

                if (roadToBuild != null) {
                    roadsToBuild.add(roadToBuild)
                }
                // We already did the hard work, there can't be any other possible roads to this city
                continue
            }

            // Try to build a plan for the road to the city
            val roadPath = if (civInfo.cities.indexOf(city) < civInfo.cities.indexOf(closeCity)) MapPathing.getRoadPath(workerUnit, city.getCenterTile(), closeCity.getCenterTile()) ?: continue
                else MapPathing.getRoadPath(workerUnit, closeCity.getCenterTile(), city.getCenterTile()) ?: continue
            val worstRoadStatus = getWorstRoadTypeInPath(roadPath)
            if (worstRoadStatus == bestRoadAvailable) continue

            // Make sure that we are taking in to account the other cities needs
            var roadPriority = max(basePriority, rankRoadCapitalPriority(closeCity.cityStats.getRoadTypeOfConnectionToCapital()))
            if (worstRoadStatus == RoadStatus.None) {
                roadPriority += 2
            } else if (worstRoadStatus == RoadStatus.Road && bestRoadAvailable == RoadStatus.Railroad) {
                roadPriority += 1
            }
            if (closeCity.cityStats.getRoadTypeOfConnectionToCapital() > roadToCapitalStatus)
                roadPriority += 1

            val newRoadPlan = RoadPlan(roadPath, roadPriority + (city.population.population + closeCity.population.population) / 4f, city, closeCity)
            roadsToBuild.add(newRoadPlan)
            for (tile in newRoadPlan.tiles) {
                if (tile !in tilesOfRoadsMap || tilesOfRoadsMap[tile]!!.priority < newRoadPlan.priority)
                    tilesOfRoadsMap[tile] = newRoadPlan
            }
        }

        // If and only if we have no roads to build to close-by cities then we check for a road to build to the capital
        if (roadsToBuild.isEmpty() && roadToCapitalStatus < bestRoadAvailable) {
            val roadToCapital = getRoadToConnectCityToCapital(workerUnit, city)

            if (roadToCapital != null) {
                val worstRoadStatus = getWorstRoadTypeInPath(roadToCapital.second)
                var roadPriority = basePriority
                roadPriority += if (worstRoadStatus == RoadStatus.None) 2f else 1f

                val newRoadPlan = RoadPlan(roadToCapital.second, roadPriority + (city.population.population) / 2f, city, roadToCapital.first)
                roadsToBuild.add(newRoadPlan)
                for (tile in newRoadPlan.tiles) {
                    if (tile !in tilesOfRoadsMap || tilesOfRoadsMap[tile]!!.priority < newRoadPlan.priority)
                        tilesOfRoadsMap[tile] = newRoadPlan
                }
            }
        }

        roadsToBuildByCitiesCache[city] = roadsToBuild
        return roadsToBuild
    }

    private fun getWorstRoadTypeInPath(path: List<Tile>): RoadStatus {
        var worstRoadTile = RoadStatus.Railroad
        for (tile in path) {
            if (tile.getUnpillagedRoad() < worstRoadTile) {
                worstRoadTile = tile.getUnpillagedRoad()
                if (worstRoadTile == RoadStatus.None)
                    return RoadStatus.None
            }
        }
        return worstRoadTile
    }

    /**
     * Returns a road that can connect this city to the capital.
     * This is a very expensive function that doesn't nessesarily produce the same roads as in [getRoadsToBuildFromCity].
     * So it should only be used if it is the only road that a city wants to build.
     * @return a pair containing a list of tiles that resemble the road to build and the city that the road will connect to
     */
    private fun getRoadToConnectCityToCapital(unit: MapUnit, city: City): Pair<City, List<Tile>>? {
        if (tilesOfConnectedCities.isEmpty()) return null // In mods with no capital city indicator, there are no connected cities
        
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

                return Pair(cityTile.getCity()!!, pathToCity.toList())
            }
            nextTile = bfs.nextStep()
        }
        return null
    }

    /**
     * Most importantly builds the cache so that [chooseImprovement] knows later what tiles a road should be built on
     * Returns a list of all the cities close by that this worker may want to connect
     */
    internal fun getNearbyCitiesToConnect(unit: MapUnit): List<City> {
        if (bestRoadAvailable == RoadStatus.None) return listOf()
        val candidateCities = civInfo.cities.filter {
            // Cities that are too far away make the canReach() calculations devastatingly long
            it.getCenterTile().aerialDistanceTo(unit.getTile()) < 20 && getRoadsToBuildFromCity(it).isNotEmpty()
        }
        if (candidateCities.none()) return listOf() // do nothing.

        return candidateCities
    }

    /**
     * Looks for work connecting cities. Used to search for far away roads to build.
     * @return whether we actually did anything
     */
    internal fun tryConnectingCities(unit: MapUnit, candidateCities: List<City>): Boolean {
        if (bestRoadAvailable == RoadStatus.None) return false

        if (candidateCities.none()) return false // do nothing.
        val currentTile = unit.getTile()

        // Search through ALL candidate cities for the closest tile to build a road on
        for (toConnectCity in candidateCities.sortedByDescending { it.getCenterTile().aerialDistanceTo(unit.getTile()) }) {
            val tilesByPriority = getRoadsToBuildFromCity(toConnectCity).flatMap { roadPlan -> roadPlan.tiles.map { tile ->  Pair(tile, roadPlan.priority) } }
            val tilesSorted = tilesByPriority.filter { it.first.getUnpillagedRoad() < bestRoadAvailable }
                    .sortedBy { it.first.aerialDistanceTo(unit.getTile()) + (it.second / 10f) }
            val bestTile = tilesSorted.firstOrNull {
                    unit.movement.canMoveTo(it.first) && unit.movement.canReach(it.first)
                }?.first ?: continue // Apparently we can't reach any of these tiles at all

            if (bestTile != currentTile && unit.hasMovement())
                unit.movement.headTowards(bestTile)
            if (unit.hasMovement() && bestTile == currentTile
                    && currentTile.improvementInProgress != bestRoadAvailable.name) {
                val improvement = bestRoadAvailable.improvement(civInfo.gameInfo.ruleset)!!
                bestTile.startWorkingOnImprovement(improvement, civInfo, unit)
            }
            return true
        }

        return false
    }
}
