package com.unciv.logic.civilization.managers

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Counter
import com.unciv.models.Religion
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.components.extensions.toPercent
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionModifiers
import java.lang.Integer.min
import kotlin.math.roundToInt
import kotlin.random.Random

class ReligionManager : IsPartOfGameInfoSerialization {

    @Transient
    lateinit var civInfo: Civilization

    var storedFaith = 0

    @Transient
    var religion: Religion? = null
    // You might ask why this is the transient variable, and not the one in GameInfo.
    // After all, filling a hashmap is much easier than later distributing its contents over multiple classes.
    // There is, however, a problem.
    // When founding a religion, the religion of your pantheon doesn't immediately disappear.
    // It just stops growing. Your new religion will then have to spread out from your holy city
    // and convert these cities. This means, that civilizations can have multiple active religions
    // in some cases. We only save one of them in this class to reduce the amount of logic necessary.
    // But the other one should still be _somewhere_. So our only option is to have the GameInfo
    // contain the master list, and the ReligionManagers retrieve it from there every time the game loads.

    var religionState = ReligionState.None
        private set

    // Counter containing the number of free beliefs types that this civ can add to its religion this turn
    // Uses String instead of BeliefType enum for serialization reasons
    var freeBeliefs: Counter<String> = Counter()

    // These cannot be transient, as saving and loading after using a great prophet but before
    // founding a religion would break :(
    private var foundingCityId: String? = null
    // Only used for keeping track of the city a prophet was used when founding a religion

    private var shouldChoosePantheonBelief: Boolean = false


    fun clone(): ReligionManager {
        val clone = ReligionManager()
        clone.foundingCityId = foundingCityId
        clone.shouldChoosePantheonBelief = shouldChoosePantheonBelief
        clone.storedFaith = storedFaith
        clone.religionState = religionState
        clone.freeBeliefs.putAll(freeBeliefs)
        return clone
    }

    fun setTransients(civInfo: Civilization) {
        this.civInfo = civInfo
        // Find our religion from the map of founded religions.
        // First check if there is any major religion
        religion = civInfo.gameInfo.religions.values.firstOrNull {
            it.foundingCivName == civInfo.civName && it.isMajorReligion()
        }
        // If there isn't, check for just pantheons.
        if (religion != null) return
        religion = civInfo.gameInfo.religions.values.firstOrNull {
            it.foundingCivName == civInfo.civName
        }
    }

    fun startTurn() {
        if (canGenerateProphet()) generateProphet()
    }

    fun endTurn(faithFromNewTurn: Int) {
        storedFaith += faithFromNewTurn
    }

    fun isMajorityReligionForCiv(religion: Religion): Boolean {
        return civInfo.cities.count { it.religion.getMajorityReligion() == religion } > civInfo.cities.size / 2
    }

    /**
     * This helper function makes it easy to interface the Counter<String> [freeBeliefs] with functions
     * that use Counter<BeliefType>
     */
    fun freeBeliefsAsEnums(): Counter<BeliefType> {
        val toReturn = Counter<BeliefType>()
        for (entry in freeBeliefs.entries) {
            toReturn.add(BeliefType.valueOf(entry.key), entry.value)
        }
        return toReturn
    }

    fun hasFreeBeliefs(): Boolean = freeBeliefs.sumValues() > 0

    fun usingFreeBeliefs(): Boolean
        = (religionState == ReligionState.None && storedFaith < faithForPantheon()) // first pantheon is free
            || religionState == ReligionState.Pantheon // any subsequent pantheons before founding a religion
            || (religionState == ReligionState.Religion || religionState == ReligionState.EnhancedReligion) // any belief adding outside of great prophet use

    fun faithForPantheon(additionalCivs: Int = 0): Int {
        val gameInfo = civInfo.gameInfo
        val numCivs = additionalCivs + gameInfo.civilizations.count { it.isMajorCiv() && it.religionManager.religion != null }
        val cost = gameInfo.ruleset.modOptions.constants.pantheonBase + numCivs * gameInfo.ruleset.modOptions.constants.pantheonGrowth
        return (cost * gameInfo.speed.faithCostModifier).roundToInt()
    }

    /** Used for founding the pantheon and for each time the player gets additional pantheon beliefs
     * before forming a religion */
    fun canFoundOrExpandPantheon(): Boolean {
        if (!civInfo.gameInfo.isReligionEnabled()) return false
        if (religionState > ReligionState.Pantheon) return false
        if (!civInfo.isMajorCiv()) return false
        if (numberOfBeliefsAvailable(BeliefType.Pantheon) == 0)
            return false // no more available pantheons
        if (civInfo.gameInfo.civilizations.any { it.religionManager.religionState == ReligionState.EnhancedReligion }
            && civInfo.gameInfo.civilizations.count { it.religionManager.religionState >= ReligionState.Pantheon } >= maxNumberOfReligions()
        ) {
            return false
        }
        return (religionState == ReligionState.None && storedFaith >= faithForPantheon()) // earned pantheon
                || freeBeliefs[BeliefType.Pantheon.name] > 0 // free pantheon belief
    }

    private fun foundPantheon(beliefName: String, useFreeBelief: Boolean) {
        if (!useFreeBelief) {
            // paid for the initial pantheon using faith
            storedFaith -= faithForPantheon()
        }
        religion = Religion(beliefName, civInfo.gameInfo, civInfo.civName)
        civInfo.gameInfo.religions[beliefName] = religion!!
        for (city in civInfo.cities)
            city.religion.addPressure(beliefName, 200 * city.population.population)
    }

    fun greatProphetsEarned(): Int = civInfo.civConstructions.boughtItemsWithIncreasingPrice[getGreatProphetEquivalent()?.name ?: ""]
        // Counter.get never returns null, but it needs the correct key type, which is non-nullable

    // https://www.reddit.com/r/civ/comments/2m82wu/can_anyone_detail_the_finer_points_of_great/
    // Game files (globaldefines.xml)
    fun faithForNextGreatProphet(): Int {
        val greatProphetsEarned = greatProphetsEarned()

        var faithCost =
            (200 + 100 * greatProphetsEarned * (greatProphetsEarned + 1) / 2f) *
            civInfo.gameInfo.speed.faithCostModifier

        for (unique in civInfo.getMatchingUniques(UniqueType.FaithCostOfGreatProphetChange))
            faithCost *= unique.params[0].toPercent()

        return faithCost.toInt()
    }

    fun canGenerateProphet(ignoreFaithAmount: Boolean = false): Boolean {
        if (!civInfo.gameInfo.isReligionEnabled()) return false // No religion, no prophets
        if (religion == null || religionState == ReligionState.None) return false // First get a pantheon, then we'll talk about a real religion
        if (getGreatProphetEquivalent() == null) return false
        if (!ignoreFaithAmount && storedFaith < faithForNextGreatProphet()) return false
        if (!civInfo.isMajorCiv()) return false
        if (civInfo.hasUnique(UniqueType.MayNotGenerateGreatProphet)) return false
        if (religionState == ReligionState.Pantheon && remainingFoundableReligions() == 0) return false // too many have been founded
        return true
    }

    fun getGreatProphetEquivalent(): BaseUnit? {
        val baseUnit = civInfo.gameInfo.ruleset.units.values.firstOrNull { it.hasUnique(UniqueType.MayFoundReligion) }
        return if (baseUnit == null) null else civInfo.getEquivalentUnit(baseUnit)
    }

    private fun generateProphet() {
        val prophetUnit = getGreatProphetEquivalent() ?: return // No prophet units in this mod

        val prophetSpawnChange = (5f + storedFaith - faithForNextGreatProphet()) / 100f

        if (Random(civInfo.gameInfo.turns).nextFloat() < prophetSpawnChange) {
            val birthCity =
                if (religionState <= ReligionState.Pantheon) civInfo.getCapital()
                else civInfo.religionManager.getHolyCity()
            val prophet = civInfo.units.addUnit(prophetUnit, birthCity) ?: return
            prophet.religion = religion!!.name
            storedFaith -= faithForNextGreatProphet()
            civInfo.civConstructions.boughtItemsWithIncreasingPrice.add(prophetUnit.name, 1)
        }
    }

    private fun maxNumberOfReligions(): Int {
        val gameInfo = civInfo.gameInfo
        val ruleset = gameInfo.ruleset
        val multiplier = ruleset.modOptions.constants.religionLimitMultiplier
        val base = ruleset.modOptions.constants.religionLimitBase
        val civCount = gameInfo.civilizations.count { it.isMajorCiv() }
        return min(ruleset.religions.size, base + (civCount * multiplier).toInt())
    }

    /** Calculates the number of religions that are already founded */
    private fun foundedReligionsCount() = civInfo.gameInfo.civilizations.count {
        it.religionManager.religion != null && it.religionManager.religionState >= ReligionState.Religion
    }

    /** Calculates the amount of religions that can still be founded */
    fun remainingFoundableReligions(): Int {
        // count the number of foundable religions left given defined ruleset religions and number of civs in game
        val maxNumberOfAdditionalReligions = maxNumberOfReligions() - foundedReligionsCount()

        val availableBeliefsToFound = min(
            numberOfBeliefsAvailable(BeliefType.Follower),
            numberOfBeliefsAvailable(BeliefType.Founder)
        )

        return min(maxNumberOfAdditionalReligions, availableBeliefsToFound)
    }

    /** Get info breaking down the reasons behind the result of [remainingFoundableReligions] */
    fun remainingFoundableReligionsBreakdown() = sequence {
        val gameInfo = civInfo.gameInfo
        val ruleset = gameInfo.ruleset
        yield("Available religion symbols" to ruleset.religions.size)

        val multiplier = ruleset.modOptions.constants.religionLimitMultiplier
        val base = ruleset.modOptions.constants.religionLimitBase
        val civCount = gameInfo.civilizations.count { it.isMajorCiv() }
        val hideCivCount = civInfo.hideCivCount()
        if (hideCivCount) {
            val knownCivs = 1 + civInfo.getKnownCivs().count { it.isMajorCiv() }
            val estimatedCivCount = (
                    gameInfo.gameParameters.minNumberOfPlayers.coerceAtLeast(knownCivs) +
                            gameInfo.gameParameters.maxNumberOfPlayers - 1
                    ) / 2 + 1
            val civsAndBase = base + (estimatedCivCount * multiplier).toInt()
            yield("Estimated number of civilizations * [$multiplier] + [$base]" to civsAndBase)
        } else {
            val civsAndBase = base + (civCount * multiplier).toInt()
            yield("Number of civilizations * [$multiplier] + [$base]" to civsAndBase)
        }

        yield("Religions already founded" to foundedReligionsCount())
        yield("Available founder beliefs" to numberOfBeliefsAvailable(BeliefType.Founder))
        yield("Available follower beliefs" to numberOfBeliefsAvailable(BeliefType.Follower))
    }

    fun numberOfBeliefsAvailable(type: BeliefType): Int {
        val gameInfo = civInfo.gameInfo
        val numberOfBeliefs = if (type == BeliefType.Any) gameInfo.ruleset.beliefs.values.count()
        else gameInfo.ruleset.beliefs.values.count { it.type == type }
        return numberOfBeliefs - gameInfo.religions.flatMap { it.value.getBeliefs(type) }.distinct().count()
        // We need to do the distinct above, as pantheons and religions founded out of those pantheons might share beliefs
    }

    fun getReligionWithBelief(belief: Belief): Religion? {
        return civInfo.gameInfo.religions.values.firstOrNull { it.hasBelief(belief.name) }
    }


    fun mayFoundReligionAtAll(): Boolean {
        if (!civInfo.gameInfo.isReligionEnabled()) return false // No religion
        if (religionState >= ReligionState.Religion) return false // Already created a major religion

        if (!civInfo.isMajorCiv()) return false // Only major civs may use religion

        if (remainingFoundableReligions() == 0)
            return false // Too bad, too many religions have already been founded

        return true
    }

    fun mayFoundReligionHere(tile: Tile): Boolean {
        if (!mayFoundReligionAtAll()) return false
        if (!tile.isCityCenter()) return false
        if (tile.getCity()!!.isHolyCity()) return false
        // No double holy cities. Not sure if these were allowed in the base game
        return true
    }

    fun foundReligion(prophet: MapUnit) {
        if (!mayFoundReligionHere(prophet.getTile())) return // How did you do this?
        if (religionState == ReligionState.None) shouldChoosePantheonBelief = true
        religionState = ReligionState.FoundingReligion
        civInfo.religionManager.foundingCityId = prophet.getTile().getCity()!!.id
    }

    fun mayEnhanceReligionAtAll(): Boolean {
        if (!civInfo.gameInfo.isReligionEnabled()) return false
        if (religion == null) return false // First found a pantheon
        if (religionState != ReligionState.Religion) return false // First found an actual religion
        if (!civInfo.isMajorCiv()) return false // Only major civs

        if (numberOfBeliefsAvailable(BeliefType.Follower) == 0)
            return false // Mod maker did not provide enough follower beliefs

        if (numberOfBeliefsAvailable(BeliefType.Enhancer) == 0)
            return false // Mod maker did not provide enough enhancer beliefs

        return true
    }

    fun mayEnhanceReligionHere(tile: Tile): Boolean {
        if (!mayEnhanceReligionAtAll()) return false
        if (!tile.isCityCenter()) return false
        return true
    }

    fun useProphetForEnhancingReligion(prophet: MapUnit) {
        if (!mayEnhanceReligionHere(prophet.getTile())) return // How did you do this?
        religionState = ReligionState.EnhancingReligion
    }

    /**
     * Unifies the selection of what beliefs are available for when a great prophet is expended. Also
     * accounts for the number of remaining beliefs of each type so that the player is not given a
     * larger number of beliefs to select than there are available for selection ([mayFoundReligionAtAll]
     * and [mayEnhanceReligionAtAll] only check if there is 1 founder/enhancer and 1 follower belief
     * available but the civ may be given more beliefs through uniques or a missing pantheon belief)
     */
    private fun getBeliefsToChooseAtProphetUse(enhancingReligion: Boolean): Counter<BeliefType> {
        val action = if (enhancingReligion) "enhancing" else "founding"
        val beliefsToChoose: Counter<BeliefType> = Counter()

        // Counter of the number of available beliefs of each type
        val availableBeliefs = Counter<BeliefType>()
        for (type in BeliefType.entries) {
            if (type == BeliefType.None) continue
            availableBeliefs[type] = numberOfBeliefsAvailable(type)
        }

        // function to help with bookkeeping
        fun chooseBeliefToAdd(type: BeliefType, number: Int) {
            val numberToAdd = min(number, availableBeliefs[type])
            beliefsToChoose.add(type, numberToAdd)
            availableBeliefs[type] = availableBeliefs[type] - numberToAdd
            if (type != BeliefType.Any) {
                // deduct from BeliefType.Any as well
                availableBeliefs[BeliefType.Any] = availableBeliefs[BeliefType.Any] - numberToAdd
            }
        }

        if (enhancingReligion) {
            chooseBeliefToAdd(BeliefType.Enhancer, 1)
        }
        else {
            chooseBeliefToAdd(BeliefType.Founder, 1)
            if (shouldChoosePantheonBelief)
                chooseBeliefToAdd(BeliefType.Pantheon, 1)
        }
        chooseBeliefToAdd(BeliefType.Follower, 1)

        for (unique in civInfo.getMatchingUniques(UniqueType.FreeExtraBeliefs)) {
            if (unique.params[2] != action) continue
            val type = BeliefType.valueOf(unique.params[1])
            chooseBeliefToAdd(type, unique.params[0].toInt())
        }
        for (unique in civInfo.getMatchingUniques(UniqueType.FreeExtraAnyBeliefs)) {
            if (unique.params[1] != action) continue
            chooseBeliefToAdd(BeliefType.Any, unique.params[0].toInt())
        }

        for (type in freeBeliefsAsEnums())
            chooseBeliefToAdd(type.key, type.value)

        return beliefsToChoose
    }

    fun getBeliefsToChooseAtFounding(): Counter<BeliefType> = getBeliefsToChooseAtProphetUse(false)
    fun getBeliefsToChooseAtEnhancing(): Counter<BeliefType> = getBeliefsToChooseAtProphetUse(true)

    fun chooseBeliefs(beliefs: List<Belief>, useFreeBeliefs: Boolean = false) {
        // Remove the free beliefs in case we had them
        // Must be done first in case when gain more later
        freeBeliefs.clear()

        if (religionState == ReligionState.None)
            foundPantheon(beliefs[0].name, useFreeBeliefs)  // makes religion non-null
        // add beliefs (religion exists at this point)
        religion!!.addBeliefs(beliefs)

        when (religionState) {
            ReligionState.None -> {
                religionState = ReligionState.Pantheon
                for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUponFoundingPantheon))
                    UniqueTriggerActivation.triggerUnique(unique, civInfo)
            }
            ReligionState.FoundingReligion -> {
                religionState = ReligionState.Religion
                for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUponFoundingReligion))
                    UniqueTriggerActivation.triggerUnique(unique, civInfo)
            }
            ReligionState.EnhancingReligion -> {
                religionState = ReligionState.EnhancedReligion
                for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUponEnhancingReligion))
                    UniqueTriggerActivation.triggerUnique(unique, civInfo)
            }
            else -> {}
        }

        for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUponAdoptingPolicyOrBelief))
            for (belief in beliefs)
                if (unique.getModifiers(UniqueType.TriggerUponAdoptingPolicyOrBelief).any { it.params[0] == belief.name})
                    UniqueTriggerActivation.triggerUnique(unique, civInfo,
                        triggerNotificationText = "due to adopting [${belief.name}]")

        for (belief in beliefs)
            for (unique in belief.uniqueObjects.filter { !it.hasTriggerConditional() && it.conditionalsApply(civInfo.state) })
                UniqueTriggerActivation.triggerUnique(unique, civInfo)

        civInfo.updateStatsForNextTurn()  // a belief can have an immediate effect on stats
    }


    internal fun foundReligion(displayName: String, name: String) {
        val newReligion = Religion(name, civInfo.gameInfo, civInfo.civName)
        newReligion.displayName = displayName
        if (religion != null) {
            newReligion.addBeliefs(religion!!.getAllBeliefsOrdered().asIterable())
        }

        religion = newReligion
        civInfo.gameInfo.religions[name] = newReligion

        val holyCity = civInfo.cities.first { it.id == foundingCityId }
        holyCity.religion.religionThisIsTheHolyCityOf = newReligion.name
        holyCity.religion.addPressure(name, holyCity.population.population * 500)

        foundingCityId = null
        shouldChoosePantheonBelief = false

        for (unit in civInfo.units.getCivUnits())
            if (unit.hasUnique(UniqueType.ReligiousUnit) && unit.hasUnique(UniqueType.TakeReligionOverBirthCity))
                unit.religion = newReligion.name
    }

    fun maySpreadReligionAtAll(missionary: MapUnit): Boolean {
        if (!civInfo.isMajorCiv()) return false // Only major civs
        if (!civInfo.gameInfo.isReligionEnabled()) return false // No religion, no spreading

        val religion = missionary.civ.gameInfo.religions[missionary.religion] ?: return false
        if (religion.isPantheon()) return false
        if (UnitActionModifiers.getUsableUnitActionUniques(missionary, UniqueType.CanSpreadReligion).none()) return false
        return true
    }

    fun maySpreadReligionNow(missionary: MapUnit): Boolean {
        if (!maySpreadReligionAtAll(missionary)) return false
        if (missionary.getTile().getOwner() == null) return false
        if (missionary.currentTile.owningCity?.religion?.getMajorityReligion()?.name == missionary.religion)
            return false
        if (missionary.getTile().getCity()!!.religion.isProtectedByInquisitor(missionary.religion)) return false
        return true
    }

    fun numberOfCitiesFollowingThisReligion(): Int {
        if (religion == null) return 0
        return civInfo.gameInfo.getCities()
            .count { it.religion.getMajorityReligion() == religion }
    }

    fun numberOfFollowersFollowingThisReligion(cityFilter: String): Int {
        if (religion == null) return 0
        return civInfo.gameInfo.getCities()
            .filter { it.matchesFilter(cityFilter, civInfo) }
            .sumOf { it.religion.getFollowersOf(religion!!.name) }
    }

    fun getHolyCity(): City? {
        if (religion == null) return null
        return civInfo.gameInfo.getCities().firstOrNull { it.isHolyCityOf(religion!!.name) }
    }

    fun getMajorityReligion(): Religion? {
        // let's count for each religion (among those actually presents in civ's cities)
        val religionCounter = Counter<Religion>()
        for (city in civInfo.cities) {
            // if city's majority Religion is null, let's just continue to next loop iteration
            val cityMajorityReligion = city.religion.getMajorityReligion() ?: continue
            // if not yet continued to next iteration from previous line, let's add the Religion to religionCounter
            religionCounter.add(cityMajorityReligion, 1)
        }
        // let's get the max-counted Religion if there is one, null otherwise; if null, return null
        val maxReligionCounterEntry = religionCounter.maxByOrNull { it.value } ?: return null
        // if not returned null from prev. line, check if the maxReligion is in most of the cities
        return if (maxReligionCounterEntry.value > civInfo.cities.size / 2)
        // if maxReligionCounterEntry > half-cities-count we return the Religion of maxReligionCounterEntry
            maxReligionCounterEntry.key
        else
        // if maxReligionCounterEntry <= half-cities-count we just return null
            null
    }
}

enum class ReligionState : IsPartOfGameInfoSerialization {
    None,
    Pantheon,
    FoundingReligion, // Great prophet used, but religion has not yet been founded
    Religion,
    EnhancingReligion, // Great prophet used, but religion has not yet been enhanced
    EnhancedReligion;

    override fun toString(): String {
        // Yes, this is the ugliest way to convert camel case to human readable format I've seen as well, but it works.
        return super.toString()
            .map { letter -> (if (letter.isUpperCase()) " " else "") + letter.lowercase() }
            .joinToString("")
            .removePrefix(" ")
            .replaceFirstChar { it.uppercase() }
    }
}
