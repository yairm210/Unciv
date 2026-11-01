package com.unciv.logic.automation.unit

import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.BFS
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.utils.Log
import com.unciv.utils.debug
import com.unciv.logic.automation.civilization.AutomationCache
import com.unciv.view.CityView
import com.unciv.view.ForeignCityView
import com.unciv.view.MapUnitView
import com.unciv.view.TileView
import com.unciv.view.getUnpillagedRoad
import com.unciv.view.hasMovement
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly
import kotlin.math.max

internal object WorkerAutomationConst {
    /** BFS max size is determined by the aerial distance of two cities to connect, padded with this */
    // two tiles longer than the distance to the nearest connected city should be enough as the 'reach' of a BFS is increased by blocked tiles
    const val maxBfsReachPadding = 2

    /**
     * Subtracted from a [RoadBetweenCitiesAutomation.RoadPlan.priority] to decide whether an automated
     * worker will treat building that road as *the* best thing to do on a tile (see
     * [WorkerAutomation.getImprovementRanking]).
     *
     * Lowered from 9 to address https://github.com/yairm210/Unciv/issues/15417 (report 3): with the old
     * value, connecting two small/new cities practically never outweighed a farm or mine, so automated
     * workers could go dozens of turns without making any progress on road connections. This still leaves
     * a real threshold - trivial road upgrades (e.g. Road -> Railroad on an already-connected route) remain
     * lower priority than good tile improvements - but a genuinely disconnected city no longer has to wait
     * for a large population before workers will act on it: two freshly-founded (population 1) cities with
     * no-road connections get an explicit priority bonus below, so this offset can stay high enough to
     * keep ordinary road upgrades below worthwhile tile improvements.
     */
    const val connectRoadPriorityOffset = 4f
}

/**
 * Responsible for the "connect cities" automation as part of worker automation
 *
 * @param civInfo Civilization which does road automation
 * @param cachedForTurn The turn number the instance was created for, road plans are recalculated each turn
 * @param cloningSource ?
 */
// TODO: Coastal cities that have harbor connection, should maintain roads only for mobility (railroads won't be built)
// TODO: Remove roads logic, e.g. toward lost or razed cities or remnant roads from conquered territory
class RoadBetweenCitiesAutomation(val civInfo: Civilization, private val cachedForTurn: Int, cloningSource: RoadBetweenCitiesAutomation? = null) {

    /**
     * Caches BFS by city locations (cities needing connecting)
     *
     * key: The city to connect from as [hex position][HexCoord]
     *
     * value: The [BFS] searching from that city, whether successful or not
     */
    // TODO: If BFS were to deal in vectors instead of Tiles, we could copy this on cloning
    @Cache private val bfsCache = HashMap<HexCoord, BFS<TileView>>()

    /** Caches road type to build for connecting cities, unless option is off or ruleset removed all roads */
    internal val bestRoadAvailable: RoadStatus =
        cloningSource?.bestRoadAvailable ?:
        // Player can choose not to auto-build roads & railroads.
        if (civInfo.isHuman() && !UncivGame.Current.settings.autoBuildingRoads
            && UncivGame.Current.worldScreen?.autoPlay?.isAutoPlayingAndFullAutoPlayAI() == false)
            RoadStatus.None
        else civInfo.tech.getBestRoadAvailable()

    /** Civ-wide city center tile list of _connected_ Cities, unsorted */
    private var tilesOfConnectedCitiesCache: List<TileView>? = null

    @Readonly @Suppress("purity")
    private fun getTilesOfConnectedCities(cityView: CityView): List<TileView> = tilesOfConnectedCitiesCache ?: run {
        val result = cityView.owningCivView.cities().asSequence()
            .filter { it.isCapital() || it.getRoadTypeOfConnectionToCapital() >= bestRoadAvailable }
            .map { it.getCenterTile() }
            .toList()
        if (Log.shouldLog()) {
            debug("Turn $cachedForTurn - civ ${civInfo.civName}: workerAutomation tilesOfConnectedCities are:")
            if (result.isEmpty())
                debug("\tempty")
            else result.forEach {
                debug("\t$it")
            }
        }
        tilesOfConnectedCitiesCache = result
        result
    }

    /**
     * Cache of cities and their road plans to connect to the surrounding cities each turn.
     * Call [getRoadsToBuildFromCity] instead of using this.
     */
    private val roadsToBuildByCitiesCache: HashMap<CityView, List<RoadPlan>> = HashMap()

    /** Hashmap of all cached tiles in each [RoadPlan] list in [roadsToBuildByCitiesCache] */
    internal val tilesOfRoadsMap: HashMap<TileView, RoadPlan> = HashMap()

    /**
     * Road plan (path) between two cities taking into account best available existing roads and population counts.
     *
     * @param tiles Tiles included in the road plan, including city centers
     * @param priority Road plan priority, used to prioritize tiles for road construction in [tryConnectingCities]
     * @param fromCity Starting city
     * @param toCity Destination city
     */
    inner class RoadPlan(val tiles: List<TileView>, val priority: Float, val fromCity: CityView, val toCity: ForeignCityView) {
        // TODO: This number is not consistent across turns because pathing depends on worker position
        val numberOfRoadsToBuild: Int by lazy { tiles.count { it.getUnpillagedRoad() != bestRoadAvailable } }
    }

    /**
     * Tries to return a list of road plans to connect [city] to the surrounding cities.
     * If there are no surrounding cities to connect to and [cityView] is still unconnected to the capital it will try and build a special road to the capital.
     *
	 * @param cityView The [CityView] for which to obtain road plans
     * @param cache Holds the road pathing maps
     * @return Every [RoadPlan] that we want to try and connect associated with [cityView].
     */
    @Readonly @Suppress("purity") 
    private fun getRoadsToBuildFromCity(cityView: CityView, cache: AutomationCache): List<RoadPlan> {
        if (roadsToBuildByCitiesCache.containsKey(cityView))
            return roadsToBuildByCitiesCache[cityView]!!
        val civView = cityView.owningCivView
        val civCities by lazy { civView.cities() }
        val roadToCapitalStatus = cityView.getRoadTypeOfConnectionToCapital()

        /** @return Rank 0, 1 or 2 of how important it is to build best available road to capital */
        @Pure
        fun rankRoadCapitalPriority(roadStatus: RoadStatus): Float {
            return when(roadStatus) {
                RoadStatus.None -> if (bestRoadAvailable != RoadStatus.None) 2f else 0f
                RoadStatus.Road -> if (bestRoadAvailable != RoadStatus.Road) 1f else 0f
                else -> 0f
            }
        }

        val basePriority = rankRoadCapitalPriority(roadToCapitalStatus)
        val roadPlans: MutableList<RoadPlan> = mutableListOf()
        for (closeCityView in cityView.getNeighboringOwnCities(8)) {
            // Try to find if the other city has planned to build a road to this city
            if (roadsToBuildByCitiesCache.containsKey(closeCityView)) {
                // There should only ever be one or zero possible connections from their city to this city
                val roadToBuild = roadsToBuildByCitiesCache[closeCityView]!!.firstOrNull { it.fromCity == cityView || it.toCity == cityView }
                if (roadToBuild != null) {
                    // We already did the hard work, there can't be any other possible roads to this city
                    roadPlans.add(roadToBuild)
                    continue
                }
            }

            if (roadToCapitalStatus == bestRoadAvailable) {
                // Avoid invoking [MapPathing.getRoadPath]
                continue
            }

            // Try to build a plan for the road to the city
            // TODO: May return inconsistent paths across turns due to worker position, this makes it impossible to plan an exact road resulting in excessive roads built
            val roadPath = 
                if (UncivGame.Current.settings.useAStarPathfinding)
                    cityView.getCenterTile().getRoadPathBetweenCities(closeCityView.getCenterTile(), cache.roadPathingCache) ?: continue
                else if (civCities.indexOf(cityView) < civCities.indexOf(closeCityView))
                    cityView.getCenterTile().getRoadPath(closeCityView.getCenterTile()) ?: continue
                else closeCityView.getCenterTile().getRoadPath(cityView.getCenterTile()) ?: continue
            val worstRoadStatus = getWorstRoadTypeInPath(roadPath)
            if (worstRoadStatus == bestRoadAvailable) continue

            // Make sure that we are taking in to account the other cities needs
            var roadPriority = max(basePriority, rankRoadCapitalPriority(closeCityView.getRoadTypeOfConnectionToCapital()))
            if (worstRoadStatus == RoadStatus.None) {
                // A completely unroaded connection is a strategic task, not just another tile improvement.
                // Give it enough extra weight to compete with ordinary farms/mines while preserving the
                // normal offset for routes that already have some road infrastructure.
                roadPriority += 4
            } else if (worstRoadStatus == RoadStatus.Road && bestRoadAvailable == RoadStatus.Railroad) {
                roadPriority += 1
            }
            if (closeCityView.getRoadTypeOfConnectionToCapital() > roadToCapitalStatus)
                roadPriority += 1

            val newRoadPlan = RoadPlan(roadPath, roadPriority + (cityView.getPopulationCount() + closeCityView.getPopulationCount()) / 4f, cityView, closeCityView)
            roadPlans.add(newRoadPlan)
        }

        var bestPlan = chooseBestPlan(roadPlans)
        if (bestPlan != null) {
            roadPlans.clear()
            roadPlans.add(bestPlan)
        }
        // If and only if we have no roads to build to close-by cities then we check for a road to build to the capital
        // The condition !city.isConnectedToCapital() is to avoid BFS for cities connected to capital with roads when railroads are unlocked
        else if (roadPlans.isEmpty() && (roadToCapitalStatus < bestRoadAvailable) && !cityView.isConnectedToCapital()) {
            val roadToCapital = getRoadToConnectCityToCapital(cityView, cache)

            if (roadToCapital != null) {
                val worstRoadStatus = getWorstRoadTypeInPath(roadToCapital.second)
                var roadPriority = basePriority
                roadPriority += if (worstRoadStatus == RoadStatus.None) 2f else 1f

                bestPlan = RoadPlan(roadToCapital.second, roadPriority + cityView.getPopulationCount() / 2f, cityView, roadToCapital.first)
                roadPlans.add(bestPlan)
            }
        }

        if (bestPlan != null) {
            for (tile in bestPlan.tiles)
                if (tile !in tilesOfRoadsMap || tilesOfRoadsMap[tile]!!.priority < bestPlan.priority)
                    tilesOfRoadsMap[tile] = bestPlan
        }

        roadsToBuildByCitiesCache[cityView] = roadPlans
        return roadPlans
    }

    /**
     * The best road plan is that which reuses existing roads to upgrade them to railroads if researched,
     * to avoid duplication of roads to newly settled or conquered cities that didn't exist during old road construction.
     * Followed by choosing a plan with shortest distance
     *
     * @return The shortest road plan and drop other plans
     */
    @Readonly
    private fun chooseBestPlan(planList: MutableList<RoadPlan>): RoadPlan? {
        if (planList.size < 2)
            return planList.firstOrNull()

        val existingRoadPlans: MutableList<RoadPlan> = mutableListOf()
        val shortestPlan: (MutableList<RoadPlan>) -> RoadPlan? = { roadPlans -> roadPlans.minByOrNull { it.numberOfRoadsToBuild } }
        // If there are multiple shortest distance road plans make minByOrNull priotirize ones with closer cities
        // helps to avoid road crossovers (but not completely) due to roadPreferredMovementCost function
        planList.sortBy { it.fromCity.getCenterTile().aerialDistanceTo(it.toCity.getCenterTile()) }

        if (bestRoadAvailable != RoadStatus.Railroad)
            return shortestPlan(planList)

        for (roadPlan in planList)
            if (roadPlan.tiles.all { (it.roadStatus > RoadStatus.None) || it.roadIsPillaged })
                existingRoadPlans.add(roadPlan)

        return if (existingRoadPlans.isEmpty()) shortestPlan(planList)
        else shortestPlan(existingRoadPlans)
    }

    /** @return Lowest road level (aka. road type) in road path */
    @Readonly
    private fun getWorstRoadTypeInPath(path: List<TileView>): RoadStatus {
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
     * This is a very expensive function that doesn't necessarily produce the same roads as in [getRoadsToBuildFromCity].
     * So it should only be used if it is the only road that a city wants to build.
     * @return a pair containing a list of tiles that resemble the road to build and the city that the road will connect to
     */
    @Readonly
    private fun getRoadToConnectCityToCapital(cityView: CityView, cache: AutomationCache): Pair<ForeignCityView, List<TileView>>? {
        val tilesOfConnectedCities = getTilesOfConnectedCities(cityView)
        if (tilesOfConnectedCities.isEmpty()) return null // In mods with no capital city indicator, there are no connected cities

        val isCandidateTilePredicate: (TileView) -> Boolean = { it.isLand && it.isValidRoadPathTile() }
        val toConnectTile = cityView.getCenterTile()
        val cityTilesToSeek = HashSet(tilesOfConnectedCities)
        if (UncivGame.Current.settings.useAStarPathfinding) {
            val pathToCity =
                cityView.getCenterTile().getRoadPathToAny(cityTilesToSeek, WorkerAutomationConst.maxBfsReachPadding, cache.roadPathingCache)
                    ?: return null
            return Pair(pathToCity.last().getCity()!!, pathToCity)
        }
        @LocalState val bfs: BFS<TileView> = bfsCache[toConnectTile.position()] ?: run {
            val bfs = BFS(toConnectTile, isCandidateTilePredicate)
            bfs.maxSize = HexMath.getNumberOfTilesInHexagon(
                WorkerAutomationConst.maxBfsReachPadding +
                        tilesOfConnectedCities.minOf { it.aerialDistanceTo(toConnectTile) }
            )
            bfsCache[toConnectTile.position()] = bfs
            bfs
        }

        var nextTile = bfs.nextStep()
        while (nextTile != null) {
            if (nextTile in cityTilesToSeek) {
                // We have a winner!
                val pathToCity = bfs.getPathTo(nextTile)

                return Pair(nextTile.getCity()!!, pathToCity.toList())
            }
            nextTile = bfs.nextStep()
        }
        return null
    }

    /**
     * Most importantly builds the cache so that [WorkerAutomation.chooseImprovement] knows later what tiles a road should be built on.
     *
     * @param unitView Civilian unit which may want to connect cities
     * @param cache Holds the road pathing maps
     * @return A list of all cities the [unitView] will try to connect if in its vicinity
     */
    @Readonly
    internal fun getNearbyCitiesToConnect(unitView: MapUnitView, cache: AutomationCache): List<CityView> {
        if (bestRoadAvailable == RoadStatus.None) return listOf()
        val unitTile = unitView.getTile()
        val candidateCities = unitView.civ().cities().filter {
            // Cities that are too far away make the canReach() calculations devastatingly long
            it.getCenterTile().aerialDistanceTo(unitTile) < 20 && getRoadsToBuildFromCity(it, cache).isNotEmpty()
        }
        if (candidateCities.none()) return listOf() // do nothing.

        return candidateCities
    }

    /**
     * Looks for work to connect cities. Used to search for far away roads to build.
     *
     * @param unitView Civilian unit which will try to connect cities
     * @param cache Holds the road pathing maps
     * @return Whether we actually did anything
     */
    internal fun tryConnectingCities(unitView: MapUnitView, candidateCities: List<CityView>, cache: AutomationCache): Boolean {
        if (bestRoadAvailable == RoadStatus.None) return false

        if (candidateCities.none()) return false // do nothing.
        val unitStartingTile = unitView.getTile()

        // Search through ALL candidate cities for the closest tile to build a road on
        for (toConnectCity in candidateCities.sortedBy { it.getCenterTile().aerialDistanceTo(unitView.getTile()) }) {
            val tilesByPriority = getRoadsToBuildFromCity(toConnectCity, cache).flatMap { roadPlan -> roadPlan.tiles.map { tile ->  Pair(tile, roadPlan.priority) } }
            val tilesSorted = tilesByPriority
                    .filter { !it.first.isMarkedForCreatesOneImprovement() && it.first.getUnpillagedRoad() < bestRoadAvailable }
                    .sortedBy { it.first.aerialDistanceTo(unitStartingTile) - (it.second / 10f) }
            val bestTile = tilesSorted.firstOrNull {
                unitStartingTile == it.first || (unitView.canMoveTo(it.first) && unitView.canReach(it.first))
            }?.first ?: continue // Apparently we can't reach any of these tiles at all

            if (bestTile != unitStartingTile && unitView.hasMovement())
                unitView.tryHeadTowards(bestTile)
            if (unitView.hasMovement() && (bestTile == unitView.getTile()) && (bestTile.improvementInProgress != bestRoadAvailable.name)) {
                val improvement = bestRoadAvailable.improvement(civInfo.gameInfo.ruleset)!!
                unitView.tryStartWorkingOnImprovement(improvement)
            }
            return true
        }

        return false
    }
}
