package com.unciv.models

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.INamed

/** Data object for Religions */
class Religion() : INamed {
    
    var iconName: String = "Pantheon"
    
    var pantheonBeliefs: HashSet<String> = hashSetOf()
    var founderBeliefs: HashSet<String> = hashSetOf()
    var followerBeliefs: HashSet<String> = hashSetOf()
    
    override lateinit var name: String
    lateinit var foundingCivName: String
    
    @Transient
    lateinit var gameInfo: GameInfo
    
    constructor(name: String, gameInfo: GameInfo, foundingCivName: String) : this() {
        this.name = name
        this.foundingCivName = foundingCivName
        this.gameInfo = gameInfo
    }
    
    fun clone(): Religion {
        val toReturn = Religion(name, gameInfo, foundingCivName)
        toReturn.pantheonBeliefs.addAll(pantheonBeliefs)
        toReturn.founderBeliefs.addAll(founderBeliefs)
        toReturn.followerBeliefs.addAll(followerBeliefs)
        return toReturn
    }
    
    fun setTransients(gameInfo: GameInfo) {
        this.gameInfo = gameInfo
    }
    
    private fun getUniquesOfBeliefs(beliefs: HashSet<String>): Sequence<Unique> {
        val rulesetBeliefs = gameInfo.ruleSet.beliefs
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