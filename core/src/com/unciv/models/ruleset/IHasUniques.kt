package com.unciv.models.ruleset

import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType

/**
 * Common interface for all 'ruleset objects' that have Uniques, like BaseUnit, Nation, etc.
 */
interface IHasUniques {
    var uniques: ArrayList<String> // Can not be a hashset as that would remove doubles
    // I bet there's a way of initializing these without having to override it everywhere...
    val uniqueObjects: List<Unique>
    /** Technically not currently needed, since the unique target can be retrieved from every unique in the uniqueObjects,
     * But making this a function is relevant for future "unify Unciv object" plans ;)
     * */
    fun getUniqueTarget(): UniqueTarget

    fun getMatchingUniques(uniqueTemplate: String) = uniqueObjects.asSequence().filter { it.placeholderText == uniqueTemplate }
    fun getMatchingUniques(uniqueType: UniqueType) = uniqueObjects.asSequence().filter { it.isOfType(uniqueType) }

    fun hasUnique(uniqueTemplate: String) = uniqueObjects.any { it.placeholderText == uniqueTemplate }
    fun hasUnique(uniqueType: UniqueType) = uniqueObjects.any { it.isOfType(uniqueType) }
}
