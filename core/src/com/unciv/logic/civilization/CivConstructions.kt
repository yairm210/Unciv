package com.unciv.logic.civilization

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.models.Counter
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.ui.components.extensions.yieldAllNotNull

class CivConstructions : IsPartOfGameInfoSerialization {

    @Transient
    lateinit var civInfo: Civilization

    // Maps objects to the amount of times bought
    val boughtItemsWithIncreasingPrice: Counter<String> = Counter()

    // Maps to cities to all free buildings they contain
    private val freeBuildings: HashMap<String, HashSet<String>> = hashMapOf()

    // Maps stats to the cities that have received a building of that stat
    // We can't use an enum instead of a string, due to the inability of the JSON-parser
    // to function properly and forcing this to be an `HashMap<String, HashSet<String>>`
    // when loading, even if this wasn't the original type, leading to run-time errors.
    private val freeStatBuildingsProvided: HashMap<String, HashSet<String>> = hashMapOf()

    // Maps buildings to the cities that have received that building
    private val freeSpecificBuildingsProvided: HashMap<String, HashSet<String>> = hashMapOf()

    init {
        for (stat in Stat.values()) {
            freeStatBuildingsProvided[stat.name] = hashSetOf()
        }
    }

    fun clone(): CivConstructions {
        val toReturn = CivConstructions()
        toReturn.civInfo = civInfo
        toReturn.freeBuildings.putAll(freeBuildings)
        toReturn.freeStatBuildingsProvided.putAll(freeStatBuildingsProvided)
        toReturn.freeSpecificBuildingsProvided.putAll(freeSpecificBuildingsProvided)
        toReturn.boughtItemsWithIncreasingPrice.add(boughtItemsWithIncreasingPrice.clone())
        return toReturn
    }

    fun setTransients(civInfo: Civilization) {
        this.civInfo = civInfo
    }

    fun startTurn() {
        tryAddFreeBuildings()
    }

    fun tryAddFreeBuildings() {
        addFreeStatsBuildings()
        addFreeSpecificBuildings()
    }

    /** Common to [hasFreeBuildingByName] and [getFreeBuildingNames] - 'has' doesn't need the whole set, one enumeration is enough.
     *  Note: Operates on String city.id and String building name, close to the serialized and stored form.
     *  When/if we do a transient cache for these using our objects, please rewrite this.
     */
    private fun getFreeBuildingNamesSequence(cityId: String) = sequence {
        yieldAllNotNull(freeBuildings[cityId])
        for (city in civInfo.cities) {
            yieldAllNotNull(city.cityConstructions.freeBuildingsProvidedFromThisCity[cityId])
        }
    }

    /** Gets a Set of all building names the [city] has for free, from nationwide sources or buildings in other cities */
    fun getFreeBuildingNames(city: City) =
        getFreeBuildingNamesSequence(city.id).toSet()

    /** Tests whether the [city] has [building] for free, from nationwide sources or buildings in other cities */
    fun hasFreeBuilding(city: City, building: Building) =
        hasFreeBuildingByName(city.id, building.name)

    /** Tests whether a city by [cityId] has a building named [buildingName] for free, from nationwide sources or buildings in other cities */
    private fun hasFreeBuildingByName(cityId: String, buildingName: String) =
        getFreeBuildingNamesSequence(cityId).contains(buildingName)

    private fun addFreeBuilding(cityId: String, building: String) {
        if (!freeBuildings.containsKey(cityId))
            freeBuildings[cityId] = hashSetOf()
        freeBuildings[cityId]!!.add(civInfo.getEquivalentBuilding(building).name)
    }

    private fun addFreeStatsBuildings() {
        val statUniquesData = civInfo.getMatchingUniques(UniqueType.FreeStatBuildings)
            .groupBy { it.params[0] }
            .mapKeys { Stat.valueOf(it.key) }
            .mapValues { unique -> unique.value.sumOf { it.params[1].toInt() } }
            .toMutableMap()

        for ((stat, amount) in statUniquesData) {
            addFreeStatBuildings(stat, amount)
        }
    }

    private fun addFreeStatBuildings(stat: Stat, amount: Int) {
        for (city in civInfo.cities.take(amount)) {
            if (freeStatBuildingsProvided[stat.name]!!.contains(city.id) || !city.cityConstructions.hasBuildableStatBuildings(stat)) continue

            val builtBuilding = city.cityConstructions.addCheapestBuildableStatBuilding(stat)
            if (builtBuilding != null) {
                freeStatBuildingsProvided[stat.name]!!.add(city.id)
                addFreeBuilding(city.id, builtBuilding)
            }
        }
    }

    private fun addFreeSpecificBuildings() {
        val buildingsUniquesData = civInfo.getMatchingUniques(UniqueType.FreeSpecificBuildings)
            .groupBy { it.params[0] }
            .mapValues { unique -> unique.value.sumOf { it.params[1].toInt() } }

        for ((building, amount) in buildingsUniquesData) {
            val civBuildingEquivalent = civInfo.getEquivalentBuilding(building)
            addFreeBuildings(civBuildingEquivalent, amount)
        }
    }

    private fun addFreeBuildings(building: Building, amount: Int) {

        for (city in civInfo.cities.take(amount)) {
            if (freeSpecificBuildingsProvided[building.name]?.contains(city.id) == true
                || city.cityConstructions.containsBuildingOrEquivalent(building.name)) continue

            building.postBuildEvent(city.cityConstructions)

            if (!freeSpecificBuildingsProvided.containsKey(building.name))
                freeSpecificBuildingsProvided[building.name] = hashSetOf()
            freeSpecificBuildingsProvided[building.name]!!.add(city.id)

            addFreeBuilding(city.id, building.name)
        }
    }

    fun countConstructedObjects(objectToCount: INonPerpetualConstruction): Int {
        val amountInSpaceShip = civInfo.victoryManager.currentsSpaceshipParts[objectToCount.name]

        return amountInSpaceShip + when (objectToCount) {
            is Building -> civInfo.cities.count {
                it.cityConstructions.containsBuildingOrEquivalent(objectToCount.name)
                || it.cityConstructions.isBeingConstructedOrEnqueued(objectToCount.name)
            }
            is BaseUnit -> civInfo.units.getCivUnits().count { it.name == objectToCount.name } +
                civInfo.cities.count { it.cityConstructions.isBeingConstructedOrEnqueued(objectToCount.name) }
            else -> 0
        }
    }
}
