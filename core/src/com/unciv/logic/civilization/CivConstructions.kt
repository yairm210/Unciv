package com.unciv.logic.civilization

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.models.Counter
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.utils.addToMapOfSets
import com.unciv.utils.contains
import com.unciv.utils.yieldAllNotNull

class CivConstructions : IsPartOfGameInfoSerialization {

    @Transient
    lateinit var civInfo: Civilization

    /** Maps construction names to the amount of times bought */
    val boughtItemsWithIncreasingPrice: Counter<String> = Counter()

    /** Maps construction names to the amount of times built */
    val builtItemsWithIncreasingCost: Counter<String> = Counter()

    /** Maps cities by id to a set of all free buildings by name they contain.
     *  The building name is the Nation-specific equivalent if available.
     *  Sources: [UniqueType.FreeStatBuildings] **and** [UniqueType.FreeSpecificBuildings]
     *  This is persisted and _never_ cleared or elements removed (per civ and game).
     */
    private val freeBuildings: HashMap<String, HashSet<String>> = hashMapOf()

    /** Maps stat names to a set of cities by id that have received a building of that stat.
     *  Source: [UniqueType.FreeStatBuildings]
     *  This is persisted and _never_ cleared or elements removed (per civ and game).
     */
    // We can't use the Stat enum instead of a string, due to the inability of the JSON-parser
    // to function properly and forcing this to be an `HashMap<String, HashSet<String>>`
    // when loading, even if this wasn't the original type, leading to run-time errors.
    private val freeStatBuildingsProvided: HashMap<String, HashSet<String>> = hashMapOf()

    /** Maps buildings by name to a set of cities by id that have received that building.
     *  The building name is the Nation-specific equivalent if available.
     *  Source: [UniqueType.FreeSpecificBuildings]
     *  This is persisted and _never_ cleared or elements removed (per civ and game).
     */
    private val freeSpecificBuildingsProvided: HashMap<String, HashSet<String>> = hashMapOf()

    fun clone(): CivConstructions {
        val toReturn = CivConstructions()
        toReturn.civInfo = civInfo
        toReturn.freeBuildings.putAll(freeBuildings)
        toReturn.freeStatBuildingsProvided.putAll(freeStatBuildingsProvided)
        toReturn.freeSpecificBuildingsProvided.putAll(freeSpecificBuildingsProvided)
        toReturn.boughtItemsWithIncreasingPrice.add(boughtItemsWithIncreasingPrice)  // add copies
        toReturn.builtItemsWithIncreasingCost.add(builtItemsWithIncreasingCost)
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
        addFreeBuildings()
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
        freeBuildings.addToMapOfSets(cityId, civInfo.getEquivalentBuilding(building).name)
    }

    private fun addFreeStatsBuildings() {
        val statUniquesData = civInfo.getMatchingUniques(UniqueType.FreeStatBuildings)
            .filter { !it.hasTriggerConditional() }
            .groupBy { it.params[0] }
            .mapKeys { Stat.valueOf(it.key) }
            .mapValues { unique -> unique.value.sumOf { it.params[1].toInt() } }

        for ((stat, amount) in statUniquesData) {
            addFreeStatBuildings(stat, amount)
        }
    }

    fun addFreeStatBuildings(stat: Stat, amount: Int) {
        for (city in civInfo.cities.take(amount)) {
            if (freeStatBuildingsProvided.contains(stat.name, city.id)) continue
            val building = city.cityConstructions.cheapestStatBuilding(stat)
                ?: continue

            freeStatBuildingsProvided.addToMapOfSets(stat.name, city.id)
            addFreeBuilding(city.id, building.name)
            city.cityConstructions.completeConstruction(building)
        }
    }

    private fun addFreeSpecificBuildings() {
        val buildingsUniquesData = civInfo.getMatchingUniques(UniqueType.FreeSpecificBuildings)
            .filter { !it.hasTriggerConditional() }
            .groupBy { it.params[0] }
            .mapValues { unique -> unique.value.sumOf { it.params[1].toInt() } }

        for ((building, amount) in buildingsUniquesData) {
            val civBuildingEquivalent = civInfo.getEquivalentBuilding(building)
            addFreeBuildings(civBuildingEquivalent, amount)
        }
    }

    fun addFreeBuildings(building: Building, amount: Int) {
        for (city in civInfo.cities.take(amount)) {
            if (freeSpecificBuildingsProvided.contains(building.name, city.id)
                || city.cityConstructions.containsBuildingOrEquivalent(building.name)) continue

            freeSpecificBuildingsProvided.addToMapOfSets(building.name, city.id)
            addFreeBuilding(city.id, building.name)
            city.cityConstructions.completeConstruction(building)
        }
    }

    fun addFreeBuildings() {
        val autoGrantedBuildings = civInfo.gameInfo.ruleset.buildings.values
            .filter { it.hasUnique(UniqueType.GainBuildingWhereBuildable) }

        // "Gain a free [buildingName] [cityFilter]"
        val freeBuildingsFromCiv = civInfo.getMatchingUniques(UniqueType.GainFreeBuildings, StateForConditionals.IgnoreConditionals)
        for (city in civInfo.cities) {
            val freeBuildingsFromCity = city.getLocalMatchingUniques(UniqueType.GainFreeBuildings, StateForConditionals.IgnoreConditionals)
            val freeBuildingUniques = (freeBuildingsFromCiv + freeBuildingsFromCity)
                .filter { city.matchesFilter(it.params[1]) && it.conditionalsApply(city.state)
                    && !it.hasTriggerConditional() }
            for (unique in freeBuildingUniques) {
                val freeBuilding = city.civ.getEquivalentBuilding(unique.params[0])
                city.cityConstructions.freeBuildingsProvidedFromThisCity.addToMapOfSets(city.id, freeBuilding.name)

                if (city.cityConstructions.containsBuildingOrEquivalent(freeBuilding.name)) continue
                city.cityConstructions.completeConstruction(freeBuilding)
            }

            for (building in autoGrantedBuildings)
                if (building.isBuildable(city.cityConstructions))
                    city.cityConstructions.completeConstruction(building)
        }
    }

    /** Calculates a civ-wide total for [objectToCount].
     *
     *  It counts:
     *  * "Spaceship part" units added to "spaceship" in capital
     *  * Built buildings or those in a construction queue
     *  * Units on the map or being constructed
     */
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
