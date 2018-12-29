package com.unciv.logic.civilization

import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats

class GreatPersonManager {
    var pointsForNextGreatPerson = 100
    var pointsForNextGreatGeneral = 30
    var greatPersonPoints = Stats()
    var greatGeneralPoints = 0
    var freeGreatPeople=0

    val statToGreatPersonMapping = HashMap<Stat,String>().apply {
        put(Stat.Science,"Great Scientist")
        put(Stat.Production,"Great Engineer")
        put(Stat.Gold, "Great Merchant")
        put(Stat.Culture, "Great Artist")
    }

    fun clone(): GreatPersonManager {
        val toReturn = GreatPersonManager()
        toReturn.freeGreatPeople=freeGreatPeople
        toReturn.greatPersonPoints=greatPersonPoints.clone()
        toReturn.pointsForNextGreatPerson=pointsForNextGreatPerson
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

        val greatPersonPointsHashmap = greatPersonPoints.toHashMap()
        for(entry in statToGreatPersonMapping){
            if(greatPersonPointsHashmap[entry.key]!!>pointsForNextGreatPerson){
                greatPersonPoints.add(entry.key,-pointsForNextGreatPerson.toFloat())
                pointsForNextGreatPerson*=2
                return entry.value
            }
        }
        return greatPerson
    }

    fun addGreatPersonPoints(greatPersonPointsForTurn: Stats) {
        greatPersonPoints.add(greatPersonPointsForTurn)
    }


}