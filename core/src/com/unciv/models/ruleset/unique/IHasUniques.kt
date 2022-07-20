package com.unciv.models.ruleset.unique

/**
 * Common interface for all 'ruleset objects' that have Uniques, like BaseUnit, Nation, etc.
 */
interface IHasUniques {
    var uniques: ArrayList<String> // Can not be a hashset as that would remove doubles
    // I bet there's a way of initializing these without having to override it everywhere...
    val uniqueObjects: List<Unique>

    val uniqueMap: Map<String, List<Unique>>

    /** Technically not currently needed, since the unique target can be retrieved from every unique in the uniqueObjects,
     * But making this a function is relevant for future "unify Unciv object" plans ;)
     * */
    fun getUniqueTarget(): UniqueTarget

    fun getMatchingUniques(uniqueTemplate: String, stateForConditionals: StateForConditionals? = null): Sequence<Unique> {
        val matchingUniques = uniqueMap[uniqueTemplate] ?: return sequenceOf()
        return matchingUniques.asSequence().filter { it.conditionalsApply(stateForConditionals) }
    }

    fun getMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals? = null) =
        getMatchingUniques(uniqueType.placeholderText, stateForConditionals)

    fun hasUnique(uniqueTemplate: String, stateForConditionals: StateForConditionals? = null) =
        getMatchingUniques(uniqueTemplate, stateForConditionals).any()

    fun hasUnique(uniqueType: UniqueType, stateForConditionals: StateForConditionals? = null) =
        getMatchingUniques(uniqueType.placeholderText, stateForConditionals).any()
}

