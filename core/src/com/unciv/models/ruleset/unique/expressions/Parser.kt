package com.unciv.models.ruleset.unique.expressions

import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.expressions.Operator.Parentheses
import com.unciv.models.ruleset.unique.expressions.Tokenizer.Token
import com.unciv.models.ruleset.unique.expressions.Tokenizer.toToken
import com.unciv.models.ruleset.unique.expressions.Tokenizer.tokenize
import org.jetbrains.annotations.VisibleForTesting

/**
 *  Parse and evaluate simple expressions
 *  - [eval] Does a one-off AST conversion and evaluation in one go
 *  - [parse] Builds the AST without evaluating
 *  - Supports [Countables] as terms, enclosed in square brackets (they're optional when the countable is a single identifier!).
 *
 *  Very freely inspired by [Keval](https://github.com/notKamui/Keval).
 *
 *  ##### Current Limitations:
 *  - Non-Alphanumeric tokens are always one character (ie needs work to support `<=` and similar operators).
 *  - Numeric constants do not support scientific notation.
 *  - Alphanumeric identifiers (can be matched with simple countables or function names) can _only_ contain letters and digits as defined by  defined by unicode properties, and '_'.
 *  - Functions with arity > 1 aren't supported. No parameter lists with comma - in fact, functions are just implemented as infix operators.
 *  - Only prefix Unary operators, e.g. no standard factorial notation.
 */
object Parser {
    /**
     *  Parse and evaluate an expression. If it needs to support countables, [context] should be supplied.
     */
    fun eval(text: String, context: StateForConditionals = StateForConditionals.EmptyState): Double =
        parse(text).eval(context)

    internal fun parse(text: String): Node {
        val tokens = text.tokenize().map { it.toToken() }
        val engine = StateEngine(tokens)
        return engine.buildAST()
    }

    @VisibleForTesting
    fun getASTDebugDescription(text: String) =
        parse(text).toString()

    //region Exceptions
    /** Parent of all exceptions [parse] can throw.
     *  If the exception caught is not [SyntaxError], then an Expression Countable should say NO "that can't possibly an expression". */
    open class ParsingError(override val message: String, val position:Int) : Exception()
    /** Less severe than [ParsingError].
     *  It allows an Expression Countable to say "Maybe", meaning the string might be of type Expression, but malformed. */
    open class SyntaxError(message: String, position: Int) : ParsingError(message, position)
    class UnmatchedBraces(position: Int) : ParsingError("Unmatched square braces", position)
    class EmptyBraces(position: Int) : ParsingError("Empty square braces", position)
    class UnmatchedParentheses(position: Int, name: String) : SyntaxError("Unmatched $name parenthesis", position)
    internal class UnexpectedToken(position: Int, expected: Token, found: Token) : ParsingError("Unexpected token: $found instead of $expected", position)
    class MissingOperand(position: Int) : SyntaxError("Missing operand", position)
    class InvalidConstant(position: Int, text: String) : SyntaxError("Invalid constant: $text", position)
    class MalformedCountable(position: Int, countable: Countables, text: String) : SyntaxError("\"$text\" seems to be a Countable(${countable.name}), but is malformed", position)
    
    class EvaluatingCountableWithoutRuleset(position: Int, text: String) : ParsingError("Evaluating countable \"$text\" without ruleset", position)
    class UnknownCountable(position: Int, text: String) : ParsingError("Unknown countable: \"$text\"", position)
    class UnknownIdentifier(position: Int, text: String) : ParsingError("Unknown identifier: \"$text\"", position)
    class EmptyExpression : ParsingError("Empty expression", 0)
    //endregion

    /** Marker for beginning of the expression */
    private data object StartToken : Token {
        override fun toString() = "start of expression"
    }
    /** Marker for end of the expression */
    private data object EndToken : Token {
        override fun toString() = "end of expression"
    }

    private class StateEngine(input: Sequence<Pair<Int,Token>>) {
        private var currentToken: Token = StartToken
        private var currentPosition: Int = 0
        private val iterator = input.iterator()
        private var openParenthesesCount = 0

        private fun expect(expected: Token) {
            if (currentToken == expected) return
            if (expected == Parentheses.Closing && currentToken == EndToken)
                throw UnmatchedParentheses(currentPosition, Parentheses.Opening.name.lowercase())
            if (expected == EndToken && currentToken == Parentheses.Closing)
                throw UnmatchedParentheses(currentPosition, Parentheses.Closing.name.lowercase())
            throw UnexpectedToken(currentPosition, expected, currentToken)
        }

        private fun next() {
            if (currentToken == Parentheses.Opening) {
                openParenthesesCount++
            } else if (currentToken == Parentheses.Closing) {
                if (openParenthesesCount == 0)
                    throw UnmatchedParentheses(currentPosition, Parentheses.Closing.name.lowercase())
                openParenthesesCount--
            }
            if (iterator.hasNext()){
                val (position, token) = iterator.next()
                currentToken = token
                currentPosition = position
            } else {
                currentToken = EndToken
                // TODO: Not sure what to do about current position here
            }
        }

        private fun handleUnary(): Node {
            val operator = currentToken.fetchUnaryOperator()
            next()
            return Node.UnaryOperation(operator, fetchOperand())
        }

        private fun expression(minPrecedence: Int = 0): Node {
            var result = fetchOperand()
            while (currentToken.canBeBinary()) {
                val operator = currentToken.fetchBinaryOperator()
                if (operator.precedence < minPrecedence) break
                next()
                val newPrecedence = if (operator.isLeftAssociative) operator.precedence + 1 else operator.precedence
                result = Node.BinaryOperation(operator, result, expression(newPrecedence))
            }
            return result
        }

        private fun fetchOperand(): Node {
            if (currentToken == StartToken) next()
            if (currentToken.canBeUnary()) {
                return handleUnary()
            } else if (currentToken == Parentheses.Opening) {
                next()
                val node = expression()
                expect(Parentheses.Closing)
                next()
                return node
            } else if (currentToken is Node.Constant || currentToken is Node.Countable) {
                val node = currentToken as Node
                next()
                return node
            } else {
                throw MissingOperand(currentPosition)
            }
        }

        fun buildAST(): Node {
            val node = expression()
            expect(EndToken)
            return node
        }
    }
}
