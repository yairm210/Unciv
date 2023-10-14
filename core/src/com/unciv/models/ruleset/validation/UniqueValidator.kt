package com.unciv.models.ruleset.validation

import com.unciv.models.ruleset.IRulesetObject
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueComplianceError
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.INamed

class UniqueValidator(val ruleset: Ruleset) {

    fun checkUniques(
        uniqueContainer: IHasUniques,
        lines: RulesetErrorList,
        severityToReport: UniqueType.UniqueComplianceErrorSeverity,
        tryFixUnknownUniques: Boolean
    ) {
        for (unique in uniqueContainer.uniqueObjects) {
            val errors = checkUnique(
                unique,
                tryFixUnknownUniques,
                uniqueContainer as? INamed,
                severityToReport
            )
            lines.addAll(errors)
        }
    }

    fun checkUnique(
        unique: Unique,
        tryFixUnknownUniques: Boolean,
        namedObj: INamed?,
        severityToReport: UniqueType.UniqueComplianceErrorSeverity
    ): List<RulesetError> {
        val prefix by lazy { (if (namedObj is IRulesetObject) "${namedObj.originRuleset}: " else "") +
            (if (namedObj == null) "The" else "${namedObj.name}'s") }
        if (unique.type == null) return checkUntypedUnique(unique, tryFixUnknownUniques, prefix)

        val rulesetErrors = RulesetErrorList()

        if (namedObj is IHasUniques && !unique.type.canAcceptUniqueTarget(namedObj.getUniqueTarget()))
            rulesetErrors.add(RulesetError("$prefix unique \"${unique.text}\" is not allowed on its target type", RulesetErrorSeverity.Warning))

        val typeComplianceErrors = getComplianceErrors(unique)
        for (complianceError in typeComplianceErrors) {
            if (complianceError.errorSeverity <= severityToReport)
                rulesetErrors.add(RulesetError("$prefix unique \"${unique.text}\" contains parameter ${complianceError.parameterName}," +
                    " which does not fit parameter type" +
                    " ${complianceError.acceptableParameterTypes.joinToString(" or ") { it.parameterName }} !",
                    complianceError.errorSeverity.getRulesetErrorSeverity(severityToReport)
                ))
        }

        for (conditional in unique.conditionals) {
            addConditionalErrors(conditional, rulesetErrors, prefix, unique, severityToReport)
        }


        if (severityToReport != UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific)
        // If we don't filter these messages will be listed twice as this function is called twice on most objects
        // The tests are RulesetInvariant in nature, but RulesetSpecific is called for _all_ objects, invariant is not.
            return rulesetErrors

        addDeprecationAnnotationErrors(unique, prefix, rulesetErrors)

        return rulesetErrors
    }

    private fun addConditionalErrors(
        conditional: Unique,
        rulesetErrors: RulesetErrorList,
        prefix: String,
        unique: Unique,
        severityToReport: UniqueType.UniqueComplianceErrorSeverity
    ) {
        if (conditional.type == null) {
            rulesetErrors.add(
                "$prefix unique \"${unique.text}\" contains the conditional \"${conditional.text}\"," +
                    " which is of an unknown type!",
                RulesetErrorSeverity.Warning
            )
            return
        }

        if (conditional.type.targetTypes.none { it.modifierType != UniqueTarget.ModifierType.None })
            rulesetErrors.add(
                "$prefix unique \"${unique.text}\" contains the conditional \"${conditional.text}\"," +
                    " which is a Unique type not allowed as conditional or trigger.",
                RulesetErrorSeverity.Warning
            )

        if (conditional.type.targetTypes.contains(UniqueTarget.UnitActionModifier)
            && unique.type!!.targetTypes.none { UniqueTarget.UnitAction.canAcceptUniqueTarget(it) }
        )
            rulesetErrors.add(
                "$prefix unique \"${unique.text}\" contains the conditional \"${conditional.text}\"," +
                    " which as a UnitActionModifier is only allowed on UnitAction uniques.",
                RulesetErrorSeverity.Warning
            )

        val conditionalComplianceErrors =
            getComplianceErrors(conditional)
        for (complianceError in conditionalComplianceErrors) {
            if (complianceError.errorSeverity == severityToReport)
                rulesetErrors.add(
                    RulesetError(
                        "$prefix unique \"${unique.text}\" contains the conditional \"${conditional.text}\"." +
                            " This contains the parameter ${complianceError.parameterName} which does not fit parameter type" +
                            " ${complianceError.acceptableParameterTypes.joinToString(" or ") { it.parameterName }} !",
                        complianceError.errorSeverity.getRulesetErrorSeverity(severityToReport)
                    )
                )
        }
    }

    private fun addDeprecationAnnotationErrors(
        unique: Unique,
        prefix: String,
        rulesetErrors: RulesetErrorList
    ) {
        val deprecationAnnotation = unique.getDeprecationAnnotation()
        if (deprecationAnnotation != null) {
            val replacementUniqueText = unique.getReplacementText(ruleset)
            val deprecationText =
                "$prefix unique \"${unique.text}\" is deprecated ${deprecationAnnotation.message}," +
                        if (deprecationAnnotation.replaceWith.expression != "") " replace with \"${replacementUniqueText}\"" else ""
            val severity = if (deprecationAnnotation.level == DeprecationLevel.WARNING)
                RulesetErrorSeverity.WarningOptionsOnly // Not user-visible
            else RulesetErrorSeverity.Warning // User visible

            rulesetErrors.add(deprecationText, severity)
        }
    }

    /** Maps uncompliant parameters to their required types */
    private fun getComplianceErrors(
        unique: Unique,
    ): List<UniqueComplianceError> {
        if (unique.type==null) return emptyList()
        val errorList = ArrayList<UniqueComplianceError>()
        for ((index, param) in unique.params.withIndex()) {
            val acceptableParamTypes = unique.type.parameterTypeMap[index]
            val errorTypesForAcceptableParameters =
                acceptableParamTypes.map { getParamTypeErrorSeverityCached(it, param) }
            if (errorTypesForAcceptableParameters.any { it == null }) continue // This matches one of the types!
            val leastSevereWarning =
                errorTypesForAcceptableParameters.minByOrNull { it!!.ordinal }!!
            errorList += UniqueComplianceError(param, acceptableParamTypes, leastSevereWarning)
        }
        return errorList
    }

    private val paramTypeErrorSeverityCache = HashMap<UniqueParameterType, HashMap<String, UniqueType.UniqueComplianceErrorSeverity?>>()
    private fun getParamTypeErrorSeverityCached(uniqueParameterType: UniqueParameterType, param:String): UniqueType.UniqueComplianceErrorSeverity? {
        if (!paramTypeErrorSeverityCache.containsKey(uniqueParameterType))
            paramTypeErrorSeverityCache[uniqueParameterType] = hashMapOf()
        val uniqueParamCache = paramTypeErrorSeverityCache[uniqueParameterType]!!

        if (uniqueParamCache.containsKey(param)) return uniqueParamCache[param]

        val severity = uniqueParameterType.getErrorSeverity(param, ruleset)
        uniqueParamCache[param] = severity
        return severity
    }

    private fun checkUntypedUnique(unique: Unique, tryFixUnknownUniques: Boolean, prefix: String ): List<RulesetError> {
        // Malformed conditional is always bad
        if (unique.text.count { it == '<' } != unique.text.count { it == '>' })
            return listOf(RulesetError(
                "$prefix unique \"${unique.text}\" contains mismatched conditional braces!",
                RulesetErrorSeverity.Warning))

        // Support purely filtering Uniques without actual implementation
        if (isFilteringUniqueAllowed(unique)) return emptyList()
        if (tryFixUnknownUniques) {
            val fixes = tryFixUnknownUnique(unique, prefix)
            if (fixes.isNotEmpty()) return fixes
        }

        return listOf(RulesetError(
            "$prefix unique \"${unique.text}\" not found in Unciv's unique types.",
            RulesetErrorSeverity.OK))
    }

    private fun isFilteringUniqueAllowed(unique: Unique): Boolean {
        // Isolate this decision, to allow easy change of approach
        // This says: Must have no conditionals or parameters, and is contained in GlobalUniques
        if (unique.conditionals.isNotEmpty() || unique.params.isNotEmpty()) return false
        return unique.text in ruleset.globalUniques.uniqueMap
    }

    private fun tryFixUnknownUnique(unique: Unique, prefix: String): List<RulesetError> {
        val similarUniques = UniqueType.values().filter {
            getRelativeTextDistance(
                it.placeholderText,
                unique.placeholderText
            ) <= RulesetCache.uniqueMisspellingThreshold
        }
        val equalUniques =
            similarUniques.filter { it.placeholderText == unique.placeholderText }
        return when {
            // This should only ever happen if a bug is or has been introduced that prevents Unique.type from being set for a valid UniqueType, I think.\
            equalUniques.isNotEmpty() -> listOf(RulesetError(
                "$prefix unique \"${unique.text}\" looks like it should be fine, but for some reason isn't recognized.",
                RulesetErrorSeverity.OK))

            similarUniques.isNotEmpty() -> {
                val text =
                    "$prefix unique \"${unique.text}\" looks like it may be a misspelling of:\n" +
                        similarUniques.joinToString("\n") { uniqueType ->
                            var text = "\"${uniqueType.text}"
                            if (unique.conditionals.isNotEmpty())
                                text += " " + unique.conditionals.joinToString(" ") { "<${it.text}>" }
                            text += "\""
                            if (uniqueType.getDeprecationAnnotation() != null) text += " (Deprecated)"
                            return@joinToString text
                        }.prependIndent("\t")
                listOf(RulesetError(text, RulesetErrorSeverity.OK))
            }
            else -> emptyList()
        }
    }
}
