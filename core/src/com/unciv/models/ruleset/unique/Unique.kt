package com.unciv.models.ruleset.unique

import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.Counter
import com.unciv.models.ruleset.GlobalUniques
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.models.stats.Stats
import com.unciv.models.translations.getModifiers
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import com.unciv.models.translations.removeConditionals
import yairm210.purity.annotations.Readonly
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


    @Readonly fun getModifiers(type: UniqueType) = modifiersMap[type] ?: emptyList()
    @Readonly fun hasModifier(type: UniqueType) = modifiersMap.containsKey(type)
    @Readonly fun isModifiedByGameSpeed() = hasModifier(UniqueType.ModifiedByGameSpeed)
    @Readonly fun isModifiedByGameProgress() = hasModifier(UniqueType.ModifiedByGameProgress)
    @Readonly
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
    @Readonly
    fun hasTriggerConditional(): Boolean {
        if (modifiers.none()) return false
        return modifiers.any { conditional ->
            conditional.type?.targetTypes?.any {
                it.canAcceptUniqueTarget(UniqueTarget.TriggerCondition)
                        || it.canAcceptUniqueTarget(UniqueTarget.UnitActionModifier)
                        || it.canAcceptUniqueTarget(UniqueTarget.UnitTriggerCondition)
            }
            ?: false
        }
    }

    fun conditionalsApply(civInfo: Civilization? = null, city: City? = null): Boolean {
        return conditionalsApply(GameContext(civInfo, city))
    }

    @Readonly
    fun conditionalsApply(state: GameContext): Boolean {
        if (state.ignoreConditionals) return true
        // Always allow Timed conditional uniques. They are managed elsewhere
        if (isTimedTriggerable) return true
        if (modifiers.isEmpty()) return true
        for (modifier in modifiers) {
            if (!Conditionals.conditionalApplies(this, modifier, state)) return false
        }
        return true
    }

    @Readonly
    private fun getUniqueMultiplier(gameContext: GameContext): Int {
        if (gameContext == GameContext.IgnoreMultiplicationForCaching)
            return 1
        
        var amount = 1
        
        val forEveryModifiers = getModifiers(UniqueType.ForEveryCountable)
        for (conditional in forEveryModifiers) { // multiple multipliers DO multiply.
            val multiplier = Countables.getCountableAmount(conditional.params[0], gameContext)
                ?: 0 // If the countable is invalid, ignore this unique entirely
            amount *= multiplier
        }
        
        val forEveryAmountModifiers = getModifiers(UniqueType.ForEveryAmountCountable)
        for (conditional in forEveryAmountModifiers) { // multiple multipliers DO multiply.
            val multiplier = Countables.getCountableAmount(conditional.params[1], gameContext)
                ?: 0 // If the countable is invalid, ignore this unique entirely
            val perEvery = conditional.params[0].toInt()
            amount *= multiplier / perEvery
        }

        if (gameContext.relevantTile != null){
            val forEveryAdjacentTileModifiers = getModifiers(UniqueType.ForEveryAdjacentTile)
            for (conditional in forEveryAdjacentTileModifiers) {
                val multiplier = gameContext.relevantTile!!.neighbors
                    .count { it.matchesFilter(conditional.params[0]) }
                amount *= multiplier
            }
        }

        return amount.coerceAtLeast(0)
    }

    /** Multiplies the unique according to the multiplication conditionals */
    @Readonly
    fun getMultiplied(gameContext: GameContext): Sequence<Unique> {
        val multiplier = getUniqueMultiplier(gameContext)
        return EndlessSequenceOf(this).take(multiplier)
    }

    private class EndlessSequenceOf<T>(private val value: T) : Sequence<T> {
        override fun iterator(): Iterator<T> = object : Iterator<T> {
            override fun next() = value
            override fun hasNext() = true
        }
    }

    fun getDeprecationAnnotation(): Deprecated? = type?.getDeprecationAnnotation()

    @Readonly
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
    
    /** Zero-based, so n=0 returns the first */
    private fun getNthIndex(string:String, list:List<String>, n: Int): Int {
        var count = 0
        for (i in list.indices) {
            if (list[i] == string) {
                if (count == n) return i
                count += 1
            }
        }
        return -1 // Not found
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
            
            val timesParameterWasSeen = Counter<String>()
            for (parameter in possibleUnique.replace('<', ' ').getPlaceholderParameters()) {
                val parameterHasSign = parameter.startsWith('-') || parameter.startsWith('+')
                val parameterUnsigned = if (parameterHasSign) parameter.drop(1) else parameter
                val timesSeen = timesParameterWasSeen[parameterUnsigned]
                
                // When deprecating a unique like "from [amount] to [amount]", we want to replace the first [amount] 
                //  in the 'replaceWith' with the first [amount] in the unique, and the second with the second, etc.
                val parameterNumberInDeprecatedUnique = getNthIndex(parameterUnsigned, deprecatedUniquePlaceholders, timesSeen)
                
                if (parameterNumberInDeprecatedUnique !in params.indices) continue
                timesParameterWasSeen.add(parameterUnsigned, 1)
                
                val positionInDeprecatedUnique =  type.text.indexOf("[$parameterUnsigned]")
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
                resultingUnique = resultingUnique.replaceFirst("[$parameter]", "[$replacementText]")
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
