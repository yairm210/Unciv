package com.unciv.logic.city

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.automation.Automation
import com.unciv.logic.map.TileInfo
import com.unciv.models.Counter
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.withItem
import com.unciv.ui.utils.withoutItem
import kotlin.math.floor
import kotlin.math.pow

class PopulationManager {
    @Transient
    lateinit var cityInfo: CityInfo

    var population = 1
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
        if (!cityInfo.civInfo.isPlayerCivilization())
            foodRequired *= cityInfo.civInfo.gameInfo.getDifficulty().aiCityGrowthModifier
        return foodRequired.toInt()
    }

    //endregion

    fun nextTurn(food: Int) {
        foodStored += food
        if (food < 0)
            cityInfo.civInfo.addNotification("[${cityInfo.name}] is starving!", cityInfo.location, Color.RED)
        if (foodStored < 0) {        // starvation!
            if (population > 1) population--
            foodStored = 0
        }
        if (foodStored >= getFoodToNextPopulation()) {  // growth!
            foodStored -= getFoodToNextPopulation()
            val percentOfFoodCarriedOver = cityInfo.cityConstructions.builtBuildingUniqueMap
                    .getUniques("[]% of food is carried over after population increases")
                    .sumBy { it.params[0].toInt() }
            foodStored += (getFoodToNextPopulation() * percentOfFoodCarriedOver / 100f).toInt()
            population++
            autoAssignPopulation()
            cityInfo.civInfo.addNotification("[${cityInfo.name}] has grown!", cityInfo.location, Color.GREEN)
        }
    }

    private fun getStatsOfSpecialist(name: String) = cityInfo.cityStats.getStatsOfSpecialist(name)


    // todo - change tile choice according to city!
    // if small city, favor production above all, ignore gold!
    // if larger city, food should be worth less!
    internal fun autoAssignPopulation(foodWeight: Float = 1f) {
        for (i in 1..getFreePopulation()) {
            //evaluate tiles
            val bestTile: TileInfo? = cityInfo.getTiles()
                    .filter { it.aerialDistanceTo(cityInfo.getCenterTile()) <= 3 }
                    .filterNot { it.isWorked() || cityInfo.location == it.position }
                    .maxBy { Automation.rankTileForCityWork(it, cityInfo, foodWeight) }
            val valueBestTile = if (bestTile == null) 0f
            else Automation.rankTileForCityWork(bestTile, cityInfo, foodWeight)

            val bestJob: String? = getMaxSpecialists()
                    .filter { specialistAllocations[it.key]!! < it.value }
                    .map { it.key }
                    .maxBy { Automation.rankSpecialist(getStatsOfSpecialist(it), cityInfo) }


            var valueBestSpecialist = 0f
            if (bestJob != null) {
                val specialistStats = getStatsOfSpecialist(bestJob)
                valueBestSpecialist = Automation.rankSpecialist(specialistStats, cityInfo)
            }

            //assign population
            if (valueBestTile > valueBestSpecialist) {
                if (bestTile != null)
                    cityInfo.workedTiles = cityInfo.workedTiles.withItem(bestTile.position)
            } else if (bestJob != null) specialistAllocations.add(bestJob, 1)
        }
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
        for ((specialistName, amount) in maxSpecialists)
            if (specialistsHashmap[specialistName]!! > amount)
                specialistAllocations[specialistName] = amount



        while (getFreePopulation() < 0) {
            //evaluate tiles
            val worstWorkedTile: TileInfo? = if (cityInfo.workedTiles.isEmpty()) null
            else {
                cityInfo.workedTiles.asSequence()
                        .map { cityInfo.tileMap[it] }
                        .minBy {
                            Automation.rankTileForCityWork(it, cityInfo)
                            +(if (it.isLocked()) 10 else 0)
                        }!!
            }
            val valueWorstTile = if (worstWorkedTile == null) 0f
            else Automation.rankTileForCityWork(worstWorkedTile, cityInfo)

            //evaluate specialists
            val worstJob: String? = specialistAllocations.keys
                    .minBy { Automation.rankSpecialist(getStatsOfSpecialist(it), cityInfo) }
            var valueWorstSpecialist = 0f
            if (worstJob != null)
                valueWorstSpecialist = Automation.rankSpecialist(getStatsOfSpecialist(worstJob), cityInfo)


            //un-assign population
            if ((worstWorkedTile != null && valueWorstTile < valueWorstSpecialist)
                    || worstJob == null) {
                cityInfo.workedTiles = cityInfo.workedTiles.withoutItem(worstWorkedTile!!.position)
            } else specialistAllocations.add(worstJob, -1)
        }

    }

    fun getMaxSpecialists(): Counter<String> {
        val counter = Counter<String>()
        for (building in cityInfo.cityConstructions.getBuiltBuildings())
            counter.add(building.newSpecialists())
        return counter
    }
}