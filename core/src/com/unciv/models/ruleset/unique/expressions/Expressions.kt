package com.unciv.models.ruleset.unique.expressions

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.ICountable
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.math.roundToInt

class Expressions : ICountable {
    override fun matches(parameterText: String, ruleset: Ruleset?) =
        parse(parameterText, ruleset).severity

    override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
        val node = parse(parameterText, null).node ?: return null
        return node.eval(stateForConditionals).roundToInt()
    }

    override fun getErrorSeverity(parameterText: String, ruleset: Ruleset) =
        when(parse(parameterText, ruleset).severity) {
            ICountable.MatchResult.No -> UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
            ICountable.MatchResult.Maybe -> UniqueType.UniqueParameterErrorSeverity.PossibleFilteringUnique
            ICountable.MatchResult.Yes -> null
        }

    private data class ParseResult(val severity: ICountable.MatchResult, val node: Node?)

    companion object {
        private val cache: MutableMap<String, ParseResult> = mutableMapOf()

        private fun parse(parameterText: String, ruleset: Ruleset?): ParseResult = cache.getOrPut(parameterText) {
            try {
                val node = Parser.parse(parameterText, ruleset)
                ParseResult(ICountable.MatchResult.Yes, node)
            } catch (ex: Parser.ParsingError) {
                ParseResult(ex.severity, null)
            }
        }
    }
}
