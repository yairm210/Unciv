package com.unciv.models.ruleset.validation

import com.unciv.json.json
import com.unciv.logic.UncivShowableException
import com.unciv.models.ruleset.ModOptions
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import yairm210.purity.annotations.Pure

/**
 *  All public methods dealing with how Mod authors can suppress RulesetValidator output.
 *
 *  This allows the outside code to be agnostic about how each entry operates, it's all here, and can easily be expanded.
 *  [uniqueDocDescription], [parameterDocDescription], [parameterDocExample], [isErrorSuppressed] and [isValidFilter] need to agree on the rules!
 *  Note there is minor influence on the rules where [RulesetErrorList.add] is called, as supplying null for its source parameters limits the scope where suppressions are found.
 *
 *  Current decisions:
 *  * You cannot suppress [RulesetErrorSeverity.Error] level messages.
 *  * Each suppression entry is either compared verbatim or as primitive wildcard pattern with '*' on both ends, case-insensitive.
 *  * Minimum selectivity of [minimumSelectivity] characters matching.
 *  * Validation of the suppression entries themselves is rudimentary.
 */
object Suppression {
    /** minimum match length for a valid suppression filter */
    private const val minimumSelectivity = 12 // arbitrary

    /** Delegated from [UniqueType.SuppressWarnings] */
    const val uniqueDocDescription = "Allows suppressing specific validation warnings." +
        " Errors, deprecation warnings, or warnings about untyped and non-filtering uniques should be heeded, not suppressed, and are therefore not accepted." +
        " Note that this can be used in ModOptions, in the uniques a warning is about, or as modifier on the unique triggering a warning -" +
        " but you still need to be specific. Even in the modifier case you will need to specify a sufficiently selective portion of the warning text as parameter."

    /** Delegated from [UniqueParameterType.ValidationWarning] */
    const val parameterDocDescription = "Suppresses one specific Ruleset validation warning. " +
        "This can specify the full text verbatim including correct upper/lower case, " +
        "or it can be a wildcard case-insensitive simple pattern starting and ending in an asterisk ('*'). " +
        "If the suppression unique is used within an object or as modifier (not ModOptions), " +
        "the wildcard symbols can be omitted, as selectivity is better due to the limited scope."

    /** Delegated from [UniqueParameterType.ValidationWarning] */
    const val parameterDocExample = "Tinman is supposed to automatically upgrade at tech Clockwork, and therefore Servos for its upgrade Mecha may not yet be researched!" +
        " -or- *is supposed to automatically upgrade*"

    private const val deprecationWarningPattern = """unique "~" is deprecated as of ~, replace with"""
    private const val untypedWarningPattern = """unique "~" not found in Unciv's unique types, and is not used as a filtering unique"""

    /** Determine whether [parameterText] is a valid Suppression filter as implemented by [isErrorSuppressed] */
    @Pure
    fun isValidFilter(parameterText: String) = when {
        // Cannot contain {} or <>
        '{' in parameterText || '<' in parameterText -> false
        // Must not be a deprecation - these should be implemented by their replacement not suppressed
        hasCommonSubstringLength(parameterText, deprecationWarningPattern, minimumSelectivity) -> false
        // Must not be a untyped/nonfiltering warning (a case for the Comment UniqueType instead)
        hasCommonSubstringLength(parameterText, untypedWarningPattern, minimumSelectivity) -> false
        // Check wildcard suppression - '*' on both ends, rest of pattern selective enough
        parameterText.startsWith('*') != parameterText.endsWith('*') -> false
        parameterText.length < minimumSelectivity + 2 -> false
        // More rules here???
        else -> true
    }

    private fun matchesFilter(error: RulesetError, filter: String): Boolean {
        if (error.text == filter) return true
        if (!filter.endsWith('*') || !filter.startsWith('*')) return false
        return error.text.contains(filter.removeSurrounding("*", "*"), ignoreCase = true)
    }

    /** Determine if [error] matches any suppression Unique in [ModOptions] or the [sourceObject], or any suppression modifier in [sourceUnique] */
    internal fun isErrorSuppressed(
        globalSuppressionFilters: Collection<String>,
        sourceObject: IHasUniques?,
        sourceUnique: Unique?,
        error: RulesetError
    ): Boolean {
        if (error.errorSeverityToReport >= RulesetErrorSeverity.Error) return false
        if (sourceObject == null && globalSuppressionFilters.isEmpty()) return false

        fun getWildcardFilter(unique: Unique) = unique.params[0].let {
            if (it.startsWith('*')) it else "*$it*"
        }
        // Allow suppressing from ModOptions
        var suppressions = globalSuppressionFilters.asSequence()
        // Allow suppressing from suppression uniques in the same Unique collection
        if (sourceObject != null)
            suppressions += sourceObject.getMatchingUniques(UniqueType.SuppressWarnings, GameContext.IgnoreConditionals).map { getWildcardFilter(it) }
        // Allow suppressing from modifiers in the same Unique
        if (sourceUnique != null)
            suppressions += sourceUnique.getModifiers(UniqueType.SuppressWarnings).map { getWildcardFilter(it) }

        for (filter in suppressions)
            if (matchesFilter(error, filter)) return true
        return false
    }

    /** Whosoever maketh this here methyd available to evil Mod whytches shall forever be cursed and damned to all Hell's tortures */
    @Suppress("unused")  // Debug tool
    fun autoSuppressAllWarnings(ruleset: Ruleset, toModOptions: ModOptions) {
        if (ruleset.folderLocation == null)
            throw UncivShowableException("autoSuppressAllWarnings needs Ruleset.folderLocation")
        for (error in RulesetValidator.create(ruleset).getErrorList()) {
            if (error.errorSeverityToReport >= RulesetErrorSeverity.Error) continue
            toModOptions.uniques += UniqueType.SuppressWarnings.text.fillPlaceholders(error.text)
        }
        json().toJson(toModOptions, ruleset.folderLocation!!.child("jsons/ModOptions.json"))
    }

    @Pure @Suppress("SameParameterValue")  // Yes we're using a constant up there, still reads clearer
    private fun hasCommonSubstringLength(x: String, y: String, minCommonLength: Int): Boolean {
        // This is brute-force, but adapting a public "longest-common-substring" algorithm was complex and still slooow.
        // Using the knowledge that we're only interested in the common length exceeding a threshold saves time.
        // This uses the fact that Int.until will _not_ throw on upper < lower.
        (0 until x.length - minCommonLength).forEach { xIndex ->
            val xSub = x.substring(xIndex)
            (0 until y.length - minCommonLength).forEach { yIndex ->
                if (xSub.commonPrefixWith(y.substring(yIndex), true).length >= minCommonLength)
                    return true
            }
        }
        return false
    }
}
