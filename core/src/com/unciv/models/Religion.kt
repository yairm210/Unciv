package com.unciv.models

import com.unciv.logic.GameInfo
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.INamed

/** Data object for Religions */
class Religion() : INamed {

    override lateinit var name: String
    var iconName: String = "Pantheon"
    lateinit var foundingCivName: String

    var founderBeliefs: HashSet<String> = hashSetOf()
    var followerBeliefs: HashSet<String> = hashSetOf()
    
    @Transient
    lateinit var gameInfo: GameInfo

    constructor(name: String, gameInfo: GameInfo, foundingCivName: String) : this() {
        this.name = name
        this.foundingCivName = foundingCivName
        this.gameInfo = gameInfo
    }

    fun clone(): Religion {
        val toReturn = Religion(name, gameInfo, foundingCivName)
        toReturn.iconName = iconName
        toReturn.founderBeliefs.addAll(founderBeliefs)
        toReturn.followerBeliefs.addAll(followerBeliefs)
        return toReturn
    }

    fun setTransients(gameInfo: GameInfo) {
        this.gameInfo = gameInfo
    }
    
    private fun mapToExistingBeliefs(beliefs: HashSet<String>): List<Belief> {
        val rulesetBeliefs = gameInfo.ruleSet.beliefs
        return beliefs.mapNotNull {
            if (it !in rulesetBeliefs) null
            else rulesetBeliefs[it]!!
        }
    }
    
    fun getBeliefs(beliefType: BeliefType): Sequence<Belief> {
        val beliefs = 
            when (beliefType) {
                BeliefType.Pantheon -> followerBeliefs
                BeliefType.Follower -> followerBeliefs
                BeliefType.Founder -> founderBeliefs
                BeliefType.Enhancer -> founderBeliefs
                else -> null!! // This is fine...
            }
        return mapToExistingBeliefs(beliefs)
            .asSequence()
            .filter { it.type == beliefType }
    }
    
    fun getAllBeliefsOrdered(): Sequence<Belief> {
        return mapToExistingBeliefs(followerBeliefs).asSequence().filter { it.type == BeliefType.Pantheon } +
            mapToExistingBeliefs(founderBeliefs).asSequence().filter { it.type == BeliefType.Founder } +
            mapToExistingBeliefs(followerBeliefs).asSequence().filter { it.type == BeliefType.Follower } +
            mapToExistingBeliefs(founderBeliefs).asSequence().filter { it.type == BeliefType.Enhancer }
    }
    
    private fun getUniquesOfBeliefs(beliefs: HashSet<String>): Sequence<Unique> {
        return mapToExistingBeliefs(beliefs)
            .flatMap { it.uniqueObjects }
            .asSequence()
    }

    fun getFollowerUniques(): Sequence<Unique> {
        return getUniquesOfBeliefs(followerBeliefs)
    }

    fun getFounderUniques(): Sequence<Unique> {
        return getUniquesOfBeliefs(founderBeliefs)
    }

    fun hasBelief(belief: String): Boolean {
        return followerBeliefs.contains(belief) || founderBeliefs.contains(belief)
    }

    fun isPantheon(): Boolean { // Currently unused
        return getBeliefs(BeliefType.Pantheon).any() && !isMajorReligion()
    }

    fun isMajorReligion(): Boolean {
        return founderBeliefs.isNotEmpty() && followerBeliefs
            .any { gameInfo.ruleSet.beliefs[it]!!.type == BeliefType.Follower}
    }
    
    fun isEnhancedReligion(): Boolean {
        return founderBeliefs.any {
            gameInfo.ruleSet.beliefs[it]!!.type == BeliefType.Enhancer
        }
    }
}
