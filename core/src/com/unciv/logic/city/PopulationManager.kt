package com.unciv.logic.city

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.automation.Automation
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.TileInfo
import com.unciv.models.Counter
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.utils.extensions.toPercent
import com.unciv.ui.utils.extensions.withItem
import com.unciv.ui.utils.extensions.withoutItem
import kotlin.math.floor
import kotlin.math.pow

class PopulationManager : IsPartOfGameInfoSerialization {
    @Transient
    lateinit var cityInfo: CityInfo

    var population = 1
        private set
    var foodStored = 0

    // In favor of this bad boy
    val specialistAllocations = Counter<String>()

    fun getNewSpecialists() = specialistAllocations //convertStatsToSpecialistHashmap(specialists)


    //region pure functions
    fun clone(): PopulationManager {
        val toReturn = PopulationManager()
        toReturn.specialistAllocations.add(specialistAllocations)
        toReturn.population = population
        toReturn.foodStored = foodStored
        return toReturn
    }

    fun getNumberOfSpecialists() = getNewSpecialists().values.sum()

    fun getFreePopulation(): Int {
        val workingPopulation = cityInfo.workedTiles.size
        return population - workingPopulation - getNumberOfSpecialists()
    }

    fun getFoodToNextPopulation(): Int {
        // civ v math, civilization.wikia
        var foodRequired = 15 + 6 * (population - 1) + floor((population - 1).toDouble().pow(1.8))
        if (cityInfo.civInfo.isCityState())
            foodRequired *= 1.5f
        if (!cityInfo.civInfo.isPlayerCivilization())
            foodRequired *= cityInfo.civInfo.gameInfo.getDifficulty().aiCityGrowthModifier
        return foodRequired.toInt()
    }

    //endregion

    /** Implements [UniqueParameterType.PopulationFilter][com.unciv.models.ruleset.unique.UniqueParameterType.PopulationFilter] */
    fun getPopulationFilterAmount(filter: String): Int {
        return when (filter) {
            "Specialists" -> getNumberOfSpecialists()
            "Population" -> population
            "Followers of the Majority Religion", "Followers of this Religion" -> cityInfo.religion.getFollowersOfMajorityReligion()
            "Unemployed" -> getFreePopulation()
            else -> 0
        }
    }


    fun nextTurn(food: Int) {
        foodStored += food
        if (food < 0)
            cityInfo.civInfo.addNotification("[${cityInfo.name}] is starving!", cityInfo.location, NotificationIcon.Growth, NotificationIcon.Death)
        if (foodStored < 0) {        // starvation!
            if (population > 1) addPopulation(-1)
            foodStored = 0
        }
        if (foodStored >= getFoodToNextPopulation()) {  // growth!
            foodStored -= getFoodToNextPopulation()
            var percentOfFoodCarriedOver =
                cityInfo.getMatchingUniques(UniqueType.CarryOverFood)
                    .filter { cityInfo.matchesFilter(it.params[1]) }
                    .sumOf { it.params[0].toInt() }
            // Try to avoid runaway food gain in mods, just in case
            if (percentOfFoodCarriedOver > 95) percentOfFoodCarriedOver = 95
            foodStored += (getFoodToNextPopulation() * percentOfFoodCarriedOver / 100f).toInt()
            addPopulation(1)
            cityInfo.updateCitizens = true
            cityInfo.civInfo.addNotification("[${cityInfo.name}] has grown!", cityInfo.location, NotificationIcon.Growth)
        }
    }

    private fun getStatsOfSpecialist(name: String) = cityInfo.cityStats.getStatsOfSpecialist(name)

    fun addPopulation(count: Int) {
        val changedAmount =
            if (population + count < 0) -population
            else count
        population += changedAmount
        val freePopulation = getFreePopulation()
        if (freePopulation < 0) {
            unassignExtraPopulation()
        } else {
            autoAssignPopulation()
        }

        if (cityInfo.civInfo.gameInfo.isReligionEnabled())
            cityInfo.religion.updatePressureOnPopulationChange(changedAmount)
    }

    fun setPopulation(count: Int) {
        addPopulation(-population + count)
    }

    internal fun autoAssignPopulation() {
        cityInfo.cityStats.update()  // calculate current stats with current assignments
        val cityStats = cityInfo.cityStats.currentCityStats
        cityInfo.currentGPPBonus = cityInfo.getGreatPersonPercentageBonus()  // pre-calculate
        var specialistFoodBonus = 2f  // See CityStats.calcFoodEaten()
        for (unique in cityInfo.getMatchingUniques(UniqueType.FoodConsumptionBySpecialists))
            if (cityInfo.matchesFilter(unique.params[1]))
                specialistFoodBonus *= unique.params[0].toPercent()
        specialistFoodBonus = 2f - specialistFoodBonus

        val currentCiv = cityInfo.civInfo

        for (i in 1..getFreePopulation()) {
            //evaluate tiles
            val (bestTile, valueBestTile) = cityInfo.getCenterTile().getTilesInDistance(3)
                    .filter { it.getOwner() == currentCiv }
                    .filterNot { it.providesYield() }
                    .associateWith { Automation.rankTileForCityWork(it, cityInfo, cityStats) }
                    .maxByOrNull { it.value }
                    ?: object : Map.Entry<TileInfo?, Float> {
                        override val key: TileInfo? = null
                        override val value = 0f
                    }

            val bestJob: String? = if (cityInfo.manualSpecialists) null else getMaxSpecialists()
                    .filter { specialistAllocations[it.key]!! < it.value }
                    .map { it.key }
                    .maxByOrNull { Automation.rankSpecialist(it, cityInfo, cityStats) }

            var valueBestSpecialist = 0f
            if (bestJob != null) {
                valueBestSpecialist = Automation.rankSpecialist(bestJob, cityInfo, cityStats)
            }

            //assign population
            if (valueBestTile > valueBestSpecialist) {
                if (bestTile != null) {
                    cityInfo.workedTiles = cityInfo.workedTiles.withItem(bestTile.position)
                    cityStats[Stat.Food] += bestTile.getTileStats(cityInfo, cityInfo.civInfo)[Stat.Food]
                }
            } else if (bestJob != null) {
                specialistAllocations.add(bestJob, 1)
                cityStats[Stat.Food] += specialistFoodBonus
            }
        }
        cityInfo.cityStats.update()
    }

    fun unassignExtraPopulation() {
        for (tile in cityInfo.workedTiles.map { cityInfo.tileMap[it] }) {
            if (tile.getOwner() != cityInfo.civInfo || tile.getWorkingCity() != cityInfo
                    || tile.aerialDistanceTo(cityInfo.getCenterTile()) > 3)
                cityInfo.workedTiles = cityInfo.workedTiles.withoutItem(tile.position)
        }

        // unassign specialists that cannot be (e.g. the city was captured and one of the specialist buildings was destroyed)
        val maxSpecialists = getMaxSpecialists()
        val specialistsHashmap = specialistAllocations
        for ((specialistName, amount) in specialistsHashmap)
            if (amount > maxSpecialists[specialistName]!!)
                specialistAllocations[specialistName] = maxSpecialists[specialistName]!!



        while (getFreePopulation() < 0) {
            //evaluate tiles
            val worstWorkedTile: TileInfo? = if (cityInfo.workedTiles.isEmpty()) null
            else {
                cityInfo.workedTiles.asSequence()
                        .map { cityInfo.tileMap[it] }
                        .minByOrNull {
                            Automation.rankTileForCityWork(it, cityInfo, cityInfo.cityStats.currentCityStats)
                            +(if (it.isLocked()) 10 else 0)
                        }!!
            }
            val valueWorstTile = if (worstWorkedTile == null) 0f
            else Automation.rankTileForCityWork(worstWorkedTile, cityInfo, cityInfo.cityStats.currentCityStats)

            //evaluate specialists
            val worstAutoJob: String? = if (cityInfo.manualSpecialists) null else specialistAllocations.keys
                    .minByOrNull { Automation.rankSpecialist(it, cityInfo, cityInfo.cityStats.currentCityStats) }
            var valueWorstSpecialist = 0f
            if (worstAutoJob != null)
                valueWorstSpecialist = Automation.rankSpecialist(worstAutoJob, cityInfo, cityInfo.cityStats.currentCityStats)


            // un-assign population
            when {
                worstAutoJob != null && worstWorkedTile != null -> {
                    // choose between removing a specialist and removing a tile
                    if (valueWorstTile < valueWorstSpecialist)
                        cityInfo.workedTiles = cityInfo.workedTiles.withoutItem(worstWorkedTile.position)
                    else
                        specialistAllocations.add(worstAutoJob, -1)
                }
                worstAutoJob != null -> specialistAllocations.add(worstAutoJob, -1)
                worstWorkedTile != null -> cityInfo.workedTiles = cityInfo.workedTiles.withoutItem(worstWorkedTile.position)
                else -> {
                    // It happens when "cityInfo.manualSpecialists == true"
                    //  and population goes below the number of specialists, e.g. city is razing.
                    // Let's give a chance to do the work automatically at least.
                    val worstJob = specialistAllocations.keys.minByOrNull {
                        Automation.rankSpecialist(it, cityInfo, cityInfo.cityStats.currentCityStats) }
                        ?: break // sorry, we can do nothing about that
                    specialistAllocations.add(worstJob, -1)
                }
            }
        }

    }

    fun getMaxSpecialists(): Counter<String> {
        val counter = Counter<String>()
        for (building in cityInfo.cityConstructions.getBuiltBuildings())
            counter.add(building.newSpecialists())
        return counter
    }
}
