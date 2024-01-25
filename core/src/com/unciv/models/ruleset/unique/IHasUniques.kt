package com.unciv.models.ruleset.unique

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tech.Era
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology
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

    fun availabilityUniques(): Sequence<Unique> = getMatchingUniques(UniqueType.OnlyAvailable, StateForConditionals.IgnoreConditionals)

    fun techsRequiredByUniques(): Sequence<String> {
        return availabilityUniques()
                // Currently an OnlyAvailableWhen can have multiple conditionals, implicitly a conjunction.
                // Therefore, if any of its several conditionals is a ConditionalTech, then that tech is required.
                .flatMap{ it.conditionals }
                .filter{ it.type == UniqueType.ConditionalTech }
                .map { it.params[0] }
    }

    fun legacyRequiredTechs(): Sequence<String> = sequenceOf()

    fun requiredTechs(): Sequence<String> = legacyRequiredTechs() + techsRequiredByUniques()

    fun requiredTechnologies(ruleset: Ruleset): Sequence<Technology?> =
        requiredTechs().map{ ruleset.technologies[it] }

    fun era(ruleset: Ruleset): Era? =
            requiredTechnologies(ruleset).map{ it?.era() }.map{ ruleset.eras[it] }.maxByOrNull{ it?.eraNumber ?: 0 }
            // This will return null only if requiredTechnologies() is empty or all required techs have no eraNumber

    fun techColumn(ruleset: Ruleset): TechColumn? =
            requiredTechnologies(ruleset).map{ it?.column }.filterNotNull().maxByOrNull{ it.columnNumber }
            // This will return null only if *all* required techs have null TechColumn.

    fun availableInEra(ruleset: Ruleset, requestedEra: String): Boolean {
        val eraAvailable: Era? = era(ruleset)
        if (eraAvailable == null)
            // No technologies are required, so available in the starting era.
            return true
        // This is not very efficient, because era() inspects the eraNumbers and then returns the whole object.
        // We could take a max of the eraNumbers directly.
        // But it's unlikely to make any significant difference.
        // Currently this is only used in CityStateFunctions.kt.
        return eraAvailable.eraNumber <= ruleset.eras[requestedEra]!!.eraNumber
    }
}
