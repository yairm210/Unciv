package com.unciv.logic.filter

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests [Technology][Technology] and [Era][com.unciv.models.ruleset.tech.Era] filters*/
@RunWith(GdxTestRunner::class)
class TechFilterTests {
    private lateinit var game: TestGame
    private lateinit var civ: Civilization

    @Before
    fun initTheWorld() {
        setupModdedGame()
    }

    /** Tests [Era.matchesFilter][com.unciv.models.ruleset.tech.Era.matchesFilter] */
    @Test
    fun testEraMatchesFilter() {
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
        val failures = ArrayList<String?>()
        for ((eraName, tests) in eraTests) {
            try {
                val era = game.ruleset.eras[eraName]
                assertNotNull(era)
                for ((filter, expected) in tests) {
                    val actual = era!!.matchesFilter(filter, state)
                    assertEquals("Testing that `$era` matchesFilter `$filter`:", expected, actual)
                }
            }
            catch (error: AssertionError) {
                failures.add(error.message)
            }
        }
        if (failures.any()) {
            println(failures.joinToString("\n"))
            throw AssertionError()
        }
    }
    
    @Test
    fun testTechMatchesFilter() {
        val testTechs = listOf(
            "Agriculture",
            "Pottery",
            "Sailing",
            "Animal Husbandry",
            "Optics"
        )
        val techObjects = testTechs.mapNotNull { game.ruleset.technologies[it] }
        val filters = listOf(
            "All" to (listOf("Agriculture",
                "Pottery",
                "Sailing",
                "Animal Husbandry",
                "Optics") to 5),
            "Ancient era" to (listOf("Sailing", "Agriculture", "Pottery", "Animal Husbandry") to 4),
            "Classical era" to (listOf("Optics") to 1),
            "Modern era" to (emptyList<String>() to 0),
            "Starting tech" to (listOf("Agriculture") to 1),
            "Pottery" to (listOf("Pottery") to 1),
            "Archery" to (emptyList<String>() to 0)
        )
        val failures = ArrayList<String>()
        for (test in filters) {
            val filtered = techObjects.filter { it.matchesFilter(test.first) }
            try {
                Assert.assertTrue(filtered.map { it.name }.containsAll(test.second.first))
                Assert.assertTrue(filtered.size == test.second.second)
            }
            catch (_: AssertionError) {
                failures.add("filter: $filtered\ntest: ${test.second.first}")
            }
        }
        if (failures.any()) {
            println(failures.joinToString("\n"))
            throw AssertionError()
        }
    }

    private fun setupModdedGame(): Ruleset {
        game = TestGame()
        game.makeHexagonalMap(3)
        civ = game.addCiv()
        return game.ruleset
    }
}
