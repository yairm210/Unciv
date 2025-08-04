package com.unciv.logic.civilization.transients

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.BFS
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly
import kotlin.collections.set

class CapitalConnectionsFinder(private val civInfo: Civilization) {
    private val citiesReachedToMediums = HashMap<City, MutableSet<String>>()
    private var citiesToCheck = mutableListOf(civInfo.getCapital()!!)
    private lateinit var newCitiesToCheck: MutableList<City>

    private val openBordersCivCities = civInfo.gameInfo.getCities().filter { canEnterBordersOf(it.civ) }

    companion object {
        private const val HARBOR = "Harbor"   // hardcoding at least centralized for this class for now
        private val road = RoadStatus.Road.name
        private val railroad = RoadStatus.Railroad.name
        private val harborFromRoad = "$HARBOR-$road"
        private val harborFromRailroad = "$HARBOR-$railroad"
    }

    private val ruleset = civInfo.gameInfo.ruleset
    private val roadIsResearched = ruleset.tileImprovements[road].let {
        it != null && (it.techRequired==null || civInfo.tech.isResearched(it.techRequired!!)) }

    private val railroadIsResearched = ruleset.tileImprovements[railroad].let {
        it != null && (it.techRequired==null || civInfo.tech.isResearched(it.techRequired!!)) }

    init {
        citiesReachedToMediums[civInfo.getCapital()!!] = hashSetOf("Start")
    }

    fun find(): Map<City, Set<String>> {
        // We map which cities we've reached, to the mediums they've been reached by -
        // this is so we know that if we've seen which cities can be connected by port A, and one
        // of those is city B, then we don't need to check the cities that B can connect to by port,
        // since we'll get the same cities we got from A, since they're connected to the same sea.
        while (citiesToCheck.isNotEmpty() && citiesReachedToMediums.size < openBordersCivCities.count()) {
            newCitiesToCheck = mutableListOf()
            for (cityToConnectFrom in citiesToCheck) {
                if (cityToConnectFrom.containsHarbor()) {
                    checkHarbor(cityToConnectFrom)
                }
                if (railroadIsResearched) {
                    val mediumsReached= citiesReachedToMediums[cityToConnectFrom]!!
                    if(mediumsReached.contains("Start") || mediumsReached.contains(railroad) || mediumsReached.contains(harborFromRailroad))
                        checkRailroad(cityToConnectFrom) // This is only relevant for city connection if there is an unbreaking line from the capital
                }
                if (roadIsResearched) {
                    checkRoad(cityToConnectFrom)
                }
            }
            citiesToCheck = newCitiesToCheck
        }
        return citiesReachedToMediums
    }

    private fun checkRoad(cityToConnectFrom: City) {
        check(
            cityToConnectFrom,
            transportType = road,
            overridingTransportType = railroad,
            tileFilter = { tile -> tile.hasConnection(civInfo) }
        )
    }

    private fun checkRailroad(cityToConnectFrom: City) {
        check(
            cityToConnectFrom,
            transportType = railroad,
            tileFilter = { tile -> tile.getUnpillagedRoad() == RoadStatus.Railroad }
        )
    }

    private fun checkHarbor(cityToConnectFrom: City) {
        check(
            cityToConnectFrom,
            transportType = if(cityToConnectFrom.wasPreviouslyReached(railroad,null)) harborFromRailroad else harborFromRoad,
            overridingTransportType = harborFromRailroad,
            tileFilter = { tile -> tile.isWater },
            cityFilter = { city -> city.civ == civInfo && city.containsHarbor() && !city.isBlockaded() } // use only own harbors
        )
    }

    @Readonly
    private fun City.containsHarbor() = 
        this.containsBuildingUnique(UniqueType.ConnectTradeRoutes)

    private fun check(cityToConnectFrom: City,
                      transportType: String,
                      overridingTransportType: String? = null,
                      tileFilter: (Tile) -> Boolean,
                      cityFilter: (City) -> Boolean = { true }) {
        // This is the time-saving mechanism we discussed earlier - If I arrived at this city via a certain BFS,
        // then obviously I already have all the cities that can be reached via that BFS so I don't need to run it again.
        if (cityToConnectFrom.wasPreviouslyReached(transportType, overridingTransportType))
            return

        val bfs = BFS(cityToConnectFrom.getCenterTile()) {
              val owner = it.getOwner()
              (it.isCityCenter() || tileFilter(it)) && (owner == null || canEnterBordersOf(owner))
        }
        bfs.stepToEnd()
        val reachedCities = openBordersCivCities.filter {
            bfs.hasReachedTile(it.getCenterTile()) && cityFilter(it)
        }
        for (reachedCity in reachedCities) {
            addCityIfFirstEncountered(reachedCity)
            if (reachedCity == cityToConnectFrom) continue
            if (!reachedCity.wasPreviouslyReached(transportType, overridingTransportType))
                reachedCity.addMedium(transportType)
        }
    }

    private fun addCityIfFirstEncountered(reachedCity: City) {
        if (!citiesReachedToMediums.containsKey(reachedCity)) {
            newCitiesToCheck.add(reachedCity)
            citiesReachedToMediums[reachedCity] = mutableSetOf()
        }
    }
    
    @Readonly
    private fun City.wasPreviouslyReached(transportType: String, overridingTransportType: String?): Boolean {
        val mediums = citiesReachedToMediums[this]!!
        return mediums.contains(transportType) || mediums.contains(overridingTransportType)
    }

    private fun City.addMedium(transportType: String) {
        citiesReachedToMediums[this]!!.add(transportType)
    }

    @Readonly
    private fun canEnterBordersOf(otherCiv: Civilization): Boolean {
        if (otherCiv == civInfo) return true // own borders are always open
        if (otherCiv.isBarbarian || civInfo.isBarbarian) return false // barbarians blocks the routes
        val diplomacyManager = civInfo.diplomacy[otherCiv.civName]
            ?: return false // not encountered yet
        if (otherCiv.isCityState && diplomacyManager.diplomaticStatus != DiplomaticStatus.War) return true
        return diplomacyManager.hasOpenBorders
    }

}
