package com.unciv.models.ruleset.unique

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tech.Era
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.stats.INamed

/**
 * Common interface for all 'ruleset objects' that have Uniques, like BaseUnit, Nation, etc.
 */
interface IHasUniques : INamed, Json.Serializable {
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

    fun dumpUniques(): String = uniqueObjects.map{ it.toString() }.joinToString(" ") +
            uniqueObjects.map{ it.type.toString() }.joinToString(" ") +
            uniqueObjects.flatMap{ it.conditionals }.map{ it.toString() }.joinToString(" ")

    fun requiredTechs(): Sequence<String> {
        val uniquesForWhenThisIsAvailable: Sequence<Unique> = getMatchingUniques(UniqueType.OnlyAvailableWhen, StateForConditionals.IgnoreConditionals)
        val conditionalsForWhenThisIsAvailable: Sequence<Unique> = uniquesForWhenThisIsAvailable.flatMap{ it.conditionals }
        val techRequiringConditionalsForWhenThisIsAvailable: Sequence<Unique> = conditionalsForWhenThisIsAvailable.filter{ it.isOfType(UniqueType.ConditionalTech) }
        // sanity check that if the string inserted for "requiredTech" by IHasUniques.read() below is in the uniques, then we had better be returning a nonempty Sequence here
        if (techRequiringConditionalsForWhenThisIsAvailable.none() && uniques.any{ it.contains("Only available <after discovering [") })
            throw Exception("Something has gone catastrophically wrong computing required techs. The following data dump might help a developer: " + dumpUniques())
        return techRequiringConditionalsForWhenThisIsAvailable.map{ it.params[0] }
        // Should this be cached? @SeventhM
    }

    fun obsoletingTechs(): Sequence<String> {
        val uniquesForWhenThisIsAvailable: Sequence<Unique> = getMatchingUniques(UniqueType.OnlyAvailableWhen, StateForConditionals.IgnoreConditionals)
        val conditionalsForWhenThisIsAvailable: Sequence<Unique> = uniquesForWhenThisIsAvailable.flatMap{ it.conditionals }
        val techRequiringConditionalsForWhenThisIsAvailable: Sequence<Unique> = conditionalsForWhenThisIsAvailable.filter{ it.isOfType(UniqueType.ConditionalNoTech) }
        if (techRequiringConditionalsForWhenThisIsAvailable.none() && uniques.any{ it.contains("Only available <before discovering [") })
            throw Exception("Something has gone catastrophically wrong computing obsoleting techs. The following data dump might help a developer: " + dumpUniques())
        return techRequiringConditionalsForWhenThisIsAvailable.map{ it.params[0] }
        // Should this be cached? @SeventhM
    }

    fun requiredResources(): Sequence<String> {
        val resourceUniques: Sequence<Unique> = getMatchingUniques(UniqueType.ConsumesResources, StateForConditionals.IgnoreConditionals)
        if (resourceUniques.none() && uniques.any{ it.contains("Consumes [") })
            throw Exception("Something has gone catastrophically wrong computing required strategic resources. The following data dump might help a developer: " + dumpUniques())
        return resourceUniques.map{ it.params[1] }
        // Should this be cached? @SeventhM
    }

    fun upgradesTo(): Sequence<String> {
        val upgradeUniques: Sequence<Unique> = getMatchingUniques(UniqueType.CanUpgrade, StateForConditionals.IgnoreConditionals)
        if (upgradeUniques.none() && uniques.any{ it.contains("Can upgrade to [") })
            throw Exception("Something has gone catastrophically wrong computing unit upgrade paths. The following data dump might help a developer: " + dumpUniques())
        return upgradeUniques.map{ it.params[0] }
        // Should this be cached? @SeventhM
    }

    fun eraNumber(ruleset: Ruleset, startingEra: String): Int {
        val era: Era? = era(ruleset)
        if (era != null)
            return era.eraNumber
        // This defaults to -1 following the practice in com.unciv.models.ruleset.tech.Era.
        // If desired, we could instead return ruleset.eras[startingEra]!!.eraNumber
        if (false)
            return ruleset.eras[startingEra]!!.eraNumber
        return -1
    }

    fun techColumn(ruleset: Ruleset): TechColumn? =
            requiredTechnologies(ruleset).map{ it.column }.maxByOrNull{ if(it == null) -1 else it.columnNumber }

    fun hasUnique(uniqueTemplate: String, stateForConditionals: StateForConditionals? = null) =
        getMatchingUniques(uniqueTemplate, stateForConditionals).any()

    fun hasUnique(uniqueType: UniqueType, stateForConditionals: StateForConditionals? = null) =
        getMatchingUniques(uniqueType.placeholderText, stateForConditionals).any()

    fun availabilityUniques(): Sequence<Unique> = getMatchingUniques(UniqueType.OnlyAvailableWhen, StateForConditionals.IgnoreConditionals)

    fun techsRequiredByUniques(): Sequence<String> {
        return availabilityUniques()
                // Currently an OnlyAvailableWhen can have multiple conditionals, implicitly a conjunction.
                // Therefore, if any of its several conditionals is a ConditionalTech, then that tech is required.
                .flatMap{ it.conditionals }
                .filter{ it.isOfType(UniqueType.ConditionalTech) }
                .map{ it.params[0] }
    }

    fun legacyRequiredTechs(): Sequence<String> = sequenceOf()

    // fun requiredTechs(): Sequence<String> = legacyRequiredTechs() + techsRequiredByUniques()

    fun requiredTechnologies(ruleset: Ruleset): Sequence<Technology> =
        requiredTechs().map{ ruleset.technologies[it]!! }

    fun era(ruleset: Ruleset): Era? =
            requiredTechnologies(ruleset).map{ it.era() }.map{ ruleset.eras[it]!! }.maxByOrNull{ it.eraNumber }
            // This will return null only if requiredTechnologies() is empty.

    fun techColumn(ruleset: Ruleset): TechColumn? =
            requiredTechnologies(ruleset).map{ it.column }.filterNotNull().maxByOrNull{ it.columnNumber }
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

    override fun write(json: Json) {
        json.writeFields(this);
    }
    /** Custom Json formatter for a [BaseUnit].
     *  This is needed for backwards compatibility.
     *  This accepts the field "requiredTech" and converts it to a Unique.
     */
    override fun read(json: Json, jsonData: JsonValue) {
        json.readFields(this, jsonData)
        // Faced with a field named "requiredTech" which the BaseUnit class does not have, readFields ignores it.
        val requiredTech: String = jsonData.getString("requiredTech", "no requiredTech")
        // Minimum possible intervention: at the moment of JSON parsing, we insert a string exactly as it could have appeared in the JSON.
        // This will later be parsed out into a OnlyAvailableWhen normally.
        // The RulesetValidator will also apply normally as if the Unique had been specified in the JSON in the first place.
        if (requiredTech != "no requiredTech")
            uniques.add("Only available <after discovering [$requiredTech]>")
        // TODO: give obsoleteTech and requiredResource and upgradesTo the same treatment
        val obsoleteTech: String = jsonData.getString("obsoleteTech", "no obsoleteTech")
        if (obsoleteTech != "no obsoleteTech")
            uniques.add("Only available <before discovering [$obsoleteTech]>")
        val requiredResource: String = jsonData.getString("requiredResource", "no requiredResource")
        // if (requiredResource != "no requiredResource")
            // uniques.add("Only available with [$requiredResource]")
            // What requiredResource actually means is the same as the Consumes unique.
        //     uniques.add("Consumes [1] [$requiredResource]")
        // Unfortunately, upgradesTo isn't so easy, it will probably need a new Unique.
        val upgradesTo: String = jsonData.getString("upgradesTo", "no upgradesTo")
        if (upgradesTo != "no upgradesTo")
            uniques.add("Can upgrade to [$upgradesTo]")
    }
}
