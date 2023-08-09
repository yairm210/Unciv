package com.unciv.models.ruleset.validation

import com.badlogic.gdx.graphics.Color

class RulesetError(val text: String, val errorSeverityToReport: RulesetErrorSeverity)

enum class RulesetErrorSeverity(val color: Color) {
    OK(Color.GREEN),
    WarningOptionsOnly(Color.YELLOW),
    Warning(Color.YELLOW),
    Error(Color.RED),
}

class RulesetErrorList : ArrayList<RulesetError>() {
    operator fun plusAssign(text: String) {
        add(text, RulesetErrorSeverity.Error)
    }

    fun add(text: String, errorSeverityToReport: RulesetErrorSeverity) {
        add(RulesetError(text, errorSeverityToReport))
    }

    override fun add(element: RulesetError): Boolean {
        // Suppress duplicates due to the double run of some checks for invariant/specific,
        // Without changing collection type or making RulesetError obey the equality contract
        val existing = firstOrNull { it.text == element.text }
            ?: return super.add(element)
        if (existing.errorSeverityToReport >= element.errorSeverityToReport) return false
        remove(existing)
        return super.add(element)
    }

    override fun addAll(elements: Collection<RulesetError>): Boolean {
        var result = false
        for (element in elements)
            if (add(element)) result = true
        return result
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
            getErrorText { unfiltered || it.errorSeverityToReport != RulesetErrorSeverity.WarningOptionsOnly }
    fun getErrorText(filter: (RulesetError)->Boolean) =
            filter(filter)
                .sortedByDescending { it.errorSeverityToReport }
                .joinToString("\n") {
                    it.errorSeverityToReport.name + ": " +
                        // This will go through tr(), unavoidably, which will move the conditionals
                        // out of place. Prevent via kludge:
                        it.text.replace('<','〈').replace('>','〉')
                }
}
