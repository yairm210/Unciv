package com.unciv.models.ruleset.unique

import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.GlobalUniques
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.models.stats.Stats
import com.unciv.models.translations.getModifiers
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import com.unciv.models.translations.removeConditionals
import java.util.EnumMap
import kotlin.math.max


class Unique(val text: String, val sourceObjectType: UniqueTarget? = null, val sourceObjectName: String? = null) {
    /** This is so the heavy regex-based parsing is only activated once per unique, instead of every time it's called
     *  - for instance, in the city screen, we call every tile unique for every tile, which can lead to ANRs */
    val placeholderText = text.getPlaceholderText()
    /** Does not include conditional params */
    val params = text.getPlaceholderParameters()
    val type = UniqueType.uniqueTypeMap[placeholderText]

    val stats: Stats by lazy {
        val firstStatParam = params.firstOrNull { Stats.isStats(it) }
        if (firstStatParam == null) Stats() // So badly-defined stats don't crash the entire game
        else Stats.parse(firstStatParam)
    }
    val modifiers: List<Unique> = text.getModifiers()
    val modifiersMap: Map<UniqueType, List<Unique>> = modifiers.filterNot { it.type == null }.groupBy { it.type!! }

    val isTimedTriggerable = hasModifier(UniqueType.ConditionalTimedUnique)

    val isTriggerable = type != null && (
        type.targetTypes.contains(UniqueTarget.Triggerable)
            || type.targetTypes.contains(UniqueTarget.UnitTriggerable)
            || isTimedTriggerable
        )

    /** Includes conditional params */
    val allParams = params + modifiers.flatMap { it.params }

    val isLocalEffect = params.contains("in this city") || hasModifier(UniqueType.ConditionalInThisCity)

    fun hasFlag(flag: UniqueFlag) = type != null && type.flags.contains(flag)
    fun isHiddenToUsers() = hasFlag(UniqueFlag.HiddenToUsers) || hasModifier(UniqueType.ModifierHiddenFromUsers)

    fun getModifiers(type: UniqueType) = modifiersMap[type] ?: emptyList()
    fun hasModifier(type: UniqueType) = modifiersMap.containsKey(type)
    fun isModifiedByGameSpeed() = hasModifier(UniqueType.ModifiedByGameSpeed)
    fun isModifiedByGameProgress() = hasModifier(UniqueType.ModifiedByGameProgress)
    fun getGameProgressModifier(civ: Civilization): Float {
        //According to: https://www.reddit.com/r/civ/comments/gvx44v/comment/fsrifc2/
        var modifier = 1f
        val ruleset = civ.gameInfo.ruleset
        val techComplete = if (ruleset.technologies.isNotEmpty()) 
            civ.tech.researchedTechnologies.size.toFloat() / ruleset.technologies.size else 0f
        val policyComplete = if (ruleset.policies.isNotEmpty()) 
            civ.policies.adoptedPolicies.size.toFloat() / ruleset.policies.size else 0f
        val gameProgess = max(techComplete, policyComplete)   
        for (unique in getModifiers(UniqueType.ModifiedByGameProgress))
            modifier *= 1 + (unique.params[0].toFloat()/100 - 1) * gameProgess
            //Mod creators likely expect this to stack multiplicatively, otherwise they'd use a single modifier 
        return modifier
    }
    fun hasTriggerConditional(): Boolean {
        if (modifiers.none()) return false
        return modifiers.any { conditional ->
            conditional.type?.targetTypes?.any {
                it.canAcceptUniqueTarget(UniqueTarget.TriggerCondition) || it.canAcceptUniqueTarget(UniqueTarget.UnitActionModifier)
            }
            ?: false
        }
    }

    fun conditionalsApply(civInfo: Civilization? = null, city: City? = null): Boolean {
        return conditionalsApply(StateForConditionals(civInfo, city))
    }

    fun conditionalsApply(state: StateForConditionals): Boolean {
        if (state.ignoreConditionals) return true
        // Always allow Timed conditional uniques. They are managed elsewhere
        if (isTimedTriggerable) return true
        if (modifiers.isEmpty()) return true
        for (modifier in modifiers) {
            if (!Conditionals.conditionalApplies(this, modifier, state)) return false
        }
        return true
    }

    private fun getUniqueMultiplier(stateForConditionals: StateForConditionals): Int {
        
        var amount = 1
        
        val forEveryModifiers = getModifiers(UniqueType.ForEveryCountable)
        for (conditional in forEveryModifiers) { // multiple multipliers DO multiply.
            val multiplier = Countables.getCountableAmount(conditional.params[0], stateForConditionals)
                ?: 0 // If the countable is invalid, ignore this unique entirely
            amount *= multiplier
        }
        
        val forEveryAmountModifiers = getModifiers(UniqueType.ForEveryAmountCountable)
        for (conditional in forEveryAmountModifiers) { // multiple multipliers DO multiply.
            val multiplier = Countables.getCountableAmount(conditional.params[1], stateForConditionals)
            val perEvery = conditional.params[0].toInt()
            if (multiplier != null) amount *= multiplier / perEvery
        }

        if (stateForConditionals.relevantTile != null){
            val forEveryAdjacentTileModifiers = getModifiers(UniqueType.ForEveryAdjacentTile)
            for (conditional in forEveryAdjacentTileModifiers) {
                val multiplier = stateForConditionals.relevantTile!!.neighbors
                    .count { it.matchesFilter(conditional.params[0]) }
                amount *= multiplier
            }
        }

        return amount.coerceAtLeast(0)
    }

    /** Multiplies the unique according to the multiplication conditionals */
    fun getMultiplied(stateForConditionals: StateForConditionals): Sequence<Unique> {
        val multiplier = getUniqueMultiplier(stateForConditionals)
        return EndlessSequenceOf(this).take(multiplier)
    }

    private class EndlessSequenceOf<T>(private val value: T) : Sequence<T> {
        override fun iterator(): Iterator<T> = object : Iterator<T> {
            override fun next() = value
            override fun hasNext() = true
        }
    }

    fun getDeprecationAnnotation(): Deprecated? = type?.getDeprecationAnnotation()

    fun getSourceNameForUser(): String {
        return when (sourceObjectType) {
            null -> ""
            UniqueTarget.Global -> GlobalUniques.getUniqueSourceDescription(this)
            UniqueTarget.Wonder -> "Wonders"
            UniqueTarget.Building -> "Buildings"
            UniqueTarget.Policy -> "Policies"
            UniqueTarget.CityState -> Constants.cityStates
            else -> sourceObjectType.name
        }
    }

    fun getReplacementText(ruleset: Ruleset): String {
        val deprecationAnnotation = getDeprecationAnnotation() ?: return ""
        val replacementUniqueText = deprecationAnnotation.replaceWith.expression
        val deprecatedUniquePlaceholders = type!!.text.getPlaceholderParameters()
        val possibleUniques = replacementUniqueText.split(Constants.uniqueOrDelimiter)

        // Here, for once, we DO want the conditional placeholder parameters together with the regular ones,
        //  so we cheat the conditional detector by removing the '<'
        //  note this is only done for the replacement, not the deprecated unique, thus parameters of
        //  conditionals on the deprecated unique are ignored

        val finalPossibleUniques = ArrayList<String>()

        for (possibleUnique in possibleUniques) {
            var resultingUnique = possibleUnique
            for (parameter in possibleUnique.replace('<', ' ').getPlaceholderParameters()) {
                val parameterHasSign = parameter.startsWith('-') || parameter.startsWith('+')
                val parameterUnsigned = if (parameterHasSign) parameter.drop(1) else parameter
                val parameterNumberInDeprecatedUnique = deprecatedUniquePlaceholders.indexOf(parameterUnsigned)
                if (parameterNumberInDeprecatedUnique !in params.indices) continue
                val positionInDeprecatedUnique = type.text.indexOf("[$parameterUnsigned]")
                var replacementText = params[parameterNumberInDeprecatedUnique]
                if (UniqueParameterType.Number in type.parameterTypeMap[parameterNumberInDeprecatedUnique]) {
                    // The following looks for a sign just before [amount] and detects replacing "-[-33]" with "[+33]" and similar situations
                    val deprecatedHadPlusSign = positionInDeprecatedUnique > 0 && type.text[positionInDeprecatedUnique - 1] == '+'
                    val deprecatedHadMinusSign = positionInDeprecatedUnique > 0 && type.text[positionInDeprecatedUnique - 1] == '-'
                    val deprecatedHadSign = deprecatedHadPlusSign || deprecatedHadMinusSign
                    val positionInNewUnique = possibleUnique.indexOf("[$parameter]")
                    val newHasMinusSign = positionInNewUnique > 0 && possibleUnique[positionInNewUnique - 1] == '-'
                    val replacementHasMinusSign = replacementText.startsWith('-')
                    val replacementHasPlusSign = replacementText.startsWith('+')
                    val replacementIsSigned = replacementHasPlusSign || replacementHasMinusSign
                    val replacementTextUnsigned = if (replacementIsSigned) replacementText.drop(1) else replacementText
                    val replacementShouldBeNegative = if (deprecatedHadMinusSign == newHasMinusSign) replacementHasMinusSign else !replacementHasMinusSign
                    val replacementShouldBeSigned = deprecatedHadSign && !newHasMinusSign || parameterHasSign
                    replacementText = when {
                        !(deprecatedHadSign || newHasMinusSign || replacementIsSigned) -> replacementText
                        replacementShouldBeNegative -> "-$replacementTextUnsigned"
                        replacementShouldBeSigned -> "+$replacementTextUnsigned"
                        else -> replacementTextUnsigned
                    }
                }
                resultingUnique = resultingUnique.replace("[$parameter]", "[$replacementText]")
            }
            finalPossibleUniques += resultingUnique
        }
        if (finalPossibleUniques.size == 1) return finalPossibleUniques.first()

        // filter out possible replacements that are obviously wrong
        val uniquesWithNoErrors = finalPossibleUniques.filter {
            val unique = Unique(it)
            val errors = UniqueValidator(ruleset).checkUnique(
                unique, true, null,
                true
            )
            errors.isEmpty()
        }
        if (uniquesWithNoErrors.size == 1) return uniquesWithNoErrors.first()

        val uniquesToUnify = uniquesWithNoErrors.ifEmpty { possibleUniques }
        return uniquesToUnify.joinToString("\", \"")
    }


    override fun toString() = if (type == null) "\"$text\"" else "$type (\"$text\")"
    fun getDisplayText(): String = if (modifiers.none { it.isHiddenToUsers() }) text
        else text.removeConditionals() + " " + modifiers.filter { !it.isHiddenToUsers() }.joinToString(" ") { "<${it.text}>" }
}

/** Used to cache results of getMatchingUniques
 * Must only be used when we're sure the matching uniques will not change in the meantime */
class LocalUniqueCache(val cache: Boolean = true) {
    // This stores sequences *that iterate directly on a list* - that is, pre-resolved
    private val keyToUniques = HashMap<String, Sequence<Unique>>()

    fun forCityGetMatchingUniques(
        city: City,
        uniqueType: UniqueType,
        stateForConditionals: StateForConditionals = city.state
    ): Sequence<Unique> {
        // City uniques are a combination of *global civ* uniques plus *city relevant* uniques (see City.getMatchingUniques())
        // We can cache the civ uniques separately, so if we have several cities using the same cache,
        //   we can cache the list of *civ uniques* to reuse between cities.

        val citySpecificUniques = get(
            "city-${city.id}-${uniqueType.name}",
            city.getLocalMatchingUniques(uniqueType, StateForConditionals.IgnoreConditionals)
        ).filter { it.conditionalsApply(stateForConditionals) }

        val civUniques = forCivGetMatchingUniques(city.civ, uniqueType, stateForConditionals)

        return citySpecificUniques + civUniques
    }

    fun forCivGetMatchingUniques(
        civ: Civilization,
        uniqueType: UniqueType,
        stateForConditionals: StateForConditionals = civ.state
    ): Sequence<Unique> {
        val sequence = civ.getMatchingUniques(uniqueType, StateForConditionals.IgnoreConditionals)
        // The uniques CACHED are ALL civ uniques, regardless of conditional matching.
        // The uniques RETURNED are uniques AFTER conditional matching.
        // This allows reuse of the cached values, between runs with different conditionals -
        //   for example, iterate on all tiles and get StatPercentForObject uniques relevant for each tile,
        //   each tile will have different conditional state, but they will all reuse the same list of uniques for the civ
        return get(
            "civ-${civ.civName}-${uniqueType.name}",
            sequence
        ).filter { it.conditionalsApply(stateForConditionals) }
    }

    /** Get cached results as a sequence */
    private fun get(key: String, sequence: Sequence<Unique>): Sequence<Unique> {
        if (!cache) return sequence
        val valueInMap = keyToUniques[key]
        if (valueInMap != null) return valueInMap
        // Iterate the sequence, save actual results as a list, as return a sequence to that
        val results = sequence.toList().asSequence()
        keyToUniques[key] = results
        return results
    }
}

open class UniqueMap() {
    protected val innerUniqueMap =  HashMap<String, ArrayList<Unique>>()

    // *shares* the list of uniques with the other map, to save on memory and allocations
    // This is a memory/speed tradeoff, since there are *600 unique types*,
    // 750 including deprecated, and EnumMap creates a N-sized array where N is the number of objects in the enum
    val typedUniqueMap = EnumMap<UniqueType, ArrayList<Unique>>(UniqueType::class.java)

    constructor(uniques: Sequence<Unique>) : this() {
        addUniques(uniques.asIterable())
    }

    fun isEmpty(): Boolean = innerUniqueMap.isEmpty()

    /** Adds one [unique] unless it has a ConditionalTimedUnique conditional */
    open fun addUnique(unique: Unique) {
        val existingArrayList = innerUniqueMap[unique.placeholderText]
        if (existingArrayList != null) existingArrayList.add(unique)
        else innerUniqueMap[unique.placeholderText] = arrayListOf(unique)
        
        if (unique.type == null) return
        if (typedUniqueMap[unique.type] != null) return
        typedUniqueMap[unique.type] = innerUniqueMap[unique.placeholderText]
    }

    /** Calls [addUnique] on each item from [uniques] */
    fun addUniques(uniques: Iterable<Unique>) {
        for (unique in uniques) addUnique(unique)
    }

    fun removeUnique(unique: Unique) {
        val existingArrayList = innerUniqueMap[unique.placeholderText]
        existingArrayList?.remove(unique)
    }
    
    fun clear() {
        innerUniqueMap.clear()
        typedUniqueMap.clear()
    }
    
    // Pure functions
    
    fun hasUnique(uniqueType: UniqueType, state: StateForConditionals = StateForConditionals.EmptyState) =
        getUniques(uniqueType).any { it.conditionalsApply(state) && !it.isTimedTriggerable }

    fun hasUnique(uniqueTag: String, state: StateForConditionals = StateForConditionals.EmptyState) =
        getUniques(uniqueTag).any { it.conditionalsApply(state) && !it.isTimedTriggerable }
    
    fun hasTagUnique(tagUnique: String) =
        innerUniqueMap.containsKey(tagUnique)

    // 160ms vs 1000-1250ms/30s
    fun getUniques(uniqueType: UniqueType) = typedUniqueMap[uniqueType]
        ?.asSequence()
        ?: emptySequence()

    fun getUniques(uniqueTag: String) = innerUniqueMap[uniqueTag]
        ?.asSequence()
        ?: emptySequence()

    fun getMatchingUniques(uniqueType: UniqueType, state: StateForConditionals = StateForConditionals.EmptyState) = 
        getUniques(uniqueType)
            // Same as .filter | .flatMap, but more cpu/mem performant (7.7 GB vs ?? for test)
            .flatMap {
                when {
                    it.isTimedTriggerable -> emptySequence()
                    !it.conditionalsApply(state) -> emptySequence()
                    else -> it.getMultiplied(state)
                }
            }

    fun getMatchingUniques(uniqueTag: String, state: StateForConditionals = StateForConditionals.EmptyState) =
        getUniques(uniqueTag)
            // Same as .filter | .flatMap, but more cpu/mem performant (7.7 GB vs ?? for test)
            .flatMap {
                when {
                    it.isTimedTriggerable -> emptySequence()
                    !it.conditionalsApply(state) -> emptySequence()
                    else -> it.getMultiplied(state)
                }
            }
    
    fun hasMatchingUnique(uniqueType: UniqueType, state: StateForConditionals = StateForConditionals.EmptyState) = 
        getUniques(uniqueType).any { it.conditionalsApply(state) }

    fun hasMatchingUnique(uniqueTag: String, state: StateForConditionals = StateForConditionals.EmptyState) =
        getUniques(uniqueTag)
            .any { it.conditionalsApply(state) }

    fun getAllUniques() = innerUniqueMap.values.asSequence().flatten()

    fun getTriggeredUniques(trigger: UniqueType, stateForConditionals: StateForConditionals,
                            triggerFilter: (Unique) -> Boolean = { true }): Sequence<Unique> {
        return getAllUniques().filter { unique ->
            unique.getModifiers(trigger).any(triggerFilter) && unique.conditionalsApply(stateForConditionals)
        }.flatMap { it.getMultiplied(stateForConditionals) }
    }
    
    companion object{
        val EMPTY = UniqueMap()
    }
}

class TemporaryUnique() : IsPartOfGameInfoSerialization {

    constructor(uniqueObject: Unique, turns: Int) : this() {
        val turnsText = uniqueObject.getModifiers(UniqueType.ConditionalTimedUnique).first().text
        unique = uniqueObject.text.replaceFirst("<$turnsText>", "").trim()
        sourceObjectType = uniqueObject.sourceObjectType
        sourceObjectName = uniqueObject.sourceObjectName
        turnsLeft = turns
    }

    var unique: String = ""

    private var sourceObjectType: UniqueTarget? = null
    private var sourceObjectName: String? = null

    @delegate:Transient
    val uniqueObject: Unique by lazy { Unique(unique, sourceObjectType, sourceObjectName) }

    var turnsLeft: Int = 0
}

fun ArrayList<TemporaryUnique>.endTurn() {
        for (unique in this) {
            if (unique.turnsLeft >= 0)
                unique.turnsLeft -= 1
        }
        removeAll { it.turnsLeft == 0 }
    }

fun ArrayList<TemporaryUnique>.getMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals): Sequence<Unique> {
        return this.asSequence()
            .map { it.uniqueObject }
            .filter { it.type == uniqueType && it.conditionalsApply(stateForConditionals) }
    }
