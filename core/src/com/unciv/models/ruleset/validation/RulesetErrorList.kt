package com.unciv.models.ruleset.validation

import com.badlogic.gdx.graphics.Color
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType

class RulesetError(val text: String, val errorSeverityToReport: RulesetErrorSeverity)

enum class RulesetErrorSeverity(val color: Color) {
    OK(Color.GREEN),
    WarningOptionsOnly(Color.YELLOW),
    Warning(Color.YELLOW),
    ErrorOptionsOnly(Color.RED),
    Error(Color.RED),
}

/**
 *  A container collecting errors in a [Ruleset]
 *
 *  While this is based a standard collection, please do not use the standard add, [plusAssign] or [addAll].
 *  Mod-controlled warning suppression is handled in [add] overloads that provide a source object, which can host suppression uniques.
 *  Bypassing these add methods means suppression is ignored. Thus using [addAll] is fine when the elements to add are all already checked.
 *
 *  @param ruleset The ruleset being validated (needed to check modOptions for suppression uniques). Leave `null` only for validation results that need no suppression checks.
 */
class RulesetErrorList(
    ruleset: Ruleset? = null
) : ArrayList<RulesetError>() {
    private val globalSuppressionFilters: Set<String> =
        ruleset?.modOptions
        ?.getMatchingUniques(UniqueType.SuppressWarnings, StateForConditionals.IgnoreConditionals)
        ?.map { it.params[0] }
        ?.toSet()
        ?: emptySet()

    /** Add an [element], preventing duplicates (in which case the highest severity wins).
     *
     *  @param sourceObject the originating object, which can host suppressions. When it is not known or not a [IHasUniques], pass `null`.
     *  @param sourceUnique the originating unique, so look for suppression modifiers. Leave `null` if unavailable.
     */
    fun add(element: RulesetError, sourceObject: IHasUniques?, sourceUnique: Unique? = null): Boolean {
        // The dupe check may be faster than the Suppression check, so do it first?
        if (!removeLowerSeverityDuplicate(element)) return false
        if (Suppression.isErrorSuppressed(globalSuppressionFilters, sourceObject, sourceUnique, element))
            return false
        return super.add(element)
    }

    /** Shortcut: Add a new [RulesetError] built from [text] and [errorSeverityToReport].
     *
     *  @param sourceObject the originating object, which can host suppressions. When it is not known or not a [IHasUniques], pass `null`.
     *  @param sourceUnique the originating unique, so look for suppression modifiers. Leave `null` if unavailable.
     */
    fun add(text: String, errorSeverityToReport: RulesetErrorSeverity = RulesetErrorSeverity.Error, sourceObject: IHasUniques? = null, sourceUnique: Unique? = null) =
        add(RulesetError(text, errorSeverityToReport), sourceObject, sourceUnique)

    @Deprecated("No adding without explicit source object", ReplaceWith("add(element, sourceObject, sourceUnique)"))
    override fun add(element: RulesetError) = super.add(element)

    /** Add all [elements] with duplicate check, but without suppression check */
    override fun addAll(elements: Collection<RulesetError>): Boolean {
        var result = false
        for (element in elements)
            if (addWithDuplicateCheck(element)) result = true
        return result
    }

    private fun addWithDuplicateCheck(element: RulesetError) =
        removeLowerSeverityDuplicate(element) && super.add(element)

    /** @return `true` if the element is not present, or it was removed due to having a lower severity */
    private fun removeLowerSeverityDuplicate(element: RulesetError): Boolean {
        // Suppress duplicates due to the double run of some checks for invariant/specific,
        // Without changing collection type or making RulesetError obey the equality contract
        val existing = firstOrNull { it.text == element.text }
            ?: return true
        if (existing.errorSeverityToReport >= element.errorSeverityToReport) return false
        remove(existing)
        return true
    }

    fun getFinalSeverity(): RulesetErrorSeverity {
        if (isEmpty()) return RulesetErrorSeverity.OK
        return this.maxOf { it.errorSeverityToReport }
    }

    /** @return `true` means severe errors make the mod unplayable */
    fun isError() = getFinalSeverity() == RulesetErrorSeverity.Error
    /** @return `true` means problems exist, Options screen mod checker or unit tests for vanilla ruleset should complain */
    fun isNotOK() = getFinalSeverity() != RulesetErrorSeverity.OK
    /** @return `true` means at least errors impacting gameplay exist, new game screen should warn or block */
    fun isWarnUser() = getFinalSeverity() >= RulesetErrorSeverity.Warning

    fun getErrorText(unfiltered: Boolean = false) =
            getErrorText { unfiltered || it.errorSeverityToReport > RulesetErrorSeverity.WarningOptionsOnly }
    fun getErrorText(filter: (RulesetError)->Boolean) =
            filter(filter)
                .sortedByDescending { it.errorSeverityToReport }
                .joinToString("\n") {
                    it.errorSeverityToReport.name + ": " +
                        // This will go through tr(), unavoidably, which will move the conditionals
                        // out of place. Prevent via kludge:
                        it.text.replace('<','〈').replace('>','〉')
                }

    companion object {
        /** Helper factory for a single entry list (which can result in an empty list due to suppression)
         *  Note: Valid source for [addAll] since suppression is already taken care of. */
        fun of(
            text: String,
            severity: RulesetErrorSeverity = RulesetErrorSeverity.Error,
            ruleset: Ruleset? = null,
            sourceObject: IHasUniques? = null,
            sourceUnique: Unique? = null
        ): RulesetErrorList {
            val result = RulesetErrorList(ruleset)
            result.add(text, severity, sourceObject, sourceUnique)
            return result
        }
    }
}
