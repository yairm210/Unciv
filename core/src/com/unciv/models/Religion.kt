package com.unciv.models

import com.unciv.logic.GameInfo
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.MultiFilter
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.INamed
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.Readonly

/** Data object for Religions */
class Religion() : INamed, IsPartOfGameInfoSerialization {

    override lateinit var name: String
    var displayName: String? = null

    private var founderBeliefs: HashSet<String> = hashSetOf()
    private var followerBeliefs: HashSet<String> = hashSetOf()

    @Transient
    var founderBeliefUniqueMap = UniqueMap()
    @Transient
    var followerBeliefUniqueMap = UniqueMap()

    @Transient
    lateinit var gameInfo: GameInfo
    
    lateinit var foundingCivName: String
        private set
    @Transient
    @Cache
    private lateinit var _foundingCiv: Civilization
    @get:Readonly
    val foundingCiv: Civilization get() {
        if (!::_foundingCiv.isInitialized) _foundingCiv = gameInfo.getCivilization(foundingCivName)
        return _foundingCiv
    }

    @delegate:Transient
    val buildingsPurchasableByBeliefs by lazy {
        unlockedBuildingsPurchasable()
    }
    
    constructor(name: String, gameInfo: GameInfo, foundingCiv: Civilization): this() {
        this.name = name
        this.gameInfo = gameInfo
        this._foundingCiv = foundingCiv
        this.foundingCivName = foundingCiv.civName
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

    fun addBelief(belief: Belief) = addBeliefs(listOf(belief))
    fun addBelief(beliefName: String) = gameInfo.ruleset.beliefs[beliefName]?.let { addBelief(it) }

    @Readonly
    fun getIconName() =
        if (isPantheon()) "Pantheon"
        else name

    @Readonly
    fun getReligionDisplayName() =
        if (displayName != null) displayName!!
        else name

    @Readonly
    private fun mapToExistingBeliefs(beliefs: Set<String>): Sequence<Belief> {
        val rulesetBeliefs = gameInfo.ruleset.beliefs
        return beliefs.asSequence().mapNotNull { rulesetBeliefs[it] }
    }

    @Readonly
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

    @Readonly fun getAllBeliefsOrdered(): Sequence<Belief> {
        return mapToExistingBeliefs(followerBeliefs).filter { it.type == BeliefType.Pantheon } +
            mapToExistingBeliefs(founderBeliefs).filter { it.type == BeliefType.Founder } +
            mapToExistingBeliefs(followerBeliefs).filter { it.type == BeliefType.Follower } +
            mapToExistingBeliefs(founderBeliefs).filter { it.type == BeliefType.Enhancer }
    }

    @Readonly fun hasBelief(belief: String) = followerBeliefs.contains(belief) || founderBeliefs.contains(belief)

    @Readonly fun isPantheon() = getBeliefs(BeliefType.Pantheon).any() && !isMajorReligion()
    @Readonly fun isMajorReligion() = getBeliefs(BeliefType.Founder).any()
    @Readonly fun isEnhancedReligion() = getBeliefs(BeliefType.Enhancer).any()

    @Readonly
    fun matchesFilter(filter: String, state: GameContext = GameContext.IgnoreConditionals, civ: Civilization? = null): Boolean {
        return MultiFilter.multiFilter(filter, { matchesSingleFilter(it, state, civ) })
    }

    @Readonly
    private fun matchesSingleFilter(filter: String, state: GameContext = GameContext.IgnoreConditionals, civ: Civilization? = null): Boolean {
        val foundingCiv = foundingCiv
        when (filter) {
            "any" -> return true
            "major" -> return isMajorReligion()
            "enhanced" -> return isEnhancedReligion()
            "your" -> return civ == foundingCiv
            "foreign" -> return civ != null && civ != foundingCiv
            "enemy" -> {
                val known = civ != null && civ.knows(foundingCiv)
                return known && civ!!.isAtWarWith(foundingCiv)
            }
            else -> {
                if (filter == name) return true
                if (filter in getBeliefs(BeliefType.Any).map { it.name }) return true
                if (founderBeliefUniqueMap.hasMatchingTagUnique(filter, state)) return true
                if (followerBeliefUniqueMap.hasMatchingTagUnique(filter, state)) return true
                return false
            }
        }
    }

    @Readonly
    private fun unlockedBuildingsPurchasable(): List<String> {
        return getAllBeliefsOrdered().flatMap { belief ->
            belief.getMatchingUniques(UniqueType.BuyBuildingsWithStat).map { it.params[0] } +
            belief.getMatchingUniques(UniqueType.BuyBuildingsForAmountStat).map { it.params[0] } +
            belief.getMatchingUniques(UniqueType.BuyBuildingsIncreasingCost).map { it.params[0] }
        }.filter { gameInfo.ruleset.buildings.containsKey(it) }.toList()
    }
}
