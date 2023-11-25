package com.unciv.models.ruleset.unique

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.models.stats.INamed
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tech.Era
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology

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

    fun requiredTechs(): Sequence<String> {
        val uniquesForWhenThisIsAvailable: Sequence<Unique> = getMatchingUniques(UniqueType.OnlyAvailableWhen, StateForConditionals.IgnoreConditionals)
        if (uniquesForWhenThisIsAvailable.none() && uniques.any{ it.contains("Only available <after discovering [") })
            throw Exception(uniqueObjects.map{ it.toString() }.joinToString(" ") + uniqueObjects.map{ it.type.toString() }.joinToString(" ") + uniqueObjects.flatMap{ it.conditionals }.map{ it.toString() }.joinToString(" ") + "uniquesForWhenThisIsAvailable" + uniquesForWhenThisIsAvailable.count().toString() + uniquesForWhenThisIsAvailable.map{ it.toString() }.joinToString(" "))
        val conditionalsForWhenThisIsAvailable: Sequence<Unique> = uniquesForWhenThisIsAvailable.flatMap{ it.conditionals }
        if (uniquesForWhenThisIsAvailable.none() && uniques.any{ it.contains("Only available <after discovering [") })
            throw Exception(uniqueObjects.map{ it.toString() }.joinToString(" ") + uniqueObjects.map{ it.type.toString() }.joinToString(" ") + uniqueObjects.flatMap{ it.conditionals }.map{ it.toString() }.joinToString(" ") + "conditionalsForWhenThisIsAvailable" + conditionalsForWhenThisIsAvailable.count().toString() + conditionalsForWhenThisIsAvailable.map{ it.toString() }.joinToString(" "))
        val techRequiringConditionalsForWhenThisIsAvailable: Sequence<Unique> = conditionalsForWhenThisIsAvailable.filter{ it.type == UniqueType.ConditionalTech }
        if (uniquesForWhenThisIsAvailable.none() && uniques.any{ it.contains("Only available <after discovering [") })
            throw Exception(uniqueObjects.map{ it.toString() }.joinToString(" ") + uniqueObjects.map{ it.type.toString() }.joinToString(" ") + uniqueObjects.flatMap{ it.conditionals }.map{ it.toString() }.joinToString(" ") + "techRequiringConditionalsForWhenThisIsAvailable" + techRequiringConditionalsForWhenThisIsAvailable.count().toString() + techRequiringConditionalsForWhenThisIsAvailable.map{ it.toString() }.joinToString(" "))
        return techRequiringConditionalsForWhenThisIsAvailable.map{ it.params[0] }
        // Should this be cached? @SeventhM
    }

    fun obsoletingTechs(): Sequence<String> {
        val uniquesForWhenThisIsAvailable: Sequence<Unique> = getMatchingUniques(UniqueType.OnlyAvailableWhen, StateForConditionals.IgnoreConditionals)
        if (uniquesForWhenThisIsAvailable.none() && uniques.any{ it.contains("Only available <before discovering [") })
            throw Exception(uniqueObjects.map{ it.toString() }.joinToString(" ") + uniqueObjects.map{ it.type.toString() }.joinToString(" ") + uniqueObjects.flatMap{ it.conditionals }.map{ it.toString() }.joinToString(" ") + "uniquesForWhenThisIsAvailable" + uniquesForWhenThisIsAvailable.count().toString() + uniquesForWhenThisIsAvailable.map{ it.toString() }.joinToString(" "))
        val conditionalsForWhenThisIsAvailable: Sequence<Unique> = uniquesForWhenThisIsAvailable.flatMap{ it.conditionals }
        if (uniquesForWhenThisIsAvailable.none() && uniques.any{ it.contains("Only available <before discovering [") })
            throw Exception(uniqueObjects.map{ it.toString() }.joinToString(" ") + uniqueObjects.map{ it.type.toString() }.joinToString(" ") + uniqueObjects.flatMap{ it.conditionals }.map{ it.toString() }.joinToString(" ") + "conditionalsForWhenThisIsAvailable" + conditionalsForWhenThisIsAvailable.count().toString() + conditionalsForWhenThisIsAvailable.map{ it.toString() }.joinToString(" "))
        val techRequiringConditionalsForWhenThisIsAvailable: Sequence<Unique> = conditionalsForWhenThisIsAvailable.filter{ it.type == UniqueType.ConditionalNoTech }
        if (uniquesForWhenThisIsAvailable.none() && uniques.any{ it.contains("Only available <before discovering [") })
            throw Exception(uniqueObjects.map{ it.toString() }.joinToString(" ") + uniqueObjects.map{ it.type.toString() }.joinToString(" ") + uniqueObjects.flatMap{ it.conditionals }.map{ it.toString() }.joinToString(" ") + "techRequiringConditionalsForWhenThisIsAvailable" + techRequiringConditionalsForWhenThisIsAvailable.count().toString() + techRequiringConditionalsForWhenThisIsAvailable.map{ it.toString() }.joinToString(" "))
        return techRequiringConditionalsForWhenThisIsAvailable.map{ it.params[0] }
        // Should this be cached? @SeventhM
    }

    fun requiredTechnologies(ruleset: Ruleset): HashSet<Technology> =
        requiredTechs().mapTo(HashSet<Technology>()){ requiredTech -> ruleset.technologies[requiredTech]!! }

    // This is the guts of the logic that used to live in CityStateFunctions.kt.
    fun era(ruleset: Ruleset): Era? =
            requiredTechnologies(ruleset).map{ it.era() }.map{ ruleset.eras[it]!! }.maxByOrNull{ it.eraNumber }

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
