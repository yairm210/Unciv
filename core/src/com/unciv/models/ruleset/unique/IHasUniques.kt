package com.unciv.models.ruleset.unique

import com.unciv.models.stats.INamed

/**
 * Common interface for all 'ruleset objects' that have Uniques, like BaseUnit, Nation, etc.
 */
interface IHasUniques : INamed {
    var uniques: ArrayList<String> // Can not be a hashset as that would remove doubles

    // Every implementation should override these with the same `by lazy (::thingsProvider)`
    // AND every implementation should annotate these with `@delegate:Transient`
    val uniqueObjects: List<Unique>
    val uniqueMap: Map<String, List<Unique>>

    fun uniqueObjectsProvider(): List<Unique> {
        if (uniques.isEmpty()) return emptyList()
        return uniques.map { Unique(it, getUniqueTarget(), name) }
    }
    fun uniqueMapProvider(): UniqueMap {
        val newUniqueMap = UniqueMap()
        if (uniques.isNotEmpty())
            newUniqueMap.addUniques(uniqueObjects)
        return newUniqueMap
    }

    /** Technically not currently needed, since the unique target can be retrieved from every unique in the uniqueObjects,
     * But making this a function is relevant for future "unify Unciv object" plans ;)
     * */
    fun getUniqueTarget(): UniqueTarget

    fun getMatchingUniques(uniqueTemplate: String, stateForConditionals: StateForConditionals? = null): Sequence<Unique> {
        val matchingUniques = uniqueMap[uniqueTemplate] ?: return sequenceOf()
        return matchingUniques.asSequence().filter { it.conditionalsApply(stateForConditionals ?: StateForConditionals()) }
    }

    fun getMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals? = null) =
        getMatchingUniques(uniqueType.placeholderText, stateForConditionals)

    fun hasUnique(uniqueTemplate: String, stateForConditionals: StateForConditionals? = null) =
        getMatchingUniques(uniqueTemplate, stateForConditionals).any()

    fun hasUnique(uniqueType: UniqueType, stateForConditionals: StateForConditionals? = null) =
        getMatchingUniques(uniqueType.placeholderText, stateForConditionals).any()

    fun requiredTechs(): Sequence<String> {
        val uniquesForWhenThisIsAvailable: Sequence<Unique> = getMatchingUniques(UniqueType.OnlyAvailableWhen, StateForConditionals.IgnoreConditionals)
        val conditionalsForWhenThisIsAvailable: Sequence<Unique> = uniquesForWhenThisIsAvailable.flatMap{ it.conditionals }
        val techRequiringConditionalsForWhenThisIsAvailable: Sequence<Unique> = conditionalsForWhenThisIsAvailable.filter{ it.isOfType(UniqueType.ConditionalTech) }
        return techRequiringConditionalsForWhenThisIsAvailable.map{ it.params[0] }
    }
}
