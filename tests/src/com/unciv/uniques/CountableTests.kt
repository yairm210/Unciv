package com.unciv.uniques

import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.*
import com.unciv.models.ruleset.validation.RulesetValidator
import com.unciv.models.stats.Stat
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

// TODO better coverage:
//      - Each modifier using UniqueParameterType.Countable
//      - Each actual Countables enum instance

@RunWith(GdxTestRunner::class)
class CountableTests {
    private var game = TestGame().apply { makeHexagonalMap(3) }
    private var civInfo = game.addCiv()
    private var city = game.addCity(civInfo, game.tileMap[2,0])


    @Test
    fun testAllCountableParametersAreUniqueParameterTypes() {
        for (countable in Countables.entries) {
            val parameters = countable.text.getPlaceholderParameters()
            for (parameter in parameters) {
                assertNotEquals("Countable ${countable.name} parameter ${parameter} is not a UniqueParameterType",
                    UniqueParameterType.safeValueOf(parameter), UniqueParameterType.Unknown)
            }
        }
    }

    @Test
    fun testPerCountableForGlobalAndLocalResources() {
        // one coal provided locally
        val providesCoal = game.createBuilding("Provides [1] [Coal]")
        city.cityConstructions.addBuilding(providesCoal)
        // one globally
        UniqueTriggerActivation.triggerUnique(Unique("Provides [1] [Coal] <for [2] turns>"), civInfo)
        val providesFaithPerCoal = game.createBuilding("[+1 Faith] [in this city] <for every [Coal]>")
        city.cityConstructions.addBuilding(providesFaithPerCoal)
        assertEquals(2f, city.cityStats.currentCityStats.faith)
    }

    @Test
    fun testStatsCountables() {
        fun verifyStats(state: StateForConditionals) {
            for (stat in Stat.entries) {
                val countableResult = Countables.Stats.eval(stat.name, state)
                val expected = if (stat == Stat.Happiness) civInfo.getHappiness()
                else state.getStatAmount(stat)
                assertEquals("Testing $stat countable:", countableResult, expected)
            }
        }

        val providesStats =
            game.createBuilding("[+1 Gold, +2 Food, +3 Production, +4 Happiness, +3 Science, +2 Culture, +1 Faith] [in this city] <when number of [Cities] is equal to [1]>")
        city.cityConstructions.addBuilding(providesStats)
        verifyStats(StateForConditionals(civInfo, city))

        val city2 = game.addCity(civInfo, game.tileMap[-2,0])
        val providesStats2 =
            game.createBuilding("[+3 Gold, +2 Food, +1 Production, -4 Happiness, +1 Science, +2 Culture, +3 Faith] [in this city] <when number of [Cities] is more than [1]>")
        city2.cityConstructions.addBuilding(providesStats2)
        verifyStats(StateForConditionals(civInfo, city2))
    }

    @Test
    fun testOwnedTilesCountable() {
        UniqueTriggerActivation.triggerUnique(Unique("Turn this tile into a [Coast] tile"), civInfo, tile = game.tileMap[-3,0])
        UniqueTriggerActivation.triggerUnique(Unique("Turn this tile into a [Coast] tile"), civInfo, tile = game.tileMap[3,0])

        val city2 = game.addCity(civInfo, game.tileMap[-2,0], initialPopulation = 9)
        val tests = listOf(
            "Owned [All] Tiles" to 14,
            "Owned [worked] Tiles" to 8,
            "Owned [Coastal] Tiles" to 6,
            "Owned [Coast] Tiles" to 2,
            "Owned [Land] Tiles" to 12,
            "Owned [Farm] Tiles" to 0,
        )
        for ((test, expected) in tests) {
            val actual = Countables.getCountableAmount(test, StateForConditionals(civInfo))
            assertEquals("Testing `$test` countable:", expected, actual)
        }
    }

    @Test
    fun testFilteredCitiesCountable() {
        UniqueTriggerActivation.triggerUnique(Unique("Turn this tile into a [Coast] tile"), civInfo, tile = game.tileMap[-3,0])

        val city2 = game.addCity(civInfo, game.tileMap[-2,0], initialPopulation = 9)
        city2.isPuppet = true
        val tests = listOf(
            "[Capital] Cities" to 1,
            "[Puppeted] Cities" to 1,
            "[Coastal] Cities" to 1,
            "[Your] Cities" to 2,
        )
        for ((test, expected) in tests) {
            val actual = Countables.getCountableAmount(test, StateForConditionals(civInfo))
            assertEquals("Testing `$test` countable:", expected, actual)
        }
    }

    @Test
    fun testRulesetValidation() {
        val mod = Ruleset().apply {
            name = "Testing"
            globalUniques.uniques.add("[+42 Faith] <when number of [turns] is less than [42]>")
            globalUniques.uniques.add("[-1 Faith] <for every [turns]> <when number of [turns] is less than [42]>")
            globalUniques.uniques.add("[+1 Happiness] <for every [City-States]>")
            globalUniques.uniques.add("[+1 Happiness] <for every [[42] Monkeys]>")
        }
        game = TestGame {
            RulesetCache[mod.name] = mod
            RulesetCache.getComplexRuleset(RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!, listOf(mod))
        }
        val ruleset = game.ruleset

        val errors = RulesetValidator(ruleset).getErrorList()
        val countDeprecations = errors.count { 
            it.text.matches(Regex("Countable.*deprecated.*"))
        }
        assertEquals("The test mod should flag one deprecated Countable", 1, countDeprecations)
        val countInvalid = errors.count {
            it.text.matches(Regex(".*Monkeys.*not fit.*countable.*"))
        }
        assertEquals("The test mod should flag one invalid Countable", 1, countInvalid)

        game.makeHexagonalMap(3)
        civInfo = game.addCiv()
        city = game.addCity(civInfo, game.tileMap[2,0])

        val cityState = game.addCiv(cityStateType = game.ruleset.cityStateTypes.keys.first())
        val cityStateCity = game.addCity(cityState, game.tileMap[-2,0], true)
        civInfo.updateStatsForNextTurn()

        val happiness = Countables.getCountableAmount("Happiness", StateForConditionals(civInfo, city))
        // Base 9, -1 city, -3 population +1 deprecated countable should still work, but the bogus one should not
        assertEquals("Testing Happiness", 6, happiness)
    }
}
