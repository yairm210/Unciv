package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.models.ruleset.unique.expressions.Parser
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ulp

@RunWith(GdxTestRunner::class)
class ExpressionTests {
    private val epsilon = 100.0.ulp

    @Test
    fun testPrimitiveExpressions() {
        val input = listOf(
            ".98234792374" to .98234792374,
            "4 - 2 + 4 + 30 + 6" to 42.0,
            "2 + 4 * 10" to 42.0,
            "2 * 4 + 10" to 18.0,
            "42 / 7 / 2" to 3.0,
            "42 / 2 / 7" to 3.0,
            "666.66 % 7" to 666.66 % 7,
            "42424 * -1 % 7" to 3.0, // true modulo, not kotlin's -4242.0 % 7 == -4
            "2 ^ 3 ^ 2" to 512.0,
            "pi * .5" to PI / 2,
            "(2+1.5)*(4+10)" to (2 + 1.5) * (4 + 10),
            "+- -+-1" to -1.0,
        )

        var fails = 0
        for ((expression, expected) in input) {
            val actual = try {
                Parser.eval(expression)
            } catch (_: Parser.ParsingError) {
                null
            }
            if (actual != null && abs(actual - expected) < epsilon) continue
            if (actual == null)
                println("Expression \"$expression\" failed to evaluate, expected: $expected")
            else {
                println("AST: ${Parser.getASTDebugDescription(expression, null)}")
                println("Expression \"$expression\" evaluated to $actual, expected: $expected")
            }
            fails++
        }

        assertEquals("failure count", 0, fails)
    }

    @Test
    fun testInvalidExpressions() {
        val input = listOf(
            "fake_function(2)" to Parser.UnknownIdentifier::class,
            "[fake countable]" to Parser.UnknownCountable::class,
            "98.234.792.374" to Parser.InvalidConstant::class,
            "" to Parser.MissingOperand::class,
            "() - 2" to Parser.MissingOperand::class,
            "((4 + 2) * 2" to Parser.UnmatchedParentheses::class,
            "(3 + 9) % 2)" to Parser.UnmatchedParentheses::class,
            "1 + []" to Parser.EmptyBraces::class,
            "1 + [[Your] Cities]]" to Parser.UnmatchedBraces::class,
            "[[[embarked] Units] + 1" to Parser.UnmatchedBraces::class,
        )

        var fails = 0
        for ((expression, expected) in input) {
            var result: Exception? = null
            try {
                Parser.eval(expression)
            } catch (ex: Exception) {
                result = ex
            }
            if (result != null && expected.isInstance(result)) continue
            if (result == null)
                println("Expression \"$expression\" should throw ${expected.simpleName} but didn't")
            else
                println("Expression \"$expression\" threw ${result::class.simpleName}, expected: ${expected.simpleName}")
            fails++
        }

        assertEquals("failure count", 0, fails)
    }

    @Test
    fun testExpressionsWithCountables() {
        val game = TestGame()
        game.makeHexagonalMap(2)
        val civ = game.addCiv()
        val city = game.addCity(civ, game.getTile(Vector2.Zero))

        val input = listOf(
            "√[[Your] Cities]" to 1.0,
            "[Owned [worked] Tiles] / [Owned [unimproved] Tiles] * 100" to 100.0 / 6, // city center counts as improved
        )

        var fails = 0
        for ((expression, expected) in input) {
            val actual = try {
                Parser.eval(expression, game.ruleset, city.state)
            } catch (_: Parser.ParsingError) {
                null
            }
            if (actual != null && abs(actual - expected) < epsilon) continue
            if (actual == null)
                println("Expression \"$expression\" failed to evaluate, expected: $expected")
            else {
                println("AST: ${Parser.getASTDebugDescription(expression, game.ruleset)}")
                println("Expression \"$expression\" evaluated to $actual, expected: $expected")
            }
            fails++
        }

        assertEquals("failure count", 0, fails)
    }
}
