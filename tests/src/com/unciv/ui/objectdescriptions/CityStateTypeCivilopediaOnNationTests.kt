package com.unciv.ui.objectdescriptions

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.logic.files.UncivFiles
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.nation.CityStateType
import com.unciv.models.ruleset.nation.Nation
import com.unciv.testing.GdxTestRunner
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class CityStateTypeCivilopediaOnNationTests {

    @Before
    fun loadRulesets() {
        UncivGame.Current = UncivGame()
        UncivGame.Current.files = UncivFiles(Gdx.files)
        UncivGame.Current.settings = GameSettings()
        if (RulesetCache.isEmpty())
            RulesetCache.loadRulesets(noMods = true)
    }

    @Test
    fun `city-state nation civilopedia shows type civilopediaText and non-StartBias uniques`() {
        val ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!.clone()
        val type = CityStateType().apply {
            name = "PediaType"
            uniques = arrayListOf(
                "Start bias [Coast]",
                "Provides a unique luxury"
            )
            civilopediaText = listOf(FormattedLine("Type lore for civilopedia"))
        }
        ruleset.cityStateTypes[type.name] = type

        val nation = Nation().apply {
            name = "PediaCS"
            cityStateType = type.name
        }
        ruleset.nations[nation.name] = nation

        val texts = nation.getCivilopediaTextLines(ruleset).map { it.text }

        Assert.assertTrue(
            "Expected type civilopediaText on nation page: $texts",
            texts.any { it.contains("Type lore for civilopedia") }
        )
        Assert.assertTrue(
            "Expected non-StartBias type unique on nation page: $texts",
            texts.any { it.contains("unique luxury", ignoreCase = true) }
        )
        Assert.assertTrue(
            "Expected Coast from effective start bias: $texts",
            texts.any { it.contains("Coast") }
        )
    }

    @Test
    fun `city-state nation civilopedia has no separator before start bias`() {
        val ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!.clone()
        val nation = ruleset.nations.values.first { it.isCityState && it.cityStateType == "Maritime" }

        val lines = nation.getCivilopediaTextLines(ruleset)
        val startBiasIndex = lines.indexOfFirst { it.text.contains("Start bias") }
        Assert.assertTrue("Expected start bias on Maritime CS page", startBiasIndex > 0)
        Assert.assertFalse(
            "Start bias should follow a blank line, not a separator (match major-civ layout)",
            lines[startBiasIndex - 1].separator
        )
        Assert.assertTrue(
            "CS start bias should use inline icons only (no link column), aligned with friend/ally bonuses",
            lines[startBiasIndex].link.isEmpty()
        )
    }
}
