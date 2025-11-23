package com.unciv.uniques

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.validation.RulesetValidator
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.models.stats.Stat
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

// TODO better coverage:
//      - Each modifier using UniqueParameterType.Countable
//      - Each actual Countables enum instance

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
                "Ancient era" to true,
                "Industrial era" to false,
                "Starting Era" to true,
                "pre-[Industrial era]" to true,
                "pre-[Ancient era]" to false,
                "post-[Ancient era]" to false
            ),
            "Industrial era" to listOf(
                "Ancient era" to false,
                "Industrial era" to true,
                "Starting Era" to false,
                "post-[Ancient era]" to true,
                "post-[Classical era]" to true,
                "post-[Industrial era]" to false,
                "pre-[Industrial era]" to false,
                "pre-[Modern era]" to true,
                "pre-[Classical era]" to false
            )
        )

        val state = GameContext(civ)
        for ((eraName, tests) in eraTests) {
            for ((filter, expected) in tests) {
                val era = game.ruleset.eras[eraName]
                assertNotNull(era)
                if (era != null) {
                    val actual = era.matchesFilter(filter, state)
                    assertEquals("Testing that `$era` matchesFilter `$filter`:", expected, actual)
                }
            }
        }
    }

    private fun setupModdedGame(vararg addGlobalUniques: String, withCiv: Boolean = true): Ruleset {
        game = TestGame(*addGlobalUniques)
        game.makeHexagonalMap(3)
        if (!withCiv) return game.ruleset
        civ = game.addCiv()
        //city = game.addCity(civ, game.tileMap[2,0])
        return game.ruleset
    }
}
