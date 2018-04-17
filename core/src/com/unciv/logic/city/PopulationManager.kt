package com.unciv.logic.city

import com.unciv.logic.map.TileInfo
import com.unciv.models.stats.Stats

class PopulationManager {

    @Transient
    @JvmField var cityInfo: CityInfo? = null
    @JvmField var population = 1
    @JvmField var foodStored = 0

    @JvmField var buildingsSpecialists = HashMap<String, Stats>()

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
        val workingPopulation = cityInfo!!.getTilesInRange().count { cityInfo!!.name == it.workingCity } - 1
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
            foodStored = 0
            cityInfo!!.civInfo.addNotification(cityInfo!!.name + " is starving!", cityInfo!!.cityLocation)
        }
        if (foodStored >= getFoodToNextPopulation())
        // growth!
        {
            foodStored -= getFoodToNextPopulation()
            if (cityInfo!!.buildingUniques.contains("FoodCarriesOver")) foodStored += (0.4f * getFoodToNextPopulation()).toInt() // Aqueduct special
            population++
            autoAssignWorker()
            cityInfo!!.civInfo.addNotification(cityInfo!!.name + " has grown!", cityInfo!!.cityLocation)
        }
    }

    internal fun autoAssignWorker() {
        val toWork: TileInfo? = cityInfo!!.getTilesInRange().filter { it.workingCity==null }.maxBy { cityInfo!!.rankTile(it) }
        if (toWork != null) // This is when we've run out of tiles!
            toWork.workingCity = cityInfo!!.name
    }

}
