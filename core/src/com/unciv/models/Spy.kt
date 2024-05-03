package com.unciv.models

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.managers.EspionageManager
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.random.Random


enum class SpyAction(val displayString: String) {
    None("None"),
    Moving("Moving"),
    EstablishNetwork("Establishing Network"),
    Surveillance("Observing City"),
    StealingTech("Stealing Tech"),
    RiggingElections("Rigging Elections"),
    CounterIntelligence("Conducting Counter-intelligence"),
    Dead("Dead")
}


class Spy() : IsPartOfGameInfoSerialization {
    // `location == null` means that the spy is in its hideout
    private var location: Vector2? = null
    lateinit var name: String
    var action = SpyAction.None
        private set
    var rank: Int = 1
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
                    turnsRemainingForAction = 10
                } else if (location.civ == civInfo) {
                    action = SpyAction.CounterIntelligence
                    turnsRemainingForAction = 10
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
                var progressThisTurn = getLocation()!!.cityStats.currentCityStats.science
                // 33% spy bonus for each level
                progressThisTurn *= (rank + 2f) / 3f
                progressThisTurn *= getEfficiencyModifier().toFloat()
                progressTowardsStealingTech += progressThisTurn.toInt()
                if (progressTowardsStealingTech > techStealCost) {
                    stealTech()
                }
            }
            SpyAction.RiggingElections -> {
                --turnsRemainingForAction
                if (turnsRemainingForAction > 0) return

                rigElection()
            }
            SpyAction.Dead -> {
                --turnsRemainingForAction
                if (turnsRemainingForAction > 0) return

                val oldSpyName = name
                name = espionageManager.getSpyName()
                action = SpyAction.None
                civInfo.addNotification("We have recruited a new spy name [$name] after [$oldSpyName] was killed.",
                    NotificationCategory.Espionage, NotificationIcon.Spy)
            }
            SpyAction.CounterIntelligence -> {
                // Counter inteligence spies don't do anything here
                // However the AI will want to keep track of how long a spy has been doing counter intelligence for
                // Once turnRemainingForAction is <= 0 the spy won't be considered to be doing work any more
                --turnsRemainingForAction
                return
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
        // Lower is better
        var spyResult = Random(randomSeed.toInt()).nextInt(300)
        // Add our spies experience
        spyResult -= getSkillModifier()
        // Subtract the experience of the counter inteligence spies
        val defendingSpy = city.civ.espionageManager.getSpyAssignedToCity(city)
        spyResult += defendingSpy?.getSkillModifier() ?: 0

        val detectionString = when {
            spyResult < 0 -> null // Not detected
            spyResult < 100 -> "An unidentified spy stole the Technology [$stolenTech] from [$city]!"
            spyResult < 200 -> "A spy from [${civInfo.civName}] stole the Technology [$stolenTech] from [$city]!"
            else -> { // The spy was killed in the attempt
                if (defendingSpy == null) "A spy from [${civInfo.civName}] was found and killed trying to steal Technology in [$city]!"
                else "A spy from [${civInfo.civName}] was found and killed by [${defendingSpy.name}] trying to steal Technology in [$city]!"
            }
        }
        if (detectionString != null)
            otherCiv.addNotification(detectionString, city.location, NotificationCategory.Espionage, NotificationIcon.Spy)

        if (spyResult < 200) {
            civInfo.addNotification("Your spy [$name] stole the Technology [$stolenTech] from [$city]!", city.location,
                NotificationCategory.Espionage, NotificationIcon.Spy)
            startStealingTech()
            levelUpSpy()
        } else {
            civInfo.addNotification("Your spy [$name] was killed trying to steal Technology in [$city]!", city.location,
                NotificationCategory.Espionage, NotificationIcon.Spy)
            defendingSpy?.levelUpSpy()
            killSpy()
        }

    }

    private fun rigElection() {
        val city = getLocation()!!
        val cityStateCiv = city.civ
        // TODO: Simple implementation, please implement this in the future. This is a guess.
        turnsRemainingForAction = 10

        if (cityStateCiv.getAllyCiv() != null && cityStateCiv.getAllyCiv() != civInfo.civName) {
            val allyCiv = civInfo.gameInfo.getCivilization(cityStateCiv.getAllyCiv()!!)
            val defendingSpy = allyCiv.espionageManager.getSpyAssignedToCity(getLocation()!!)
            if (defendingSpy != null) {
                val randomSeed = city.location.x * city.location.y + 123f * civInfo.gameInfo.turns
                var spyResult = Random(randomSeed.toInt()).nextInt(120)
                spyResult -= getSkillModifier()
                spyResult += defendingSpy.getSkillModifier()
                if (spyResult > 100) {
                    // The Spy was killed
                    allyCiv.addNotification("A spy from [${civInfo.civName}] tried to rig elections and was found and killed in [${city}] by [${defendingSpy.name}]!",
                        getLocation()!!.location, NotificationCategory.Espionage, NotificationIcon.Spy)
                    civInfo.addNotification("Your spy [$name] was killed trying to rig the election in [$city]!", city.location,
                        NotificationCategory.Espionage, NotificationIcon.Spy)
                    killSpy()
                    defendingSpy.levelUpSpy()
                    return
                }
            }
        }
        // Starts at 10 influence and increases by 3 for each extra rank.
        cityStateCiv.getDiplomacyManager(civInfo).addInfluence(7f + getSpyRank() * 3)
        civInfo.addNotification("Your spy successfully rigged the election in [$city]!", city.location,
            NotificationCategory.Espionage, NotificationIcon.Spy)
    }

    fun moveTo(city: City?) {
        if (city == null) { // Moving to spy hideout
            location = null
            action = SpyAction.None
            turnsRemainingForAction = 0
            return
        }
        location = city.location
        action = SpyAction.Moving
        turnsRemainingForAction = 1
    }

    fun canMoveTo(city: City): Boolean {
        if (getLocation() == city) return true
        if (!city.getCenterTile().isVisible(civInfo)) return false
        return espionageManager.getSpyAssignedToCity(city) == null
    }

    fun isSetUp() = action !in listOf(SpyAction.Moving, SpyAction.None, SpyAction.EstablishNetwork)

    fun isIdle(): Boolean =action == SpyAction.None || action == SpyAction.Surveillance

    fun isDoingWork(): Boolean {
        if (action == SpyAction.StealingTech || action == SpyAction.EstablishNetwork || action == SpyAction.Moving) return true
        if (action == SpyAction.RiggingElections && !civInfo.isAtWarWith(getLocation()!!.civ)) return true
        if (action == SpyAction.CounterIntelligence && turnsRemainingForAction > 0) return true
        else return false
    }

    fun getLocation(): City? {
        if (location == null) return null
        return civInfo.gameInfo.tileMap[location!!].getCity()
    }

    fun getLocationName(): String {
        return getLocation()?.name ?: Constants.spyHideout
    }

    fun getSpyRank(): Int {
        return rank
    }

    fun levelUpSpy() {
        //TODO: Make the spy level cap dependent on some unique
        if (rank >= 3) return
        if (getLocation() != null) {
            civInfo.addNotification("Your spy [$name] has leveled up!", getLocation()!!.location,
                NotificationCategory.Espionage, NotificationIcon.Spy)
        } else {
            civInfo.addNotification("Your spy [$name] has leveled up!",
                NotificationCategory.Espionage, NotificationIcon.Spy)
        }
        rank++
    }

    fun getSkillModifier(): Int {
        return getSpyRank() * 30
    }

    /**
     * Gets a friendly and enemy efficiency uniques for the spy at the location
     * @return a value centered around 100 for the work efficiency of the spy, won't be negative
     */
    fun getEfficiencyModifier(): Double {
        lateinit var friendlyUniques: Sequence<Unique>
        lateinit var enemyUniques: Sequence<Unique>
        if (getLocation() != null) {
            val city = getLocation()!!
            if (city.civ == civInfo) {
                friendlyUniques = city.getMatchingUniques(UniqueType.SpyEffectiveness, StateForConditionals(city), includeCivUniques = true)
                enemyUniques = sequenceOf()
            } else {
                friendlyUniques = civInfo.getMatchingUniques(UniqueType.SpyEffectiveness)
                enemyUniques = city.getMatchingUniques(UniqueType.EnemySpyEffectiveness, StateForConditionals(city), includeCivUniques = true)
            }
        } else {
            friendlyUniques = civInfo.getMatchingUniques(UniqueType.SpyEffectiveness)
            enemyUniques = sequenceOf()
        }
        var totalEfficiency = 1.0
        totalEfficiency *= (100.0 + friendlyUniques.sumOf { it.params[0].toInt() }) / 100
        totalEfficiency *= (100.0 + enemyUniques.sumOf { it.params[0].toInt() }) / 100
        return totalEfficiency.coerceAtLeast(0.0)
    }

    fun killSpy() {
        // We don't actually remove this spy object, we set them as dead and let them revive
        moveTo(null)
        action = SpyAction.Dead
        turnsRemainingForAction = 5
        rank = 1
    }

    fun isAlive(): Boolean = action != SpyAction.Dead
}
