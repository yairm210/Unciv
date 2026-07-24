package com.unciv.logic.map

import com.unciv.logic.map.mapgenerator.mapregions.MinorCivPlacer
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.nation.CityStateType
import com.unciv.models.ruleset.nation.Nation
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class CityStateTypeStartBiasTests {

    @Before
    fun loadRulesets() {
        if (RulesetCache.isEmpty())
            RulesetCache.loadRulesets(noMods = true)
    }

    @Test
    fun `G&K Maritime city-state type has Coast start bias`() {
        val ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
        val maritime = ruleset.cityStateTypes["Maritime"]!!
        Assert.assertTrue("Coast" in maritime.startBias)
    }

    @Test
    fun `Nation getStartBias merges type and nation biases`() {
        val ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
        val type = CityStateType().apply {
            name = "TestType"
            startBias = arrayListOf("Coast")
        }
        ruleset.cityStateTypes[type.name] = type

        val nation = Nation().apply {
            name = "TestCS"
            cityStateType = type.name
            startBias = arrayListOf("Grassland")
        }

        val bias = nation.getStartBias(ruleset)
        Assert.assertEquals(listOf("Grassland", "Coast"), bias)
    }

    @Test
    fun `prefersCoastalStart uses city-state type startBias`() {
        val game = TestGame()
        game.makeHexagonalMap(3)
        val ruleset = game.ruleset
        val type = CityStateType().apply {
            name = "CoastalType"
            startBias = arrayListOf("Coast")
        }
        ruleset.cityStateTypes[type.name] = type

        val nation = Nation().apply {
            name = "CoastalCS"
            cityStateType = type.name
        }
        ruleset.nations[nation.name] = nation

        val civ = game.addCiv(nation)
        Assert.assertTrue(MinorCivPlacer.prefersCoastalStart(civ, ruleset))
    }
}
