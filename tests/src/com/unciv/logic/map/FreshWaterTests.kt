package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.files.MapSaver
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
        @Suppress("SpellCheckingInspection")
        private const val testingMap = "H4sIAAAAAAAA/52SwU7DMAyGX6UPkB5WxMVHBhOHSqCBdpl6CKqh1tKmit3REvXdSdVb17GIS6Qkn//f+WNf6/ZVO12joGPwMrQIT3Urgwo3b/SD4J0uqWPI1DeVUsG9qpC+KoE71YQ62HYsth7Vh2bcdwYZBbZ0Tg5Jmhx0Q8boUQkZzIkFjn7i3tE5TQ08BtrJqHxrmYRsA76HdKOGsMyKf5ITeJuLEosR6qOYqM7TbHpjtkYqmbc71NI5ZDi+aCYuFgqTwKI+1yfk1TAjjZ7DZy19Npc+8eXXrBfQbWbuIy7auKnoo1yvJFdpfrASxj7HT9nTGR2I63Dli1YbuYxuZ6wtk9aEQy4i5ecZ+o/BWIy/FE5uFvkDAAA="
        private val adjacentToFreshWater = setOf(Vector2(-1f,-1f),Vector2(1f,1f),Vector2(-1f,-2f),Vector2(1f,2f),Vector2(2f,0f),Vector2(-2f,0f),Vector2(2f,1f),Vector2(-2f,-1f))
    }

    @Before
    fun initTheWorld() {
        RulesetCache.loadRulesets(noMods = true)
        ruleSet = RulesetCache.getVanillaRuleset()
        map = MapSaver.mapFromSavedString(testingMap)
        map.setTransients(ruleSet, false)
    }

    @Test
    fun isAdjacentToFreshWater() {
        for (tile in map.values) {
            val isFresh = tile.isAdjacentTo(Constants.freshWater)
            val shouldFresh = tile.position in adjacentToFreshWater
            Assert.assertFalse("Tile $tile has fresh water but should not", isFresh && !shouldFresh)
            Assert.assertFalse("Tile $tile should have fresh water but doesn't", !isFresh && shouldFresh)
        }
    }
}
