package com.unciv.models.ruleset.unique.expressions

import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.StateForConditionals

internal sealed interface Node {
    fun eval(context: StateForConditionals): Double

    // All elements below are not members, they're nested for namespace notation and common visibility
    // All toString() are for debugging only

    interface Constant : Node, Tokenizer.Token {
        val value: Double
        override fun eval(context: StateForConditionals) = value
    }

    class NumericConstant(override val value: Double) : Constant {
        override fun toString() = value.toString()
    }

    class UnaryOperation(private val operator: Operator.Unary, private val operand: Node): Node {
        override fun eval(context: StateForConditionals): Double = operator.implementation(operand.eval(context))
        override fun toString() = "($operator $operand)"
    }

    class BinaryOperation(private val operator: Operator.Binary, private val left: Node, private val right: Node): Node {
        override fun eval(context: StateForConditionals): Double = operator.implementation(left.eval(context), right.eval(context))
        override fun toString() = "($left $operator $right)"
    }

    class Countable(private val countable: Countables, private val parameterText: String): Node, Tokenizer.Token {
        override fun eval(context: StateForConditionals): Double = countable.eval(parameterText, context)?.toDouble() ?: 0.0
        override fun toString() = "[Countables.${countable.name}: $parameterText]"
    }
}
