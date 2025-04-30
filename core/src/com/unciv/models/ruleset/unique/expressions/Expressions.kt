package com.unciv.models.ruleset.unique.expressions

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.ICountable
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import kotlin.math.roundToInt

class Expressions : ICountable {
    override fun matches(parameterText: String, ruleset: Ruleset): Boolean {
        val parseResult = parse(parameterText)
        return parseResult.node != null && parseResult.node.getErrors(ruleset).isEmpty()
    }

    override fun eval(parameterText: String, stateForConditionals: StateForConditionals): Int? {
        val node = parse(parameterText).node ?: return null
        return node.eval(stateForConditionals).roundToInt()
    }

    override fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
        val parseResult = parse(parameterText)
        return when {
            parseResult.node == null -> UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
            parseResult.node.getErrors(ruleset).isNotEmpty() -> UniqueType.UniqueParameterErrorSeverity.PossibleFilteringUnique
            else -> null
        }
    }

    override fun getDeprecationAnnotation(): Deprecated? = null

    private data class ParseResult(/** null if there was a parse error */ val node: Node?, val exception: Parser.ParsingError?)

    companion object {
        private val cache: MutableMap<String, ParseResult> = mutableMapOf()

        private fun parse(parameterText: String): ParseResult = cache.getOrPut(parameterText) {
            try {
                val node = Parser.parse(parameterText)
                ParseResult(node, null)
            } catch (ex: Parser.ParsingError) {
                ParseResult(null, ex)
            }
        }
        
        fun getParsingError(parameterText: String): Parser.ParsingError? = 
            parse(parameterText).exception
        
        fun getCountableErrors(parameterText: String, ruleset: Ruleset): List<String> {
            val parseResult = parse(parameterText)
            return if (parseResult.node == null) emptyList() else parseResult.node.getErrors(ruleset)
        }
    }
}
