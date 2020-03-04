package com.unciv.logic.civilization

import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.BFS
import com.unciv.logic.map.TileInfo
import kotlin.collections.set

class CapitalConnectionsFinder(private val civInfo: CivilizationInfo) {
    private val citiesReachedToMediums = HashMap<CityInfo, MutableSet<String>>()
    private var citiesToCheck = mutableListOf(civInfo.getCapital())
    private lateinit var newCitiesToCheck: MutableList<CityInfo>

    private val allCivCities = UncivGame.Current.gameInfo.getCities()

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
                tileFilter = { tile -> tile.hasRoad(civInfo) || tile.hasRailroad() || tile.isCityCenter() }
        )
    }

    private fun checkRailroad(cityToConnectFrom: CityInfo) {
        check(
                cityToConnectFrom = cityToConnectFrom,
                transportType = railroad,
                tileFilter = { tile -> tile.hasRailroad() || tile.isCityCenter() }
        )
    }

    private fun checkHarbor(cityToConnectFrom: CityInfo) {
        check(
                cityToConnectFrom = cityToConnectFrom,
                transportType = harbor,
                tileFilter = { tile -> tile.isWater || tile.isCityCenter() },
                cityFilter = { city -> city.containsHarbor() }
        )
    }

    private fun CityInfo.containsHarbor() =
            this.cityConstructions.containsBuildingOrEquivalent(harbor)

    private fun check(cityToConnectFrom: CityInfo,
                      transportType: String,
                      overridingTransportType: String? = null,
                      tileFilter: (TileInfo) -> Boolean,
                      cityFilter: (CityInfo) -> Boolean = { true }) {
        val bfs = BFS(cityToConnectFrom.getCenterTile(), tileFilter)
        bfs.stepToEnd()
        val reachedCities = allCivCities.filter {
            bfs.hasReachedTile(it.getCenterTile()) && cityFilter(it)
        }
        for (reachedCity in reachedCities) {
            addCityIfFirstEncountered(reachedCity)
            if (reachedCity == cityToConnectFrom) continue
            if (reachedCity.wasNotPreviouslyReached(transportType, overridingTransportType))
                reachedCity.addMedium(transportType)
        }
    }

    private fun addCityIfFirstEncountered(reachedCity: CityInfo) {
        if (!citiesReachedToMediums.containsKey(reachedCity)) {
            newCitiesToCheck.add(reachedCity)
            citiesReachedToMediums[reachedCity] = mutableSetOf()
        }
    }

    private fun CityInfo.wasNotPreviouslyReached(transportType: String, overridingTransportType: String?): Boolean {
        val mediums = citiesReachedToMediums[this]!!
        return !mediums.contains(transportType) && !mediums.contains(overridingTransportType)
    }

    private fun CityInfo.addMedium(transportType: String) {
        citiesReachedToMediums[this]!!.add(transportType)
    }

}