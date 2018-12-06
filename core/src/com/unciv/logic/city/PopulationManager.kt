package com.unciv.logic.city

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.automation.Automation
import com.unciv.logic.map.TileInfo
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.getRandom
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
            foodRequired *= cityInfo.civInfo.gameInfo.getPlayerCivilization().getDifficulty().aiCityGrowthModifier
        return foodRequired.toInt()
    }

    //endregion

    fun nextTurn(food: Float) {
        foodStored += food.roundToInt()
        if(food.roundToInt() < 0)
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
            if (cityInfo.getBuildingUniques().contains("40% of food is carried over after a new citizen is born")) foodStored += (0.4f * getFoodToNextPopulation()).toInt() // Aqueduct special
            if (cityInfo.getBuildingUniques().contains("25% of food is carried over after a new citizen is born")) foodStored += (0.25f * getFoodToNextPopulation()).toInt() // Medical Lab special
            population++
            autoAssignPopulation()
            cityInfo.civInfo.addNotification("["+cityInfo.name + "] has grown!", cityInfo.location, Color.GREEN)
        }
    }

    internal fun autoAssignPopulation() {
        if(getFreePopulation()==0) return

        //evaluate tiles
        val bestTile: TileInfo? = cityInfo.getTiles()
                .filter { it.arialDistanceTo(cityInfo.getCenterTile()) <= 3 }
                .filterNot { cityInfo.workedTiles.contains(it.position) || cityInfo.location==it.position}
                .maxBy { Automation().rankTile(it,cityInfo.civInfo) }
        val valueBestTile = Automation().rankTile(bestTile, cityInfo.civInfo)

        //evaluate specialists
        val maxSpecialistsMap = getMaxSpecialists().toHashMap()
        val policies = cityInfo.civInfo.policies.adoptedPolicies
        val bestJob: Stat? = specialists.toHashMap()
                .filter {maxSpecialistsMap.containsKey(it.key) && it.value < maxSpecialistsMap[it.key]!!}
                .maxBy { Automation().rankSpecialist(cityInfo.cityStats.getStatsOfSpecialist(it.key, policies), cityInfo.civInfo) }
                ?.key
        var valueBestSpecialist = 0f
        if (bestJob != null)
            valueBestSpecialist = Automation().rankSpecialist(cityInfo.cityStats.getStatsOfSpecialist(bestJob, policies), cityInfo.civInfo)

        //assign population
        if (valueBestTile > valueBestSpecialist) {
            if (bestTile != null)
                cityInfo.workedTiles.add(bestTile.position)
        } else {
            if (bestJob != null) {
                specialists.add(bestJob, 1f)
            }
        }
    }

    fun unassignExtraPopulation() {
        for(tile in cityInfo.workedTiles.map { cityInfo.tileMap[it] }) {
            if (tile.getCity() != cityInfo)
                cityInfo.workedTiles.remove(tile.position)
            if(tile.arialDistanceTo(cityInfo.getCenterTile()) > 3) // AutoAssignPopulation used to assign pop outside of allowed range, fixed as of 2.10.4
                cityInfo.workedTiles.remove(tile.position)
        }

        while (getFreePopulation()<0) {
            //evaluate tiles
            val worstWorkedTile: TileInfo? = cityInfo.workedTiles
                    .asSequence()
                    .map { cityInfo.tileMap[it] }
                    .minBy { Automation().rankTile(it, cityInfo.civInfo) }!!
            val valueWorstTile = Automation().rankTile(worstWorkedTile, cityInfo.civInfo)

            //evaluate specialists
            val policies = cityInfo.civInfo.policies.adoptedPolicies
            val worstJob: Stat? = specialists.toHashMap()
                    .filter { it.value > 0 }
                    .minBy { Automation().rankSpecialist(cityInfo.cityStats.getStatsOfSpecialist(it.key, policies), cityInfo.civInfo) }
                    ?.key
            var valueWorstSpecialist = 0f
            if (worstJob != null)
                valueWorstSpecialist = Automation().rankSpecialist(cityInfo.cityStats.getStatsOfSpecialist(worstJob, policies), cityInfo.civInfo)

            //un-assign population
            if ((valueWorstTile < valueWorstSpecialist && worstWorkedTile != null)
                    || (getNumberOfSpecialists() == 0)) {
                cityInfo.workedTiles.remove(worstWorkedTile!!.position)
            } else {
                specialists.add(worstJob!!, -1f)
            }
        }

        // unassign specialists that cannot be (e.g. the city was captured and one of the specialist buildings was destroyed)
        val maxSpecialists = getMaxSpecialists().toHashMap()
        val specialistsHashmap = specialists.toHashMap()
        for(entry in maxSpecialists)
            if(specialistsHashmap[entry.key]!! > entry.value)
                specialists.add(entry.key,specialistsHashmap[entry.key]!! - maxSpecialists[entry.key]!!)
    }

    fun getMaxSpecialists(): Stats {
        val maximumSpecialists = Stats()
        for (building in cityInfo.cityConstructions.getBuiltBuildings().filter { it.specialistSlots!=null })
            maximumSpecialists.add(building.specialistSlots!!)
        return maximumSpecialists
    }

}