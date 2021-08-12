//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.testing

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.UncivGameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
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

        val statsThatShouldBe = Stats().add(Stat.Gold,1f).add(Stat.Production, 2f)
        Assert.assertTrue(Stats.parse("+1 Gold, +2 Production").equals(statsThatShouldBe))

        UncivGame.Current = UncivGame("")
        UncivGame.Current.settings = GameSettings().apply { language = "Italian" }
    }

    @Test
    fun baseRulesetHasNoBugs() {
        ruleset.modOptions.isBaseRuleset=true
        val modCheck = ruleset.checkModLinks()
        if(modCheck.isNotOK()) println(modCheck)
        Assert.assertFalse(modCheck.isNotOK())
    }

    //@Test  // commented so github doesn't run this
    fun statMathStressTest() {
        val runtime = Runtime.getRuntime()
        runtime.gc()
        Thread.sleep(5000)
        val startTime = System.nanoTime()
        statMathRunner(iterations = 1000000)
        println("statMathStressTest took ${(System.nanoTime()-startTime)/1000}µs")
    }

    @Test
    fun statMathRandomResultTest() {
        val iterations = 42
        val expectedStats = Stats().apply {
            production = 1381.1195f
            food = 37650.625f
            gold = -54857.508f
            science = 82838.1f
            culture = 264289.88f
            happiness = -98249.61f
            faith = -21620.709f
        }
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
            stats.toHashMap().forEach {
                val stat = Stat.values()[(it.key.ordinal + random.nextInt(1,statCount)).rem(statCount)]
                stats.add(stat, -it.value)
            }
            val stat = Stat.values()[random.nextInt(statCount)]
            stats.add(stat, stats.times(4).get(stat))
            stats.timesInPlace(0.8f)
            if (abs(stats.toHashMap().maxOfOrNull { it.value }!!) > 1000000f)
                stats.timesInPlace(0.1f)
        }
        return stats
    }
}
