package com.unciv.logic.civilization

import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.MapUnit
import com.unciv.models.Religion
import com.unciv.models.ruleset.Belief
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
    // But the other one should still be _somehwere_. So our only option is to have the GameInfo
    // contain the master list, and the ReligionManagers retrieve it from there every time the game loads.

    private var greatProphetsEarned = 0

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

    private fun faithForPantheon() = 10 + civInfo.gameInfo.civilizations.count { it.isMajorCiv() && it.religionManager.religion != null } * 5

    fun canFoundPantheon(): Boolean {
        if (!civInfo.gameInfo.hasReligionEnabled()) return false
        if (religionState != ReligionState.None) return false
        if (!civInfo.isMajorCiv()) return false
        if (civInfo.gameInfo.ruleSet.beliefs.values.none { isPickablePantheonBelief(it) })
            return false
        return storedFaith >= faithForPantheon()
    }

    fun isPickablePantheonBelief(belief: Belief): Boolean {
        if (belief.type != "Pantheon") return false
        if (civInfo.gameInfo.civilizations.any { it.religionManager.religion != null && it.religionManager.religion!!.pantheonBeliefs.contains(belief.name)})
            return false
        return true
    }

    fun choosePantheonBelief(belief: Belief) {
        storedFaith -= faithForPantheon()
        religion = Religion(belief.name, civInfo.gameInfo, civInfo.civName)
        religion!!.pantheonBeliefs.add(belief.name)
        civInfo.gameInfo.religions[belief.name] = religion!!
        // This should later be changed when religions can have multiple beliefs
        civInfo.getCapital().religion[belief.name] = 100 // Capital is religious, other cities are not
        religionState = ReligionState.Pantheon
    }

    private fun generateProphet() {
        val prophetSpawnChange = (5f + storedFaith - faithForNextGreatProphet()) / 100f

        if (Random(civInfo.gameInfo.turns).nextFloat() < prophetSpawnChange) {
            val birthCities =
                civInfo.cities.filter { it.religion.getMajorityReligion() == religion!!.name }
            val birthCity: CityInfo =
                if (birthCities.isEmpty()) {
                    if (religionState == ReligionState.Pantheon)  civInfo.getCapital()
                    else civInfo.cities.first { it.id == religion!!.holyCityId }
                } else {
                    birthCities.random()
                }

            val prophet = civInfo.addUnit("Great Prophet", birthCity)
            if (prophet == null) return
            prophet.religion = religion!!.name
            prophet.abilityUsedCount["Religion Spread"] = 0
            storedFaith -= faithForNextGreatProphet()
        }
    }

    fun mayUseGreatProphetAtAll(prophet: MapUnit): Boolean {
        if (religion == null) return false // First found a pantheon
        if (religion!!.isMajorReligion()) return false // Already created a major religion
        if (prophet.abilityUsedCount["Religion Spread"] != 0) return false // Already used its power for other things
        if (civInfo.gameInfo.civilizations.count {
                it.religionManager.religion != null && it.religionManager.religion!!.isMajorReligion()
            }
                >= civInfo.gameInfo.civilizations.count { it.isMajorCiv() } / 2 + 1
            ) return false // Too bad, too many religions have already been founded.

        return true
    }

    fun mayUseGreatProphetNow(prophet: MapUnit): Boolean {
        if (!mayUseGreatProphetAtAll(prophet)) return false
        if (prophet.getTile().getCity() == null) return false
        return true
    }

    fun useGreatProphet(prophet: MapUnit) {
        if (!mayUseGreatProphetNow(prophet)) return // How did you do this?
        religionState = ReligionState.FoundingReligion
        foundingCityId = prophet.getTile().getCity()!!.id
    }

    fun foundReligion(iconName: String, name: String, founderBelief: String, followerBelief: String) {
        val newReligion = Religion(name, civInfo.gameInfo, civInfo.civName)
        newReligion.iconName = iconName
        if (religion != null) {
            newReligion.pantheonBeliefs.addAll(religion!!.pantheonBeliefs)
            newReligion.followerBeliefs.addAll(religion!!.followerBeliefs)
            newReligion.founderBeliefs.addAll(religion!!.founderBeliefs)
        }
        newReligion.followerBeliefs.add(followerBelief)
        newReligion.founderBeliefs.add(founderBelief)
        newReligion.holyCityId = foundingCityId
        religion = newReligion
        civInfo.gameInfo.religions[name] = newReligion

        religionState = ReligionState.Religion
        val holyCity = civInfo.cities.firstOrNull { it.id == newReligion.holyCityId }!!
        holyCity.religion.clear()
        holyCity.religion[name] = 100

        foundingCityId = null
    }

    // https://www.reddit.com/r/civ/comments/2m82wu/can_anyone_detail_the_finer_points_of_great/
    // Game files (globaldefines.xml)
    fun faithForNextGreatProphet() = (
        (200 + 100 * greatProphetsEarned * (greatProphetsEarned + 1) / 2) *
        civInfo.gameInfo.gameParameters.gameSpeed.modifier
    ).toInt()

    fun canGenerateProphet(): Boolean {
        if (religion == null || religionState == ReligionState.None) return false // First get a pantheon, then we'll talk about a real religion
        if (storedFaith < faithForNextGreatProphet()) return false
        // In the base game, great prophets shouldn't generate anymore starting from the industrial era
        // This is difficult to implement in the current codebase, probably requires an additional variable in eras.json
        return false
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