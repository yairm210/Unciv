package com.unciv.models

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.INamed

/** Data object for Religions */
class Religion() : INamed {

    override lateinit var name: String
    var iconName: String = "Pantheon"
    lateinit var foundingCivName: String
    var holyCityId: String? = null

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
        toReturn.holyCityId = holyCityId
        toReturn.founderBeliefs.addAll(founderBeliefs)
        toReturn.followerBeliefs.addAll(followerBeliefs)
        return toReturn
    }

    fun setTransients(gameInfo: GameInfo) {
        this.gameInfo = gameInfo
    }

    fun getPantheonBeliefs(): Sequence<Belief> {
        val rulesetBeleifs = gameInfo.ruleSet.beliefs
        return followerBeliefs.mapNotNull {
            if (it !in rulesetBeleifs) null
            else rulesetBeleifs[it]!!
        }.filter { it.type == "Pantheon" }
            .asSequence()
    }
    
    fun getFollowerBeliefs(): Sequence<Belief> {
        val rulesetBeliefs = gameInfo.ruleSet.beliefs
        return followerBeliefs.mapNotNull {
            if (it !in rulesetBeliefs) null
            else rulesetBeliefs[it]!!
        }.filter { it.type == "Follower" }
            .asSequence()
    }
    
    private fun getUniquesOfBeliefs(beliefs: HashSet<String>): Sequence<Unique> {
        val rulesetBeliefs = gameInfo.ruleSet.beliefs
        return beliefs.mapNotNull {
            if (it !in rulesetBeliefs) null
            else rulesetBeliefs[it]!!.uniqueObjects
        }.flatten().asSequence()
    }

    fun getFollowerUniques(): Sequence<Unique> {
        return getUniquesOfBeliefs(followerBeliefs)
    }

    fun getFounderUniques(): Sequence<Unique> {
        return getUniquesOfBeliefs(founderBeliefs)
    }

    fun isPantheon(): Boolean {
        return hasPantheon() && !isMajorReligion()
    }

    fun isMajorReligion(): Boolean {
        if ("" in followerBeliefs) return true // Temporary as a result of follower beliefs not yet being implemented
        return founderBeliefs.isNotEmpty() && followerBeliefs.any { gameInfo.ruleSet.beliefs[it]!!.type == "Follower"}
    }

    fun hasPantheon(): Boolean {
        // Temporary as a result of follower beliefs not yet being implemented
        return followerBeliefs.any { it != "" && gameInfo.ruleSet.beliefs[it]!!.type == "Pantheon" }
    }
    
    fun hasBelief(belief: String): Boolean {
        return followerBeliefs.contains(belief) || founderBeliefs.contains(belief)
    }
}