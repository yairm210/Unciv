package com.unciv.uniques

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Ruleset
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
    private lateinit var game: TestGame
    private lateinit var civ: Civilization
    private lateinit var city: City

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
        setupGame()
        // one coal provided locally
        val providesCoal = game.createBuilding("Provides [1] [Coal]")
        city.cityConstructions.addBuilding(providesCoal)
        // one globally
        UniqueTriggerActivation.triggerUnique(Unique("Provides [1] [Coal] <for [2] turns>"), civ)
        val providesFaithPerCoal = game.createBuilding("[+1 Faith] [in this city] <for every [Coal]>")
        city.cityConstructions.addBuilding(providesFaithPerCoal)
        assertEquals(2f, city.cityStats.currentCityStats.faith)
    }

    @Test
    fun testStatsCountables() {
        setupGame()
        fun verifyStats(state: StateForConditionals) {
            for (stat in Stat.entries) {
                val countableResult = Countables.Stats.eval(stat.name, state)
                val expected = if (stat == Stat.Happiness) civ.getHappiness()
                else state.getStatAmount(stat)
                assertEquals("Testing $stat countable:", countableResult, expected)
            }
        }

        val providesStats =
            game.createBuilding("[+1 Gold, +2 Food, +3 Production, +4 Happiness, +3 Science, +2 Culture, +1 Faith] [in this city] <when number of [Cities] is equal to [1]>")
        city.cityConstructions.addBuilding(providesStats)
        verifyStats(StateForConditionals(civ, city))

        val city2 = game.addCity(civ, game.tileMap[-2,0])
        val providesStats2 =
            game.createBuilding("[+3 Gold, +2 Food, +1 Production, -4 Happiness, +1 Science, +2 Culture, +3 Faith] [in this city] <when number of [Cities] is more than [1]>")
        city2.cityConstructions.addBuilding(providesStats2)
        verifyStats(StateForConditionals(civ, city2))
    }

    @Test
    fun testOwnedTilesCountable() {
        setupGame()
        UniqueTriggerActivation.triggerUnique(Unique("Turn this tile into a [Coast] tile"), civ, tile = game.tileMap[-3,0])
        UniqueTriggerActivation.triggerUnique(Unique("Turn this tile into a [Coast] tile"), civ, tile = game.tileMap[3,0])

        game.addCity(civ, game.tileMap[-2,0], initialPopulation = 9)
        val tests = listOf(
            "Owned [All] Tiles" to 14,
            "Owned [worked] Tiles" to 8,
            "Owned [Coastal] Tiles" to 6,
            "Owned [Coast] Tiles" to 2,
            "Owned [Land] Tiles" to 12,
            "Owned [Farm] Tiles" to 0,
        )
        for ((test, expected) in tests) {
            val actual = Countables.getCountableAmount(test, StateForConditionals(civ))
            assertEquals("Testing `$test` countable:", expected, actual)
        }
    }

    @Test
    fun testFilteredCitiesCountable() {
        setupGame()
        UniqueTriggerActivation.triggerUnique(Unique("Turn this tile into a [Coast] tile"), civ, tile = game.tileMap[-3,0])

        val city2 = game.addCity(civ, game.tileMap[-2,0], initialPopulation = 9)
        city2.isPuppet = true
        val tests = listOf(
            "[Capital] Cities" to 1,
            "[Puppeted] Cities" to 1,
            "[Coastal] Cities" to 1,
            "[Your] Cities" to 2,
        )
        for ((test, expected) in tests) {
            val actual = Countables.getCountableAmount(test, StateForConditionals(civ))
            assertEquals("Testing `$test` countable:", expected, actual)
        }
    }

    @Test
    fun testFilteredBuildingsCountable () {
        setupGame(withCiv = false)
        val building = game.createBuilding("Ancestor Tree") // That's a filtering Unique, not the building name 
        building[Stat.Culture] = 1f

        civ = game.addCiv(
            "[+1 Culture] from all [Ancestor Tree] buildings <for every [1] [[Ancestor Tree] Buildings]> <when number of [[Ancestor Tree] Buildings] is more than [1]>",
            "[+50]% [Culture] from every [Ancestor Tree] <when number of [[Ancestor Tree] Buildings] is more than [0]>",
        )
        city = game.addCity(civ, game.tileMap[2,0])
        city.cityConstructions.addBuilding(building)
        val city2 = game.addCity(civ, game.tileMap[-2,0])
        city2.cityConstructions.addBuilding(building)

        // updateStatsForNextTurn won't run the city part because no happiness change across a boundary
        for (city3 in civ.cities)
            city3.cityStats.update(updateCivStats = false)
        civ.updateStatsForNextTurn()

        // Expect: (1 Palace + 1 Base Ancestor Tree + 2 for-every) * 1.5 = 6
        val capitalCulture = city.cityStats.currentCityStats.culture
        // Expect: capitalCulture + (1 Base Ancestor Tree + 2 for-every) * 1.5 = 10.5
        val civCulture = civ.stats.statsForNextTurn.culture

        assertEquals(6f, capitalCulture, 0.005f)
        assertEquals(10.5f, civCulture, 0.005f)
    }

    @Test
    fun testRulesetValidation() {
        setupGame(
            "[+42 Faith] <when number of [turns] is less than [42]>",
            "[-1 Faith] <for every [turns]> <when number of [turns] is between [0] and [41]>",
            "[+1 Happiness] <for every [City-States]>", // 1 deprecation
            "[+1 Happiness] <for every [Remaining [City-State] Civilizations]>",
            "[+1 Happiness] <for every [[42] Monkeys]>", // 1 not a countable, 1 monkeys
            "[+1 Gold] <when number of [year] is equal to [countable]>", // 1 not a countable
            "[+1 Food] <when number of [-0] is different than [+0]>",
            "[+1 Food] <when number of [5e1] is more than [0.5]>", // 2 not a countable
            "[+1 Food] <when number of [0x12] is between [.99] and [99.]>",  // 3 not a countable
            "[+1 Food] <when number of [[~Nonexisting~] Cities] is between [[Annexed] Cities] and [Cities]>",  // 1 not a countable
            "[+1 Food] <when number of [[Paratrooper] Units] is between [[Air] Units] and [Units]>",
            "[+1 Food] <when number of [[~Bogus~] Units] is between [[Land] Units] and [[Air] Units]>", // 1 not a countable
            "[+1 Food] <when number of [[Barbarian] Units] is between [[Japanese] Units] and [[Embarked] Units]>", // 1 not a countable
            "[+1 Food] <when number of [[Science] Buildings] is between [[Wonder] Buildings] and [[All] Buildings]>",
            "[+1 Food] <when number of [[42] Buildings] is between [[Universe] Buildings] and [[Library] Buildings]>", // 2 not a countable
            "[+1 Food] <when number of [Remaining [Human player] Civilizations] is between [Remaining [City-State] Civilizations] and [Remaining [Major] Civilizations]>",
            "[+1 Food] <when number of [Remaining [city-state] Civilizations] is between [Remaining [la la la] Civilizations] and [Remaining [all] Civilizations]>", // 2 not a countable
            "[+1 Food] <when number of [Owned [Land] Tiles] is between [Owned [Desert] Tiles] and [Owned [All] Tiles]>",
            "[+1 Food] <when number of [Owned [worked] Tiles] is between [Owned [Camp] Tiles] and [Owned [Forest] Tiles]>",
            // Attention: `Barbarians` is technically a valid TileFilter because it's a CivFilter. Validation can't know it's meaningless for the OwnedTiles countable. 
            // `Food` is currently not a valid TileFilter, but might be, and the last is just wrong case for a valid filter and should be flagged.
            "[+1 Food] <when number of [Owned [Barbarians] Tiles] is between [Owned [Food] Tiles] and [Owned [strategic Resource] Tiles]>", // 2 not a countable
            "[+1 Food] <when number of [Iron] is between [Whales] and [Crab]>",
            "[+1 Food] <when number of [Cocoa] is between [Bison] and [Maryjane]>", // 3 not a countable
            withCiv = false // City founding would massively slow down
        )

        val errors = RulesetValidator(game.ruleset).getErrorList()
        var fails = 0
        val checks = sequenceOf(
            "deprecated" to "Countable.*deprecated.*" to 1,
            "monkeys" to ".*Monkeys.*not fit.*countable.*" to 1,
            "not a countable" to ".*does not fit parameter type countable.*" to 19,
        )

        println("Countables validation over synthetic test input:")
        for ((check, expected) in checks) {
            val (label, pattern) = check
            val actual = errors.count(pattern)
            if (actual == expected) continue
            fails++
            println("\tTest '$label' should find $expected errors, found: $actual")
        }

        assertEquals("failure count", 0, fails)
    }

    @Test
    fun testForEveryWithInvalidCountable() {
        setupGame(
            "[+42 Faith] <when number of [turns] is less than [42]>",
            "[+1 Happiness] <for every [City-States]>",
            "[+1 Happiness] <for every [[42] Monkeys]>",
        )

        val cityState = game.addCiv(cityStateType = game.ruleset.cityStateTypes.keys.first())
        game.addCity(cityState, game.tileMap[-2,0], true)
        civ.updateStatsForNextTurn()

        val happiness = Countables.getCountableAmount("Happiness", StateForConditionals(civ, city))
        // Base 9, -1 city, -3 population +1 deprecated countable should still work, but the bogus one should not
        assertEquals("Testing Happiness", 6, happiness)
    }

    private fun setupGame(vararg addGlobalUniques: String, withCiv: Boolean = true) {
        game = TestGame(*addGlobalUniques)
        game.makeHexagonalMap(3)
        if (!withCiv) return
        civ = game.addCiv()
        city = game.addCity(civ, game.tileMap[2,0])
    }

    private fun RulesetErrorList.count(pattern: String): Int {
        val regex = Regex(pattern)
        return count { it.text.matches(regex) }
    }
}
