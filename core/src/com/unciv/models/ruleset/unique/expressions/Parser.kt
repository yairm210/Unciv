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

        /**
         * EXAMPLE 1 - 1+2*3
         * We first build the '1' node in the initial fetchOperand().
         * Since the next token `+` is binary, we enter the while loop. But, do we build a `+` node now?
         * Let's see: We started minPrecedence of 0, so yes! We build (+ 1 PENDING), and run expression with minPrecedence 2.
         *  - This ensures that if we get another '+' token it will NOT be added to this tree, but this tree will be added to IT.
         *  - This is relevant in cases like e.g. 4/2/2, which is parsed as (4/2)/2, and not as 4/(2/2). That's what 'isLeftAssociative' is about.
         * Now we're in expression again, trying to build PENDING.
         * We fetch the operand - 2. The next token - * is binary, and has precedence 3.
         * OK, operator precedence (3) is >= than our minPrecedence (2), so we build a `*` node, and call expression again.
         * So far we have (+ 1 (* 2 PENDING)).
         * We fetch the next operand - 3 - and since the next token is EndToken, NOT binary, we return.
         * 
         * EXAMPLE 2 - 1*2+3
         * The first level is the same - we build (* 1 PENDING). We then call expression(3+1), since * has precedence 3.
         * We fetch the 2, we see the next token is binary. Do we build a + node now? 
         * The precedence is higher, so WE DO NOT. We break! The result here is just `2`,
         * We return to the first level, expression(0). So far we have (* 1 2), but the original `while` loop is still going! 
         * So we check the next token. It's STILL a binary operator, `+`! The precedence is still fine (2 >= 0)!
         * So we build a + node, taking the previous value (* 1 2) as the left operand. (+ (* 1 2) PENDING)
         * Next expression() call returns just 3, we put it in (+ (* 1 2) 3), check next token. It's EndToken, not binary, we return.
         */
        private fun expression(
            /** Precedence of the operator that called this function.
             * Each node under an operator will always be of equal or higher precenence */
            minPrecedence: Int = 0): Node {
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
