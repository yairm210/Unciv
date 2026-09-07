package com.unciv.logic.civilization.managers

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.MayaLongCountAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.Counter
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.MayaCalendar
import yairm210.purity.annotations.Readonly


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

    @Readonly
    private fun getPoolKey(greatPerson: String) = civInfo.getEquivalentUnit(greatPerson)
        .getMatchingUniques(UniqueType.GPPointPool)
        // An empty string is used to indicate the Unique wasn't found
        .firstOrNull()?.params?.get(0) ?: ""
    
    @Readonly @Suppress("purity") 
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
    @Readonly
    fun getGreatPeople() = civInfo.gameInfo.ruleset.units.values.asSequence()
        .filter { it.isGreatPerson }
        .map { civInfo.getEquivalentUnit(it.name) }
        .filterNot { it.isUnavailableBySettings(civInfo.gameInfo) }
        .toHashSet()

    /**
     * Returns the Great People this Civilization can currently select for a free grant.
     *
     * An option being present does not guarantee placement will succeed when it is chosen.
     */
    @Readonly
    fun getFreeGreatPersonOptions(): Set<BaseUnit> {
        if (freeGreatPeople <= 0 || civInfo.cities.isEmpty()) return emptySet()

        val greatPeople = getGreatPeople()
        if (mayaLimitedFreeGP <= 0) return greatPeople
        return greatPeople.filter { it.name in longCountGPPool }.toSet()
    }

    /**
     * Attempts to grant the named option, returning the placed unit or `null` if the choice is not
     * currently available or placement fails. A failed placement consumes no entitlement or Maya
     * pool entry, though the existing placement internals may already have allocated a unit ID.
     */
    fun chooseFreeGreatPerson(unitName: String): MapUnit? {
        val choice = getFreeGreatPersonOptions().singleOrNull { it.name == unitName } ?: return null
        val useMayaLongCount = mayaLimitedFreeGP > 0
        val unit = civInfo.units.addUnit(choice, civInfo.getCapital()) ?: return null

        freeGreatPeople--
        if (useMayaLongCount) {
            mayaLimitedFreeGP--
            longCountGPPool.remove(choice.name)
        }
        return unit
    }

    @Readonly
    fun getGreatPersonPointsForNextTurn(): Counter<String> {
        val greatPersonPoints = Counter<String>()
        for (city in civInfo.cities) greatPersonPoints.add(city.getGreatPersonPoints())
        return greatPersonPoints
    }

}
