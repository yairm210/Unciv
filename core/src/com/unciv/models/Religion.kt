package com.unciv.models

import com.unciv.logic.GameInfo
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.stats.INamed

/** Data object for Religions */
class Religion() : INamed {

    override lateinit var name: String
    var displayName: String? = null
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
        toReturn.displayName = displayName
        toReturn.founderBeliefs.addAll(founderBeliefs)
        toReturn.followerBeliefs.addAll(followerBeliefs)
        return toReturn
    }

    fun setTransients(gameInfo: GameInfo) {
        this.gameInfo = gameInfo
    }

    fun getIconName() =
        if (isPantheon()) "Pantheon"
        else name

    fun getReligionDisplayName() =
        if (displayName != null) displayName!!
        else name

    private fun mapToExistingBeliefs(beliefs: HashSet<String>): List<Belief> {
        val rulesetBeliefs = gameInfo.ruleSet.beliefs
        return beliefs.mapNotNull {
            if (it !in rulesetBeliefs) null
            else rulesetBeliefs[it]!!
        }
    }

    fun getBeliefs(beliefType: BeliefType): Sequence<Belief> {
        if (beliefType == BeliefType.Any)
            return mapToExistingBeliefs((founderBeliefs + followerBeliefs).toHashSet()).asSequence()

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

    private fun getUniquesOfBeliefs(beliefs: HashSet<String>) =
        mapToExistingBeliefs(beliefs).asSequence().flatMap { it.uniqueObjects }

    fun getFollowerUniques() = getUniquesOfBeliefs(followerBeliefs)

    fun getFounderUniques() = getUniquesOfBeliefs(founderBeliefs)

    fun hasBelief(belief: String) = followerBeliefs.contains(belief) || founderBeliefs.contains(belief)

    fun isPantheon() = getBeliefs(BeliefType.Pantheon).any() && !isMajorReligion()

    fun isMajorReligion() = getBeliefs(BeliefType.Founder).any()

    fun isEnhancedReligion() = getBeliefs(BeliefType.Enhancer).any()
}
