package com.unciv.uniques

import com.unciv.logic.map.HexCoord
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.expressions.Parser
import com.unciv.models.stats.Stats
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.RedirectOutput
import com.unciv.testing.RedirectPolicy
import com.unciv.testing.TestGame
import com.unciv.testing.runTestParcours
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
                println("AST: ${Parser.getASTDebugDescription(expression)}")
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
        val city = game.addCity(civ, game.getTile(HexCoord.Zero))

        val input = listOf(
            "√[[Your] Cities]" to 1.0,
            "[Owned [worked] Tiles] / [Owned [unimproved] Tiles] * 100" to 100.0 / 6, // city center counts as improved
        )

        var fails = 0
        for ((expression, expected) in input) {
            val actual = try {
                Parser.eval(expression, GameContext(city))
            } catch (_: Parser.ParsingError) {
                null
            }
            if (actual != null && abs(actual - expected) < epsilon) continue
            if (actual == null)
                println("Expression \"$expression\" failed to evaluate, expected: $expected")
            else {
                println("AST: ${Parser.getASTDebugDescription(expression)}")
                println("Expression \"$expression\" evaluated to $actual, expected: $expected")
            }
            fails++
        }

        assertEquals("failure count", 0, fails)
    }

    @Test
    fun testIsStatsUsingCountables() {
        RulesetCache.loadRulesets(noMods = true)
        val ruleset = RulesetCache.getVanillaRuleset()

        runTestParcours("Test Stats.isStatsUsingCountables", { input: String ->
            Stats.isStatsUsingCountables(input, ruleset)
        },
            "+1 Faith", true,
            "[+(350 + 50 * [Era number]) * [Speed modifier for [Gold]] / 100] Gold", true,
            "+[42 / 5 + 9] Science, -[Remaining [Enemy] Civilizations] Happiness", true,
            "[+[year] / 100] Gold, [-√[turns] * 0.2] Culture", true,
            "+1 Trust", false,
            "+[42 / 5 + 9] Science, -[sqrt Cities + sqrt [Units] Happiness", false,
            "+[Dragons] Production", false,
        )
    }

    @Test
    fun testParseStatsUsingCountables() {
        val game = TestGame()
        val civ = game.addCiv()
        val context = GameContext(civ, gameInfo = game.gameInfo)

        // Stats violates the equality contract by providing a nonstandard `equals`, so we can't let `runTestParcours` do the comparison
        runTestParcours("Test Stats.parseUsingCountables", { pair: Pair<String, Stats> ->
            val actual = Stats.parseUsingCountables(pair.first, context)
            actual.equals(pair.second)
        },
            "+1 Faith" to Stats(faith = 1f), true,
            "+[42 / 5 + 9] Science, -[π] Happiness" to Stats(science = 17f, happiness = -3f), true,
            "[+[10^2] / 100] Gold, [-√(25) * 0.2] Culture" to Stats(gold = 1f, culture = -1f), true,
        )
    }
}
