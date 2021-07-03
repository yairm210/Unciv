package com.unciv.logic.civilization

import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.Unique
import kotlin.random.Random

class ReligionManager {
    @Transient
    lateinit var civInfo: CivilizationInfo

    var storedFaith = 0

    var pantheonBelief: String? = null
    
    var greatProphetsEarned = 0

    fun clone(): ReligionManager {
        val clone = ReligionManager()
        clone.pantheonBelief = pantheonBelief
        clone.storedFaith = storedFaith
        return clone
    }

    fun startTurn() {
        if (canGenerateProphet() && storedFaith > faithForNextGreatProphet()) {
            val prophetSpawnChange = (5f + storedFaith - faithForNextGreatProphet()) / 100f
            if (Random.nextFloat() < prophetSpawnChange) {
                civInfo.addUnit("Great Prophet")
                storedFaith = 0 
                // No, we don't just subtract the amount of faith it requires, you lose ALL of it. Why would we be nice?
            }
        }
    }
    
    fun endTurn(faithFromNewTurn: Int) {
        storedFaith += faithFromNewTurn
    }
    
    private fun faithForPantheon() = 10 + civInfo.gameInfo.civilizations.count { it.isMajorCiv() && it.religionManager.pantheonBelief != null } * 5

    fun canFoundPantheon(): Boolean {
        if (pantheonBelief != null) return false
        if (!civInfo.gameInfo.hasReligionEnabled()) return false
        if (civInfo.gameInfo.ruleSet.beliefs.values.none { isPickablePantheonBelief(it) })
            return false
        return storedFaith >= faithForPantheon()
    }

    fun isPickablePantheonBelief(belief: Belief): Boolean {
        if (belief.type != "Pantheon") return false
        if (civInfo.gameInfo.civilizations.any { it.religionManager.pantheonBelief == belief.name })
            return false
        return true
    }

    fun choosePantheonBelief(belief: Belief){
        storedFaith -= faithForPantheon()
        pantheonBelief = belief.name
        // This should later be changed when religions can have multiple beliefs
        civInfo.getCapital().religion[belief.name] = 100 // Capital is religious, other cities are not
    }
    
    // https://www.reddit.com/r/civ/comments/2m82wu/can_anyone_detail_the_finer_points_of_great/
    // Game files (globaldefines.xml)
    fun faithForNextGreatProphet() = ((200 + 100 * greatProphetsEarned * (greatProphetsEarned + 1)/2) * civInfo.gameInfo.gameParameters.gameSpeed.modifier).toInt()
    
    fun canGenerateProphet(): Boolean {
        // In the base game, great prophets shouldn't generate anymore starting from the industrial era
        // This is difficult to implement in the current codebase, probably requires an additional variable in eras.json
        // Also only if you either [have founded a religion] or [the max amount of religions (players/2 + 1) has not been reached].
        // As this is yet to implement, this function does absolutely nothing
        return true
    }
}
