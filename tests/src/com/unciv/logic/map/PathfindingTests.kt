package com.unciv.logic.map

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(GdxTestRunner::class)
class PathfindingTests {

    private lateinit var tile: Tile
    private lateinit var civInfo: Civilization
    private var testGame = TestGame()

    @Before
    fun initTheWorld() {
        testGame.makeHexagonalMap(5)
        tile = testGame.tileMap[0,0]
        civInfo = testGame.addCiv()
        for (tile in testGame.tileMap.values)
            tile.setExplored(civInfo, true)
    }

    // These are interesting use-cases because it shows that for the *exact same map* for units with *no special uniques*
    //  we can still have different optimal paths!
    @Test
    fun shortestPathByTurnsNotSumOfMovements(){
        // Naive Djikstra would calculate distance to 0,3 to be 5 movement points through hills, and only 4 by going around hills.
        // However, from a *unit turn* perspective, going through the hills is 3 turns, and going around is 4, so through the hills is the correct solution
        testGame.setTileTerrain(HexCoord(0,1), "Hill")
        testGame.setTileTerrain(HexCoord(0,2), "Hill")
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1

        val unit = testGame.addUnit(baseUnit.name, civInfo, tile)
        // expect movement through hills (2 hill tiles plus one default desert)
        Assert.assertEquals(3, unit.movement.getShortestPath(testGame.getTile(0,3)).size)
    }


    // Looks like our current movement is actually unoptimized, since it fails this test :)
    @Test
    fun maximizeRemainingMovementWhenReachingDestination(){
        // Moving in a direct path, you'd waste 5 movement points to get there, ending up with 0.
        // Moving around the hills, you'd waste 4 movement points, leaving you with one remaining
        testGame.setTileTerrain(HexCoord(0,1), "Hill")
        testGame.setTileTerrain(HexCoord(0,2), "Hill")
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 5

        val unit = testGame.addUnit(baseUnit.name, civInfo, tile)
//         val pathToTile = unit.movement.getDistanceToTilesWithinTurn(HexCoord(0,0), 5f).getPathToTile(testGame.getTile(0,3))
        unit.movement.moveToTile(testGame.getTile(0,3))
        Assert.assertEquals(1f, unit.currentMovement)
    }
}
