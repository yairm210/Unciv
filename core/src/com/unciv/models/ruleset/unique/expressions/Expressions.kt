package com.unciv.models.ruleset.unique.expressions

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.Readonly
import kotlin.math.roundToInt

/** 
 * TL;DR if you can provide the functionality is an easier way, be my guest :D
 * 
 * "Geez, why does this STRATEGY GAME contain a EXPRESSION PARSER? Overengineering", I hear you say
 * Well. We discussed this at https://github.com/yairm210/Unciv/pull/13218,
 * and the conclusion is that this is ACTUALLY the easiest solution.
 * - Keval does NOT support late-insert values, meaning there's no way to separate parsing and eval, 
 *   meaning there's no way to warn modders their expression is badly built. Right out.
 *   - Changing Keval to support this is way more work than building our own
 * - exp4j DOES support this, but does not support the countables, especially the [] format. 
 * This is more devatable, but converting a countable-expression string TO a exp4j expression string, detecting errors THERE, 
 *   and then converting the error locations back so tell modders "your problem is HERE", is ALSO more work!
 * */

class Expressions {
    @Readonly
    fun matches(parameterText: String, ruleset: Ruleset): Boolean {
        val parseResult = parse(parameterText)
        return parseResult.node != null && parseResult.node.getErrors(ruleset).isEmpty()
    }

    @Readonly
    fun eval(parameterText: String, gameContext: GameContext): Int? {
        val node = parse(parameterText).node ?: return null
        return node.eval(gameContext).roundToInt()
    }

    fun getErrorSeverity(parameterText: String, ruleset: Ruleset): UniqueType.UniqueParameterErrorSeverity? {
        val parseResult = parse(parameterText)
        return when {
            parseResult.node == null -> UniqueType.UniqueParameterErrorSeverity.RulesetInvariant
            parseResult.node.getErrors(ruleset).isNotEmpty() -> UniqueType.UniqueParameterErrorSeverity.PossibleFilteringUnique
            else -> null
        }
    }

    private data class ParseResult(/** null if there was a parse error */ val node: Node?, val exception: Parser.ParsingError?)

    companion object {
        @Cache private val cache: MutableMap<String, ParseResult> = mutableMapOf()

        @Readonly
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
