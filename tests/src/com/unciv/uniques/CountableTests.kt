package com.unciv.uniques

import com.unciv.Constants
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
import com.unciv.testing.runTestParcours
import java.lang.reflect.Modifier
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

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
        if (!com.unciv.platform.PlatformCapabilities.current.backgroundThreadPools) return
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
    fun testAllCountablesAreCovered() {
        if (!com.unciv.platform.PlatformCapabilities.current.backgroundThreadPools) return
        val actual = sequenceOf(CountableTests::class.java, ExpressionTests::class.java)
            .flatMap { it.declaredMethods.asSequence() }
            .mapNotNull { it.getAnnotation(CoversCountable::class.java) }
            .flatMap { it.countable.asIterable() }
            .toSet()
        val expected = Countables.entries.filterNot { it::class.java.isAnnotationPresent(Deprecated::class.java) }.toSet()
        if (actual == expected) return
        val missing = (expected - actual).sorted() // by ordinal ergo source order
        Assert.fail("Every Countable should be covered by a unit test.\nMissing: $missing")
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
        if (!com.unciv.platform.PlatformCapabilities.current.backgroundThreadPools) return
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

    //region Coverage for specific Countables
    @Test
    @CoversCountable(Countables.TileResources) // Just barely
    fun testPerCountableForGlobalAndLocalResources() {
        setupModdedGame()
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
    @CoversCountable(Countables.StatOrResourcePerTurn)
    fun testStatOrResourcePerTurn() {
        setupModdedGame()
        city.cityConstructions.addBuilding(game.createBuilding(
            "Provides [5] [Coal]",
            "[+1 Gold, +3 Culture] [in this city]"
        ))
        val context = GameContext(civ)

        runTestParcours("Stat or resource per turn countable", { test: String ->
            Countables.getCountableAmount(test, context)
        },
            "[Gold] Per Turn", 4, // Palace provides 3
            "[Culture] Per Turn", 4, // Palace provides 1
            "[Coal] Per Turn", 5,
            "[Iron] Per Turn", 0,
            "[Null] Per Turn", null,
        )
    }

    @Test
    @CoversCountable(Countables.Stats)
    fun testStatsCountables() {
        setupModdedGame()
        fun verifyStats(context: GameContext) {
            for (stat in Stat.entries) {
                val countableResult = Countables.Stats.eval(stat.name, context)
                val expected = if (stat == Stat.Happiness) civ.getHappiness()
                else context.getStatAmount(stat)
                assertEquals("Testing $stat countable:", countableResult, expected)
            }
        }

        val providesStats =
            game.createBuilding("[+1 Gold, +2 Food, +3 Production, +4 Happiness, +3 Science, +2 Culture, +1 Faith] [in this city] <when number of [Cities] is equal to [1]>")
        city.cityConstructions.addBuilding(providesStats)
        verifyStats(GameContext(civ, city))

        val city2 = game.addCity(civ, game.tileMap[-2,0])
        val providesStats2 =
            game.createBuilding("[+3 Gold, +2 Food, +1 Production, -4 Happiness, +1 Science, +2 Culture, +3 Faith] [in this city] <when number of [Cities] is more than [1]>")
        city2.cityConstructions.addBuilding(providesStats2)
        verifyStats(GameContext(civ, city2))
    }

    @Test
    @CoversCountable(Countables.OwnedTiles)
    fun testOwnedTilesCountable() {
        setupModdedGame()
        UniqueTriggerActivation.triggerUnique(Unique("Turn this tile into a [Coast] tile"), civ, tile = game.tileMap[-3,0])
        UniqueTriggerActivation.triggerUnique(Unique("Turn this tile into a [Coast] tile"), civ, tile = game.tileMap[3,0])

        game.addCity(civ, game.tileMap[-2,0], initialPopulation = 9)
        val context = GameContext(civ)

        runTestParcours("Owned tiles countable", { test: String ->
            Countables.getCountableAmount(test, context)
        },
            "Owned [All] Tiles", 14,
            "Owned [worked] Tiles", 8,
            "Owned [Coastal] Tiles", 6,
            "Owned [Coast] Tiles", 2,
            "Owned [Land] Tiles", 12,
            "Owned [Farm] Tiles", 0,
        )
    }

    @Test
    @CoversCountable(Countables.FilteredCities)
    fun testFilteredCitiesCountable() {
        setupModdedGame()
        UniqueTriggerActivation.triggerUnique(Unique("Turn this tile into a [Coast] tile"), civ, tile = game.tileMap[-3,0])

        val city2 = game.addCity(civ, game.tileMap[-2,0], initialPopulation = 9)
        city2.isPuppet = true
        val context = GameContext(civ)

        runTestParcours("Filtered cities countable", { test: String ->
            Countables.getCountableAmount(test, context)
        },
            "[Capital] Cities", 1,
            "[Puppeted] Cities", 1,
            "[Coastal] Cities", 1,
            "[Your] Cities", 2,
        )
    }

    @Test
    @CoversCountable(Countables.FilteredBuildings, Countables.FilteredBuildingsByCivs)
    fun testFilteredBuildingsCountable () {
        setupModdedGame(withCiv = false)
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

        // FilteredBuildingsByCivs
        val civ2 = game.addCiv()
        val civ2city = game.addCity(civ2, game.tileMap[-2, -2])
        game.addCity(civ2, game.tileMap[2, 2])
        civ2city.cityConstructions.addBuilding(building)
        val tests = listOf(
            "[Ancestor Tree] Buildings" to 2,
            "[Ancestor Tree] Buildings by [All] Civilizations" to 3,
            "[Ancestor Tree] Buildings by [${civ.civName}] Civilizations" to 2,
            "[Ancestor Tree] Buildings by [${civ2.civName}] Civilizations" to 1,
            "[Ancestor Tree] Buildings by [City-State] Civilizations" to 0
        )
        for ((test, expected) in tests) {
            val actual = Countables.getCountableAmount(test, GameContext(civ))
            assertEquals("Testing `$test` countable:", expected, actual)
        }
    }

    @Test
    @CoversCountable(Countables.PolicyBranches, Countables.FilteredPolicies, Countables.FilteredPoliciesByCivs)
    fun testPoliciesCountables() {
        setupModdedGame()
        civ.policies.run {
            for (name in listOf(
                "Tradition", "Aristocracy", "Legalism", "Oligarchy", "Landed Elite", "Monarchy",
                "Liberty", "Citizenship", "Honor", "Piety"
            )) {
                freePolicies++
                val policy = getPolicyByName(name)
                adopt(policy)
            }
            // Don't use a fake Policy without a branch, the policyFilter would stumble over a lateinit.
            val taggedPolicyBranch = game.createPolicyBranch("Some marker")
            freePolicies++
            adopt(taggedPolicyBranch) // Will be completed as it has no member policies
        }

        // Add a second Civilization
        val civ2 = game.addCiv()
        game.addCity(civ2, game.tileMap[2,2])
        civ2.policies.run {
            freePolicies += 2
            adopt(getPolicyByName("Tradition"))
            adopt(getPolicyByName("Oligarchy"))
        }
        val context = GameContext(civ)

        runTestParcours("Filtered policies by filtered civs countable", { test: String ->
            Countables.getCountableAmount(test, context)
        },
            "Completed Policy branches", 2,               // Tradition and taggedPolicyBranch
            "Adopted [Tradition Complete] Policies", 1,
            "Adopted [[Tradition] branch] Policies", 7,   // Branch start and completion plus 5 members
            "Adopted [Liberty Complete] Policies", 0,
            "Adopted [[Liberty] branch] Policies", 2,     // Liberty has only 1 member adopted
            "Adopted [Some marker] Policies", 1,
            "Adopted [Military Tradition] Policies by [All] Civilizations", 0,
            "Adopted [Some marker] Policies by [All] Civilizations", 1,
            "Adopted [Oligarchy] Policies by [All] Civilizations", 2,
            "Adopted [Oligarchy] Policies by [City-State] Civilizations", 0,
        )
    }

    @Test
    @CoversCountable(Countables.EraNumber)
    fun testEraNumberCountable() {
        setupModdedGame()
        val context = GameContext(civ)

        runTestParcours("Era number countable", { tech: String ->
            civ.tech.addTechnology(tech, false)
            Countables.getCountableAmount("Era number", context)
        },
            "Agriculture", 0,
            "Optics", 1,
            "Archery", 1,
            "Navigation", 3,
        )
    }

    @Test
    @CoversCountable(Countables.Integer, Countables.Turns, Countables.Year, Countables.Expression)
    fun testTimeCountables() {
        setupModdedGame()
        val context = GameContext(civ)

        runTestParcours("Year and turns countables", { input: Pair<Int, String> ->
            game.gameInfo.turns = input.first
            Countables.getCountableAmount(input.second, context)
        },
            7 to "[year] - ([-4000] + [40] * [turns])", 0,
            42 to "[year] - (-4000 + 40 * [turns])", 0,
            124 to "[year]", 225,
        )
    }

    @Test
    @CoversCountable(Countables.GameSpeedModifier)
    fun testSpeedModifierCountable() {
        setupModdedGame()
        val context = GameContext(civ)

        runTestParcours("Game speed modifier countable", { input: Pair<String, String> ->
            game.gameInfo.gameParameters.speed = input.first
            game.gameInfo.setGlobalTransients()
            Countables.getCountableAmount("Speed modifier for [${input.second}]", context)
        },
            "Quick" to "Production", 67,
            "Standard" to "Gold", 100,
            "Marathon" to "Faith", 300,
        )
    }

    @Test
    @CoversCountable(Countables.Cities, Countables.Units)
    fun testCitiesAndUnitsCountable() {
        setupModdedGame()
        val city2 = game.addCity(civ, game.tileMap[-2,0])
        game.addUnit("Warrior", civ, city.getCenterTile())
        game.addUnit("Warrior", civ, city2.getCenterTile())
        game.addUnit("Scout", civ, game.tileMap.values.first())
        game.addUnit("Scout", civ, game.tileMap.values.last())
        val actual = Countables.getCountableAmount("[Cities] + 10 * [Units]", GameContext(civ))
        assertEquals("There should be four units and 2 cities", 42, actual)
    }

    @Test
    @CoversCountable(Countables.FilteredCitiesByCivs)
    fun testFilteredCitiesByCivs() {
        setupModdedGame()
        val civ2 = game.addCiv()
        val city2 = game.addCity(civ2, game.tileMap[-2, 0])
        city2.isPuppet = true
        val context = GameContext(civ)

        runTestParcours("Filtered cities by civs countable", { test: String ->
            Countables.getCountableAmount(test, context)
        },
            "[Capital] Cities of [All] Civilizations", 2,
            "[Puppeted] Cities of [All] Civilizations", 1,
            "[All] Cities of [All] Civilizations", 2,
            "[Capital] Cities of [${civ.civName}] Civilizations", 1,
            "[Puppeted] Cities of [${civ.civName}] Civilizations", 0,
            "[Puppeted] Cities of [${civ2.civName}] Civilizations", 1,
            "[All] Cities of [${civ2.civName}] Civilizations", 1,
            "[Capital] Cities of [City-State] Civilizations", 0,
        )
    }

    @Test
    @CoversCountable(Countables.RemainingCivs, Countables.FilteredUnits, Countables.Carried)
    fun testFilteredUnitsCountable() {
        val ruleset = setupModdedGame()
        val wetTile = game.tileMap[3,1]
        wetTile.setBaseTerrain(ruleset.terrains[Constants.coast]!!)
        val carrier = game.addUnit("Carrier", civ, wetTile)
        val carried = game.addUnit("Fighter", civ, wetTile)
        game.addUnit("Warrior", civ, city.getCenterTile())
        game.addUnit("Scout", civ, game.tileMap.values.first())
        game.addUnit("Scout", civ, game.tileMap.values.last())
        game.addUnit("Worker", civ, game.tileMap[2,1])
        game.addUnit("Worker", civ, game.tileMap[2,-1])
        val deSela = game.addCiv(game.ruleset.nations["Lhasa"]!!)
        val city2 = game.addCity(deSela, game.tileMap[-2,1])
        game.addUnit("Scout", deSela, city2.getCenterTile())
        val context = GameContext(civ, unit = carrier) // The carried countable runs on a unit scope
        val actual = Countables.getCountableAmount("[Remaining [all] Civilizations] + 100 * [[Military] Units] + 10 * [Carried [Fighter] units]", context)
        assertEquals("There should be five military units with 1 being carried and 2 civilizations", 512, actual)
    }

    @Test
    @CoversCountable(Countables.TileFilterTiles, Countables.TileResources, Countables.TileResourcesByCivs)
    fun testTileFilterTilesCountable() {
        val ruleset = setupModdedGame("Provides [40] [Horses]")
        val wetTile = game.tileMap[0,0]
        wetTile.setBaseTerrain(ruleset.terrains[Constants.coast]!!)
        for (tile in wetTile.getTilesAtDistance(3))
            tile.addTerrainFeature(if (Random.nextBoolean()) "Forest" else "Jungle")
        city.getCenterTile().setTileResource(ruleset.tileResources["Horses"]!!, false, Random(42))
        civ.tech.addTechnology("Animal Husbandry", false)
        val context = GameContext(civ)
        runTestParcours("Countables TileFilterTiles and TileResources",
            { test: String -> Countables.getCountableAmount(test, context) },
            "Horses", 42,
            "[Coastal] Tiles", 6,
            "[Coast] Tiles", 1,
            "[Land] Tiles", 36,
            "[City center] Tiles", 1,
            "[Nation-0] Tiles", 7,
            "[Vegetation] Tiles", 18,
            // TileResourcesByCivs
            "[Horses] resource of [${civ.civName}] Civilizations", 42,
            "[Strategic] resource of [${civ.civName}] Civilizations", 42,
            "[Horses] resource of [all] Civilizations", 42,
            "[Horses] resource of [City-State] Civilizations", 0
        )
    }

    @Test
    @CoversCountable(Countables.FilteredTechnologies)
    fun testFilteredTechnologies() {
        setupModdedGame()
        val techs = listOf(
            "Agriculture",
            "Pottery",
            "Sailing",
            "Animal Husbandry",
            "Optics"
        )
        for (techName in techs) {
            civ.tech.addTechnology(techName, false)
        }
        val context = GameContext(civ)

        runTestParcours("Filtered technologies countable", { filter: String ->
            Countables.getCountableAmount("Researched [$filter] Technologies", context)
        },
            "All", 5,
            "Ancient era", 4,
            "Classical era", 1, // Optics
            "Modern era", 0,
            "Pottery", 1,
            "Archery", 0,
        )
    }
    //endregion
}
