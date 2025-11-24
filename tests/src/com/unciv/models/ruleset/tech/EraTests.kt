package com.unciv.models.ruleset.tech

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Tests [Era][com.unciv.models.ruleset.tech.Era] */
@RunWith(GdxTestRunner::class)
class EraTests {
    private lateinit var game: TestGame
    private lateinit var civ: Civilization

    @Before
    fun initTheWorld() {
        game = TestGame()
        civ = game.addCiv()
    }

    /** Tests [Era.matchesFilter][com.unciv.models.ruleset.tech.Era.matchesFilter] */
    @Test
    fun testMatchesFilter() {
        setupModdedGame()
        val eraTests = hashMapOf(
            "Ancient era" to listOf(
                "any era" to true,
                "Ancient era" to true,
                "Industrial era" to false,
                "Starting Era" to true,
                "pre-[Industrial era]" to true,
                "pre-[Ancient era]" to false,
                "post-[Ancient era]" to false,
                "Invalid Filter" to false
            ),
            "Industrial era" to listOf(
                "any era" to true,
                "Ancient era" to false,
                "Industrial era" to true,
                "Starting Era" to false,
                "post-[Ancient era]" to true,
                "post-[Classical era]" to true,
                "post-[Industrial era]" to false,
                "pre-[Industrial era]" to false,
                "pre-[Modern era]" to true,
                "pre-[Classical era]" to false,
                "pre-[Invalid era]" to false,
                "post-[Invalid era]" to false
            )
        )

        val state = GameContext(civ)
        for ((eraName, tests) in eraTests) {
            val era = game.ruleset.eras[eraName]
            assertNotNull(era)
            if (era != null) {
                for ((filter, expected) in tests) {
                    val actual = era.matchesFilter(filter, state)
                    assertEquals("Testing that `$era` matchesFilter `$filter`:", expected, actual)
                }
            }
        }
    }

    private fun setupModdedGame(): Ruleset {
        game = TestGame()
        game.makeHexagonalMap(3)
        civ = game.addCiv()
        return game.ruleset
    }
}
