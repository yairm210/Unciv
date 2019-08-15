package com.unciv.logic.civilization

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.BFS
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.ResourceSupplyList
import com.unciv.models.gamebasics.tr
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.set

/** CivInfo class was getting too crowded */
class CivInfoTransientUpdater(val civInfo: CivilizationInfo){

    // This is a big performance
    fun updateViewableTiles() {
        val newViewableTiles = HashSet<TileInfo>()

        // There are a LOT of tiles usually.
        // And making large lists of them just as intermediaries before we shove them into the hashset is very space-inefficient.
        // Ans so, sequences to the rescue!
        val ownedTiles = civInfo.cities.asSequence().flatMap { it.getTiles().asSequence() }
        newViewableTiles.addAll(ownedTiles)
        val neighboringUnownedTiles = ownedTiles.flatMap { it.neighbors.asSequence().filter { it.getOwner()!=civInfo } }
        newViewableTiles.addAll(neighboringUnownedTiles)
        newViewableTiles.addAll(civInfo.getCivUnits().asSequence().flatMap { it.viewableTiles.asSequence()})
        civInfo.viewableTiles = newViewableTiles // to avoid concurrent modification problems

        val newViewableInvisibleTiles = HashSet<TileInfo>()
        newViewableInvisibleTiles.addAll(civInfo.getCivUnits().asSequence()
                .filter {it.hasUnique("Can attack submarines")}
                .flatMap {it.viewableTiles.asSequence()})
        civInfo.viewableInvisibleUnitsTiles = newViewableInvisibleTiles
        // updating the viewable tiles also affects the explored tiles, obvs

        // So why don't we play switcharoo with the explored tiles as well?
        // Well, because it gets REALLY LARGE so it's a lot of memory space,
        // and we never actually iterate on the explored tiles (only check contains()),
        // so there's no fear of concurrency problems.
        val newlyExploredTiles = newViewableTiles.asSequence().map { it.position }
                .filterNot { civInfo.exploredTiles.contains(it) }
        civInfo.exploredTiles.addAll(newlyExploredTiles)


        val viewedCivs = HashSet<CivilizationInfo>()
        for(tile in civInfo.viewableTiles){
            val tileOwner = tile.getOwner()
            if(tileOwner!=null) viewedCivs+=tileOwner
            for(unit in tile.getUnits()) viewedCivs+=unit.civInfo
        }

        if(!civInfo.isBarbarian()) {
            for (otherCiv in viewedCivs.filterNot { it == civInfo || it.isBarbarian() })
                if (!civInfo.diplomacy.containsKey(otherCiv.civName)) {
                    civInfo.meetCivilization(otherCiv)
                    civInfo.addNotification("We have encountered [${otherCiv.civName}]!".tr(), null, Color.GOLD)
                }
        }
    }

    fun updateHasActiveGreatWall(){
        civInfo.hasActiveGreatWall = !civInfo.tech.isResearched("Dynamite") &&
                civInfo.containsBuildingUnique("Enemy land units must spend 1 extra movement point when inside your territory (obsolete upon Dynamite)")
    }

    fun setCitiesConnectedToCapitalTransients(){
        if(civInfo.cities.isEmpty()) return // eg barbarians

        // We map which cities we've reached, to the mediums they've been reached by -
        // this is so we know that if we've seen which cities can be connected by port A, and one
        // of those is city B, then we don't need to check the cities that B can connect to by port,
        // since we'll get the same cities we got from A, since they're connected to the same sea.
        val citiesReachedToMediums = HashMap<CityInfo, ArrayList<String>>()
        var citiesToCheck = mutableListOf(civInfo.getCapital())
        citiesReachedToMediums[civInfo.getCapital()] = arrayListOf("Start")
        val allCivCities = civInfo.gameInfo.civilizations.flatMap { it.cities }

        while(citiesToCheck.isNotEmpty() && citiesReachedToMediums.size<allCivCities.size){
            val newCitiesToCheck = mutableListOf<CityInfo>()
            for(cityToConnectFrom in citiesToCheck){
                val reachedMediums = citiesReachedToMediums[cityToConnectFrom]!!

                // This is copypasta and can be cleaned up
                if(!reachedMediums.contains("Road")){

                    val roadBfs = BFS(cityToConnectFrom.getCenterTile()) { it.roadStatus != RoadStatus.None }
                    roadBfs.stepToEnd()
                    val reachedCities = allCivCities.filter { roadBfs.tilesReached.containsKey(it.getCenterTile())}
                    for(reachedCity in reachedCities){
                        if(!citiesReachedToMediums.containsKey(reachedCity)){
                            newCitiesToCheck.add(reachedCity)
                            citiesReachedToMediums[reachedCity] = arrayListOf()
                        }
                        val cityReachedByMediums = citiesReachedToMediums[reachedCity]!!
                        if(!cityReachedByMediums.contains("Road"))
                            cityReachedByMediums.add("Road")
                    }
                    citiesReachedToMediums[cityToConnectFrom]!!.add("Road")
                }

                if(!reachedMediums.contains("Harbor")
                        && cityToConnectFrom.cityConstructions.containsBuildingOrEquivalent("Harbor")){
                    val seaBfs = BFS(cityToConnectFrom.getCenterTile()) { it.isWater || it.isCityCenter() }
                    seaBfs.stepToEnd()
                    val reachedCities = allCivCities.filter { seaBfs.tilesReached.containsKey(it.getCenterTile())}
                    for(reachedCity in reachedCities){
                        if(!citiesReachedToMediums.containsKey(reachedCity)){
                            newCitiesToCheck.add(reachedCity)
                            citiesReachedToMediums[reachedCity] = arrayListOf()
                        }
                        val cityReachedByMediums = citiesReachedToMediums[reachedCity]!!
                        if(!cityReachedByMediums.contains("Harbor"))
                            cityReachedByMediums.add("Harbor")
                    }
                    citiesReachedToMediums[cityToConnectFrom]!!.add("Harbor")
                }
            }
            citiesToCheck = newCitiesToCheck
        }

        civInfo.citiesConnectedToCapital = citiesReachedToMediums.keys.toList()
    }


    fun updateDetailedCivResources() {
        val newDetailedCivResources = ResourceSupplyList()
        for (city in civInfo.cities) newDetailedCivResources.add(city.getCityResources())
        for (dip in civInfo.diplomacy.values) newDetailedCivResources.add(dip.resourcesFromTrade())
        for(resource in civInfo.getCivUnits().mapNotNull { it.baseUnit.requiredResource }.map { GameBasics.TileResources[it]!! })
            newDetailedCivResources.add(resource,-1,"Units")
        civInfo.detailedCivResources = newDetailedCivResources
    }
}