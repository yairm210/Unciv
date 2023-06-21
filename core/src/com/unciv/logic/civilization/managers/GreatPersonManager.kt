package com.unciv.logic.civilization.managers

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.Civilization
import com.unciv.models.Counter
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit


// todo: Great Admiral?
// todo: Free GP from policies and wonders should increase threshold according to the wiki
// todo: GP from Maya long count should increase threshold as well - implement together

class GreatPersonManager : IsPartOfGameInfoSerialization {

    @Transient
    lateinit var civInfo: Civilization

    /** Base points, without speed modifier */
    @Deprecated("Values are now maintaned in pointsForNextGreatPersonCounter", ReplaceWith("pointsForNextGreatPersonCounter[\"\"]"))
    var pointsForNextGreatPerson = 100
+   var pointsForNextGreatPersonCounter = Counter<String>()  // Initial values assigned in getPointsRequiredForGreatPerson as needed
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
        toReturn.pointsForNextGreatPersonCounter = pointsForNextGreatPersonCounter.clone()
        toReturn.pointsForNextGreatGeneral = pointsForNextGreatGeneral
        toReturn.greatGeneralPoints = greatGeneralPoints
        toReturn.mayaLimitedFreeGP = mayaLimitedFreeGP
        toReturn.longCountGPPool = longCountGPPool.toHashSet()
        return toReturn
    }

    private fun getPoolKey(greatPerson: String) = civInfo.getEquivalentUnit(greatPerson)
        .getMatchingUniques(UniqueType.GPPointPool)
        // An empty string is used to indicate the Unique wasn't found
        .firstOrNull()?.params?.get(0) ?: ""

    fun getPointsRequiredForGreatPerson(greatPerson: String): Int {
        val key = getPoolKey(greatPerson)
        if (pointsForNextGreatPersonCounter[key] == 0) {
            pointsForNextGreatPersonCounter[key] = 100
        }
        return (pointsForNextGreatPersonCounter[key] * civInfo.gameInfo.speed.modifier).toInt()
    }

    fun getNewGreatPerson(): String? {
        if (greatGeneralPoints > pointsForNextGreatGeneral) {
            greatGeneralPoints -= pointsForNextGreatGeneral
            pointsForNextGreatGeneral += 50
            return "Great General"
        }

        for ((greatPerson, value) in greatPersonPointsCounter) {
            val requiredPoints = getPointsRequiredForGreatPerson(greatPerson)
            if (value >= requiredPoints) {
                greatPersonPointsCounter.add(greatPerson, -requiredPoints)
                pointsForNextGreatPersonCounter[getPoolKey(greatPerson)] *= 2
                return key
            }
        }
        return null
    }

    fun addGreatPersonPoints() {
        greatPersonPointsCounter.add(getGreatPersonPointsForNextTurn())
    }


    fun getGreatPeople(): HashSet<BaseUnit> {
        val greatPeople = civInfo.gameInfo.ruleset.units.values.asSequence()
            .filter { it.isGreatPerson() }
            .map { civInfo.getEquivalentUnit(it.name) }
        return if (!civInfo.gameInfo.isReligionEnabled())
            greatPeople.filter { !it.hasUnique(UniqueType.HiddenWithoutReligion) }.toHashSet()
        else greatPeople.toHashSet()
    }

    fun getGreatPersonPointsForNextTurn(): Counter<String> {
        val greatPersonPoints = Counter<String>()
        for (city in civInfo.cities) greatPersonPoints.add(city.getGreatPersonPoints())
        return greatPersonPoints
    }

}
