package com.unciv.logic.civilization

import com.unciv.logic.city.INonPerpetualConstruction
import com.unciv.models.Counter
import com.unciv.models.stats.Stat
import java.util.*
import kotlin.collections.HashMap

class CivConstructions() {

    @Transient
    lateinit var civInfo: CivilizationInfo

    // Maps objects to the amount of times bought
    val boughtItemsWithIncreasingPrice: Counter<String> = Counter()

    // Maps to cities to all free buildings they contain
    private val maintenanceFreeBuildings: HashMap<String,HashSet<String>> = hashMapOf()

    // Maps stats to the cities that have received a building of that stat
    // Android Studio said an EnumMap would be better, so I've used that
    private val freeStatBuildingsProvided: HashMap<Stat, HashSet<String>> = hashMapOf()
    
    // Maps buildings to the cities that have received that building
    private val freeSpecificBuildingsProvided: HashMap<String, HashSet<String>> = hashMapOf()
    
    init {
        for (stat in Stat.values()) {
            freeStatBuildingsProvided[stat] = hashSetOf()
        }
    }
    
    fun clone(): CivConstructions {
        val toReturn = CivConstructions()
        toReturn.civInfo = civInfo
        toReturn.maintenanceFreeBuildings.putAll(maintenanceFreeBuildings)
        toReturn.freeStatBuildingsProvided.putAll(freeStatBuildingsProvided)
        toReturn.freeSpecificBuildingsProvided.putAll(freeSpecificBuildingsProvided)
        toReturn.boughtItemsWithIncreasingPrice.add(boughtItemsWithIncreasingPrice.clone())
        return toReturn
    }
    
    fun setTransients(civInfo: CivilizationInfo) {
        this.civInfo = civInfo
        
        // civInfo.boughtConstructionsWithGloballyIncreasingPrice deprecated since 3.16.15, this is replacement code
            if (civInfo.boughtConstructionsWithGloballyIncreasingPrice.isNotEmpty()) {
                for (item in civInfo.boughtConstructionsWithGloballyIncreasingPrice) {
                    boughtItemsWithIncreasingPrice.add(item.key, item.value)
                }
                civInfo.boughtConstructionsWithGloballyIncreasingPrice.clear()
            }
        //
        
        // Deprecated variables in civ.policies since 3.16.15, this is replacement code
            if (civInfo.policies.specificBuildingsAdded.isNotEmpty()) {
                for ((building, cities) in civInfo.policies.specificBuildingsAdded) {
                    for (cityId in cities) {
                        if (building !in freeSpecificBuildingsProvided)
                            freeSpecificBuildingsProvided[building] = hashSetOf()
                        freeSpecificBuildingsProvided[building]!!.add(cityId)

                        if (cityId !in maintenanceFreeBuildings)
                            maintenanceFreeBuildings[cityId] = hashSetOf()
                        maintenanceFreeBuildings[cityId]!!.add(building)
                    }
                }
                civInfo.policies.specificBuildingsAdded.clear()
            }
        
            if (civInfo.policies.cultureBuildingsAdded.isNotEmpty()) {
                for ((cityId, building) in civInfo.policies.cultureBuildingsAdded) {
                    freeStatBuildingsProvided[Stat.Culture]!!.add(cityId)
                    
                    if (cityId !in maintenanceFreeBuildings)
                        maintenanceFreeBuildings[cityId] = hashSetOf()
                    maintenanceFreeBuildings[cityId]!!.add(building)
                }
                civInfo.policies.cultureBuildingsAdded.clear()
            }
        //
    }
    
    fun startTurn() {
        tryAddFreeBuildings()
    }
    
    fun tryAddFreeBuildings() {
        addFreeStatsBuildings()
        addFreeSpecificBuildings()
    }
    
    fun getMaintenanceFreeBuildings(cityId: String) = maintenanceFreeBuildings[cityId] ?: hashSetOf()
    
    
    private fun addFreeStatsBuildings() {
        val statUniquesData = civInfo.getMatchingUniques("Provides the cheapest [] building in your first [] cities for free")
            .groupBy { it.params[0] }
            .mapKeys { Stat.valueOf(it.key) }
            .mapValues { unique -> unique.value.sumOf { it.params[1].toInt() } }
            .toMutableMap()
        
        // Deprecated since 3.16.15
            statUniquesData[Stat.Culture] = (statUniquesData[Stat.Culture] ?: 0) +
                civInfo.getMatchingUniques("Immediately creates the cheapest available cultural building in each of your first [] cities for free")
                    .sumOf { it.params[1].toInt() }
        //
        
        
        for ((stat, amount) in statUniquesData) {
            addFreeStatBuildings(stat, amount)
        }
    }
    
    private fun addFreeStatBuildings(stat: Stat, amount: Int) {
        for (city in civInfo.cities.take(amount)) {
            if (freeStatBuildingsProvided[stat]!!.contains(city.id) || !city.cityConstructions.hasBuildableStatBuildings(stat)) continue
            
            val builtBuilding = city.cityConstructions.addCheapestBuildableStatBuilding(stat)
            if (builtBuilding != null) {
                freeStatBuildingsProvided[stat]!!.add(city.id)
                if (!maintenanceFreeBuildings.containsKey(city.id))
                    maintenanceFreeBuildings[city.id] = hashSetOf()
                maintenanceFreeBuildings[city.id]!!.add(builtBuilding)
            }
        }
    }
    
    private fun addFreeSpecificBuildings() {
        val buildingsUniquesData = (civInfo.getMatchingUniques("Provides a [] in your first [] cities for free")
            // Deprecated since 3.16.15
                + civInfo.getMatchingUniques("Immediately creates a [] in each of your first [] cities for free")
            //
            ).groupBy { it.params[0] }
            .mapValues { unique -> unique.value.sumOf { it.params[1].toInt() } }

        for ((building, amount) in buildingsUniquesData) {
            addFreeBuildings(building, amount)
        }
    }
    
    private fun addFreeBuildings(building: String, amount: Int) {
        for (city in civInfo.cities.take(amount)) {
            if (freeSpecificBuildingsProvided[building]?.contains(city.id) == true || city.cityConstructions.containsBuildingOrEquivalent(building)) continue

            (city.cityConstructions.getConstruction(building) as INonPerpetualConstruction).postBuildEvent(city.cityConstructions)
            
            if (!freeSpecificBuildingsProvided.containsKey(building))
                freeSpecificBuildingsProvided[building] = hashSetOf()
            freeSpecificBuildingsProvided[building]!!.add(city.id)

            if (!maintenanceFreeBuildings.containsKey(city.id))
                maintenanceFreeBuildings[city.id] = hashSetOf()
            maintenanceFreeBuildings[city.id]!!.add(building)
        }
    }
}