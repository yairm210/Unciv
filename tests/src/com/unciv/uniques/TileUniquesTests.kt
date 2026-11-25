package com.unciv.uniques

import com.unciv.Constants
import com.unciv.logic.map.HexCoord
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import com.unciv.logic.map.mapunit.actions.UnitActions
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
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

        val tile = game.setTileTerrain(HexCoord.Zero, Constants.grassland)
        val cityTile = game.setTileTerrain(HexCoord(2,0), Constants.grassland)
        val city = game.addCity(civInfo, cityTile, true)
        city.population.foodStored = 0 // just to be sure
        civInfo.addGold(-civInfo.gold) // reset gold just to be sure

        val testImprovement = game.createTileImprovement("Pillaging this improvement yields [+20 Gold, +11 Food]")
        tile.setImprovement(testImprovement)
        val unit = game.addUnit("Warrior", civInfo, tile)
        unit.currentMovement = 2f

        UnitActions.invokeUnitAction(unit, UnitActionType.Pillage)
        Assert.assertTrue("Pillaging should transfer gold to the civ", civInfo.gold == 20)
        Assert.assertTrue("Pillaging should transfer food to the nearest city", city.population.foodStored == 11)
    }

    @Test
    fun revealTilesWithGenericResourceFilter() {
        game.makeHexagonalMap(3)
        val civInfo = game.addCiv()
        val resourceTile = game.setTileTerrain(HexCoord(2, 0), Constants.plains)
        resourceTile.setTileResource("Wheat")
        val emptyTile = game.setTileTerrain(HexCoord(-2, 0), Constants.plains)

        val unique = Unique("Reveal up to [all] [resource] within a [3] tile radius")
        UniqueTriggerActivation.triggerUnique(unique, civInfo, tile = game.getTile(HexCoord.Zero))

        Assert.assertTrue("The resource tile should be revealed", resourceTile.isExplored(civInfo))
        Assert.assertFalse("A tile without a resource should stay hidden", emptyTile.isExplored(civInfo))
    }
}
