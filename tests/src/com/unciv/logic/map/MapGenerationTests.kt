package com.unciv.logic.map

import com.unciv.Constants
import com.unciv.logic.files.MapSaver
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class MapGenerationTests {
    private var ruleSet = Ruleset()
    

    @Test
    fun testAllMapGenerations() {
        RulesetCache.loadRulesets(noMods = true)
        ruleSet = RulesetCache.getVanillaRuleset()
        val mapGenerator = MapGenerator(ruleSet)
        for (mapType in MapType.allValues) {
            for (mapShape in MapShape.allValues) {
                for (worldWrap in listOf(true, false)) {
                    val mapParameters = MapParameters()
                    mapParameters.type = mapType
                    mapParameters.shape = mapShape
                    mapParameters.worldWrap = worldWrap
                    mapGenerator.generateMap(mapParameters)
                }
            }
        }
    }
}
