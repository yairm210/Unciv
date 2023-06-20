package com.unciv.models

import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.managers.EspionageManager
import kotlin.random.Random


enum class SpyAction(val displayString: String) {
    None("None"),
    Moving("Moving"),
    EstablishNetwork("Establishing Network"),
    Surveillance("Observing City"),
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
    var turnsRemainingForAction = 0
        private set
    private var progressTowardsStealingTech = 0

    @Transient
    lateinit var civInfo: Civilization

    @Transient
    private lateinit var espionageManager: EspionageManager

    constructor(name: String) : this() {
        this.name = name
    }

    fun clone(): Spy {
        val toReturn = Spy(name)
        toReturn.location = location
        toReturn.action = action
        toReturn.turnsRemainingForAction = turnsRemainingForAction
        toReturn.progressTowardsStealingTech = progressTowardsStealingTech
        return toReturn
    }

    fun setTransients(civInfo: Civilization) {
        this.civInfo = civInfo
        this.espionageManager = civInfo.espionageManager
    }

    fun endTurn() {
        when (action) {
            SpyAction.None -> return
            SpyAction.Moving -> {
                --turnsRemainingForAction
                if (turnsRemainingForAction > 0) return

                action = SpyAction.EstablishNetwork
                turnsRemainingForAction = 3 // Depending on cultural familiarity level if that is ever implemented
            }
            SpyAction.EstablishNetwork -> {
                --turnsRemainingForAction
                if (turnsRemainingForAction > 0) return

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
                    turnsRemainingForAction = 0
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
        turnsRemainingForAction = 0
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
            0 -> "A spy from [${civInfo.civName}] stole the Technology [$stolenTech] from [$city]!"
            1 -> "An unidentified spy stole the Technology [$stolenTech] from [$city]!"
            else -> null // Not detected
        }
        if (detectionString != null)
            otherCiv.addNotification(detectionString, city.location, NotificationCategory.Espionage, NotificationIcon.Spy)

        civInfo.addNotification("Your spy [$name] stole the Technology [$stolenTech] from [$city]!", city.location,
            NotificationCategory.Espionage,
            NotificationIcon.Spy
        )


        startStealingTech()
    }


    fun moveTo(city: City?) {
        location = city?.id
        if (city == null) { // Moving to spy hideout
            action = SpyAction.None
            turnsRemainingForAction = 0
            return
        }
        action = SpyAction.Moving
        turnsRemainingForAction = 1
    }

    fun isSetUp() = action !in listOf(SpyAction.Moving, SpyAction.None, SpyAction.EstablishNetwork)

    fun getLocation(): City? {
        return civInfo.gameInfo.getCities().firstOrNull { it.id == location }
    }

    fun getLocationName(): String {
        return getLocation()?.name ?: Constants.spyHideout
    }
}
