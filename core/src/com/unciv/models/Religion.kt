package com.unciv.models

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.INamed

/** Data object for Religions */
class Religion(override var name: String, val foundingCiv: CivilizationInfo) : INamed {
    
    var pantheonBeliefs: HashSet<String> = hashSetOf()
    var founderBeliefs: HashSet<String> = hashSetOf()
    var followerBeliefs: HashSet<String> = hashSetOf()

    fun clone(): Religion {
        val toReturn = Religion(name, foundingCiv)
        toReturn.pantheonBeliefs.addAll(pantheonBeliefs)
        toReturn.founderBeliefs.addAll(founderBeliefs)
        toReturn.followerBeliefs.addAll(followerBeliefs)
        return toReturn
    }
    
    private fun getUniquesOfBeliefs(beliefs: HashSet<String>): Sequence<Unique> {
        val rulesetBeliefs = foundingCiv.gameInfo.ruleSet.beliefs
        return beliefs.mapNotNull {
            if (it !in rulesetBeliefs) null
            else rulesetBeliefs[it]!!.uniqueObjects
        }.flatten().asSequence()
    }
    
    fun getFollowerUniques(): Sequence<Unique> {
        return getUniquesOfBeliefs((followerBeliefs + pantheonBeliefs).toHashSet())   
    }
    
    fun getFounderUniques(): Sequence<Unique> {
        return getUniquesOfBeliefs(founderBeliefs)
    }
    
    fun isPantheon(): Boolean {
        return hasPantheon() && !isMajorReligion()
    }
    
    fun isMajorReligion(): Boolean {
        return founderBeliefs.isNotEmpty() && followerBeliefs.isNotEmpty()
    }
    
    fun hasPantheon(): Boolean {
        return pantheonBeliefs.isNotEmpty()
    }
}