package com.unciv.models

import com.unciv.logic.GameInfo
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.MultiFilter
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.INamed

/** Data object for Religions */
class Religion() : INamed, IsPartOfGameInfoSerialization {

    override lateinit var name: String
    var displayName: String? = null
    lateinit var foundingCivName: String

    private var founderBeliefs: HashSet<String> = hashSetOf()
    private var followerBeliefs: HashSet<String> = hashSetOf()

    @Transient
    var founderBeliefUniqueMap = UniqueMap()
    @Transient
    var followerBeliefUniqueMap = UniqueMap()

    @Transient
    lateinit var gameInfo: GameInfo

    @delegate:Transient
    val buildingsPurchasableByBeliefs by lazy {
        unlockedBuildingsPurchasable()
    }

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
        updateUniqueMaps()
    }

    private fun updateUniqueMaps(){
        followerBeliefUniqueMap = UniqueMap(mapToExistingBeliefs(followerBeliefs).flatMap { it.uniqueObjects })
        founderBeliefUniqueMap = UniqueMap(mapToExistingBeliefs(founderBeliefs).flatMap { it.uniqueObjects })
    }

    fun addBeliefs(beliefs: Iterable<Belief>){
        for (belief in beliefs){
            when (belief.type){
                BeliefType.Founder, BeliefType.Enhancer -> founderBeliefs.add(belief.name)
                BeliefType.Pantheon, BeliefType.Follower -> followerBeliefs.add(belief.name)
                else -> continue // 'None' and 'Any' are not valid for beliefs, they're used for internal purposes
            }
        }
        updateUniqueMaps()
    }

    fun getIconName() =
        if (isPantheon()) "Pantheon"
        else name

    fun getReligionDisplayName() =
        if (displayName != null) displayName!!
        else name

    private fun mapToExistingBeliefs(beliefs: Set<String>): Sequence<Belief> {
        val rulesetBeliefs = gameInfo.ruleset.beliefs
        return beliefs.asSequence().mapNotNull {
            if (it !in rulesetBeliefs) null
            else rulesetBeliefs[it]!!
        }
    }

    fun getBeliefs(beliefType: BeliefType): Sequence<Belief> {
        if (beliefType == BeliefType.Any)
            return mapToExistingBeliefs((founderBeliefs + followerBeliefs).toHashSet())

        val beliefs =
            when {
                beliefType.isFollower -> followerBeliefs
                beliefType.isFounder -> founderBeliefs
                else -> null!! // This is fine...
            }

        return mapToExistingBeliefs(beliefs)
            .filter { it.type == beliefType }
    }

    fun getAllBeliefsOrdered(): Sequence<Belief> {
        return mapToExistingBeliefs(followerBeliefs).filter { it.type == BeliefType.Pantheon } +
            mapToExistingBeliefs(founderBeliefs).filter { it.type == BeliefType.Founder } +
            mapToExistingBeliefs(followerBeliefs).filter { it.type == BeliefType.Follower } +
            mapToExistingBeliefs(founderBeliefs).filter { it.type == BeliefType.Enhancer }
    }

    fun hasBelief(belief: String) = followerBeliefs.contains(belief) || founderBeliefs.contains(belief)

    fun isPantheon() = getBeliefs(BeliefType.Pantheon).any() && !isMajorReligion()

    fun isMajorReligion() = getBeliefs(BeliefType.Founder).any()

    fun isEnhancedReligion() = getBeliefs(BeliefType.Enhancer).any()

    fun getFounder() = gameInfo.getCivilization(foundingCivName)

    fun matchesFilter(filter: String, state: StateForConditionals = StateForConditionals.IgnoreConditionals, civ: Civilization? = null): Boolean {
        return MultiFilter.multiFilter(filter, { matchesSingleFilter(it, state, civ) })
    }
    
    private fun matchesSingleFilter(filter: String, state: StateForConditionals = StateForConditionals.IgnoreConditionals, civ: Civilization? = null): Boolean {
        if (filter == "any") return true
        if (filter == name) return true
        if (filter == "major") return isMajorReligion()
        if (filter == "enhanced") return isEnhancedReligion()
        val foundingCiv = getFounder()
        if (filter == "your") return civ == foundingCiv
        if (filter == "foreign") return civ != null && civ != foundingCiv
        val known = civ != null && civ.knows(foundingCiv)
        if (filter == "enemy") return known && civ!!.isAtWarWith(foundingCiv)
        if (founderBeliefUniqueMap.hasMatchingUnique(filter, state)) return true
        if (founderBeliefUniqueMap.hasMatchingUnique(filter, state)) return true
        return false
    }

    private fun unlockedBuildingsPurchasable(): List<String> {
        return getAllBeliefsOrdered().flatMap { belief ->
            belief.getMatchingUniques(UniqueType.BuyBuildingsWithStat).map { it.params[0] } +
            belief.getMatchingUniques(UniqueType.BuyBuildingsForAmountStat).map { it.params[0] } +
            belief.getMatchingUniques(UniqueType.BuyBuildingsIncreasingCost).map { it.params[0] }
        }.filter { gameInfo.ruleset.buildings.containsKey(it) }.toList()
    }
}
