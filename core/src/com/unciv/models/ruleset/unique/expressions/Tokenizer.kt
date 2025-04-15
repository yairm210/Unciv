package com.unciv.models.ruleset.unique.expressions

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.ICountable
import com.unciv.models.ruleset.unique.expressions.Operator.Parentheses
import com.unciv.models.ruleset.unique.expressions.Parser.EmptyBraces
import com.unciv.models.ruleset.unique.expressions.Parser.InternalError
import com.unciv.models.ruleset.unique.expressions.Parser.InvalidConstant
import com.unciv.models.ruleset.unique.expressions.Parser.MalformedCountable
import com.unciv.models.ruleset.unique.expressions.Parser.UnmatchedBraces
import com.unciv.models.ruleset.unique.expressions.Parser.UnrecognizedToken

internal object Tokenizer {
    /**
     *  Possible types:
     *  - [Parentheses] (defined in [Operator]  - for convenience, they conform to the minimal interface)
     *  - [Operator]
     *  - [Node.Constant]
     *  - [Node.Countable]
     */
    internal sealed interface Token {
        fun canBeUnary() = this is Operator.Unary || this is Operator.UnaryOrBinary
        fun canBeBinary() = this is Operator.Binary || this is Operator.UnaryOrBinary
        // Note: not naming this `getUnaryOperator` because of kotlin's habit to interpret that as property accessor. Messes up debugging a bit.
        fun fetchUnaryOperator() = when(this) {
            is Operator.Unary -> this
            is Operator.UnaryOrBinary -> unary
            else -> throw InternalError()
        }
        fun fetchBinaryOperator() = when(this) {
            is Operator.Binary -> this
            is Operator.UnaryOrBinary -> binary
            else -> throw InternalError()
        }
    }

    // Define our own "Char is part of literal constant" and "Char is part of identifier" functions - decouple from Java CharacterData
    private fun Char.isNumberLiteral() = this == '.' || this in '0'..'9' // NOT using library isDigit() here - potentially non-latin
    private fun Char.isIdentifierStart() = isLetter() // Allow potentially non-latin script //TODO questionable
    private fun Char.isIdentifierContinuation() = this == '_' || isLetterOrDigit()

    fun String.toToken(ruleset: Ruleset?): Token {
        assert(isNotBlank())
        if (first().isDigit() || first() == '.')
            return Node.NumericConstant(toDouble())
        val operator = Operator.of(this)
        if (operator != null) return operator
        // Countable tokens must come here still wrapped in braces to avoid infinite recursion
        if(!startsWith('[') || !endsWith(']'))
            throw UnrecognizedToken()
        val countableText = substring(1, length - 1)
        val (countable, severity) = Countables.getBestMatching(countableText, ruleset)
        if (severity == ICountable.MatchResult.Yes)
            return Node.Countable(countable!!, countableText)
        if (severity == ICountable.MatchResult.Maybe)
            throw MalformedCountable(countable!!, countableText)
        throw UnrecognizedToken()
    }

    fun String.tokenize() = sequence {
        var firstLetter = -1
        var firstDigit = -1
        var openingBraceAt = -1
        var braceNestingLevel = 0

        suspend fun SequenceScope<String>.emitIdentifier(pos: Int = this@tokenize.length) {
            assert(firstDigit < 0)
            yield(this@tokenize.substring(firstLetter, pos))
            firstLetter = -1
        }
        suspend fun SequenceScope<String>.emitNumericLiteral(pos: Int = this@tokenize.length) {
            assert(firstLetter < 0)
            val token = this@tokenize.substring(firstDigit, pos)
            if (token.toDoubleOrNull() == null) throw InvalidConstant(token)
            yield(token)
            firstDigit = -1
        }

        for ((pos, char) in this@tokenize.withIndex()) {
            if (firstLetter >= 0) {
                if (char.isIdentifierContinuation()) continue
                emitIdentifier(pos)
            } else if (firstDigit >= 0) {
                if (char.isNumberLiteral()) continue
                emitNumericLiteral(pos)
            }
            if (char.isWhitespace()) {
                continue
            } else if (openingBraceAt >= 0) {
                if (char == '[')
                    braceNestingLevel++
                else if (char == ']')
                    braceNestingLevel--
                if (braceNestingLevel == 0) {
                    if (pos - openingBraceAt <= 1) throw EmptyBraces()
                    yield(this@tokenize.substring(openingBraceAt, pos + 1)) // Leave the braces
                    openingBraceAt = -1
                }
            } else if (char.isIdentifierStart()) {
                firstLetter = pos
                continue
            } else if (char.isNumberLiteral()) {
                firstDigit = pos
                continue
            } else if (char == '[') {
                openingBraceAt = pos
                assert(braceNestingLevel == 0)
                braceNestingLevel++
            } else if (char == ']') {
                throw UnmatchedBraces(pos)
            } else {
                yield(char.toString())
            }
        }
        if (firstLetter >= 0) emitIdentifier()
        if (firstDigit >= 0) emitNumericLiteral()
        if (braceNestingLevel > 0) throw UnmatchedBraces(this@tokenize.length)
    }
}
