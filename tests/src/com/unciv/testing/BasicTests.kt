//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.testing

import com.badlogic.gdx.Gdx
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.RulesetStatsObject
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import com.unciv.utils.DebugUtils
import com.unciv.utils.Log
import com.unciv.utils.debug
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.random.Random

@RunWith(GdxTestRunner::class)
class BasicTests {

    var ruleset = Ruleset()
    @Before
    fun loadTranslations() {
        Log.shouldLog()
        RulesetCache.loadRulesets(noMods = true)
        ruleset = RulesetCache.getVanillaRuleset()
    }

    @Test
    fun gamePngExists() {
        Assert.assertTrue("This test will only pass when the game.png exists",
                Gdx.files.local("game.png").exists())
    }

    @Test
    fun loadRuleset() {
        Assert.assertTrue("This test will only pass when the jsons can be loaded",
                ruleset.buildings.size > 0)
    }

    @Test
    fun gameIsNotRunWithDebugModes() {
        val game = UncivGame()
        Assert.assertTrue("This test will only pass if the game is not run with debug modes",
                !DebugUtils.SUPERCHARGED
                        && !DebugUtils.VISIBLE_MAP
                        && DebugUtils.SIMULATE_UNTIL_TURN <= 0
                        && !game.isConsoleMode
        )
    }

    // If there's a unit that obsoletes with no upgrade then when it obsoletes
// and we try to work on its upgrade, we'll get an exception - see techManager
    @Test
    fun allObsoletingUnitsHaveUpgrades() {
        val units: Collection<BaseUnit> = ruleset.units.values
        var allObsoletingUnitsHaveUpgrades = true
        for (unit in units) {
            if (unit.techsThatObsoleteThis().any() && unit.upgradesTo == null && unit.name !="Scout" ) {
                debug("%s obsoletes but has no upgrade", unit.name)
                allObsoletingUnitsHaveUpgrades = false
            }
        }
        Assert.assertTrue(allObsoletingUnitsHaveUpgrades)
    }

    @Test
    fun statParserWorks() {
        Assert.assertTrue(Stats.isStats("+1 Production"))
        Assert.assertTrue(Stats.isStats("+1 Gold, +2 Production"))
        Assert.assertFalse(Stats.isStats("+1 Gold from tree"))

        val statsThatShouldBe = Stats(gold = 1f, production = 2f)
        Assert.assertTrue(Stats.parse("+1 Gold, +2 Production").equals(statsThatShouldBe))

        UncivGame.Current = UncivGame()
        UncivGame.Current.settings = GameSettings().apply { language = "Italian" }
    }

    @Test
    fun baseRulesetHasNoBugs() {
        for (baseRuleset in BaseRuleset.values()) {
            val ruleset = RulesetCache[baseRuleset.fullName]!!
            val modCheck = ruleset.checkModLinks()
            if (modCheck.isNotOK())
                debug("%s", modCheck.getErrorText(true))
            Assert.assertFalse(modCheck.isNotOK())
        }
    }

    @Test
    fun uniqueTypesHaveNoUnknownParameters() {
        var noUnknownParameters = true
        for (uniqueType in UniqueType.values()) {
            if (uniqueType.getDeprecationAnnotation()!=null) continue
            for (entry in uniqueType.parameterTypeMap.withIndex()) {
                for (paramType in entry.value) {
                    if (paramType == UniqueParameterType.Unknown) {
                        val badParam = uniqueType.text.getPlaceholderParameters()[entry.index]
                        debug("%s param[%s] type \"%s\" is unknown", uniqueType.name, entry.index, badParam)
                        noUnknownParameters = false
                    }
                }
            }
        }
        Assert.assertTrue("This test succeeds only if all UniqueTypes have all their placeholder parameters mapped to a known UniqueParameterType", noUnknownParameters)
    }

    @Test
    fun allUniqueTypesHaveAtLeastOneTarget() {
        var allOK = true
        for (uniqueType in UniqueType.values()) {
            if (uniqueType.targetTypes.isEmpty()) {
                debug("%s has no targets.", uniqueType.name)
                allOK = false
            }
        }
        Assert.assertTrue("This test succeeds only if all UniqueTypes have at least one UniqueTarget", allOK)
    }

    @Test
    fun allUnitsUniquesHaveTheirUniqueTypes() {
        val units: Collection<BaseUnit> = ruleset.units.values
        var allOK = true
        for (unit in units) {
            for (unique in unit.uniques) {
                if (!UniqueType.values().any { it.placeholderText == unique.getPlaceholderText() }) {
                    debug("%s: %s", unit.name, unique)
                    allOK = false
                }
            }
        }
        Assert.assertTrue("This test succeeds only if all uniques of units are presented in UniqueType.values()", allOK)
    }

    @Test
    fun allBuildingsUniquesHaveTheirUniqueTypes() {
        val buildings = ruleset.buildings.values
        var allOK = true
        for (building in buildings) {
            for (unique in building.uniques) {
                if (!UniqueType.values().any { it.placeholderText == unique.getPlaceholderText() }) {
                    debug("%s: %s", building.name, unique)
                    allOK = false
                }
            }
        }
        Assert.assertTrue("This test succeeds only if all uniques of buildings are presented in UniqueType.values()", allOK)
    }

    @Test
    fun allPromotionsUniquesHaveTheirUniqueTypes() {
        val promotions = ruleset.unitPromotions.values
        var allOK = true
        for (promotion in promotions) {
            for (unique in promotion.uniques) {
                if (!UniqueType.values().any { it.placeholderText == unique.getPlaceholderText() }) {
                    debug("%s: %s", promotion.name, unique)
                    allOK = false
                }
            }
        }
        Assert.assertTrue("This test succeeds only if all uniques of promotions are presented in UniqueType.values()", allOK)
    }

    @Test
    fun allTerrainRelatedUniquesHaveTheirUniqueTypes() {
        val objects : MutableCollection<RulesetStatsObject> = mutableListOf()
        objects.addAll(ruleset.tileImprovements.values)
        objects.addAll(ruleset.tileResources.values)
        objects.addAll(ruleset.terrains.values)
        var allOK = true
        for (obj in objects) {
            for (unique in obj.uniques) {
                if (!UniqueType.values().any { it.placeholderText == unique.getPlaceholderText() }) {
                    debug("%s: %s", obj.name, unique)
                    allOK = false
                }
            }
        }
        Assert.assertTrue("This test succeeds only if all uniques are presented in UniqueType.values()", allOK)
    }

    @Test
    fun allPolicyRelatedUniquesHaveTheirUniqueTypes() {
        val policies = ruleset.policies.values
        var allOK = true
        for (policy in policies) {
            for (unique in policy.uniques) {
                if (!UniqueType.values().any { it.placeholderText == unique.getPlaceholderText() }) {
                    println("${policy.name}: $unique")
                    allOK = false
                }
            }
        }
        Assert.assertTrue("This test succeeds only if all policy uniques are presented in UniqueType.values()", allOK)
    }

    @Test
    fun allBeliefRelatedUniquesHaveTheirUniqueTypes() {
        val ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!.clone() // vanilla doesn't have beliefs
        val beliefs = ruleset.beliefs.values
        var allOK = true
        for (belief in beliefs) {
            for (unique in belief.uniques) {
                if (!UniqueType.values().any { it.placeholderText == unique.getPlaceholderText() }) {
                    println("${belief.name}: $unique")
                    allOK = false
                }
            }
        }
        Assert.assertTrue("This test succeeds only if all belief uniques are presented in UniqueType.values()", allOK)
    }

    @Test
    fun allEraRelatedUniquesHaveTheirUniqueTypes() {
        val eras = ruleset.eras.values
        var allOK = true
        for (era in eras) {
            for (unique in era.uniques) {
                if (!UniqueType.values().any { it.placeholderText == unique.getPlaceholderText() }) {
                    println("${era.name}: $unique")
                    allOK = false
                }
            }
        }
        Assert.assertTrue("This test succeeds only if all era uniques are presented in UniqueType.values()", allOK)
    }

    @Test
    fun allRuinRelatedUniquesHaveTheirUniqueTypes() {
        val ruinRewards = ruleset.ruinRewards.values
        var allOK = true
        for (reward in ruinRewards) {
            for (unique in reward.uniques) {
                if (!UniqueType.values().any { it.placeholderText == unique.getPlaceholderText() }) {
                    println("${reward.name}: $unique")
                    allOK = false
                }
            }
        }
        Assert.assertTrue("This test succeeds only if all ruin reward uniques are presented in UniqueType.values()", allOK)
    }

    @Test
    fun allDeprecatedUniqueTypesHaveReplacewithThatMatchesOtherType() {
        var allOK = true
        for (uniqueType in UniqueType.values()) {
            val deprecationAnnotation = uniqueType.getDeprecationAnnotation() ?: continue

            val uniquesToCheck = deprecationAnnotation.replaceWith.expression.split("\", \"", Constants.uniqueOrDelimiter)

            for (uniqueText in uniquesToCheck) {
                val replacementTextUnique = Unique(uniqueText)


                if (replacementTextUnique.type == null) {
                    debug("%s's deprecation text \"%s\" does not match any existing type!", uniqueType.name, uniqueText)
                    allOK = false
                }
                if (replacementTextUnique.type == uniqueType) {
                    debug("%s's deprecation text references itself!", uniqueType.name)
                    allOK = false
                }
                for (conditional in replacementTextUnique.conditionals) {
                    if (conditional.type == null) {
                        debug("%s's deprecation text contains conditional \"%s\" which does not match any existing type!", uniqueType.name, conditional.text)
                        allOK = false
                    }
                }

                var iteration = 1
                var replacementUnique = Unique(uniqueType.placeholderText)
                while (replacementUnique.getDeprecationAnnotation() != null) {
                    if (iteration == 10) {
                        allOK = false
                        debug("%s's deprecation text never references an undeprecated unique!", uniqueType.name)
                        break
                    }
                    iteration++
                    replacementUnique = Unique(replacementUnique.getReplacementText(ruleset))
                }
            }
        }
        Assert.assertTrue("This test succeeds only if all deprecated uniques have a replaceWith text that matches an existing type", allOK)
    }

    //@Test  // commented so github doesn't run this
    fun statMathStressTest() {
        val runtime = Runtime.getRuntime()
        runtime.gc()
        Thread.sleep(5000) // makes timings a little more repeatable
        val startTime = System.nanoTime()
        statMathRunner(iterations = 1_000_000)
        debug("statMathStressTest took %sµs", (System.nanoTime()-startTime) / 1000)
    }

    @Test
    fun statMathRandomResultTest() {
        val iterations = 42
        val expectedStats = Stats(
            production = 212765.08f,
            food = 776.8394f,
            gold = -4987.297f,
            science = 14880.18f,
            culture = -49435.21f,
            happiness = -13046.4375f,
            faith = 7291.375f
        )
        // This is dependent on iterator order, so when that changes the expected values must change too
        val stats = statMathRunner(iterations)
        Assert.assertTrue(stats.equals(expectedStats))
    }

    private fun statMathRunner(iterations: Int): Stats {
        val random = Random(42)
        val statCount = Stat.values().size
        val stats = Stats()

        repeat(iterations) {
            val value: Float = random.nextDouble(-10.0, 10.0).toFloat()
            stats.add( Stats(gold = value) )
            stats.forEach {
                val stat = Stat.values()[(it.key.ordinal + random.nextInt(1,statCount)).rem(statCount)]
                stats.add(stat, -it.value)
            }
            val stat = Stat.values()[random.nextInt(statCount)]
            stats.add(stat, stats.times(4)[stat])
            stats.timesInPlace(0.8f)
            if (abs(stats.values.maxOrNull()!!) > 1000000f)
                stats.timesInPlace(0.1f)
        }
        return stats
    }
}
