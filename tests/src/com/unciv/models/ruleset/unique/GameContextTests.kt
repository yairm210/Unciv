//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.stats.Stats
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(GdxTestRunner::class)
class GameContextTests {

    private lateinit var game: TestGame

    @Before
    fun initTheWorld() {
        game = TestGame()
    }

    // region String Conversion

    @Test
    fun testFromSerializedString() {
        val civInfo = game.addCiv()
        game.makeHexagonalMap(2)
        val tile = game.setTileTerrain(Vector2(1f, 0f), Constants.grassland)
        val city = game.addCity(civInfo, tile, true)
        val unit = game.addUnit("Warrior", civInfo, tile)
        val gameContext = GameContext(unit)
        val x = tile.position.x.toInt()
        val y = tile.position.y.toInt()
        val value = "unitId=${unit.id}&tileX=${x}&tileY=${y}"
        val result = GameContext.fromSerializedString(value, civInfo, "&")
        Assert.assertEquals(unit.name, result.unit?.name)
        Assert.assertEquals(tile.position.x.toInt(), result.tile?.position?.x?.toInt())
        Assert.assertEquals(tile.position.y.toInt(), result.tile?.position?.y?.toInt())
    }

    @Test
    fun testToSerializedString() {
        val civInfo = game.addCiv()
        game.makeHexagonalMap(2)
        val tile = game.setTileTerrain(Vector2(1f,0f), Constants.grassland)
        val city = game.addCity(civInfo, tile, true)
        val unit = game.addUnit("Warrior", civInfo, tile)

        val gameContext = GameContext(unit)
        val result = gameContext.toSerializedString()
        Assert.assertTrue(result.contains("unitId=${unit.id}"))
        Assert.assertTrue(result.contains("civName=${civInfo.civName}"))
        Assert.assertTrue(result.contains("tileX=${tile.position.x.toInt()}"))
    }

    // endregion

}
