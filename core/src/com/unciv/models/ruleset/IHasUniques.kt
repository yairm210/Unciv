package com.unciv.models.ruleset

/**
 * Common interface for all 'ruleset objects' that have Uniques, like BaseUnit, Nation, etc.
 */
interface IHasUniques {
    var uniques: ArrayList<String> // Can not be a hashset as that would remove doubles
    val uniqueObjects: List<Unique>
    
    fun getMatchingUniques(uniqueTemplate: String) = uniqueObjects.asSequence().filter { it.placeholderText == uniqueTemplate }
    
    fun hasUnique(uniqueTemplate: String) = uniqueObjects.any { it.placeholderText == uniqueTemplate }
}
