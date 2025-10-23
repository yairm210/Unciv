package com.unciv.logic.civilization.transients

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.BFS
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly
import java.util.EnumSet
import kotlin.collections.set

class CapitalConnectionsFinder(private val civInfo: Civilization) {
    enum class CapitalConnectionMedium(
        val roadType: RoadStatus = RoadStatus.None,
        val checkRailroad: Boolean = false
    ) {
        Unknown, // future use only
        Start(checkRailroad = true),
        Road(RoadStatus.Road),
        Railroad(RoadStatus.Railroad, true),
        Harbor,
        HarborFromRoad(RoadStatus.Road),
        HarborFromRailroad(RoadStatus.Railroad, true),
        ;
        companion object {
            /** TODO ignores Harbor - but the notification code in updateCitiesConnectedToCapital should probably include it.
             *       The use for CityStats.getRoadTypeOfConnectionToCapital, however, shoudl not - it's used for Worker automation */
            @Readonly
            fun EnumSet<CapitalConnectionMedium>?.bestRoadStatus() = this?.maxOfOrNull { it.roadType } ?: RoadStatus.None
        }
    }

    private var citiesToCheck = listOfNotNull(civInfo.getCapital()).toMutableList()
    private val citiesReachedToMediums = citiesToCheck.associateWithTo(
        HashMap<City, EnumSet<CapitalConnectionMedium>>()
    ) { city -> EnumSet.of(CapitalConnectionMedium.Start) }
    private lateinit var newCitiesToCheck: MutableList<City>

    private val openBordersCivCities = civInfo.gameInfo.getCities().filter { canEnterBordersOf(it.civ) }

    private val ruleset = civInfo.gameInfo.ruleset
    private val roadIsResearched = ruleset.tileImprovements[RoadStatus.Road.name].let {
        it != null && (it.techRequired==null || civInfo.tech.isResearched(it.techRequired!!)) }

    private val railroadIsResearched = ruleset.tileImprovements[RoadStatus.Railroad.name].let {
        it != null && (it.techRequired==null || civInfo.tech.isResearched(it.techRequired!!)) }

    fun find(): Map<City, EnumSet<CapitalConnectionMedium>> {
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
                    val mediumsReached = citiesReachedToMediums[cityToConnectFrom]!!
                    if (mediumsReached.any { it.checkRailroad })
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
            transportType = CapitalConnectionMedium.Road,
            overridingTransportType = CapitalConnectionMedium.Railroad,
            tileFilter = { tile -> tile.hasConnection(civInfo) }
        )
    }

    private fun checkRailroad(cityToConnectFrom: City) {
        check(
            cityToConnectFrom,
            transportType = CapitalConnectionMedium.Railroad,
            tileFilter = { tile -> tile.getUnpillagedRoad() == RoadStatus.Railroad }
        )
    }

    private fun checkHarbor(cityToConnectFrom: City) {
        check(
            cityToConnectFrom,
            transportType = if (cityToConnectFrom.wasPreviouslyReached(CapitalConnectionMedium.Railroad,null))
                CapitalConnectionMedium.HarborFromRailroad else CapitalConnectionMedium.HarborFromRoad,
            overridingTransportType = CapitalConnectionMedium.HarborFromRailroad,
            tileFilter = { tile -> tile.isWater },
            cityFilter = { city -> city.civ == civInfo && city.containsHarbor() && !city.isBlockaded() } // use only own harbors
        )
    }

    @Readonly
    private fun City.containsHarbor() =
        this.containsBuildingUnique(UniqueType.ConnectTradeRoutes)

    private fun check(cityToConnectFrom: City,
                      transportType: CapitalConnectionMedium,
                      overridingTransportType: CapitalConnectionMedium? = null,
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
            citiesReachedToMediums[reachedCity] = EnumSet.noneOf(CapitalConnectionMedium::class.java)
        }
    }

    @Readonly
    private fun City.wasPreviouslyReached(
        transportType: CapitalConnectionMedium, overridingTransportType: CapitalConnectionMedium?
    ): Boolean {
        val mediums = citiesReachedToMediums[this]!!
        return transportType in mediums || overridingTransportType in mediums
    }

    private fun City.addMedium(transportType: CapitalConnectionMedium) {
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
