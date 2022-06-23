package com.unciv.logic.civilization

import com.unciv.logic.map.MapUnit
import com.unciv.models.Counter
import com.unciv.models.Religion
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.utils.extensions.toPercent
import java.lang.Integer.min
import kotlin.random.Random

class ReligionManager {

    @Transient
    lateinit var civInfo: CivilizationInfo

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
        return clone
    }

    fun setTransients() {
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

    fun faithForPantheon(additionalCivs: Int = 0) =
        10 + (civInfo.gameInfo.civilizations.count { it.isMajorCiv() && it.religionManager.religion != null } + additionalCivs) * 5

    fun canFoundPantheon(): Boolean {
        if (!civInfo.gameInfo.isReligionEnabled()) return false
        if (religionState != ReligionState.None) return false
        if (!civInfo.isMajorCiv()) return false
        if (civInfo.gameInfo.ruleSet.beliefs.values.none { isPickablePantheonBelief(it) })
            return false
        if (civInfo.gameInfo.civilizations.any { it.religionManager.religionState == ReligionState.EnhancedReligion })
            return false
        return storedFaith >= faithForPantheon()
    }

    fun isPickablePantheonBelief(belief: Belief): Boolean {
        if (belief.type != BeliefType.Pantheon) return false
        return (civInfo.gameInfo.civilizations.none { it.religionManager.religion?.followerBeliefs?.contains(belief.name) == true })
    }

    fun choosePantheonBelief(belief: Belief) {
        storedFaith -= faithForPantheon()
        religion = Religion(belief.name, civInfo.gameInfo, civInfo.civName)
        religion!!.followerBeliefs.add(belief.name)
        civInfo.gameInfo.religions[belief.name] = religion!!
        for (city in civInfo.cities)
            city.religion.addPressure(belief.name, 200 * city.population.population)
        religionState = ReligionState.Pantheon
        civInfo.updateStatsForNextTurn()  // a belief can have an immediate effect on stats
    }

    // https://www.reddit.com/r/civ/comments/2m82wu/can_anyone_detail_the_finer_points_of_great/
    // Game files (globaldefines.xml)
    fun faithForNextGreatProphet(): Int {
        val greatProphetsEarned = civInfo.civConstructions.boughtItemsWithIncreasingPrice[getGreatProphetEquivalent()!!] ?: 0

        var faithCost =
            (200 + 100 * greatProphetsEarned * (greatProphetsEarned + 1) / 2f) *
            civInfo.gameInfo.speed.faithCostModifier

        for (unique in civInfo.getMatchingUniques(UniqueType.FaithCostOfGreatProphetChange))
            faithCost *= unique.params[0].toPercent()

        return faithCost.toInt()
    }

    fun canGenerateProphet(): Boolean {
        if (!civInfo.gameInfo.isReligionEnabled()) return false // No religion, no prophets
        if (religion == null || religionState == ReligionState.None) return false // First get a pantheon, then we'll talk about a real religion
        if (getGreatProphetEquivalent() == null) return false
        if (storedFaith < faithForNextGreatProphet()) return false
        if (!civInfo.isMajorCiv()) return false
        if (civInfo.hasUnique(UniqueType.MayNotGenerateGreatProphet)) return false
        return true
    }

    fun getGreatProphetEquivalent(): String? {
        return civInfo.gameInfo.ruleSet.units.values.firstOrNull { it.hasUnique(UniqueType.MayFoundReligion) }?.name
    }

    private fun generateProphet() {
        val prophetUnitName = getGreatProphetEquivalent() ?: return // No prophet units in this mod

        val prophetSpawnChange = (5f + storedFaith - faithForNextGreatProphet()) / 100f

        if (Random(civInfo.gameInfo.turns).nextFloat() < prophetSpawnChange) {
            val birthCity =
                if (religionState <= ReligionState.Pantheon) civInfo.getCapital()
                else civInfo.cities.firstOrNull { it.religion.religionThisIsTheHolyCityOf == religion!!.name }
            val prophet = civInfo.addUnit(prophetUnitName, birthCity) ?: return
            prophet.religion = religion!!.name
            storedFaith -= faithForNextGreatProphet()
            civInfo.civConstructions.boughtItemsWithIncreasingPrice.add(prophetUnitName, 1)
        }
    }

    fun amountOfFoundableReligions() = civInfo.gameInfo.civilizations.count { it.isMajorCiv() } / 2 + 1

    fun remainingFoundableReligions(): Int {
        val foundedReligionsCount = civInfo.gameInfo.civilizations.count {
            it.religionManager.religion != null && it.religionManager.religionState >= ReligionState.Religion
        }

        // count the number of foundable religions left given defined ruleset religions and number of civs in game
        val maxNumberOfAdditionalReligions = min(civInfo.gameInfo.ruleSet.religions.size,
            civInfo.gameInfo.civilizations.count { it.isMajorCiv() } / 2 + 1) - foundedReligionsCount

        val availableBeliefsToFound = min(
            civInfo.gameInfo.ruleSet.beliefs.values.count {
                it.type == BeliefType.Follower
                && civInfo.gameInfo.religions.values.none { religion -> it in religion.getBeliefs(BeliefType.Follower) }
            },
            civInfo.gameInfo.ruleSet.beliefs.values.count {
                it.type == BeliefType.Founder
                && civInfo.gameInfo.religions.values.none { religion -> it in religion.getBeliefs(BeliefType.Founder) }
            }
        )

        return min(maxNumberOfAdditionalReligions, availableBeliefsToFound)
    }

    fun mayFoundReligionAtAll(prophet: MapUnit): Boolean {
        if (!civInfo.gameInfo.isReligionEnabled()) return false // No religion

        if (religionState >= ReligionState.Religion) return false // Already created a major religion

        // Already used its power for other things
        if (prophet.abilityUsesLeft.any { it.value != prophet.maxAbilityUses[it.key] }) return false

        if (!civInfo.isMajorCiv()) return false // Only major civs may use religion

        if (remainingFoundableReligions() == 0)
            return false // Too bad, too many religions have already been founded

        return true
    }

    fun mayFoundReligionNow(prophet: MapUnit): Boolean {
        if (!mayFoundReligionAtAll(prophet)) return false
        if (!prophet.getTile().isCityCenter()) return false
        if (prophet.getTile().getCity()!!.isHolyCity()) return false
        // No double holy cities. Not sure if these were allowed in the base game
        return true
    }

    fun useProphetForFoundingReligion(prophet: MapUnit) {
        if (!mayFoundReligionNow(prophet)) return // How did you do this?
        if (religionState == ReligionState.None) shouldChoosePantheonBelief = true
        religionState = ReligionState.FoundingReligion
        civInfo.religionManager.foundingCityId = prophet.getTile().getCity()!!.id
    }

    fun getBeliefsToChooseAtFounding(): Counter<BeliefType> {
        val beliefsToChoose: Counter<BeliefType> = Counter()
        beliefsToChoose.add(BeliefType.Founder, 1)
        beliefsToChoose.add(BeliefType.Follower, 1)
        if (shouldChoosePantheonBelief)
            beliefsToChoose.add(BeliefType.Pantheon, 1)

        for (unique in civInfo.getMatchingUniques(UniqueType.FreeExtraBeliefs)) {
            if (unique.params[2] != "founding") continue
            beliefsToChoose.add(BeliefType.valueOf(unique.params[1]), unique.params[0].toInt())
        }
        for (unique in civInfo.getMatchingUniques(UniqueType.FreeExtraAnyBeliefs)) {
            if (unique.params[1] != "founding") continue
            beliefsToChoose.add(BeliefType.Any, unique.params[0].toInt())
        }

        return beliefsToChoose
    }

    fun chooseBeliefs(iconName: String?, religionName: String?, beliefs: List<Belief>) {
        when(religionState) {
            ReligionState.FoundingReligion ->
                foundReligion(iconName!!, religionName!!, beliefs)
            ReligionState.EnhancingReligion ->
                enhanceReligion(beliefs)
            else -> return
        }
        civInfo.updateStatsForNextTurn()  // a belief can have an immediate effect on stats
    }


    fun foundReligion(displayName: String, name: String, beliefs: List<Belief>) {
        val newReligion = Religion(name, civInfo.gameInfo, civInfo.civName)
        newReligion.displayName = displayName
        if (religion != null) {
            newReligion.followerBeliefs.addAll(religion!!.followerBeliefs)
            newReligion.founderBeliefs.addAll(religion!!.founderBeliefs)
        }

        newReligion.followerBeliefs.addAll(
            beliefs
                .filter { it.type == BeliefType.Pantheon || it.type == BeliefType.Follower }
                .map { it.name }
        )
        newReligion.founderBeliefs.addAll(
            beliefs
                .filter { it.type == BeliefType.Founder || it.type == BeliefType.Enhancer }
                .map { it.name }
        )

        religion = newReligion
        civInfo.gameInfo.religions[name] = newReligion

        religionState = ReligionState.Religion

        val holyCity = civInfo.cities.first { it.id == foundingCityId }
        holyCity.religion.religionThisIsTheHolyCityOf = newReligion.name
        holyCity.religion.addPressure(name, holyCity.population.population * 500)

        foundingCityId = null
        shouldChoosePantheonBelief = false

        for (unit in civInfo.getCivUnits())
            if (unit.hasUnique(UniqueType.ReligiousUnit) && unit.hasUnique(UniqueType.TakeReligionOverBirthCity))
                unit.religion = newReligion.name
    }

    fun mayEnhanceReligionAtAll(prophet: MapUnit): Boolean {
        if (!civInfo.gameInfo.isReligionEnabled()) return false // No religion, no enhancing
        if (religion == null) return false // First found a pantheon
        if (religionState != ReligionState.Religion) return false // First found an actual religion
        // Already used its power for other things
        if (prophet.abilityUsesLeft.any { it.value != prophet.maxAbilityUses[it.key] }) return false
        if (!civInfo.isMajorCiv()) return false // Only major civs

        if (civInfo.gameInfo.ruleSet.beliefs.values.none {
            it.type == BeliefType.Follower
            && civInfo.gameInfo.religions.values.none { religion -> religion.getBeliefs(BeliefType.Follower).contains(it) }
        }) return false // Mod maker did not provide enough follower beliefs

        if (civInfo.gameInfo.ruleSet.beliefs.values.none {
            it.type == BeliefType.Enhancer
            && civInfo.gameInfo.religions.values.none { religion -> religion.getBeliefs(BeliefType.Enhancer).contains(it) }
        }) return false // Mod maker did not provide enough enhancer beliefs

        return true
    }

    fun mayEnhanceReligionNow(prophet: MapUnit): Boolean {
        if (!mayEnhanceReligionAtAll(prophet)) return false
        if (!prophet.getTile().isCityCenter()) return false
        return true
    }

    fun useProphetForEnhancingReligion(prophet: MapUnit) {
        if (!mayEnhanceReligionNow(prophet)) return // How did you do this?
        religionState = ReligionState.EnhancingReligion
    }

    fun getBeliefsToChooseAtEnhancing(): Counter<BeliefType> {
        val beliefsToChoose: Counter<BeliefType> = Counter()
        beliefsToChoose.add(BeliefType.Follower, 1)
        beliefsToChoose.add(BeliefType.Enhancer, 1)

        for (unique in civInfo.getMatchingUniques(UniqueType.FreeExtraBeliefs)) {
            if (unique.params[2] != "enhancing") continue
            beliefsToChoose.add(BeliefType.valueOf(unique.params[1]), unique.params[0].toInt())
        }
        for (unique in civInfo.getMatchingUniques(UniqueType.FreeExtraAnyBeliefs)) {
            if (unique.params[1] != "enhancing") continue
            beliefsToChoose.add(BeliefType.Any, unique.params[0].toInt())
        }

        return beliefsToChoose
    }

    fun enhanceReligion(beliefs: List<Belief>) {
        religion!!.followerBeliefs.addAll(beliefs.filter { it.type == BeliefType.Follower || it.type == BeliefType.Pantheon }.map { it.name })
        religion!!.founderBeliefs.addAll(beliefs.filter { it.type == BeliefType.Enhancer || it.type == BeliefType.Founder }.map { it.name })
        religionState = ReligionState.EnhancedReligion
    }

    fun maySpreadReligionAtAll(missionary: MapUnit): Boolean {
        if (!civInfo.gameInfo.isReligionEnabled()) return false // No religion, no spreading
        if (religion == null) return false // need a religion
        if (religionState < ReligionState.Religion) return false // First found an actual religion
        if (!civInfo.isMajorCiv()) return false // Only major civs

        return true
    }

    fun maySpreadReligionNow(missionary: MapUnit): Boolean {
        if (!maySpreadReligionAtAll(missionary)) return false
        if (missionary.getTile().getOwner() == null) return false
        if (missionary.currentTile.owningCity?.religion?.getMajorityReligion()?.name == missionary.religion)
            return false
        if (missionary.getTile().getCity()!!.religion.isProtectedByInquisitor()) return false
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
            .sumOf { it.religion.getFollowersOf(religion!!.name)!! }
    }
}

enum class ReligionState {
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
