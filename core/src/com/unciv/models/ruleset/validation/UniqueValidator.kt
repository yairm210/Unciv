package com.unciv.models.ruleset.validation

import com.unciv.Constants
import com.unciv.logic.MultiFilter
import com.unciv.logic.map.mapunit.MapUnitCache
import com.unciv.models.ruleset.IRulesetObject
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueComplianceError
import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unique.expressions.Expressions

class UniqueValidator(val ruleset: Ruleset) {

    /** Used to determine if certain uniques are used for filtering */
    private val allNonTypedUniques = HashSet<String>()
    /** Used to determine if certain uniques are used for filtering */
    private val allUniqueParameters = HashSet<String>()

    private fun addToHashsets(uniqueHolder: IHasUniques) {
        for (unique in uniqueHolder.uniqueObjects) {
            if (unique.type == null) allNonTypedUniques.add(unique.text)
            else allUniqueParameters.addAll(
                unique.allParams.asSequence().flatMap { MultiFilter.getAllSingleFilters(it) }
            )
        }
    }

    fun populateFilteringUniqueHashsets() {
        ruleset.allRulesetObjects().forEach { addToHashsets(it) }
    }

    fun checkUniques(
        uniqueContainer: IHasUniques,
        lines: RulesetErrorList,
        reportRulesetSpecificErrors: Boolean,
        tryFixUnknownUniques: Boolean
    ) {
        for (unique in uniqueContainer.uniqueObjects) {
            val errors = checkUnique(
                unique,
                tryFixUnknownUniques,
                uniqueContainer,
                reportRulesetSpecificErrors
            )
            lines.addAll(errors)
        }
    }

    private val performanceHeavyConditionals = setOf(UniqueType.ConditionalNeighborTiles, UniqueType.ConditionalAdjacentTo,
        UniqueType.ConditionalNotAdjacentTo
    )

    fun checkUnique(
        unique: Unique,
        tryFixUnknownUniques: Boolean,
        uniqueContainer: IHasUniques?,
        reportRulesetSpecificErrors: Boolean
    ): RulesetErrorList {
        val prefix by lazy { getUniqueContainerPrefix(uniqueContainer) + "\"${unique.text}\"" }
        if (unique.type == null) return checkUntypedUnique(unique, tryFixUnknownUniques, uniqueContainer, prefix, reportRulesetSpecificErrors)

        val rulesetErrors = RulesetErrorList(ruleset)

        if (uniqueContainer != null &&
            !(unique.type.canAcceptUniqueTarget(uniqueContainer.getUniqueTarget()) ||
                    // "for X turns" effectively turns a global unique into a trigger
                    unique.hasModifier(UniqueType.ConditionalTimedUnique)
                        && uniqueContainer.getUniqueTarget().canAcceptUniqueTarget(UniqueTarget.Triggerable)
                    ))
            rulesetErrors.add("$prefix is not allowed on its target type", RulesetErrorSeverity.Warning, uniqueContainer, unique)

        val typeComplianceErrors = getComplianceErrors(unique)
        for (complianceError in typeComplianceErrors) {
            if (!reportRulesetSpecificErrors && complianceError.errorSeverity == UniqueType.UniqueParameterErrorSeverity.RulesetSpecific)
                continue

            rulesetErrors.add(
                "$prefix contains parameter \"${complianceError.parameterName}\", $whichDoesNotFitParameterType" +
                " ${complianceError.acceptableParameterTypes.joinToString(" or ") { it.parameterName }} !",
                complianceError.errorSeverity.getRulesetErrorSeverity(), uniqueContainer, unique
            )

            addExpressionParseErrors(complianceError, rulesetErrors, uniqueContainer, unique)
        }

        for (conditional in unique.modifiers) {
            addConditionalErrors(conditional, rulesetErrors, prefix, unique, uniqueContainer, reportRulesetSpecificErrors)
        }

        val conditionals = unique.modifiers.filter { it.type?.canAcceptUniqueTarget(UniqueTarget.Conditional) == true }
        if (conditionals.size > 1){
            val lastCheapConditional = conditionals.lastOrNull { it.type !in performanceHeavyConditionals }
            val firstExpensiveConditional = conditionals.firstOrNull { it.type in performanceHeavyConditionals }
            if (lastCheapConditional != null && firstExpensiveConditional != null){
                if (conditionals.indexOf(lastCheapConditional) > conditionals.indexOf(firstExpensiveConditional))
                    rulesetErrors.add("$prefix contains multiple conditionals," +
                            " of which \"${firstExpensiveConditional.text}\" is more expensive to calculate than \"${lastCheapConditional.text}\". " +
                            "For performance, consider switching their locations.", RulesetErrorSeverity.WarningOptionsOnly, uniqueContainer, unique)
            }
        }


        if (unique.type in MapUnitCache.UnitMovementUniques
                && unique.modifiers.any { it.type != UniqueType.ConditionalOurUnit || it.params[0] !in Constants.all }
            )
            // (Stay silent if the only conditional is `<for [All] units>` - as in G&K Denmark)
            // Not necessarily even a problem, but yes something mod maker should be aware of
            rulesetErrors.add(
                "$prefix contains a conditional on a unit movement unique. " +
                "Due to performance considerations, this unique is cached on the unit," +
                " and the conditional may not always limit the unique correctly.",
                RulesetErrorSeverity.OK, uniqueContainer, unique
            )

        if (reportRulesetSpecificErrors)
        // If we don't filter these messages will be listed twice as this function is called twice on most objects
        // The tests are RulesetInvariant in nature, but RulesetSpecific is called for _all_ objects, invariant is not.
            addDeprecationAnnotationErrors(unique, prefix, rulesetErrors, uniqueContainer)

        return rulesetErrors
    }

    private fun addExpressionParseErrors(
        complianceError: UniqueComplianceError,
        rulesetErrors: RulesetErrorList,
        uniqueContainer: IHasUniques?,
        unique: Unique
    ) {
        if (!complianceError.acceptableParameterTypes.contains(UniqueParameterType.Countable)) return

        val parseError = Expressions.getParsingError(complianceError.parameterName)
        if (parseError != null) {
            val marker = "HERE➡"
            val errorLocation = parseError.position
            val parameterWithErrorLocationMarked =
                complianceError.parameterName.substring(0, errorLocation) + marker +
                        complianceError.parameterName.substring(errorLocation)
            val text = "\"${complianceError.parameterName}\" could not be parsed as an expression due to:" +
                    " ${parseError.message}. \n$parameterWithErrorLocationMarked"
            rulesetErrors.add(text, RulesetErrorSeverity.WarningOptionsOnly, uniqueContainer, unique)
            return
        }

        val countableErrors = Expressions.getCountableErrors(complianceError.parameterName, ruleset)
        if (countableErrors.isNotEmpty()) {
            val text = "\"${complianceError.parameterName}\" was parsed as an expression, but has the following errors with this ruleset:" +
                    " ${countableErrors.joinToString(", ")}"
            rulesetErrors.add(text, RulesetErrorSeverity.WarningOptionsOnly, uniqueContainer, unique)
        }
    }

    private val resourceUniques = setOf(UniqueType.ProvidesResources, UniqueType.ConsumesResources,
        UniqueType.DoubleResourceProduced, UniqueType.StrategicResourcesIncrease, UniqueType.PercentResourceProduction)
    private val resourceConditionals = setOf(
        UniqueType.ConditionalWithResource,
        UniqueType.ConditionalWithoutResource,
        UniqueType.ConditionalWhenBetweenStatResource,
        UniqueType.ConditionalWhenAboveAmountStatResource,
        UniqueType.ConditionalWhenBelowAmountStatResource,
    )

    private fun addConditionalErrors(
        conditional: Unique,
        rulesetErrors: RulesetErrorList,
        prefix: String,
        unique: Unique,
        uniqueContainer: IHasUniques?,
        reportRulesetSpecificErrors: Boolean
    ) {
        if (unique.hasFlag(UniqueFlag.NoConditionals)) {
            rulesetErrors.add(
                "$prefix contains the conditional \"${conditional.text}\"," +
                    " but the unique does not accept conditionals!",
                RulesetErrorSeverity.Error, uniqueContainer, unique
            )
            return
        }

        if (conditional.type == null) {
            var text = "$prefix contains the conditional \"${conditional.text}\"," +
                " which is of an unknown type!"

            val similarConditionals = UniqueType.entries.filter {
                getRelativeTextDistance(
                    it.placeholderText,
                    conditional.placeholderText
                ) <= RulesetCache.uniqueMisspellingThreshold
            }
            if (similarConditionals.isNotEmpty())
                text += " May be a misspelling of \""+ similarConditionals.joinToString("\", or \"") { it.text } +"\""
            rulesetErrors.add(
                text,
                RulesetErrorSeverity.Warning, uniqueContainer, unique
            )
            return
        }

        if (conditional.type.targetTypes.none { it.modifierType != UniqueTarget.ModifierType.None })
            rulesetErrors.add(
                "$prefix contains the conditional \"${conditional.text}\"," +
                    " which is a Unique type not allowed as conditional or trigger.",
                RulesetErrorSeverity.Warning, uniqueContainer, unique
            )

        if (conditional.type.targetTypes.contains(UniqueTarget.UnitActionModifier)
            && unique.type!!.targetTypes.none { UniqueTarget.UnitAction.canAcceptUniqueTarget(it) }
        )
            rulesetErrors.add(
                "$prefix contains the conditional \"${conditional.text}\"," +
                    " which as a UnitActionModifier is only allowed on UnitAction uniques.",
                RulesetErrorSeverity.Warning, uniqueContainer, unique
            )

        if (unique.type in resourceUniques && conditional.type in resourceConditionals
            && ruleset.tileResources[conditional.params.last()]?.isCityWide == true)
            rulesetErrors.add(
                "$prefix contains the conditional \"${conditional.text}\"," +
                    " which references a citywide resource. This is not a valid conditional for a resource uniques, " +
                    "as it causes a recursive evaluation loop.",
                RulesetErrorSeverity.Error, uniqueContainer, unique)

        // Find resource uniques with countable parameters in conditionals, that depend on citywide resources
        // This too leads to an endless loop
        if (unique.type in resourceUniques)
            for ((index, param) in conditional.params.withIndex()){
                if (ruleset.tileResources[param]?.isCityWide != true) continue
                if (unique.type!!.parameterTypeMap.getOrNull(index)?.contains(UniqueParameterType.Countable) != true) continue

                rulesetErrors.add(
                    "$prefix contains the modifier \"${conditional.text}\"," +
                        " which references a citywide resource as a countable." +
                        " This is not a valid conditional for a resource uniques, as it causes a recursive evaluation loop.",
                    RulesetErrorSeverity.Error, uniqueContainer, unique)
            }

        val conditionalComplianceErrors =
            getComplianceErrors(conditional)

        for (complianceError in conditionalComplianceErrors) {
            if (!reportRulesetSpecificErrors && complianceError.errorSeverity == UniqueType.UniqueParameterErrorSeverity.RulesetSpecific)
                continue

            rulesetErrors.add(
                "$prefix contains modifier \"${conditional.text}\"." +
                " This contains the parameter \"${complianceError.parameterName}\" $whichDoesNotFitParameterType" +
                " ${complianceError.acceptableParameterTypes.joinToString(" or ") { it.parameterName }} !",
                complianceError.errorSeverity.getRulesetErrorSeverity(), uniqueContainer, unique
            )

            addExpressionParseErrors(complianceError, rulesetErrors, uniqueContainer, unique)
        }

        addDeprecationAnnotationErrors(conditional, "$prefix contains modifier \"${conditional.text}\" which", rulesetErrors, uniqueContainer)
    }

    private fun addDeprecationAnnotationErrors(
        unique: Unique,
        prefix: String,
        rulesetErrors: RulesetErrorList,
        uniqueContainer: IHasUniques?
    ) {
        val deprecationAnnotation = unique.getDeprecationAnnotation()
        if (deprecationAnnotation != null) {
            val replacementUniqueText = unique.getReplacementText(ruleset)
            val deprecationText =
                "$prefix is deprecated ${deprecationAnnotation.message}" +
                        if (deprecationAnnotation.replaceWith.expression != "") ", replace with \"${replacementUniqueText}\"" else ""
            val severity = if (deprecationAnnotation.level == DeprecationLevel.WARNING)
                RulesetErrorSeverity.WarningOptionsOnly // Not user-visible
            else RulesetErrorSeverity.ErrorOptionsOnly // User visible

            rulesetErrors.add(deprecationText, severity, uniqueContainer, unique)
        }

        // Check for deprecated Countables
        if (unique.type == null) return
        val countables =
            unique.type.parameterTypeMap.withIndex()
            .filter { UniqueParameterType.Countable in it.value }
            .map { unique.params[it.index] }
            .mapNotNull { Countables.getMatching(it, ruleset) }
        for (countable in countables) {
            val deprecation = countable.getDeprecationAnnotation() ?: continue
            // This is less flexible than unique.getReplacementText(ruleset)
            val replaceExpression = deprecation.replaceWith.expression
            val text = "Countable `${countable.name}` is deprecated ${deprecation.message}" +
                if (replaceExpression.isEmpty()) "" else ", replace with \"$replaceExpression\""
            val severity = if (deprecation.level == DeprecationLevel.WARNING)
                    RulesetErrorSeverity.WarningOptionsOnly // Not user-visible
                else RulesetErrorSeverity.ErrorOptionsOnly // User visible in new game and red in options
            rulesetErrors.add(text, severity, uniqueContainer, unique)
        }
    }

    /** Maps uncompliant parameters to their required types */
    private fun getComplianceErrors(
        unique: Unique,
    ): List<UniqueComplianceError> {
        if (unique.type == null) return emptyList()
        val errorList = ArrayList<UniqueComplianceError>()
        for ((index, param) in unique.params.withIndex()) {
            // Trying to catch the error at #11404
            if (unique.type.parameterTypeMap.size != unique.params.size) {
                throw Exception("Unique ${unique.text} has ${unique.params.size} parameters, " +
                        "but its type ${unique.type} only ${unique.type.parameterTypeMap.size} parameters?!")
            }
            val acceptableParamTypes = unique.type.parameterTypeMap[index]
            if (acceptableParamTypes.size == 0) continue // This is a deprecated parameter type, don't bother checking it

            val errorTypesForAcceptableParameters =
                acceptableParamTypes.map { getParamTypeErrorSeverityCached(it, param) }
            if (errorTypesForAcceptableParameters.any { it == null }) continue // This matches one of the types!
            if (errorTypesForAcceptableParameters.contains(UniqueType.UniqueParameterErrorSeverity.PossibleFilteringUnique)
                && param in allNonTypedUniques)
                continue // This is a filtering param, and the unique it's filtering for actually exists, no problem here!
            val leastSevereWarning =
                errorTypesForAcceptableParameters.minByOrNull { it!!.ordinal }
            if (leastSevereWarning == null)
                throw Exception("Unique ${unique.text} from mod ${ruleset.name} is acting strangely - please open a bug report")
            errorList += UniqueComplianceError(param, acceptableParamTypes, leastSevereWarning)
        }
        return errorList
    }

    private val paramTypeErrorSeverityCache = HashMap<UniqueParameterType, HashMap<String, UniqueType.UniqueParameterErrorSeverity?>>()
    private fun getParamTypeErrorSeverityCached(uniqueParameterType: UniqueParameterType, param: String): UniqueType.UniqueParameterErrorSeverity? {
        if (!paramTypeErrorSeverityCache.containsKey(uniqueParameterType))
            paramTypeErrorSeverityCache[uniqueParameterType] = hashMapOf()
        val uniqueParamCache = paramTypeErrorSeverityCache[uniqueParameterType]!!

        if (uniqueParamCache.containsKey(param)) return uniqueParamCache[param]

        val severity = uniqueParameterType.getErrorSeverity(param, ruleset)
        uniqueParamCache[param] = severity
        return severity
    }

    private fun checkUntypedUnique(
        unique: Unique,
        tryFixUnknownUniques: Boolean,
        uniqueContainer: IHasUniques?,
        prefix: String,
        reportRulesetSpecificErrors: Boolean
    ): RulesetErrorList {
        // Malformed conditional is always bad
        if (unique.text.count { it == '<' } != unique.text.count { it == '>' })
            return RulesetErrorList.of(
                "$prefix contains mismatched conditional braces!",
                RulesetErrorSeverity.Warning, ruleset, uniqueContainer, unique
            )

        // Support purely filtering Uniques without actual implementation
        if (isFilteringUniqueAllowed(unique, reportRulesetSpecificErrors)) return RulesetErrorList()

        if (tryFixUnknownUniques) {
            val fixes = tryFixUnknownUnique(unique, uniqueContainer, prefix)
            if (fixes.isNotEmpty()) return fixes
        }

        return RulesetErrorList.of(
            "$prefix not found in Unciv's unique types, and is not used as a filtering unique.",
            if (unique.params.isEmpty()) RulesetErrorSeverity.OK else RulesetErrorSeverity.Warning,
            ruleset, uniqueContainer, unique
        )
    }

    private fun isFilteringUniqueAllowed(unique: Unique, reportRulesetSpecificErrors: Boolean): Boolean {
        // Isolate this decision, to allow easy change of approach
        // This says: Must have no conditionals or parameters, and is used in any "filtering" parameter of another Unique
        if (unique.modifiers.isNotEmpty() || unique.params.isNotEmpty()) return false
        if (!reportRulesetSpecificErrors) return true // Don't report unless checking a complete Ruleset
        return unique.text in allUniqueParameters // referenced at least once from elsewhere
    }

    private fun tryFixUnknownUnique(unique: Unique, uniqueContainer: IHasUniques?, prefix: String): RulesetErrorList {
        val similarUniques = UniqueType.entries.filter {
            getRelativeTextDistance(
                it.placeholderText,
                unique.placeholderText
            ) <= RulesetCache.uniqueMisspellingThreshold
        }
        val equalUniques =
            similarUniques.filter { it.placeholderText == unique.placeholderText }
        return when {
            // This should only ever happen if a bug is or has been introduced that prevents Unique.type from being set for a valid UniqueType, I think.\
            equalUniques.isNotEmpty() -> RulesetErrorList.of(
                "$prefix looks like it should be fine, but for some reason isn't recognized.",
                RulesetErrorSeverity.OK,
                ruleset, uniqueContainer, unique
            )

            similarUniques.isNotEmpty() -> {
                val text =
                    "$prefix looks like it may be a misspelling of:\n" +
                        similarUniques.joinToString("\n") { uniqueType ->
                            var text = "\"${uniqueType.text}"
                            if (unique.modifiers.isNotEmpty())
                                text += " " + unique.modifiers.joinToString(" ") { "<${it.text}>" }
                            text += "\""
                            if (uniqueType.getDeprecationAnnotation() != null) text += " (Deprecated)"
                            return@joinToString text
                        }.prependIndent("\t")
                RulesetErrorList.of(text, RulesetErrorSeverity.OK, ruleset, uniqueContainer, unique)
            }
            else -> RulesetErrorList()
        }
    }

    companion object {
        const val whichDoesNotFitParameterType = "which does not fit parameter type"

        internal fun getUniqueContainerPrefix(uniqueContainer: IHasUniques?) =
            (if (uniqueContainer is IRulesetObject) "${uniqueContainer.originRuleset}: " else "") +
                (if (uniqueContainer == null) "The" else "(${uniqueContainer.getUniqueTarget().name}) ${uniqueContainer.name}'s") +
                " unique "
    }
}
