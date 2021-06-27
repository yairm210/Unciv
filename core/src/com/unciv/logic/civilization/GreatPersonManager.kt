package com.unciv.logic.civilization

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats

class GreatPersonManager {
    var pointsForNextGreatPerson = 100
    var pointsForNextGreatGeneral = 30
    var pointsForNextGreatAdmiral = 200 // https://www.carlsguides.com/strategy/civilization5/greatpeople/greatadmiral.php
    var greatPersonPoints = Stats()
    var greatGeneralPoints = 0
    var greatAdmiralPoints = 0
    var freeGreatPeople = 0
    
    @Transient
    lateinit var greatGeneralEquivalents: List<String>
    
    @Transient
    lateinit var greatAdmiralEquivalents: List<String>

    val statToGreatPersonMapping = HashMap<Stat, String>().apply {
        put(Stat.Science, "Great Scientist")
        put(Stat.Production, "Great Engineer")
        put(Stat.Gold, "Great Merchant")
        put(Stat.Culture, "Great Artist")
    }

    fun clone(): GreatPersonManager {
        val toReturn = GreatPersonManager()
        toReturn.freeGreatPeople = freeGreatPeople
        toReturn.pointsForNextGreatPerson = pointsForNextGreatPerson
        toReturn.greatPersonPoints = greatPersonPoints.clone()
        toReturn.pointsForNextGreatGeneral = pointsForNextGreatGeneral
        toReturn.greatGeneralPoints = greatGeneralPoints
        toReturn.pointsForNextGreatAdmiral = pointsForNextGreatAdmiral
        toReturn.greatAdmiralPoints = greatAdmiralPoints
        return toReturn
    }
    
    fun setTransients(ruleset: Ruleset) {
        greatGeneralEquivalents = ruleset.units.filter { it.value.uniques.contains("Great Person - [War - Land]")  && it.value.replaces == null}.keys.toList()
        greatAdmiralEquivalents = ruleset.units.filter { it.value.uniques.contains("Great Person - [War - Water]") && it.value.replaces == null}.keys.toList()
    }

    fun getNewGreatPerson(): String? {
        val greatPerson: String? = null

        if (greatGeneralPoints > pointsForNextGreatGeneral) {
            greatGeneralPoints -= pointsForNextGreatGeneral
            pointsForNextGreatGeneral += 50
            return greatGeneralEquivalents.random()
        }
        
        if (greatAdmiralPoints > pointsForNextGreatAdmiral) {
            greatAdmiralPoints -= pointsForNextGreatAdmiral
            pointsForNextGreatAdmiral += 50 // This number is completely random and based on no source whatsoever
            return greatAdmiralEquivalents.random()
        }

        val greatPersonPointsHashmap = greatPersonPoints.toHashMap()
        for (entry in statToGreatPersonMapping) {
            if (greatPersonPointsHashmap[entry.key]!! > pointsForNextGreatPerson) {
                greatPersonPoints.add(entry.key, -pointsForNextGreatPerson.toFloat())
                pointsForNextGreatPerson *= 2
                return entry.value
            }
        }
        return greatPerson
    }

    fun addGreatPersonPoints(greatPersonPointsForTurn: Stats) {
        greatPersonPoints.add(greatPersonPointsForTurn)
    }

}