package com.unciv.logic.city

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.automation.Automation
import com.unciv.logic.map.TileInfo
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.withItem
import com.unciv.ui.utils.withoutItem
import kotlin.math.roundToInt

class PopulationManager {
    @Transient
    lateinit var cityInfo: CityInfo

    var population = 1
    var foodStored = 0

    val specialists = Stats()

    //region pure functions
    fun clone(): PopulationManager {
        val toReturn = PopulationManager()
        toReturn.specialists.add(specialists)
        toReturn.population=population
        toReturn.foodStored=foodStored
        return toReturn
    }

    fun getNumberOfSpecialists(): Int {
        return (specialists.science + specialists.production + specialists.culture + specialists.gold).toInt()
    }

    fun getFreePopulation(): Int {
        val workingPopulation = cityInfo.workedTiles.size
        return population - workingPopulation - getNumberOfSpecialists()
    }

    fun getFoodToNextPopulation(): Int {
        // civ v math, civilization.wikia
        var foodRequired =  15 + 6 * (population - 1) + Math.floor(Math.pow((population - 1).toDouble(), 1.8))
        if(!cityInfo.civInfo.isPlayerCivilization())
            foodRequired *= cityInfo.civInfo.gameInfo.getDifficulty().aiCityGrowthModifier
        return foodRequired.toInt()
    }

    //endregion

    fun nextTurn(food: Int) {
        foodStored += food
        if(food < 0)
            cityInfo.civInfo.addNotification("["+cityInfo.name + "] is starving!", cityInfo.location, Color.RED)
        if (foodStored < 0)
        // starvation!
        {
            if(population>1){
                population--
            }
            foodStored = 0
        }
        if (foodStored >= getFoodToNextPopulation())
        // growth!
        {
            foodStored -= getFoodToNextPopulation()
            if (cityInfo.containsBuildingUnique("40% of food is carried over after a new citizen is born")) foodStored += (0.4f * getFoodToNextPopulation()).toInt() // Aqueduct special
            if (cityInfo.containsBuildingUnique("25% of food is carried over after a new citizen is born")) foodStored += (0.25f * getFoodToNextPopulation()).toInt() // Medical Lab special
            population++
            autoAssignPopulation()
            cityInfo.civInfo.addNotification("["+cityInfo.name + "] has grown!", cityInfo.location, Color.GREEN)
        }
    }

    // todo - change tile choice according to city!
    // if small city, favor production above all, ignore gold!
    // if larger city, food should be worth less!
    internal fun autoAssignPopulation(foodWeight: Float = 1f) {
        if(getFreePopulation()==0) return

        //evaluate tiles
        val bestTile: TileInfo? = cityInfo.getTiles()
                .filter { it.aerialDistanceTo(cityInfo.getCenterTile()) <= 3 }
                .filterNot { cityInfo.workedTiles.contains(it.position) || cityInfo.location==it.position}
                .maxBy { Automation.rankTileForCityWork(it,cityInfo, foodWeight) }
        val valueBestTile = if(bestTile==null) 0f
        else Automation.rankTileForCityWork(bestTile, cityInfo, foodWeight)

        //evaluate specialists
        val maxSpecialistsMap = getMaxSpecialists().toHashMap()
        val policies = cityInfo.civInfo.policies.adoptedPolicies
        val bestJob: Stat? = specialists.toHashMap()
                .filter {maxSpecialistsMap.containsKey(it.key) && it.value < maxSpecialistsMap[it.key]!!}
                .map {it.key}
                .maxBy { Automation.rankSpecialist(cityInfo.cityStats.getStatsOfSpecialist(it, policies), cityInfo) }
        var valueBestSpecialist = 0f
        if (bestJob != null) {
            val specialistStats = cityInfo.cityStats.getStatsOfSpecialist(bestJob, policies)
            valueBestSpecialist = Automation.rankSpecialist(specialistStats, cityInfo)
        }

        //assign population
        if (valueBestTile > valueBestSpecialist) {
            if (bestTile != null)
                cityInfo.workedTiles = cityInfo.workedTiles.withItem(bestTile.position)
        } else {
            if (bestJob != null) {
                specialists.add(bestJob, 1f)
            }
        }
    }

    fun unassignExtraPopulation() {
        for(tile in cityInfo.workedTiles.map { cityInfo.tileMap[it] }) {
            if (tile.getCity() != cityInfo)
                cityInfo.workedTiles = cityInfo.workedTiles.withoutItem(tile.position)
            if(tile.aerialDistanceTo(cityInfo.getCenterTile()) > 3)
                cityInfo.workedTiles = cityInfo.workedTiles.withoutItem(tile.position)
        }

        // unassign specialists that cannot be (e.g. the city was captured and one of the specialist buildings was destroyed)
        val maxSpecialists = getMaxSpecialists().toHashMap()
        val specialistsHashmap = specialists.toHashMap()
        for(entry in maxSpecialists)
            if(specialistsHashmap[entry.key]!! > entry.value)
                specialists.add(entry.key,maxSpecialists[entry.key]!! - specialistsHashmap[entry.key]!!)

        while (getFreePopulation()<0) {
            //evaluate tiles
            val worstWorkedTile: TileInfo? = if(cityInfo.workedTiles.isEmpty()) null
                    else {
                cityInfo.workedTiles.asSequence()
                        .map { cityInfo.tileMap[it] }
                        .minBy { Automation.rankTileForCityWork(it, cityInfo)
                            + (if(it.isLocked()) 10 else 0) }!!
            }
            val valueWorstTile = if(worstWorkedTile==null) 0f
            else Automation.rankTileForCityWork(worstWorkedTile, cityInfo)


            //evaluate specialists
            val policies = cityInfo.civInfo.policies.adoptedPolicies
            val worstJob: Stat? = specialists.toHashMap()
                    .filter { it.value > 0 }
                    .map {it.key}
                    .minBy { Automation.rankSpecialist(cityInfo.cityStats.getStatsOfSpecialist(it, policies), cityInfo) }
            var valueWorstSpecialist = 0f
            if (worstJob != null)
                valueWorstSpecialist = Automation.rankSpecialist(cityInfo.cityStats.getStatsOfSpecialist(worstJob, policies), cityInfo)

            //un-assign population
            if ((worstWorkedTile != null && valueWorstTile < valueWorstSpecialist)
                    || worstJob == null) {
                cityInfo.workedTiles = cityInfo.workedTiles.withoutItem(worstWorkedTile!!.position)
            } else {
                specialists.add(worstJob, -1f)
            }
        }

    }

    fun getMaxSpecialists(): Stats {
        val maximumSpecialists = Stats()
        for (building in cityInfo.cityConstructions.getBuiltBuildings().filter { it.specialistSlots!=null })
            maximumSpecialists.add(building.specialistSlots!!)
        return maximumSpecialists
    }

}