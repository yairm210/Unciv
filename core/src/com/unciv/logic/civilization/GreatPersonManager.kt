package com.unciv.logic.civilization

import com.unciv.models.Counter
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats

class GreatPersonManager {
    var pointsForNextGreatPerson = 100
    var pointsForNextGreatGeneral = 30

    @Deprecated("As of 3.15.15 - Should be converted to greatPersonPointsCounter")
    var greatPersonPoints = Stats()
    var greatPersonPointsCounter = Counter<String>()
    var greatGeneralPoints = 0
    var freeGreatPeople = 0

    companion object {
        val statToGreatPersonMapping = hashMapOf<Stat, String>(
            Stat.Science to "Great Scientist",
            Stat.Production to "Great Engineer",
            Stat.Gold to "Great Merchant",
            Stat.Culture to "Great Artist",
        )

        fun statsToGreatPersonCounter(stats: Stats): Counter<String> {
            val counter = Counter<String>()
            for ((key, value) in stats)
                if (statToGreatPersonMapping.containsKey(key))
                    counter.add(statToGreatPersonMapping[key]!!, value.toInt())
            return counter
        }

        fun greatPersonCounterToStats(counter: Counter<String>): Stats {
            val stats = Stats()
            for ((key, value) in counter) {
                val stat = statToGreatPersonMapping.entries.firstOrNull { it.value == key }?.key
                if (stat != null) stats.add(stat, value.toFloat())
            }
            return stats
        }
    }

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