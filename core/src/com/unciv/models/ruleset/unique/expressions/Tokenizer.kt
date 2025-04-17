package com.unciv.models.ruleset.unique.expressions

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.ICountable
import com.unciv.models.ruleset.unique.expressions.Operator.Parentheses
import com.unciv.models.ruleset.unique.expressions.Parser.EmptyBraces
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

    // Position in text, to token found
    fun Pair<Int,String>.toToken(ruleset: Ruleset?): Pair<Int,Token> {
        val (position, text) = this
        if (text.isEmpty()) throw UnrecognizedToken(position)
        assert(text.isNotBlank())
        if (text.first().isDigit() || text.first() == '.')
            return position to Node.NumericConstant(text.toDouble())
        val operator = Operator.of(text)
        if (operator != null) return position to operator
        // Countable tokens must come here still wrapped in braces to avoid infinite recursion
        if(!text.startsWith('[') || !text.endsWith(']'))
            throw UnrecognizedToken(position)
        val countableText = text.substring(1, text.length - 1)
        val (countable, severity) = Countables.getBestMatching(countableText, ruleset)
        if (severity == ICountable.MatchResult.Yes)
            return position to Node.Countable(countable!!, countableText)
        if (severity == ICountable.MatchResult.Maybe)
            throw MalformedCountable(position, countable!!, countableText)
        throw UnrecognizedToken(position)
    }

    fun String.tokenize() = sequence<Pair<Int, String>> {
        var firstLetter = -1
        var firstDigit = -1
        var openingBraceAt = -1
        var braceNestingLevel = 0

        suspend fun SequenceScope<Pair<Int, String>>.emitIdentifier(pos: Int) {
            assert(firstDigit < 0)
            yield(pos to this@tokenize.substring(firstLetter, pos))
            firstLetter = -1
        }
        suspend fun SequenceScope<Pair<Int, String>>.emitNumericLiteral(pos: Int) {
            assert(firstLetter < 0)
            val token = this@tokenize.substring(firstDigit, pos)
            if (token.toDoubleOrNull() == null) throw InvalidConstant(pos, token)
            yield(pos to token)
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
                    if (pos - openingBraceAt <= 1) throw EmptyBraces(pos)
                    yield(pos to this@tokenize.substring(openingBraceAt, pos + 1)) // Leave the braces
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
                yield(pos to char.toString())
            }
        }
        if (firstLetter >= 0) emitIdentifier(this@tokenize.length)
        if (firstDigit >= 0) emitNumericLiteral(this@tokenize.length)
        if (braceNestingLevel > 0) throw UnmatchedBraces(this@tokenize.length)
    }
}
