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
}
