package com.unciv.logic.civilization

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
    // I would agree that that is better. However, there is, of course, a problem.
    // When founding a religion, the religion of your pantheon doesn't immediately disappear.
    // It just stops growing. Your new religion will then have to spread out from your holy city
    // and convert these cities. This means, that civilizations can have multiple active religions
    // in some cases. We only save one of them in this class to reduce the amount of logic necessary.
    // But the other one should still be _somehwere_. So our only option is to have the GameInfo
    // contain the master list, and the ReligionManagers retrieve it from there every time the game loads.
    
    private var greatProphetsEarned = 0

    fun clone(): ReligionManager {
        val clone = ReligionManager()
        clone.storedFaith = storedFaith
        return clone
    }
    
    fun setTransients() {
        // Find our religion from the map of founded religions.
        // First check if there is any major religion
        religion = civInfo.gameInfo.religions.values.firstOrNull { 
            it.foundingCiv.civName == civInfo.civName && it.isMajorReligion()
        }
        // If there isn't, check for just pantheons.
        if (religion != null) return
        religion = civInfo.gameInfo.religions.values.firstOrNull {
            it.foundingCiv.civName == civInfo.civName
        }
    }

    fun startTurn() {
        if (canGenerateProphet()) {
            val prophetSpawnChange = (5f + storedFaith - faithForNextGreatProphet()) / 100f
            if (Random(civInfo.gameInfo.turns).nextFloat() < prophetSpawnChange) {
                val birthCity = civInfo.cities.filter { it.religion.getMajorityReligion() == religion!!.name }.random()
                val prophet = civInfo.addUnit("Great Prophet", birthCity)
                if (prophet == null) return
                prophet.religion = religion!!.name
                prophet.abilityUsedCount["Religion Spread"] = 0
                storedFaith -= faithForNextGreatProphet()
            }
        }
    }
    
    fun endTurn(faithFromNewTurn: Int) {
        storedFaith += faithFromNewTurn
    }
    
    private fun faithForPantheon() = 10 + civInfo.gameInfo.civilizations.count { it.isMajorCiv() && it.religionManager.religion != null } * 5

    fun canFoundPantheon(): Boolean {
        if (religion != null) return false
        if (!civInfo.gameInfo.hasReligionEnabled()) return false
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
        religion = Religion(belief.name, civInfo)
        religion!!.pantheonBeliefs.add(belief.name)
        civInfo.gameInfo.religions[belief.name] = religion!!
        // This should later be changed when religions can have multiple beliefs
        civInfo.getCapital().religion[belief.name] = 100 // Capital is religious, other cities are not
    }
    
    // https://www.reddit.com/r/civ/comments/2m82wu/can_anyone_detail_the_finer_points_of_great/
    // Game files (globaldefines.xml)
    fun faithForNextGreatProphet() = ((200 + 100 * greatProphetsEarned * (greatProphetsEarned + 1)/2) * civInfo.gameInfo.gameParameters.gameSpeed.modifier).toInt()
    
    fun canGenerateProphet(): Boolean {
        if (religion == null || !religion!!.hasPantheon()) return false // First get a pantheon, then we'll talk about a real religion
        if (storedFaith < faithForNextGreatProphet()) return false
        // In the base game, great prophets shouldn't generate anymore starting from the industrial era
        // This is difficult to implement in the current codebase, probably requires an additional variable in eras.json
        // Also only if you either [have founded a religion] or [the max amount of religions (players/2 + 1) has not been reached].
        // As this is yet to be implemented, this function does almost nothing
        return true
    }
}
