package com.unciv.logic.civilization.managers

import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import kotlin.random.Random

enum class SpyAction(val stringName: String) {
    None("None"),
    Moving("Moving"),
    EstablishNetwork("Establishing Network"),
    Surveillance("Surveiying City"),
    StealingTech("Stealing Tech"),
    RiggingElections("Rigging Elections"),
    CounterIntelligence("Conducting Counter-intelligence")
}


class Spy() : IsPartOfGameInfoSerialization {
    // `location == null` means that the spy is in its hideout
    var location: String? = null
    lateinit var name: String
    var action = SpyAction.None
        private set
    var timeTillActionFinish = 0
        private set
    private var progressTowardsStealingTech = 0

    @Transient
    lateinit var civInfo: Civilization

    @Transient
    lateinit var espionageManager: EspionageManager

    constructor(name: String) : this() {
        this.name = name
    }

    fun clone(): Spy {
        val toReturn = Spy(name)
        toReturn.location = location
        toReturn.action = action
        toReturn.timeTillActionFinish = timeTillActionFinish
        toReturn.progressTowardsStealingTech = progressTowardsStealingTech
        return toReturn
    }

    fun setTransients(civInfo: Civilization, espionageManager: EspionageManager) {
        this.civInfo = civInfo
        this.espionageManager = espionageManager
    }

    fun endTurn() {
        when (action) {
            SpyAction.None -> return
            SpyAction.Moving -> {
                --timeTillActionFinish
                if (timeTillActionFinish != 0) return

                action = SpyAction.EstablishNetwork
                timeTillActionFinish = 3 // Depending on cultural familiarity level if that is ever implemented
            }
            SpyAction.EstablishNetwork -> {
                --timeTillActionFinish
                if (timeTillActionFinish != 0) return

                val location = getLocation()!! // This should never throw an exception, as going to the hideout sets your action to None.
                if (location.civ.isCityState()) {
                    action = SpyAction.RiggingElections
                } else if (location.civ == civInfo) {
                    action = SpyAction.CounterIntelligence
                } else {
                    startStealingTech()
                }
            }
            SpyAction.Surveillance -> {
                if (!getLocation()!!.civ.isMajorCiv()) return

                val stealableTechs = espionageManager.getTechsToSteal(getLocation()!!.civ)
                if (stealableTechs.isEmpty()) return
                action = SpyAction.StealingTech // There are new techs to steal!
            }
            SpyAction.StealingTech -> {
                val stealableTechs = espionageManager.getTechsToSteal(getLocation()!!.civ)
                if (stealableTechs.isEmpty()) {
                    action = SpyAction.Surveillance
                    timeTillActionFinish = 0
                    val notificationString = "Your spy [$name] cannot steal any more techs from [${getLocation()!!.civ}] as we've already researched all the technology they know!"
                    civInfo.addNotification(notificationString, getLocation()!!.location, NotificationCategory.Espionage, NotificationIcon.Spy)
                    return
                }
                val techStealCost = stealableTechs.maxOfOrNull { civInfo.gameInfo.ruleset.technologies[it]!!.cost }!!
                val progressThisTurn = getLocation()!!.cityStats.currentCityStats.science
                progressTowardsStealingTech += progressThisTurn.toInt()
                if (progressTowardsStealingTech > techStealCost) {
                    stealTech()
                }
            }
            else -> return // Not implemented yet, so don't do anything
        }
    }

    fun startStealingTech() {
        action = SpyAction.StealingTech
        timeTillActionFinish = 0
        progressTowardsStealingTech = 0
    }

    private fun stealTech() {
        val city = getLocation()!!
        val otherCiv = city.civ
        val randomSeed = city.location.x * city.location.y + 123f * civInfo.gameInfo.turns

        val stolenTech = espionageManager.getTechsToSteal(getLocation()!!.civ)
            .randomOrNull(Random(randomSeed.toInt())) // Could be improved to for example steal the most expensive tech or the tech that has the least progress as of yet
        if (stolenTech != null) {
            civInfo.tech.addTechnology(stolenTech)
        }

        val spyDetected = Random(randomSeed.toInt()).nextInt(3)
        val detectionString = when (spyDetected) {
            0 -> "A Spy from [${civInfo.civName}] stole the Technology [$stolenTech] from [$city]!"
            1 -> "An unidentifeid spy stole the Technology [$stolenTech] from [$city]!"
            else -> null // Not detected
        }
        if (detectionString != null)
            otherCiv.addNotification(detectionString, city.location, NotificationCategory.Espionage, NotificationIcon.Spy)
        civInfo.addNotification("Your spy [$name] stole the Technology [$stolenTech] from [$city]!", city.location, NotificationCategory.Espionage, NotificationIcon.Spy)


        startStealingTech()
    }


    fun moveTo(city: City?) {
        location = city?.id
        if (city == null) { // Moving to spy hideout
            action = SpyAction.None
            timeTillActionFinish = 0
            return
        }
        action = SpyAction.Moving
        timeTillActionFinish = 1
    }

    fun isSetUp() = action !in listOf(SpyAction.Moving, SpyAction.None, SpyAction.EstablishNetwork)

    fun getLocation(): City? {
        return civInfo.gameInfo.getCities().firstOrNull { it.id == location }
    }

    fun getLocationName(): String {
        return getLocation()?.name ?: Constants.spyHideout
    }
}

class EspionageManager : IsPartOfGameInfoSerialization {

    var spyCount = 0
    var spyList = mutableListOf<Spy>()
    var erasSpyEarnedFor = mutableListOf<String>()

    @Transient
    lateinit var civInfo: Civilization

    fun clone(): EspionageManager {
        val toReturn = EspionageManager()
        toReturn.spyCount = spyCount
        toReturn.spyList.addAll(spyList.map { it.clone() })
        toReturn.erasSpyEarnedFor.addAll(erasSpyEarnedFor)
        return toReturn
    }

    fun setTransients(civInfo: Civilization) {
        this.civInfo = civInfo
        for (spy in spyList) {
            spy.setTransients(civInfo, this)
        }
    }

    fun endTurn() {
        for (spy in spyList)
            spy.endTurn()
    }

    private fun getSpyName(): String {
        val usedSpyNames = spyList.map { it.name }.toHashSet()
        val validSpyNames = civInfo.nation.spyNames.filter { it !in usedSpyNames }
        if (validSpyNames.isEmpty()) { return "Spy ${spyList.size+1}" } // +1 as non-programmers count from 1
        return validSpyNames.random()
    }

    fun addSpy(): String {
        val spyName = getSpyName()
        val newSpy = Spy(spyName)
        newSpy.setTransients(civInfo, this)
        spyList.add(newSpy)
        ++spyCount
        return spyName
    }

    fun getTechsToSteal(otherCiv: Civilization): Set<String> {
        val techsToSteal = mutableSetOf<String>()
        for (tech in otherCiv.tech.techsResearched) {
            if (civInfo.tech.isResearched(tech)) continue
            if (!civInfo.tech.canBeResearched(tech)) continue
            techsToSteal.add(tech)
        }
        return techsToSteal
    }
}
