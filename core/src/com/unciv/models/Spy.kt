package com.unciv.models

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.EspionageAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.civilization.managers.EspionageManager
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.random.Random


enum class SpyAction(val displayString: String, val hasTurns: Boolean, internal val isSetUp: Boolean, private val isDoingWork: Boolean = false) {
    None("None", false, false),
    Moving("Moving", true, false, true),
    EstablishNetwork("Establishing Network", true, false, true),
    Surveillance("Observing City", false, true),
    StealingTech("Stealing Tech", false, true, true),
    RiggingElections("Rigging Elections", true, true) {
        override fun isDoingWork(spy: Spy) = !spy.civInfo.isAtWarWith(spy.getCity().civ)
    },
    CounterIntelligence("Conducting Counter-intelligence", false, true) {
        override fun isDoingWork(spy: Spy) = spy.turnsRemainingForAction > 0
    },
    Dead("Dead", true, false),
    ;
    internal open fun isDoingWork(spy: Spy) = isDoingWork
}


class Spy private constructor() : IsPartOfGameInfoSerialization {
    lateinit var name: String
        private set
    var rank: Int = 1
        private set

    // `location == null` means that the spy is in its hideout
    private var location: Vector2? = null

    var action = SpyAction.None
        private set

    var turnsRemainingForAction = 0
        private set
    private var progressTowardsStealingTech = 0

    @Transient
    lateinit var civInfo: Civilization
        private set

    @Transient
    private lateinit var espionageManager: EspionageManager

    @Transient
    private var city: City? = null

    constructor(name: String, rank:Int) : this() {
        this.name = name
        this.rank = rank
    }

    fun clone(): Spy {
        val toReturn = Spy(name, rank)
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

    private fun setAction(newAction: SpyAction, turns: Int = 0) {
        assert(!newAction.hasTurns || turns > 0) // hasTurns==false but turns > 0 is allowed (CounterIntelligence), hasTurns==true and turns==0 is not.
        action = newAction
        turnsRemainingForAction = turns
    }

    fun endTurn() {
        if (action.hasTurns && --turnsRemainingForAction > 0) return
        when (action) {
            SpyAction.None -> return
            SpyAction.Moving ->
                setAction(SpyAction.EstablishNetwork, 3)
            SpyAction.EstablishNetwork -> {
                val city = getCity() // This should never throw an exception, as going to the hideout sets your action to None.
                if (city.civ.isCityState())
                    setAction(SpyAction.RiggingElections, 10)
                else if (city.civ == civInfo)
                    setAction(SpyAction.CounterIntelligence, 10)
                else
                    startStealingTech()
            }
            SpyAction.Surveillance -> {
                if (!getCity().civ.isMajorCiv()) return

                val stealableTechs = espionageManager.getTechsToSteal(getCity().civ)
                if (stealableTechs.isEmpty()) return
                setAction(SpyAction.StealingTech) // There are new techs to steal!
            }
            SpyAction.StealingTech -> {
                val stealableTechs = espionageManager.getTechsToSteal(getCity().civ)
                if (stealableTechs.isEmpty()) {
                    setAction(SpyAction.Surveillance)
                    addNotification("Your spy [$name] cannot steal any more techs from [${getCity().civ}] as we've already researched all the technology they know!")
                    return
                }
                val techStealCost = stealableTechs.maxOfOrNull { civInfo.gameInfo.ruleset.technologies[it]!!.cost }!!
                var progressThisTurn = getCity().cityStats.currentCityStats.science
                // 33% spy bonus for each level
                progressThisTurn *= (rank + 2f) / 3f
                progressThisTurn *= getEfficiencyModifier().toFloat()
                progressTowardsStealingTech += progressThisTurn.toInt()
                if (progressTowardsStealingTech > techStealCost) {
                    stealTech()
                }
            }
            SpyAction.RiggingElections -> {
                rigElection()
            }
            SpyAction.Dead -> {
                val oldSpyName = name
                name = espionageManager.getSpyName()
                setAction(SpyAction.None)
                rank = espionageManager.getStartingSpyRank()
                addNotification("We have recruited a new spy name [$name] after [$oldSpyName] was killed.")
            }
            SpyAction.CounterIntelligence -> {
                // Counter intelligence spies don't do anything here
                // However the AI will want to keep track of how long a spy has been doing counter intelligence for
                // Once turnsRemainingForAction is <= 0 the spy won't be considered to be doing work any more
                --turnsRemainingForAction
                return
            }
        }
    }

    private fun startStealingTech() {
        setAction(SpyAction.StealingTech)
        progressTowardsStealingTech = 0
    }

    private fun stealTech() {
        val city = getCity()
        val otherCiv = city.civ
        val randomSeed = randomSeed()

        val stolenTech = espionageManager.getTechsToSteal(getCity().civ)
            .randomOrNull(Random(randomSeed)) // Could be improved to for example steal the most expensive tech or the tech that has the least progress as of yet
        if (stolenTech != null) {
            civInfo.tech.addTechnology(stolenTech)
        }

        // Lower is better
        var spyResult = Random(randomSeed).nextInt(300)
        // Add our spies experience
        spyResult -= getSkillModifier()
        // Subtract the experience of the counter intelligence spies
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
            // Not using Spy.addNotification, shouldn't open the espionage screen
            otherCiv.addNotification(detectionString, city.location, NotificationCategory.Espionage, NotificationIcon.Spy)

        if (spyResult < 200) {
            addNotification("Your spy [$name] stole the Technology [$stolenTech] from [$city]!")
            startStealingTech()
            levelUpSpy()
        } else {
            addNotification("Your spy [$name] was killed trying to steal Technology in [$city]!")
            defendingSpy?.levelUpSpy()
            killSpy()
        }

        if (spyResult >= 100) {
            otherCiv.getDiplomacyManager(civInfo).addModifier(DiplomaticModifiers.SpiedOnUs, -15f)
        }
    }

    private fun rigElection() {
        val city = getCity()
        val cityStateCiv = city.civ
        // TODO: Simple implementation, please implement this in the future. This is a guess.
        turnsRemainingForAction = 10

        if (cityStateCiv.getAllyCiv() != null && cityStateCiv.getAllyCiv() != civInfo.civName) {
            val allyCiv = civInfo.gameInfo.getCivilization(cityStateCiv.getAllyCiv()!!)
            val defendingSpy = allyCiv.espionageManager.getSpyAssignedToCity(city)
            if (defendingSpy != null) {
                var spyResult = Random(randomSeed()).nextInt(120)
                spyResult -= getSkillModifier()
                spyResult += defendingSpy.getSkillModifier()
                if (spyResult > 100) {
                    // The Spy was killed (use the notification without EspionageAction)
                    allyCiv.addNotification("A spy from [${civInfo.civName}] tried to rig elections and was found and killed in [${city}] by [${defendingSpy.name}]!",
                        city.location, NotificationCategory.Espionage, NotificationIcon.Spy)
                    addNotification("Your spy [$name] was killed trying to rig the election in [$city]!")
                    killSpy()
                    defendingSpy.levelUpSpy()
                    return
                }
            }
        }
        // Starts at 10 influence and increases by 3 for each extra rank.
        cityStateCiv.getDiplomacyManager(civInfo).addInfluence(7f + rank * 3)
        civInfo.addNotification("Your spy successfully rigged the election in [$city]!", city.location,
            NotificationCategory.Espionage, NotificationIcon.Spy)
    }

    fun moveTo(city: City?) {
        if (city == null) { // Moving to spy hideout
            location = null
            this.city = null
            setAction(SpyAction.None)
            return
        }
        location = city.location
        this.city = city
        setAction(SpyAction.Moving, 1)
    }

    fun canMoveTo(city: City): Boolean {
        if (getCityOrNull() == city) return true
        if (!city.getCenterTile().isExplored(civInfo)) return false
        return espionageManager.getSpyAssignedToCity(city) == null
    }

    fun isSetUp() = action.isSetUp

    fun isIdle() = action == SpyAction.None

    fun isDoingWork() = action.isDoingWork(this)

    /** Returns the City this Spy is in, or `null` if it is in the hideout. */
    fun getCityOrNull(): City? {
        if (location == null) return null
        if (city == null) city = civInfo.gameInfo.tileMap[location!!].getCity()
        return city
    }

    /** Non-null version of [getCityOrNull] for the frequent case it is known the spy cannot be in the hideout.
     *  @throws NullPointerException if the spy is in the hideout */
    fun getCity(): City = getCityOrNull()!!

    fun getLocationName() = getCityOrNull()?.name ?: Constants.spyHideout

    fun levelUpSpy() {
        //TODO: Make the spy level cap dependent on some unique
        if (rank >= 3) return
        addNotification("Your spy [$name] has leveled up!")
        rank++
    }

    private fun getSkillModifier() = rank * 30

    /**
     * Gets a friendly and enemy efficiency uniques for the spy at the location
     * @return a value centered around 100 for the work efficiency of the spy, won't be negative
     */
    fun getEfficiencyModifier(): Double {
        val friendlyUniques: Sequence<Unique>
        val enemyUniques: Sequence<Unique>
        val city = getCityOrNull()
        when {
            city == null -> {
                // Spy is in hideout - effectiveness won't matter
                friendlyUniques = civInfo.getMatchingUniques(UniqueType.SpyEffectiveness)
                enemyUniques = sequenceOf()
            }
            city.civ == civInfo -> {
                // Spy is in our own city
                friendlyUniques = city.getMatchingUniques(UniqueType.SpyEffectiveness, StateForConditionals(city), includeCivUniques = true)
                enemyUniques = sequenceOf()
            }
            else -> {
                // Spy is active in a foreign city
                friendlyUniques = civInfo.getMatchingUniques(UniqueType.SpyEffectiveness)
                enemyUniques = city.getMatchingUniques(UniqueType.EnemySpyEffectiveness, StateForConditionals(city), includeCivUniques = true)
            }
        }
        var totalEfficiency = 1.0
        totalEfficiency *= (100.0 + friendlyUniques.sumOf { it.params[0].toInt() }) / 100
        totalEfficiency *= (100.0 + enemyUniques.sumOf { it.params[0].toInt() }) / 100
        return totalEfficiency.coerceAtLeast(0.0)
    }

    private fun killSpy() {
        // We don't actually remove this spy object, we set them as dead and let them revive
        moveTo(null)
        setAction(SpyAction.Dead, 5)
        rank = 1
    }

    fun isAlive(): Boolean = action != SpyAction.Dead

    /** Shorthand for [Civilization.addNotification] specialized for espionage - action, category and icon are always the same */
    fun addNotification(text: String) =
        civInfo.addNotification(text, EspionageAction.withLocation(location), NotificationCategory.Espionage, NotificationIcon.Spy)

    /** Anti-save-scum: Deterministic random from city and turn
     *  @throws NullPointerException for spies in the hideout */
    private fun randomSeed() = (getCity().run { location.x * location.y } + 123f * civInfo.gameInfo.turns).toInt()
}
