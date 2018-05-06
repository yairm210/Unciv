package com.unciv.logic.city

import com.unciv.logic.automation.Automation
import com.unciv.logic.map.TileInfo
import com.unciv.models.stats.Stats

class PopulationManager {

    @Transient
    lateinit var cityInfo: CityInfo
    var population = 1
    var foodStored = 0

    var buildingsSpecialists = HashMap<String, Stats>()

    fun getSpecialists(): Stats {
        val allSpecialists = Stats()
        for (stats in buildingsSpecialists.values)
            allSpecialists.add(stats)
        return allSpecialists
    }

    fun getNumberOfSpecialists(): Int {
        val specialists = getSpecialists()
        return (specialists.science + specialists.production + specialists.culture + specialists.gold).toInt()
    }


    // 1 is the city center
    fun getFreePopulation(): Int {
        val workingPopulation = cityInfo.workedTiles.size
        return population - workingPopulation - getNumberOfSpecialists()
    }


    fun getFoodToNextPopulation(): Int {
        // civ v math,civilization.wikia
        return 15 + 6 * (population - 1) + Math.floor(Math.pow((population - 1).toDouble(), 1.8)).toInt()
    }


    fun nextTurn(food: Float) {
        foodStored += food.toInt()
        if (foodStored < 0)
        // starvation!
        {
            population--
            val lowestRankedWorkedTile = cityInfo.workedTiles
                    .map{cityInfo.tileMap[it]}
                    .minBy { Automation().rankTile(it,cityInfo.civInfo) }!!
            cityInfo.workedTiles.remove(lowestRankedWorkedTile.position)
            foodStored = 0
            cityInfo.civInfo.addNotification(cityInfo.name + " is starving!", cityInfo.location)
        }
        if (foodStored >= getFoodToNextPopulation())
        // growth!
        {
            foodStored -= getFoodToNextPopulation()
            if (cityInfo.buildingUniques.contains("FoodCarriesOver")) foodStored += (0.4f * getFoodToNextPopulation()).toInt() // Aqueduct special
            population++
            autoAssignPopulation()
            cityInfo.civInfo.addNotification(cityInfo.name + " has grown!", cityInfo.location)
        }
    }

    internal fun autoAssignPopulation() {
        val toWork: TileInfo? = cityInfo.getTiles()
                .filterNot { cityInfo.workedTiles.contains(it.position) || cityInfo.location==it.position}
                .maxBy { Automation().rankTile(it,cityInfo.civInfo) }
        if (toWork != null) // This is when we've run out of tiles!
            cityInfo.workedTiles.add(toWork.position)
    }

}
