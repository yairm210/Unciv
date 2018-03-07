package com.unciv.logic.city

import com.unciv.logic.map.TileInfo
import com.unciv.models.stats.Stats

class PopulationManager {

    @Transient
    @JvmField var cityInfo: CityInfo? = null
    @JvmField var population = 1
    @JvmField var foodStored = 0

    @JvmField var buildingsSpecialists = HashMap<String, Stats>()

    val specialists: Stats
        get() {
            val allSpecialists = Stats()
            for (stats in buildingsSpecialists.values)
                allSpecialists.add(stats)
            return allSpecialists
        }

    val numberOfSpecialists: Int
        get() {
            val specialists = specialists
            return (specialists.science + specialists.production + specialists.culture + specialists.gold).toInt()
        }


    // 1 is the city center
    val freePopulation: Int
        get() {
            val workingPopulation = cityInfo!!.tilesInRange.count { cityInfo!!.name == it.workingCity } - 1
            return population - workingPopulation - numberOfSpecialists
        }


    val foodToNextPopulation: Int
        get() {
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
            cityInfo!!.civInfo.gameInfo.addNotification(cityInfo!!.name + " is starving!", cityInfo!!.cityLocation)
        }
        if (foodStored >= foodToNextPopulation)
        // growth!
        {
            foodStored -= foodToNextPopulation
            if (cityInfo!!.buildingUniques.contains("FoodCarriesOver")) foodStored += (0.4f * foodToNextPopulation).toInt() // Aqueduct special
            population++
            autoAssignWorker()
            cityInfo!!.civInfo.gameInfo.addNotification(cityInfo!!.name + " has grown!", cityInfo!!.cityLocation)
        }
    }

    internal fun autoAssignWorker() {
        val toWork: TileInfo? = cityInfo!!.tilesInRange.filter { it.workingCity==null }.maxBy { cityInfo!!.rankTile(it) }
        if (toWork != null) // This is when we've run out of tiles!
            toWork.workingCity = cityInfo!!.name
    }

}
