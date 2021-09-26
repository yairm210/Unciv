package com.unciv.logic.civilization

import com.unciv.models.Counter
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats

class GreatPersonManager {
    var pointsForNextGreatPerson = 100
    var pointsForNextGreatGeneral = 200

    var greatPersonPointsCounter = Counter<String>()
    var greatGeneralPoints = 0
    var freeGreatPeople = 0


    fun clone(): GreatPersonManager {
        val toReturn = GreatPersonManager()
        toReturn.freeGreatPeople = freeGreatPeople
        toReturn.greatPersonPointsCounter = greatPersonPointsCounter.clone()
        toReturn.pointsForNextGreatPerson = pointsForNextGreatPerson
        toReturn.pointsForNextGreatGeneral = pointsForNextGreatGeneral
        toReturn.greatGeneralPoints = greatGeneralPoints
        return toReturn
    }

    fun getNewGreatPerson(): String? {
        val greatPerson: String? = null

        if (greatGeneralPoints > pointsForNextGreatGeneral) {
            greatGeneralPoints -= pointsForNextGreatGeneral
            pointsForNextGreatGeneral += 50
            return "Great General"
        }

        for ((key, value) in greatPersonPointsCounter) {
            if (value > pointsForNextGreatPerson) {
                greatPersonPointsCounter.add(key, -pointsForNextGreatPerson)
                pointsForNextGreatPerson *= 2
                return key
            }
        }
        return greatPerson
    }

    fun addGreatPersonPoints(greatPersonPointsForTurn: Counter<String>) {
        greatPersonPointsCounter.add(greatPersonPointsForTurn)
    }


}