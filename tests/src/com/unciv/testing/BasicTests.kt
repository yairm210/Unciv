//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.testing

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.UncivGameParameters
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.getPlaceholderParameters
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
        RulesetCache.loadRulesets()
        ruleset = RulesetCache.getBaseRuleset()
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
        val params = UncivGameParameters("", null)
        val game = UncivGame(params)
        Assert.assertTrue("This test will only pass if the game is not run with debug modes",
                !game.superchargedForDebug
                        && !game.viewEntireMapForDebug
                        && game.simulateUntilTurnForDebug <= 0
                        && !game.consoleMode
        )
    }

    // If there's a unit that obsoletes with no upgrade then when it obsoletes
// and we try to work on its upgrade, we'll get an exception - see techManager
    @Test
    fun allObsoletingUnitsHaveUpgrades() {
        val units: Collection<BaseUnit> = ruleset.units.values
        var allObsoletingUnitsHaveUpgrades = true
        for (unit in units) {
            if (unit.obsoleteTech != null && unit.upgradesTo == null && unit.name !="Scout" ) {
                println(unit.name + " obsoletes but has no upgrade")
                allObsoletingUnitsHaveUpgrades = false
            }
        }
        Assert.assertTrue(allObsoletingUnitsHaveUpgrades)
    }

    @Test
    fun statParserWorks(){
        Assert.assertTrue(Stats.isStats("+1 Production"))
        Assert.assertTrue(Stats.isStats("+1 Gold, +2 Production"))
        Assert.assertFalse(Stats.isStats("+1 Gold from tree"))

        val statsThatShouldBe = Stats(gold = 1f, production = 2f)
        Assert.assertTrue(Stats.parse("+1 Gold, +2 Production").equals(statsThatShouldBe))

        UncivGame.Current = UncivGame("")
        UncivGame.Current.settings = GameSettings().apply { language = "Italian" }
    }

    @Test
    fun baseRulesetHasNoBugs() {
        for (baseRuleset in BaseRuleset.values()) {
            val ruleset = RulesetCache[baseRuleset.fullName]!!
            val modCheck = ruleset.checkModLinks()
            if (modCheck.isNotOK())
                println(modCheck.getErrorText(true))
            Assert.assertFalse(modCheck.isNotOK())
        }
    }

    @Test
    fun uniqueTypesHaveNoUnknownParameters() {
        var noUnknownParameters = true
        for (uniqueType in UniqueType.values()) {
            for (entry in uniqueType.parameterTypeMap.withIndex()) {
                for (paramType in entry.value) {
                    if (paramType == UniqueParameterType.Unknown) {
                        val badParam = uniqueType.text.getPlaceholderParameters()[entry.index]
                        println("${uniqueType.name} param[${entry.index}] type \"$badParam\" is unknown")
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
                println("${uniqueType.name} has no targets.")
                allOK = false
            }
        }
        Assert.assertTrue("This test succeeds only if all UniqueTypes have at least one UniqueTarget", allOK)
    }

    //@Test  // commented so github doesn't run this
    fun statMathStressTest() {
        val runtime = Runtime.getRuntime()
        runtime.gc()
        Thread.sleep(5000) // makes timings a little more repeatable
        val startTime = System.nanoTime()
        statMathRunner(iterations = 1_000_000)
        println("statMathStressTest took ${(System.nanoTime()-startTime)/1000}µs")
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

        for (i in 0 until iterations) {
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
