package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.models.UnitActionType
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class TileUniquesTests {
    private lateinit var game: TestGame

    @Before
    fun initTheWorld() {
        game = TestGame()
    }

    @Test
    fun pillageYieldTest() {
        game.makeHexagonalMap(2)
        val civInfo = game.addCiv()

        val tile = game.setTileTerrain(Vector2.Zero, Constants.grassland)
        val cityTile = game.setTileTerrain(Vector2(2f,0f), Constants.grassland)
        val city = game.addCity(civInfo, cityTile, true)
        city.population.foodStored = 0 // just to be sure
        civInfo.addGold(-civInfo.gold) // reset gold just to be sure

        val testImprovement = game.createTileImprovement("Pillaging this improvement yields [+20 Gold, +11 Food]")
        tile.setImprovement(testImprovement.name)
        val unit = game.addUnit("Warrior", civInfo, tile)
        unit.currentMovement = 2f

        UnitActions.invokeUnitAction(unit, UnitActionType.Pillage)
        Assert.assertTrue("Pillaging should transfer gold to the civ", civInfo.gold == 20)
        Assert.assertTrue("Pillaging should transfer food to the nearest city", city.population.foodStored == 11)
    }

    @Test
    fun testOneTimeAddResourceInTile() {
        game.makeHexagonalMap(2)
        val civInfo = game.addCiv()
        val tile = game.setTileTerrain(Vector2.Zero, Constants.grassland)
        tile.resource = null
        tile.resourceAmount = 0
        val testImprovement = game.createTileImprovement("Add [Cattle] in this tile")
        tile.setImprovement(testImprovement.name, civInfo)
        Assert.assertEquals("Cattle", tile.resource)
        Assert.assertEquals(0, tile.resourceAmount)
    }

    @Test
    fun testOneTimeAddResourceAmountInTile() {
        game.makeHexagonalMap(2)
        val civInfo = game.addCiv()
        val tile = game.setTileTerrain(Vector2.Zero, Constants.grassland)
        tile.resource = null
        tile.resourceAmount = 0
        val testImprovement = game.createTileImprovement("[+3] [Cattle] resource in this tile")
        tile.setImprovement(testImprovement.name, civInfo)
        Assert.assertEquals("Cattle", tile.resource)
        Assert.assertEquals(3, tile.resourceAmount)
        val bonusImprovement = game.createTileImprovement("[+3] [Bonus] resource in this tile")
        tile.setImprovement(bonusImprovement.name, civInfo)
        Assert.assertEquals("Cattle", tile.resource)
        Assert.assertEquals(6, tile.resourceAmount)
    }
}
