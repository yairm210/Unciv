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
    RiggingElections("Rigging Elections", false, true) {
        override fun isDoingWork(spy: Spy) = !spy.civInfo.isAtWarWith(spy.getCity().civ)
    },
    Coup("Coup", true, true, true),
    CounterIntelligence("Counter-intelligence", false, true) {
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

    fun setAction(newAction: SpyAction, turns: Int = 0) {
        assert(!newAction.hasTurns || turns > 0) // hasTurns==false but turns > 0 is allowed (CounterIntelligence), hasTurns==true and turns==0 is not.
        action = newAction
        turnsRemainingForAction = turns
    }

    fun endTurn() {
        if (action.hasTurns && --turnsRemainingForAction > 0) return
        when (action) {
            SpyAction.None -> return
            SpyAction.Moving -> {
                if (getCity().civ == civInfo)
                    // Your own cities are certainly familiar surroundings, so skip establishing a network
                    setAction(SpyAction.CounterIntelligence, 10)
                else
                    // Should depend on cultural familiarity level if that is ever implemented inter-civ
                    setAction(SpyAction.EstablishNetwork, 3)
            }
            SpyAction.EstablishNetwork -> {
                val city = getCity() // This should never throw an exception, as going to the hideout sets your action to None.
                if (city.civ.isCityState())
                    setAction(SpyAction.RiggingElections, getCity().civ.cityStateTurnsUntilElection - 1)
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
                // No action done here
                // Handled in CityStateFunctions.nextTurnElections()
                turnsRemainingForAction = getCity().civ.cityStateTurnsUntilElection - 1
            }
            SpyAction.Coup -> {
                initiateCoup()
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

        // Lower is better
        var spyResult = Random(randomSeed).nextInt(300)
        // Add our spies experience
        spyResult -= getSkillModifier()
        // Subtract the experience of the counter intelligence spies
        val defendingSpy = city.civ.espionageManager.getSpyAssignedToCity(city)
        spyResult += defendingSpy?.getSkillModifier() ?: 0

        val detectionString = when {
            spyResult >= 200 -> { // The spy was killed in the attempt (should be able to happen even if there's nothing to steal?)
                if (defendingSpy == null) "A spy from [${civInfo.civName}] was found and killed trying to steal Technology in [$city]!"
                else "A spy from [${civInfo.civName}] was found and killed by [${defendingSpy.name}] trying to steal Technology in [$city]!"
            }
            stolenTech == null -> null // Nothing to steal
            spyResult < 0 -> null // Not detected
            spyResult < 100 -> "An unidentified spy stole the Technology [$stolenTech] from [$city]!"
            else -> "A spy from [${civInfo.civName}] stole the Technology [$stolenTech] from [$city]!"
        }
        if (detectionString != null)
            // Not using Spy.addNotification, shouldn't open the espionage screen
            otherCiv.addNotification(detectionString, city.location, NotificationCategory.Espionage, NotificationIcon.Spy)

        if (spyResult < 200 && stolenTech != null) {
            civInfo.tech.addTechnology(stolenTech)
            addNotification("Your spy [$name] stole the Technology [$stolenTech] from [$city]!")
            levelUpSpy()
        }

        if (spyResult >= 200) {
            addNotification("Your spy [$name] was killed trying to steal Technology in [$city]!")
            defendingSpy?.levelUpSpy()
            killSpy()
        } else startStealingTech()  // reset progress

        if (spyResult >= 100) {
            otherCiv.getDiplomacyManager(civInfo).addModifier(DiplomaticModifiers.SpiedOnUs, -15f)
        }
    }

    fun canDoCoup(): Boolean = getCityOrNull() != null && getCity().civ.isCityState() && isSetUp() && getCity().civ.getAllyCiv() != civInfo.civName

    /**
     * Initiates a coup if this spies civ is not the ally of the city-state.
     * The coup will only happen at the end of the Civ's turn for save scum reasons, so a play may not reload in multiplayer.
     * If successfull the coup will 
     */
    private fun initiateCoup() {
        if (!canDoCoup()) {
            // Maybe we are the new ally of the city-state
            // However we know that we are still in the city and it hasn't been conquered
            setAction(SpyAction.RiggingElections, 10)
            return
        }
        val successChance = getCoupChanceOfSuccess(true)
        val randomValue = Random(randomSeed()).nextFloat()
        if (randomValue <= successChance) {
            // Success
            val cityState = getCity().civ
            val pastAlly = cityState.getAllyCiv()?.let { civInfo.gameInfo.getCivilization(it) }
            val previousInfluence = if (pastAlly != null) cityState.getDiplomacyManager(pastAlly).getInfluence() else 80f
            cityState.getDiplomacyManager(civInfo).setInfluence(previousInfluence)

            civInfo.addNotification("Your spy [$name] successfully staged a coup in [${cityState.civName}]!", getCity().location,
                    NotificationCategory.Espionage, NotificationIcon.Spy, cityState.civName)
            if (pastAlly != null) {
                cityState.getDiplomacyManager(pastAlly).reduceInfluence(20f)
                pastAlly.addNotification("A spy from [${civInfo.civName}] successfully staged a coup in our former ally [${cityState.civName}]!", getCity().location,
                        NotificationCategory.Espionage, civInfo.civName,  NotificationIcon.Spy, cityState.civName)
                pastAlly.getDiplomacyManager(civInfo).addModifier(DiplomaticModifiers.SpiedOnUs, -15f)
            }
            for (civ in cityState.getKnownCivsWithSpectators()) {
                if (civ == pastAlly || civ == civInfo) continue
                civ.addNotification("A spy from [${civInfo.civName}] successfully staged a coup in [${cityState.civName}]!", getCity().location,
                        NotificationCategory.Espionage, civInfo.civName,  NotificationIcon.Spy, cityState.civName)
                if (civ.isSpectator()) continue
                cityState.getDiplomacyManager(civ).reduceInfluence(10f) // Guess
            }
            setAction(SpyAction.RiggingElections, 10)
            cityState.cityStateFunctions.updateAllyCivForCityState()

        } else {
            // Failure
            val cityState = getCity().civ
            val allyCiv = cityState.getAllyCiv()?.let { civInfo.gameInfo.getCivilization(it) }
            val spy = allyCiv?.espionageManager?.getSpyAssignedToCity(getCity())
            cityState.getDiplomacyManager(civInfo).addInfluence(-20f)
            allyCiv?.addNotification("A spy from [${civInfo.civName}] failed to stag a coup in our ally [${cityState.civName}] and was killed!", getCity().location,
                    NotificationCategory.Espionage, civInfo.civName,  NotificationIcon.Spy, cityState.civName)
            allyCiv?.getDiplomacyManager(civInfo)?.addModifier(DiplomaticModifiers.SpiedOnUs, -10f)

            civInfo.addNotification("Our spy [$name] failed to stag a coup in [${cityState.civName}] and was killed!", getCity().location,
                    NotificationCategory.Espionage, civInfo.civName,  NotificationIcon.Spy, cityState.civName)

            killSpy()
            spy?.levelUpSpy() // Technically not in Civ V, but it's like the same thing as with counter-intelligence
        }
    }

    /**
     * Calculates the success chance of a coup in this city state.
     */
    fun getCoupChanceOfSuccess(includeUnkownFactors: Boolean): Float {
        val cityState = getCity().civ
        var successPercentage = 50f

        // Influence difference should always be a positive value
        var influenceDifference: Float = if (cityState.getAllyCiv() != null)
            cityState.getDiplomacyManager(cityState.getAllyCiv()!!).getInfluence()
        else 60f
        influenceDifference -= cityState.getDiplomacyManager(civInfo).getInfluence()
        successPercentage -= influenceDifference / 2f

        // If we are viewing the success chance we don't want to reveal that there is a defending spy
        val defendingSpy = if (includeUnkownFactors) 
            cityState.getAllyCiv()?.let { civInfo.gameInfo.getCivilization(it) }?.espionageManager?.getSpyAssignedToCity(getCity()) 
        else null

        val spyRanks = getSkillModifier() - (defendingSpy?.getSkillModifier() ?: 0)
        successPercentage += spyRanks / 2f // Each rank counts for 15%

        successPercentage = successPercentage.coerceIn(0f, 85f)
        return successPercentage / 100f
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
        if (rank >= civInfo.gameInfo.ruleset.modOptions.constants.maxSpyLevel) return
        addNotification("Your spy [$name] has leveled up!")
        rank++
    }

    /** Zero-based modifier expressing shift of probabilities from Spy Rank
     *
     *  100 units change one step in results, there are 4 such steps, and the default random spans 300 units and excludes the best result (undetected success).
     *  Thus the return value translates into (return / 3) percent chance to get the very best result, reducing the chance to get the worst result (kill) by the same amount.
     *  The same modifier from defending counter-intelligence spies goes linearly in the opposite direction.
     *  With the range of this function being hardcoded to 30..90 (and 0 for no defensive spy present), ranks cannot guarantee either best or worst outcome.
     *  Or - chance range of best result is 0% (rank 1 vs rank 3 defender) to 30% (rank 3 vs no defender), range of worst is 53% to 3%, respectively.
     */
    // Todo Moddable as some global and/or in-game-gainable Uniques?
    fun getSkillModifier() = rank * 30

    /**
     * Gets a friendly and enemy efficiency uniques for the spy at the location
     * @return a value centered around 1.0 for the work efficiency of the spy, won't be negative
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
