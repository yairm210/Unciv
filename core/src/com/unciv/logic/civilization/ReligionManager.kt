package com.unciv.logic.civilization

import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.Unique

class ReligionManager {
    @Transient
    lateinit var civInfo: CivilizationInfo

    var storedFaith = 0

    var pantheonBelief: String? = null

    fun getUniques(): Sequence<Unique> {
        if(pantheonBelief==null) return sequenceOf()
        else return civInfo.gameInfo.ruleSet.beliefs[pantheonBelief!!]!!.uniqueObjects.asSequence()
    }

    fun faithForPantheon() = 10 + civInfo.gameInfo.civilizations.count { it.isMajorCiv() && it.religionManager.pantheonBelief != null } * 5

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

    fun endTurn(faithFromNewTurn: Int) {
        storedFaith += faithFromNewTurn
    }

    fun choosePantheonBelief(belief: Belief){
        storedFaith -= faithForPantheon()
        pantheonBelief = belief.name
    }

    fun clone(): ReligionManager {
        val clone = ReligionManager()
        clone.pantheonBelief = pantheonBelief
        clone.storedFaith = storedFaith
        return clone
    }
}
