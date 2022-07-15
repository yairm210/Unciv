package com.unciv.logic.civilization

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.INonPerpetualConstruction
import com.unciv.models.Counter
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import java.util.*
import kotlin.collections.HashMap

class CivConstructions : IsPartOfGameInfoSerialization {

    @Transient
    lateinit var civInfo: CivilizationInfo

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

    fun setTransients(civInfo: CivilizationInfo) {
        this.civInfo = civInfo
    }

    fun startTurn() {
        tryAddFreeBuildings()
    }

    fun tryAddFreeBuildings() {
        addFreeStatsBuildings()
        addFreeSpecificBuildings()
    }

    fun getFreeBuildings(cityId: String): HashSet<String> {
        val toReturn = freeBuildings[cityId] ?: hashSetOf()
        for (city in civInfo.cities) {
            val freeBuildingsProvided =
                city.cityConstructions.freeBuildingsProvidedFromThisCity[cityId]
            if (freeBuildingsProvided != null)
                toReturn.addAll(freeBuildingsProvided)
        }
        return toReturn
    }

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

            addFreeBuilding(city.id, building)
        }
    }

    fun countConstructedObjects(objectToCount: INonPerpetualConstruction): Int {
        val amountInSpaceShip = civInfo.victoryManager.currentsSpaceshipParts[objectToCount.name] ?: 0

        return amountInSpaceShip + when (objectToCount) {
            is Building -> civInfo.cities.count {
                it.cityConstructions.containsBuildingOrEquivalent(objectToCount.name)
                || it.cityConstructions.isBeingConstructedOrEnqueued(objectToCount.name)
            }
            is BaseUnit -> civInfo.getCivUnits().count { it.name == objectToCount.name } +
                civInfo.cities.count { it.cityConstructions.isBeingConstructedOrEnqueued(objectToCount.name) }
            else -> 0
        }
    }
}
