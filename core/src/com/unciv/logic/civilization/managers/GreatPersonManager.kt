package com.unciv.logic.civilization.managers

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.MayaLongCountAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.models.Counter
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.MayaCalendar


// todo: Great Admiral?
// todo: Free GP from policies and wonders should increase threshold according to the wiki
// todo: GP from Maya long count should increase threshold as well - implement together

class GreatPersonManager : IsPartOfGameInfoSerialization {

    @Transient
    lateinit var civInfo: Civilization

    /** Base points, without speed modifier */
    var pointsForNextGreatPersonCounter = Counter<String>()  // Initial values assigned in getPointsRequiredForGreatPerson as needed
    var pointsForNextGreatGeneral = 200
    var pointsForNextGreatGeneralCounter = Counter<String>() // Initial values assigned when needed

    var greatPersonPointsCounter = Counter<String>()
    var greatGeneralPointsCounter = Counter<String>()
    var greatGeneralPoints = 0
    var freeGreatPeople = 0
    /** Marks subset of [freeGreatPeople] as subject to maya ability restrictions (each only once until all used) */
    var mayaLimitedFreeGP = 0
    /** Remaining candidates for maya ability - whenever empty refilled from all GP, starts out empty */
    var longCountGPPool = HashSet<String>()

    fun clone(): GreatPersonManager {
        val toReturn = GreatPersonManager()
        toReturn.freeGreatPeople = freeGreatPeople
        toReturn.greatPersonPointsCounter = greatPersonPointsCounter.clone()
        toReturn.pointsForNextGreatPersonCounter = pointsForNextGreatPersonCounter.clone()
        toReturn.pointsForNextGreatGeneralCounter = pointsForNextGreatGeneralCounter.clone()
        toReturn.greatGeneralPointsCounter = greatGeneralPointsCounter.clone()
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
        for ((unit, value) in greatGeneralPointsCounter){
            if (pointsForNextGreatGeneralCounter[unit] == 0) {
                pointsForNextGreatGeneralCounter[unit] = 200
            }
            val requiredPoints = pointsForNextGreatGeneralCounter[unit]
            if (value > requiredPoints) {
                greatGeneralPointsCounter[unit] -= requiredPoints
                pointsForNextGreatGeneralCounter[unit] += 50
                return unit
            }
        }

        for ((greatPerson, value) in greatPersonPointsCounter) {
            val requiredPoints = getPointsRequiredForGreatPerson(greatPerson)
            if (value >= requiredPoints) {
                greatPersonPointsCounter.add(greatPerson, -requiredPoints)
                pointsForNextGreatPersonCounter[getPoolKey(greatPerson)] *= 2
                return greatPerson
            }
        }
        return null
    }

    fun addGreatPersonPoints() {
        greatPersonPointsCounter.add(getGreatPersonPointsForNextTurn())
    }

    fun triggerMayanGreatPerson() {
        if (civInfo.isSpectator()) return
        val greatPeople = getGreatPeople()
        if (longCountGPPool.isEmpty())
            longCountGPPool = greatPeople.map { it.name }.toHashSet()

        freeGreatPeople++
        mayaLimitedFreeGP++

        // Anyone an idea for a good icon?
        val notification = "{A new b'ak'tun has just begun!}\n{A Great Person joins you!}"
        civInfo.addNotification(notification, MayaLongCountAction(), NotificationCategory.General, MayaCalendar.notificationIcon)
    }

    /** Get Great People specific to this manager's Civilization, already filtered by `isHiddenBySettings` */
    fun getGreatPeople() = civInfo.gameInfo.ruleset.units.values.asSequence()
        .filter { it.isGreatPerson }
        .map { civInfo.getEquivalentUnit(it.name) }
        .filterNot { it.isUnavailableBySettings(civInfo.gameInfo) }
        .toHashSet()

    fun getGreatPersonPointsForNextTurn(): Counter<String> {
        val greatPersonPoints = Counter<String>()
        for (city in civInfo.cities) greatPersonPoints.add(city.getGreatPersonPoints())
        return greatPersonPoints
    }

}
