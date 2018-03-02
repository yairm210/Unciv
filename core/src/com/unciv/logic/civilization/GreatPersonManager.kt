package com.unciv.logic.civilization

import com.unciv.models.stats.Stats

class GreatPersonManager {
    private var pointsForNextGreatPerson = 100
    private val greatPersonPoints = Stats()

    fun getNewGreatPerson(): String? {
        var greatPerson: String? = null
        when {
            greatPersonPoints.science > pointsForNextGreatPerson -> greatPerson = "Great Scientist"
            greatPersonPoints.production > pointsForNextGreatPerson -> greatPerson = "Great Engineer"
            greatPersonPoints.culture > pointsForNextGreatPerson -> greatPerson = "Great Artist"
            greatPersonPoints.gold > pointsForNextGreatPerson -> greatPerson = "Great Merchant"
        }

        if (greatPerson != null) {
            greatPersonPoints.science -= pointsForNextGreatPerson.toFloat()
            pointsForNextGreatPerson *= 2
        }
        return greatPerson
    }

    fun addGreatPersonPoints(greatPersonPoints: Stats) {
        greatPersonPoints.add(greatPersonPoints)
    }

}