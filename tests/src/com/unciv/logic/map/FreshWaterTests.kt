package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.MapSaver
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class FreshWaterTests {
    private var map = TileMap()
    private var ruleSet = Ruleset()

    companion object {
        private const val testingMap = "H4sIAAAAAAAA/52Sv07DMBDGXyUP4A4NYrmRQsUQCRRQl6rDoRzkVCeO7EtpsPLuOMqWpMRisWT7d993/3yFzStarEjIOvDSNQRPVSOdCj9v/EPgLRbcOkjVNxdSwr0qib9KgTtVhzjYtU5M1asPdJS3mhwJ7PiSHJJNcsCatcZeCWvK2Akc/cC9k7XINTwG2kqvfGMcC5sa/BU2W9WFY1T8kxzAdS5KLEboGsVEZb5JhxrTJVLJeN0TSmvJwfEFHbvTRGEQmMRneCa32MxIo+cwrKnPdu4TH37LegKtM2Mey61VJboHI2EJM/qUnC9kQWxLS51YEciHxb6pEJNmRL3j7JYzmfV0r40pkkaHx9kKjEv0L6FT/wsygYGG+gMAAA=="
        private val shouldHaveFreshWater = setOf(Vector2(-1f,-1f),Vector2(1f,1f),Vector2(-2f,-2f),Vector2(2f,2f),Vector2(-1f,-2f),Vector2(1f,2f),Vector2(1f,-1f),Vector2(-1f,1f),Vector2(2f,1f),Vector2(-2f,-1f))
    }

    @Before
    fun initTheWorld() {
        RulesetCache.loadRulesets()
        ruleSet = RulesetCache.getVanillaRuleset()
        map = MapSaver.mapFromSavedString(testingMap, false)
        map.setTransients(ruleSet, false)
    }

    @Test
    fun isAdjacentToFreshWater() {
        for (tile in map.values) {
            val isFresh = tile.isAdjacentTo(Constants.freshWater)
            val shouldFresh = tile.position in shouldHaveFreshWater
            Assert.assertFalse("Tile $tile has fresh water but should not", isFresh && !shouldFresh)
            Assert.assertFalse("Tile $tile should have fresh water but doesn't", !isFresh && shouldFresh)
        }
    }
}
