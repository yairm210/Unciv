package com.unciv.logic.civilization

import com.unciv.Constants
import com.unciv.logic.map.MapUnit
import com.unciv.models.Religion
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
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

    var greatProphetsEarned = 0
        private set

    var religionState = ReligionState.None
        private set

    private var foundingCityId: String? = null
    // Only used for keeping track of the city a prophet was used when founding a religion

    fun clone(): ReligionManager {
        val clone = ReligionManager()
        clone.foundingCityId = foundingCityId
        clone.storedFaith = storedFaith
        clone.religionState = religionState
        clone.greatProphetsEarned = greatProphetsEarned
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

    fun faithForPantheon(additionalCivs: Int = 0) =
        10 + (civInfo.gameInfo.civilizations.count { it.isMajorCiv() && it.religionManager.religion != null } + additionalCivs) * 5
        
    fun canFoundPantheon(): Boolean {
        if (!civInfo.gameInfo.hasReligionEnabled()) return false
        if (religionState != ReligionState.None) return false
        if (!civInfo.isMajorCiv()) return false
        if (civInfo.gameInfo.ruleSet.beliefs.values.none { isPickablePantheonBelief(it) })
            return false
        return storedFaith >= faithForPantheon()
    }

    fun isPickablePantheonBelief(belief: Belief): Boolean {
        if (belief.type != BeliefType.Pantheon) return false
        if (civInfo.gameInfo.civilizations.any { it.religionManager.religion != null && it.religionManager.religion!!.followerBeliefs.contains(belief.name)})
            return false
        return true
    }

    fun choosePantheonBelief(belief: Belief) {
        storedFaith -= faithForPantheon()
        religion = Religion(belief.name, civInfo.gameInfo, civInfo.civName)
        religion!!.followerBeliefs.add(belief.name)
        civInfo.gameInfo.religions[belief.name] = religion!!
        for (city in civInfo.cities)
            city.religion.addPressure(belief.name, 200 * city.population.population) 
        religionState = ReligionState.Pantheon
    }
    
    // https://www.reddit.com/r/civ/comments/2m82wu/can_anyone_detail_the_finer_points_of_great/
    // Game files (globaldefines.xml)
    fun faithForNextGreatProphet() = (
            (200 + 100 * greatProphetsEarned * (greatProphetsEarned + 1) / 2) *
                    civInfo.gameInfo.gameParameters.gameSpeed.modifier
            ).toInt()

    private fun canGenerateProphet(): Boolean {
        if (religion == null || religionState == ReligionState.None) return false // First get a pantheon, then we'll talk about a real religion
        if (storedFaith < faithForNextGreatProphet()) return false
        if (!civInfo.isMajorCiv()) return false
        // In the base game, great prophets shouldn't generate anymore starting from the industrial era
        // This is difficult to implement in the current codebase, probably requires an additional variable in eras.json
        return true
    }

    private fun generateProphet() {
        val prophetSpawnChange = (5f + storedFaith - faithForNextGreatProphet()) / 100f

        if (Random(civInfo.gameInfo.turns).nextFloat() < prophetSpawnChange) {
            val birthCity =
                if (religionState <= ReligionState.Pantheon) civInfo.getCapital()
                else civInfo.cities.firstOrNull { it.religion.religionThisIsTheHolyCityOf == religion!!.name }
            val prophet = civInfo.addUnit("Great Prophet", birthCity) ?: return
            prophet.religion = religion!!.name
            storedFaith -= faithForNextGreatProphet()
            greatProphetsEarned += 1
        }
    }

    fun mayFoundReligionAtAll(prophet: MapUnit): Boolean {
        if (religion == null) return false // First found a pantheon
        if (religion!!.isMajorReligion()) return false // Already created a major religion
        if (prophet.abilityUsedCount[Constants.spreadReligionAbilityCount] != null && prophet.abilityUsedCount[Constants.spreadReligionAbilityCount] != 0) return false // Already used its power for other things
        if (prophet.abilityUsedCount[Constants.removeHeresyAbilityCount] != null && prophet.abilityUsedCount[Constants.removeHeresyAbilityCount] != 0) return false // Already used its power for other things
        if (!civInfo.isMajorCiv()) return false // Only major civs may use religion
        
        val foundedReligionsCount = civInfo.gameInfo.civilizations.count {
            it.religionManager.religion != null && it.religionManager.religion!!.isMajorReligion()
        }
        
        if (foundedReligionsCount >= civInfo.gameInfo.civilizations.count { it.isMajorCiv() } / 2 + 1) 
            return false // Too bad, too many religions have already been founded
        
        if (foundedReligionsCount >= civInfo.gameInfo.ruleSet.religions.count())
            return false // Mod maker did not provide enough religions for the amount of civs present
        
        if (foundedReligionsCount >= civInfo.gameInfo.ruleSet.beliefs.values.count { it.type == BeliefType.Follower })
            return false // Mod maker did not provide enough follower beliefs
        
        if (foundedReligionsCount >= civInfo.gameInfo.ruleSet.beliefs.values.count { it.type == BeliefType.Founder })
            return false // Mod maker did not provide enough founder beliefs
        
        return true
    }

    fun mayFoundReligionNow(prophet: MapUnit): Boolean {
        if (!mayFoundReligionAtAll(prophet)) return false
        if (!prophet.getTile().isCityCenter()) return false
        return true
    }

    fun useGreatProphet(prophet: MapUnit) {
        if (!mayFoundReligionNow(prophet)) return // How did you do this?
        religionState = ReligionState.FoundingReligion
        foundingCityId = prophet.getTile().getCity()!!.id
    }

    fun foundReligion(iconName: String, name: String, founderBelief: List<String>, followerBeliefs: List<String>) {
        val newReligion = Religion(name, civInfo.gameInfo, civInfo.civName)
        newReligion.iconName = iconName
        if (religion != null) {
            newReligion.followerBeliefs.addAll(religion!!.followerBeliefs)
        }
        newReligion.followerBeliefs.addAll(followerBeliefs)
        newReligion.founderBeliefs.addAll(founderBelief)
        religion = newReligion
        civInfo.gameInfo.religions[name] = newReligion

        religionState = ReligionState.Religion

        val holyCity = civInfo.cities.first { it.id == foundingCityId }
        holyCity.religion.religionThisIsTheHolyCityOf = newReligion.name
        holyCity.religion.addPressure(name, holyCity.population.population * 500)

        foundingCityId = null
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
            .sumBy { it.religion.getFollowersOf(religion!!.name)!! }
    }
}

enum class ReligionState {
    None,
    Pantheon,
    FoundingReligion, // Great prophet used, but religion has not yet been founded
    Religion,
    EnhancingReligion, // Great prophet used, but religion has not yet been enhanced
    EnhancedReligion
}
