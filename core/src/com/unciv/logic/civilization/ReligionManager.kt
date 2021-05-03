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
        return storedFaith >= faithForPantheon()
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
