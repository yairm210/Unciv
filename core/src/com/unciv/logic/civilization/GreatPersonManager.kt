package com.unciv.logic.civilization

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.models.Counter
import java.util.HashSet

// todo: Great Admiral?
// todo: Free GP from policies and wonders should increase threshold according to the wiki
// todo: GP from Maya long count should increase threshold as well - implement together

class GreatPersonManager : IsPartOfGameInfoSerialization {
    var pointsForNextGreatPerson = 100
    var pointsForNextGreatGeneral = 200

    var greatPersonPointsCounter = Counter<String>()
    var greatGeneralPoints = 0
    var freeGreatPeople = 0
    /** Marks subset of [freeGreatPeople] as subject to maya ability restrictions (each only once untill all used) */
    var mayaLimitedFreeGP = 0
    /** Remaining candidates for maya ability - whenever empty refilled from all GP, starts out empty */
    var longCountGPPool = HashSet<String>()

    fun clone(): GreatPersonManager {
        val toReturn = GreatPersonManager()
        toReturn.freeGreatPeople = freeGreatPeople
        toReturn.greatPersonPointsCounter = greatPersonPointsCounter.clone()
        toReturn.pointsForNextGreatPerson = pointsForNextGreatPerson
        toReturn.pointsForNextGreatGeneral = pointsForNextGreatGeneral
        toReturn.greatGeneralPoints = greatGeneralPoints
        toReturn.mayaLimitedFreeGP = mayaLimitedFreeGP
        toReturn.longCountGPPool = longCountGPPool.toHashSet()
        return toReturn
    }

    fun getNewGreatPerson(): String? {
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
        return null
    }

    fun addGreatPersonPoints(greatPersonPointsForTurn: Counter<String>) {
        greatPersonPointsCounter.add(greatPersonPointsForTurn)
    }


}
