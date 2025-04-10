package com.unciv.uniques

import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.validation.RulesetErrorList
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
    fun testCountableConventions() {
        fun Class<out Countables>.hasOverrideFor(name: String, vararg args: Class<out Any>): Boolean {
            try {
                getDeclaredMethod(name, *args)
            } catch (ex: NoSuchMethodException) {
                return false
            }
            return true
        }

        var fails = 0
        println("Reflection check of the Countables class:")
        for (instance in Countables::class.java.enumConstants) {
            val instanceClazz = instance::class.java

            val matchesRulesetOverridden = instanceClazz.hasOverrideFor("matches", String::class.java, Ruleset::class.java)
            val matchesPlainOverridden = instanceClazz.hasOverrideFor("matches", String::class.java)
            if (instance.matchesWithRuleset && !matchesRulesetOverridden) {
                println("`$instance` is marked as working _with_ a `Ruleset` but fails to override `matches(String,Ruleset)`,")
                fails++
            } else if (instance.matchesWithRuleset && matchesPlainOverridden) {
                println("`$instance` is marked as working _with_ a `Ruleset` but overrides `matches(String)` which is worthless.")
                fails++
            } else if (!instance.matchesWithRuleset && matchesRulesetOverridden) {
                println("`$instance` is marked as working _without_ a `Ruleset` but overrides `matches(String,Ruleset)` which is worthless.")
                fails++
            }
            if (instance.text.isEmpty() && !matchesPlainOverridden && !matchesRulesetOverridden) {
                println("`$instance` has no `text` but fails to override either `matches` overload.")
                fails++
            }

            val getErrOverridden = instanceClazz.hasOverrideFor("getErrorSeverity", String::class.java, Ruleset::class.java)
            if (instance.noPlaceholders && getErrOverridden) {
                println("`$instance` has no placeholders but overrides `getErrorSeverity` which is likely an error.")
                fails++
            } else if (!instance.noPlaceholders && !getErrOverridden) {
                println("`$instance` has placeholders that must be treated and therefore **must** override `getErrorSeverity` but does not.")
                fails++
            }
        }
        assertEquals("failure count", 0, fails)
    }

    @Test
    fun testAllCountableParametersAreUniqueParameterTypes() {
        for (countable in Countables.entries) {
            val parameters = countable.text.getPlaceholderParameters()
            for (parameter in parameters) {
                assertNotEquals("Countable ${countable.name} parameter $parameter is not a UniqueParameterType",
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

        game.addCity(civInfo, game.tileMap[-2,0], initialPopulation = 9)
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
        /** These are `Pair<String, Int>` with the second being the expected number of parameters to fail UniqueParameterType validation */
        val testData = listOf(
            "[+42 Faith] <when number of [turns] is less than [42]>" to 0,
            "[-1 Faith] <for every [turns]> <when number of [turns] is between [0] and [41]>" to 0,
            "[+1 Happiness] <for every [City-States]>" to 0, // +1 deprecation
            "[+1 Happiness] <for every [Remaining [City-State] Civilizations]>" to 0,
            "[+1 Happiness] <for every [[42] Monkeys]>" to 1, // +1 monkeys
            "[+1 Gold] <when number of [year] is equal to [countable]>" to 1,
            "[+1 Food] <when number of [-0] is different than [+0]>" to 0,
            "[+1 Food] <when number of [5e1] is more than [0.5]>" to 2,
            "[+1 Food] <when number of [0x12] is between [.99] and [99.]>" to 3,
            "[+1 Food] <when number of [[~Nonexisting~] Cities] is between [[Annexed] Cities] and [Cities]>" to 1,
            "[+1 Food] <when number of [[Paratrooper] Units] is between [[Air] Units] and [Units]>" to 0,
            "[+1 Food] <when number of [[~Bogus~] Units] is between [[Land] Units] and [[Air] Units]>" to 1,
            "[+1 Food] <when number of [[Barbarian] Units] is between [[Japanese] Units] and [[Embarked] Units]>" to 1,
            "[+1 Food] <when number of [[Science] Buildings] is between [[Wonder] Buildings] and [[All] Buildings]>" to 0,
            "[+1 Food] <when number of [[42] Buildings] is between [[Universe] Buildings] and [[Library] Buildings]>" to 2,
            "[+1 Food] <when number of [Remaining [Human player] Civilizations] is between [Remaining [City-State] Civilizations] and [Remaining [Major] Civilizations]>" to 0,
            "[+1 Food] <when number of [Remaining [city-state] Civilizations] is between [Remaining [la la la] Civilizations] and [Remaining [all] Civilizations]>" to 2,
            "[+1 Food] <when number of [Owned [Land] Tiles] is between [Owned [Desert] Tiles] and [Owned [All] Tiles]>" to 0,
            "[+1 Food] <when number of [Owned [worked] Tiles] is between [Owned [Camp] Tiles] and [Owned [Forest] Tiles]>" to 0,
            // Attention: `Barbarians` is technically a valid TileFilter because it's a CivFilter. Validation can't know it's meaningless for the OwnedTiles countable.
            // `Food` is currently not a valid TileFilter, but might be, and the last is just wrong case for a valid filter and should be flagged.
            "[+1 Food] <when number of [Owned [Barbarians] Tiles] is between [Owned [Food] Tiles] and [Owned [strategic Resource] Tiles]>" to 2,
            "[+1 Food] <when number of [Iron] is between [Whales] and [Crab]>" to 0,
            "[+1 Food] <when number of [Cocoa] is between [Bison] and [Maryjane]>" to 3,
        )
        val totalNotACountableExpected = testData.sumOf { it.second }
        val notACountableRegex = Regex(""".*parameter "(.*)" which does not fit parameter type countable.*""")

        val ruleset = setupModdedGame(*testData.map { it.first }.toTypedArray())
        ruleset.modOptions.isBaseRuleset = true  // To get ruleset-specific validation

        val errors = RulesetValidator(ruleset).getErrorList()
        var fails = 0

        println("Countables validation over synthetic test input:")
        for ((uniqueText, expected) in testData) {
            var actual = 0
            val badOnes = mutableListOf<String>()
            for (error in errors) {
                if (uniqueText !in error.text) continue
                val match = notACountableRegex.matchEntire(error.text) ?: continue
                actual++
                badOnes += match.groups[1]!!.value
            }
            if (actual == expected) continue
            fails++
            println("\tTest '$uniqueText' should find $expected errors, found: $actual, bad parameters: ${badOnes.joinToString()}")
        }

        val coarseChecks = sequenceOf(
            "deprecated" to Regex("Countable.*deprecated.*") to 1,
            "monkeys" to Regex(".*Monkeys.*not fit.*countable.*") to 1,
            "not a countable" to notACountableRegex to totalNotACountableExpected,
        )
        for ((check, expected) in coarseChecks) {
            val (label, regex) = check
            val actual = errors.count { it.text.matches(regex) }
            if (actual == expected) continue
            fails++
            println("\tTest '$label' should find $expected errors, found: $actual")
        }

        assertEquals("failure count", 0, fails)
    }

    @Test
    fun testForEveryWithInvalidCountable() {
        setupModdedGame(
            "[+42 Faith] <when number of [turns] is less than [42]>",
            "[+1 Happiness] <for every [City-States]>",
            "[+1 Happiness] <for every [[42] Monkeys]>",
        )

        game.makeHexagonalMap(3)
        civInfo = game.addCiv()
        city = game.addCity(civInfo, game.tileMap[2,0])

        val cityState = game.addCiv(cityStateType = game.ruleset.cityStateTypes.keys.first())
        game.addCity(cityState, game.tileMap[-2,0], true)
        civInfo.updateStatsForNextTurn()

        val happiness = Countables.getCountableAmount("Happiness", StateForConditionals(civInfo, city))
        // Base 9, -1 city, -3 population +1 deprecated countable should still work, but the bogus one should not
        assertEquals("Testing Happiness", 6, happiness)
    }

    private fun setupModdedGame(vararg uniques: String): Ruleset {
        val mod = Ruleset()
        mod.name = "Testing"
        for (unique in uniques)
            mod.globalUniques.uniques.add(unique)
        game = TestGame {
            RulesetCache[mod.name] = mod
            RulesetCache.getComplexRuleset(RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!, listOf(mod))
        }
        return game.ruleset
    }
}
