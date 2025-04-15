package com.unciv.models.ruleset.unique.expressions

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.ICountable
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
     *  Parse and evaluate an expression. If it needs to support countables, [ruleset] and [context] should be supplied.
     */
    fun eval(text: String, ruleset: Ruleset? = null, context: StateForConditionals = StateForConditionals.EmptyState): Double =
        parse(text, ruleset).eval(context)

    internal fun parse(text: String, ruleset: Ruleset?): Node {
        val tokens = text.tokenize().map { it.toToken(ruleset) }
        val engine = StateEngine(tokens)
        return engine.buildAST()
    }

    @VisibleForTesting
    fun getASTDebugDescription(text: String, ruleset: Ruleset?) =
        parse(text, ruleset).toString()

    //region Exceptions
    /** Parent of all exceptions [parse] can throw.
     *  If the exception caught is not [SyntaxError], then an Expression Countable should say NO "that can't possibly an expression". */
    open class ParsingError(override val message: String, open val severity: ICountable.MatchResult = ICountable.MatchResult.No) : Exception()
    /** Less severe than [ParsingError].
     *  It allows an Expression Countable to say "Maybe", meaning the string might be of type Expression, but malformed. */
    open class SyntaxError(message: String) : ParsingError(message, ICountable.MatchResult.Maybe)
    class UnmatchedBraces(pos: Int) : ParsingError("Unmatched square braces at $pos")
    class EmptyBraces : ParsingError("Empty square braces")
    class UnmatchedParentheses(name: String) : SyntaxError("Unmatched $name parenthesis")
    internal class UnexpectedToken(expected: Token, found: Token) : ParsingError("Unexpected token: $found instead of $expected")
    class MissingOperand : SyntaxError("Missing operand")
    class InvalidConstant(text: String) : SyntaxError("Invalid constant: $text")
    class MalformedCountable(countable: Countables, text: String) : SyntaxError("Token \"$text\" seems to be a Countable(${countable.name}), but is malformed")
    class UnrecognizedToken : ParsingError("Unrecognized token")
    class InternalError : ParsingError("Internal error")
    //endregion

    /** Marker for beginning of the expression */
    private data object StartToken : Token {
        override fun toString() = "start of expression"
    }
    /** Marker for end of the expression */
    private data object EndToken : Token {
        override fun toString() = "end of expression"
    }

    private class StateEngine(input: Sequence<Token>) {
        private var currentToken: Token = StartToken
        private val iterator = input.iterator()
        private var openParenthesesCount = 0

        private fun expect(expected: Token) {
            if (currentToken == expected) return
            if (expected == Parentheses.Closing && currentToken == EndToken)
                throw UnmatchedParentheses(Parentheses.Opening.name.lowercase())
            if (expected == EndToken && currentToken == Parentheses.Closing)
                throw UnmatchedParentheses(Parentheses.Closing.name.lowercase())
            throw UnexpectedToken(expected, currentToken)
        }

        private fun next() {
            if (currentToken == Parentheses.Opening) {
                openParenthesesCount++
            } else if (currentToken == Parentheses.Closing) {
                if (openParenthesesCount == 0)
                    throw UnmatchedParentheses(Parentheses.Closing.name.lowercase())
                openParenthesesCount--
            }
            currentToken = if (iterator.hasNext()) iterator.next() else EndToken
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
                throw MissingOperand()
            }
        }

        fun buildAST(): Node {
            val node = expression()
            expect(EndToken)
            return node
        }
    }
}
