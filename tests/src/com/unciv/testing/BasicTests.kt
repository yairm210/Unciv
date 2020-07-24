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
import com.unciv.models.translations.tr
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
        val params = UncivGameParameters("", null, null)
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
        val x = "+1 Gold, +2 Production".tr()
        print(x)
    }
}