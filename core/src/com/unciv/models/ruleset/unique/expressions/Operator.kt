package com.unciv.models.ruleset.unique.expressions

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

internal sealed interface Operator : Tokenizer.Token {
    val symbol: String

    // All elements below are not members, they're nested for namespace notation and common visibility
    // All toString() are for use in exception messages only

    interface Unary : Operator {
        val implementation: (Double) -> Double
    }

    interface Binary : Operator {
        val precedence: Int
        val isLeftAssociative: Boolean
        val implementation: (Double, Double) -> Double
    }

    interface UnaryOrBinary : Operator {
        val unary: Unary
        val binary: Binary
    }

    enum class UnaryOperators(
        override val symbol: String,
        override val implementation: (Double) -> Double
    ) : Unary {
        Identity("+", { operand -> operand }),
        Negation("-", { operand -> -operand }),
        Sqrt("√", ::sqrt),
        Abs("abs", ::abs),
        Sqrt2("sqrt", ::sqrt),
        Cbrt("cbtr", ::cbrt),
        Exp("exp", ::exp),
        Log("ln", ::ln),
        Log10("log10", ::log10),
        Sin("sin", ::sin),
        Cos("cos", ::cos),
        Tan("tan", ::tan),
        Atan("atan", ::atan),
        ;
        override fun toString() = symbol
    }

    enum class BinaryOperators(
        override val symbol: String,
        override val precedence: Int,
        override val isLeftAssociative: Boolean,
        override val implementation: (Double, Double) -> Double
    ) : Binary {
        Addition("+", 2, true, { left, right -> left + right }),
        Subtraction("-", 2, true, { left, right -> left - right }),
        Multiplication("*", 3, true, { left, right -> left * right }),
        Division("/", 3, true, { left, right -> left / right }),
        Remainder("%", 3, true, { left, right -> ((left % right) + right) % right }), // true modulo, always non-negative
        Exponent("^", 4, false, { left, right -> left.pow(right) }),
        ;
        override fun toString() = symbol
    }

    enum class UnaryOrBinaryOperators(
        override val symbol: String,
        override val unary: Unary,
        override val binary: Binary
    ) : UnaryOrBinary {
        Plus("+", UnaryOperators.Identity, BinaryOperators.Addition),
        Minus("-", UnaryOperators.Negation, BinaryOperators.Subtraction),
        ;
        override fun toString() = symbol
    }

    enum class NamedConstants(override val symbol: String, override val value: Double) : Node.Constant, Operator {
        Pi("pi", kotlin.math.PI),
        Pi2("π", kotlin.math.PI),
        Euler("e", kotlin.math.E),
        ;
        override fun toString() = symbol
    }

    enum class Parentheses(override val symbol: String) : Operator {
        Opening("("), Closing(")")
        ;
        override fun toString() = symbol
    }

    companion object {
        private fun allEntries(): Sequence<Operator> =
            UnaryOperators.entries.asSequence() +
            BinaryOperators.entries +
            UnaryOrBinaryOperators.entries + // Will overwrite the previous entries in the map
            NamedConstants.entries +
            Parentheses.entries
        private val cache = allEntries().associateBy { it.symbol }
        fun of(symbol: String): Operator? = cache[symbol]
    }
}
