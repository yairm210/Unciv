package com.unciv.models.ruleset.unique

import com.unciv.models.stats.INamed

/**
 * Common interface for all 'ruleset objects' that have Uniques, like BaseUnit, Nation, etc.
 */
interface IHasUniques {
    /** The Uniques as they are read from json */
    var uniques: ArrayList<String> // Can not be a hashset as that would remove doubles

    /** Do not use except for overriding and initializing to null in [IHasUniques] implementation */
    var uniqueObjectsInternal: List<Unique>?
    /** [uniques] as list of [Unique]s. Late-initialized with a twist:
     *  Accessing [uniqueObjects] while [uniques] is empty will return an emptyList without marking the objects as initialized. */
    val uniqueObjects: List<Unique> get() {
        if (uniques.isEmpty()) return emptyList()
        if (uniqueObjectsInternal == null)
            uniqueObjectsInternal = uniquesToUniqueObjects(uniques)
        return uniqueObjectsInternal!!
    }
    /** Convert a collection of String [uniques] to [Unique] objects, can be used in [uniqueObjects] overrides. */
    fun uniquesToUniqueObjects(uniques: Iterable<String>): List<Unique> {
        if (uniques.none()) return emptyList()
        return uniques.map { Unique(it, getUniqueTarget(), (this as? INamed)?.name) }
    }

    /** Do not use except for overriding and initializing to null in [IHasUniques] implementation */
    var uniqueMapInternal: UniqueMap?
    /** A map of (lists of) [Unique]s indexed by [UniqueType.placeholderText]. Late-initialized from [uniqueObjects]. */
    val uniqueMap: UniqueMap get() {
        if (uniques.isEmpty()) return UniqueMap.empty
        if (uniqueMapInternal == null) uniqueMapInternal = UniqueMap(uniqueObjects)
        return uniqueMapInternal!!
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
}
