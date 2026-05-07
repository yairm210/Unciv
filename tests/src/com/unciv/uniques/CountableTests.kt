package com.unciv.uniques

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.validation.RulesetValidator
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.RedirectOutput
import com.unciv.testing.RedirectPolicy
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Modifier
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

// TODO better coverage:
//      - Each modifier using UniqueParameterType.Countable

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@Suppress("unused") // The parameter *is* used in `testAllCountablesAreCovered`
internal annotation class CoversCountable(vararg val countable: Countables)

@RunWith(GdxTestRunner::class)
class CountableTests {
    //region Helpers
    private lateinit var game: TestGame
    private lateinit var civ: Civilization
    private lateinit var city: City

    private fun setupModdedGame(vararg addGlobalUniques: String, withCiv: Boolean = true): Ruleset {
        game = TestGame(*addGlobalUniques)
        game.makeHexagonalMap(3)
        if (!withCiv) return game.ruleset
        civ = game.addCiv()
        city = game.addCity(civ, game.tileMap[2,0])
        return game.ruleset
    }

    /** Test whether a Countables instance has a _specific_ override - by name and parameter signature.
     *
     *  This uses Java reflection, pure kotlin reflection was tested and `declaredFunctions` seems broken.
     *  With kotlin reflection there should be a nice `overrides` extension,
     *  but that seems to be missing in 2.1.21 (and you'd need a lot of boilerplate to get there).
     */
    private fun Class<out Countables>.hasOverrideFor(name: String, vararg argTypes: Class<out Any?>): Boolean {
        try {
            getDeclaredMethod(name, *argTypes)
        } catch (_: NoSuchMethodException) {
            return false
        }
        // Verify there's actually a super method
        val superMethod = try {
            superclass.getDeclaredMethod(name, *argTypes)
        } catch (_: NoSuchMethodException) {
            return false
        }
        return !Modifier.isFinal(superMethod.modifiers)
    }
    //endregion

    //region Meta and General tests
    @Test
    fun testCountableConventions() {
        var fails = 0
        println("Reflection check of the Countables class:")
        for (instance in Countables.entries) {
            val instanceClazz = instance::class.java

            val matches1Overridden = instanceClazz.hasOverrideFor("matches", String::class.java)
            val matches2Overridden = instanceClazz.hasOverrideFor("matches", String::class.java, Ruleset::class.java)
            if (matches1Overridden && matches2Overridden) {
                println("`$instance` overrides both `matches` overloads. You either need a Ruleset to match or you don't.")
                fails++
            } else if (matches1Overridden && instance.matchesWithRuleset) {
                println("`$instance` overrides the `matches` overload without ruleset but has `matchesWithRuleset` true.")
                fails++
            } else if (matches2Overridden && !instance.matchesWithRuleset) {
                println("`$instance` overrides the `matches` overload with ruleset but has `matchesWithRuleset` false.")
                fails++
            }

            val matchesOverridden = matches1Overridden || matches2Overridden
            if (instance.text.isEmpty() && !matchesOverridden) {
                println("`$instance` has no `text` but fails to override `matches`.")
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
    @RedirectOutput(RedirectPolicy.Show)
    fun testAllCountablesAreCovered() {
        val actual = CountableTests::class.declaredFunctions.asSequence()
            .plus(ExpressionTests::class.declaredFunctions)
            .mapNotNull { it.findAnnotation<CoversCountable>() }
            .flatMap { it.countable.asIterable() }
            .toSet()
        val expected = Countables.entries.filterNot { it::class.hasAnnotation<Deprecated>() }.toSet()
        if (actual == expected) return
        val missing = (expected - actual).sorted() // by ordinal ergo source order
        println("Every Countable should be covered by a unit test.\nMissing: $missing")
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
    fun testAllCountableAutocompleteValuesMatch() {
        RulesetCache.loadRulesets(noMods = true)
        val ruleset = RulesetCache.getVanillaRuleset()
        for (countable in Countables.entries) {
            val knownValues = countable.getKnownValuesForAutocomplete(ruleset)
            for (value in knownValues) {
                val matchedCountable = Countables.getMatching(value, ruleset)
                assertEquals(
                    "Countable ${countable.name} should match its own autocomplete value: $value",
                    countable, matchedCountable
                )
            }
        }
    }

    @Test
    fun testPlaceholderParams() {
        val text = "when number of [Iron] is equal to [3 * 2 + [Iron] + [bob]]"
        val placeholderText = text.getPlaceholderText()
        assertEquals("when number of [] is equal to []", placeholderText)
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
            "[+1 Food] <when number of [[~Nonexisting~] Cities] is between [[Annexed] Cities] and [Cities]>" to 1,
            "[+1 Food] <when number of [[Paratrooper] Units] is between [[Air] Units] and [Units]>" to 0,
            "[+1 Food] <when number of [[~Bogus~] Units] is between [[Land] Units] and [[Air] Units]>" to 1,
            "[+1 Food] <when number of [[Barbarian] Units] is between [[Japanese] Units] and [[Embarked] Units]>" to 1,
            "[+1 Food] <when number of [[Science] Buildings] is between [[Wonder] Buildings] and [[All] Buildings]>" to 0,
            "[+1 Food] <when number of [[42] Buildings] is between [[Universe] Buildings] and [[Library] Buildings]>" to 2,
            "[+1 Food] <when number of [Adopted [Tradition] Policies] is between [Adopted [[Tradition] branch] Policies] and [Adopted [all] Policies]>" to 0,
            "[+1 Food] <when number of [Adopted [[Legalism] branch] Policies] is between [Adopted [Folklore] Policies] and [Completed Policy branches]>" to 2,
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
        val notACountableRegex = Regex(""".*parameter "(.*)" ${UniqueValidator.whichDoesNotFitParameterType} countable.*""")

        val ruleset = setupModdedGame(
            *testData.map { it.first }.toTypedArray(),
            withCiv = false // City founding would only slow this down
        )
        ruleset.modOptions.isBaseRuleset = true  // To get ruleset-specific validation

        val errors = RulesetValidator.create(ruleset).getErrorList()
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

        val cityState = game.addCiv(cityStateType = game.ruleset.cityStateTypes.keys.first())
        game.addCity(cityState, game.tileMap[-2,0], true)
        civ.updateStatsForNextTurn()

        val happiness = Countables.getCountableAmount("Happiness", GameContext(civ, city))
        // Base 9, -1 city, -3 population +1 deprecated countable should still work, but the bogus one should not
        assertEquals("Testing Happiness", 6, happiness)
    }
    //endregion

    @Test
    fun countableCanUseContext() {
        setupModdedGame()
        val cityCount = Countables.getCountableAmount("Cities", civ.state)
        assertEquals("City count should match the civ's city count", cityCount, 1)

        val unit = game.createBaseUnit()
        val fakeUnit = game.createBaseUnit(uniques = arrayOf("[This Unit] is destroyed", "Free [${unit.name}] appears <for every [[Cities] + [3]]>"))
        civ.units.placeUnitNearTile(city.location, fakeUnit)
        val unitCount = Countables.getCountableAmount("Units", civ.state)
        assertEquals("Units should get a context implictly with a countable", unitCount, 4)

        setupModdedGame() // reset
        val resource = game.createResource("Stockpiled")
        val localResource = game.createResource("Stockpiled", "City-level resource")
        val building1 = game.createBuilding("Instantly provides [5] [${resource.name}]")
        val building2 = game.createBuilding("Instantly provides [1] [${localResource.name}] <for every [[${resource.name}] - [3]]>")
        city.cityConstructions.addBuilding(building1)
        city.cityConstructions.addBuilding(building2)
        var resourceAmount = Countables.getCountableAmount(localResource.name, civ.state)
        assertEquals("Civ state alone shouldn't see citywide resources", resourceAmount, 0)
        resourceAmount = Countables.getCountableAmount(localResource.name, city.state)
        assertEquals("City state should see city resources", resourceAmount, 2)
    }
}
