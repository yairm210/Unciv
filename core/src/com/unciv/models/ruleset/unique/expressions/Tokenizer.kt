package com.unciv.models.ruleset.unique.expressions

import com.unciv.models.ruleset.unique.expressions.Operator.Parentheses
import com.unciv.models.ruleset.unique.expressions.Parser.EmptyBraces
import com.unciv.models.ruleset.unique.expressions.Parser.EmptyExpression
import com.unciv.models.ruleset.unique.expressions.Parser.InvalidConstant
import com.unciv.models.ruleset.unique.expressions.Parser.UnknownIdentifier
import com.unciv.models.ruleset.unique.expressions.Parser.UnmatchedBraces

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
    fun Pair<Int,String>.toToken(): Pair<Int,Token> {
        val (position, text) = this
        if (text.isEmpty()) throw EmptyExpression()
        assert(text.isNotBlank())
        if (text.first().isNumberLiteral())
            return position to Node.NumericConstant(text.toDouble())
        val operator = Operator.of(text)
        if (operator != null) return position to operator
        
        // Countable tokens must come here still wrapped in braces to avoid infinite recursion
        if (!text.startsWith('[') || !text.endsWith(']'))
            throw UnknownIdentifier(position, text)
        
        val countableText = text.substring(1, text.length - 1)
        return position to Node.Countable(countableText)
    }

    fun String.tokenize() = sequence<Pair<Int, String>> {
        /** If set, indicates we're in the middle of an identifier */
        var firstIdentifierPosition = -1
        /** If set, indicates we're in the middle of a number */
        var firstNumberPosition = -1
        /** If set, indicates we're in the middle of a countable */
        var openingBracePosition = -1
        var braceNestingLevel = 0

        suspend fun SequenceScope<Pair<Int, String>>.emitIdentifier(pos: Int) {
            assert(firstNumberPosition < 0)
            yield(firstIdentifierPosition to this@tokenize.substring(firstIdentifierPosition, pos))
            firstIdentifierPosition = -1
        }
        suspend fun SequenceScope<Pair<Int, String>>.emitNumericLiteral(pos: Int) {
            assert(firstIdentifierPosition < 0)
            val token = this@tokenize.substring(firstNumberPosition, pos)
            if (token.toDoubleOrNull() == null) throw InvalidConstant(firstNumberPosition, token)
            yield(firstNumberPosition to token)
            firstNumberPosition = -1
        }

        for ((pos, char) in this@tokenize.withIndex()) {
            if (firstIdentifierPosition >= 0) {
                if (char.isIdentifierContinuation()) continue
                emitIdentifier(pos)
            } else if (firstNumberPosition >= 0) {
                if (char.isNumberLiteral()) continue
                emitNumericLiteral(pos)
            }
            if (char.isWhitespace()) continue
            
            if (openingBracePosition >= 0) {
                if (char == '[')
                    braceNestingLevel++
                else if (char == ']')
                    braceNestingLevel--
                if (braceNestingLevel == 0) {
                    if (pos - openingBracePosition <= 1) throw EmptyBraces(pos)
                    yield(pos to this@tokenize.substring(openingBracePosition, pos + 1)) // Leave the braces
                    openingBracePosition = -1
                }
            } else if (char.isIdentifierStart()) {
                firstIdentifierPosition = pos
                continue
            } else if (char.isNumberLiteral()) {
                firstNumberPosition = pos
                continue
            } else if (char == '[') {
                openingBracePosition = pos
                assert(braceNestingLevel == 0)
                braceNestingLevel++
            } else if (char == ']') {
                throw UnmatchedBraces(pos)
            } else {
                yield(pos to char.toString())
            }
        }
        // End of expression, let's see if there's still anything open
        if (firstIdentifierPosition >= 0) emitIdentifier(this@tokenize.length)
        if (firstNumberPosition >= 0) emitNumericLiteral(this@tokenize.length)
        if (braceNestingLevel > 0) throw UnmatchedBraces(this@tokenize.length)
    }
}
