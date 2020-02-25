package com.unciv.logic.civilization

import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.BFS
import com.unciv.logic.map.TileInfo
import kotlin.collections.set

class CapitalConnectionsFinder(private val civInfo: CivilizationInfo) {
    private val citiesReachedToMediums = HashMap<CityInfo, MutableSet<String>>()
    private var citiesToCheck = mutableListOf(civInfo.getCapital())
    private lateinit var newCitiesToCheck: MutableList<CityInfo>

    private val allCivCities = civInfo.gameInfo.getCities()

    private val theWheelIsResearched = civInfo.tech.isResearched("The Wheel")
    private val railroadIsResearched = civInfo.tech.isResearched("Railroad")

    private val road = "Road"
    private val railroad = "Railroad"
    private val harbor = "Harbor"

    init {
        citiesReachedToMediums[civInfo.getCapital()] = hashSetOf("Start")
    }

    fun find(): Map<CityInfo, Set<String>> {
        // We map which cities we've reached, to the mediums they've been reached by -
        // this is so we know that if we've seen which cities can be connected by port A, and one
        // of those is city B, then we don't need to check the cities that B can connect to by port,
        // since we'll get the same cities we got from A, since they're connected to the same sea.
        while (citiesToCheck.isNotEmpty() && citiesReachedToMediums.size < allCivCities.size) {
            newCitiesToCheck = mutableListOf()
            for (cityToConnectFrom in citiesToCheck) {
                if (cityToConnectFrom.containsHarbor()) {
                    checkHarbor(cityToConnectFrom)
                }
                if (railroadIsResearched) {
                    checkRailroad(cityToConnectFrom)
                }
                if (theWheelIsResearched) {
                    checkRoad(cityToConnectFrom)
                }
            }
            citiesToCheck = newCitiesToCheck
        }
        return citiesReachedToMediums
    }

    private fun checkRoad(cityToConnectFrom: CityInfo) {
        check(
                cityToConnectFrom = cityToConnectFrom,
                transportType = road,
                overridingTransportType = railroad,
                tileFilter = { tile -> tile.hasRoad(civInfo) || tile.hasRailroad() || tile.owningCity?.getCenterTile() == tile },
                cityFilter = { bfs, city -> bfs.tilesReached.containsKey(city.getCenterTile()) }
        )
    }

    private fun checkRailroad(cityToConnectFrom: CityInfo) {
        check(
                cityToConnectFrom = cityToConnectFrom,
                transportType = railroad,
                tileFilter = { tile -> tile.hasRailroad() || tile.owningCity?.getCenterTile() == tile },
                cityFilter = { bfs, city -> bfs.tilesReached.containsKey(city.getCenterTile()) }
        )
    }

    private fun checkHarbor(cityToConnectFrom: CityInfo) {
        check(
                cityToConnectFrom = cityToConnectFrom,
                transportType = harbor,
                tileFilter = { tile -> tile.isWater || tile.isCityCenter() },
                cityFilter = { bfs, city ->
                    bfs.tilesReached.containsKey(city.getCenterTile())
                            && city.containsHarbor()
                }
        )
    }

    private fun CityInfo.containsHarbor() =
            this.cityConstructions.containsBuildingOrEquivalent(harbor)

    private fun check(cityToConnectFrom: CityInfo,
                      transportType: String,
                      overridingTransportType: String? = null,
                      tileFilter: (TileInfo) -> Boolean,
                      cityFilter: (BFS, CityInfo) -> Boolean) {
        val bfs = BFS(cityToConnectFrom.getCenterTile(), tileFilter)
        bfs.stepToEnd()
        val reachedCities = allCivCities.filter { cityFilter(bfs, it) }
        for (reachedCity in reachedCities) {
            if (!citiesReachedToMediums.containsKey(reachedCity)) {
                newCitiesToCheck.add(reachedCity)
                citiesReachedToMediums[reachedCity] = mutableSetOf()
            }
            if (reachedCity == cityToConnectFrom) continue
            val cityReachedByMediums = citiesReachedToMediums[reachedCity]!!
            if (!cityReachedByMediums.contains(transportType)
                    && !cityReachedByMediums.contains(overridingTransportType))
                cityReachedByMediums.add(transportType)
        }
    }

}