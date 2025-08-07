package com.unciv.models.ruleset.unique.expressions

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.GameContext
import yairm210.purity.annotations.Readonly

internal sealed interface Node {
    @Readonly fun eval(context: GameContext): Double
    @Readonly fun getErrors(ruleset: Ruleset): List<String>

    // All elements below are not members, they're nested for namespace notation and common visibility
    // All toString() are for debugging only

    interface Constant : Node, Tokenizer.Token {
        val value: Double
        override fun eval(context: GameContext): Double = value
        override fun getErrors(ruleset: Ruleset) = emptyList<String>()
    }

    class NumericConstant(override val value: Double) : Constant {
        override fun toString() = value.toString()
    }

    class UnaryOperation(private val operator: Operator.Unary, private val operand: Node): Node {
        @Suppress("purity") // cannot mark class val as @Read
        override fun eval(context: GameContext): Double = operator.implementation(operand.eval(context))
        override fun toString() = "($operator $operand)"
        override fun getErrors(ruleset: Ruleset) = operand.getErrors(ruleset)
    }

    class BinaryOperation(private val operator: Operator.Binary, private val left: Node, private val right: Node): Node {
        @Suppress("purity") 
        override fun eval(context: GameContext): Double = operator.implementation(left.eval(context), right.eval(context))
        override fun toString() = "($left $operator $right)"
        override fun getErrors(ruleset: Ruleset): List<String> {
            val leftErrors = left.getErrors(ruleset)
            val rightErrors = right.getErrors(ruleset)
            return leftErrors + rightErrors
        }
    }

    class Countable(private val parameterText: String, 
                    /** Most countables can be detected via string pattern */ private val rulesetInvariantCountable: Countables?): Node, Tokenizer.Token {
        override fun eval(context: GameContext): Double {
            val ruleset = context.gameInfo?.ruleset
                ?: return 0.0 // We use "surprised pikachu face" for any unexpected issue so games don't crash 
            
            val countable = getCountable(ruleset)
                ?: return 0.0
            
            return countable.eval(parameterText, context)?.toDouble() ?: 0.0
        }

        @Readonly
        private fun getCountable(ruleset: Ruleset): Countables? {
            return rulesetInvariantCountable
                ?: Countables.getMatching(parameterText, ruleset)
        }

        @Readonly
        override fun getErrors(ruleset: Ruleset): List<String> {
            if (getCountable(ruleset) == null)
                return listOf("Unknown countable: $parameterText")
            return emptyList()
        }

        override fun toString() = "[Countable: $parameterText]"
    }
}
