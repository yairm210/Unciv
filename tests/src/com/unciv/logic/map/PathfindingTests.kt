package com.unciv.logic.map

import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.metadata.GameSettings.PathfindingAlgorithm
import com.unciv.models.metadata.GameSettings.PathfindingAlgorithm.AStarPathfinding
import com.unciv.models.metadata.GameSettings.PathfindingAlgorithm.ClassicPathfinding
import com.unciv.testing.GdxTestRunnerFactory
import com.unciv.testing.TestGame
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.junit.runners.Parameterized.UseParametersRunnerFactory


@RunWith(Parameterized::class)
@UseParametersRunnerFactory(GdxTestRunnerFactory::class)
class PathfindingTests(
    // parameters come from the Compantion#parameters method
    private val pathfindingAlgorithm: PathfindingAlgorithm,
) {
    private lateinit var originTile: Tile
    private lateinit var civInfo: Civilization
    private var testGame = TestGame()

    @Before
    fun initTheWorld() {
        UncivGame.Current.settings.useAStarPathfinding = (pathfindingAlgorithm == AStarPathfinding)
        testGame.makeHexagonalMap(8)
        originTile = testGame.tileMap[0,0]
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

        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        // expect movement through hills (2 hill tiles plus one default desert)
        assertEquals(3, unit.movement.getShortestPath(testGame.getTile(0,3)).size)
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

        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
//         val pathToTile = unit.movement.getDistanceToTilesWithinTurn(HexCoord(0,0), 5f).getPathToTile(testGame.getTile(0,3))
        unit.movement.moveToTile(testGame.getTile(0,3))
        assertEquals(1f, unit.currentMovement)
    }
    @Test
    fun getShortestPathReturnsOnlyEndOfTurns() {
        // Moving in a direct path, you'd waste 5 movement points to get there, ending up with 0.
        // Moving around the hills, you'd waste 4 movement points, leaving you with one remaining
        testGame.setTileTerrain(HexCoord(0, 1), "Hill")
        testGame.setTileTerrain(HexCoord(0, 2), "Hill")
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 3
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        val target = testGame.getTile(HexCoord(0, 3))

        val path = unit.movement.getShortestPath(target)

        assertEquals(path.toString(), 2, path.size)
        assertEquals(path.toString(), target, path[1])
    }

    @Test
    fun shortestPathEvenWhenItsWayMoreTiles() {
        assumeThat(pathfindingAlgorithm, equalTo(AStarPathfinding)) // Classic pathfinding can't handle this case correctly
        
        // A straight road from 0,0 up the x axis
        testGame.getTile(HexCoord(0, 0)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(1, 0)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(2, 0)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(3, 0)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(4, 0)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(5, 0)).setRoadStatus(RoadStatus.Railroad, civInfo)
        // then straight down the y axis for 4 tiles
        testGame.getTile(HexCoord(5, 1)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(5, 2)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(5, 3)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(5, 4)).setRoadStatus(RoadStatus.Railroad, civInfo)
        // then straight down the x axis for 4 tiles
        testGame.getTile(HexCoord(4, 4)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(3, 4)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(2, 4)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(1, 4)).setRoadStatus(RoadStatus.Railroad, civInfo)
        testGame.getTile(HexCoord(0, 4)).setRoadStatus(RoadStatus.Railroad, civInfo)
        // The total roads are be 14 tiles, but only 1.4 movement. the direct route is 3 tiles, but
        // 3 movement.  So the road route should be chosen, despite gong way out of the way.
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        val target = testGame.getTile(HexCoord(0, 4))

        val path = unit.movement.getShortestPath(target)

        // expect movement along the railroad, even though it's 13 tiles
        assertEquals(
            listOf(
                HexCoord(3, 4),
                HexCoord(0, 4)
            ),
            path.map { it.position },
        )
    }


    @Test
    fun whenThereIsNoRouteThenReturnNull() {
        // 2,0 is surrounded by Mountains
        testGame.setTileTerrain(HexCoord(1, 0), "Mountain")
        testGame.setTileTerrain(HexCoord(1, 1), "Mountain")
        testGame.setTileTerrain(HexCoord(2, 1), "Mountain")
        testGame.setTileTerrain(HexCoord(3, 1), "Mountain")
        testGame.setTileTerrain(HexCoord(3, 0), "Mountain")
        testGame.setTileTerrain(HexCoord(3, -1), "Mountain")
        testGame.setTileTerrain(HexCoord(2, -1), "Mountain")
        testGame.setTileTerrain(HexCoord(1, -1), "Mountain")
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)

        val target = testGame.getTile(HexCoord(2, 0))
        val path = unit.movement.getShortestPath(target)

        assertEquals(path.toString(), true, path.isEmpty())
    }

    @Test
    fun canPauseBeforeMountainsToCrossWithoutDamage() {
        assumeThat(pathfindingAlgorithm, equalTo(AStarPathfinding)) // Classic pathfinding can't handle this case correctly
        
        // Everything is mountains
        for (tile in testGame.tileMap.tileList) {
            testGame.setTileTerrain(tile.position, "Mountain")
        }
        testGame.setTileTerrain(HexCoord(0, 0), "Plains")
        testGame.setTileTerrain(HexCoord(0, 1), "Plains")
        // unit has two cross two mountains here, so MUST stop on 0,1 to safely cross
        testGame.setTileTerrain(HexCoord(0, 4), "Plains")
        testGame.setTileTerrain(HexCoord(0, 5), "Plains")
        // unit has two cross two mountains here, so MUST stop on 0,5 to safely cross
        testGame.setTileTerrain(HexCoord(0, 8), "Plains")

        // Enable Carthage LandUnitsCrossTerrainAfterUnitGained
        civInfo.passThroughImpassableUnlocked = true
        civInfo.passableImpassables.add("Mountain")

        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 3
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        val target = testGame.getTile(HexCoord(0, 8))

        val path = unit.movement.getShortestPath(target, avoidDamagingTerrain = true)

        assertEquals(listOf(
            HexCoord(0, 1),
            HexCoord(0, 4),
            HexCoord(0, 5),
            HexCoord(0, 8),
        ), path.map { it.position })
    }

    @Test
    fun getMovementToTilesAtPosition_returnsRightTiles() {
        testGame.setTileTerrain(HexCoord(0, 1), "Hill")
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 2
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 2f

        val path = unit.movement.getMovementToTilesAtPosition(originTile.position, 2f)

        assertEquals(path.toString(), 18, path.size)
        assertNotEquals(path.toString(), path.firstEntry(), path.lastEntry())
    }

    @Test
    fun getMovementToTilesAtPosition_withFractionalMovementGreaterThanOne_ReturnsRightTiles() {
        testGame.setTileTerrain(HexCoord(0, 1), "Hill")
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 2
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 1.5f

        val path = unit.movement.getMovementToTilesAtPosition(originTile.position, 1.5f)

        assertEquals(path.toString(), 18, path.size)
        assertNotEquals(path.toString(), path.firstEntry(), path.lastEntry())
    }

    @Test
    fun getMovementToTilesAtPosition_withFractionalMovementLessThanOne_ReturnsNeighbors() {
        testGame.setTileTerrain(HexCoord(0, 1), "Hill")
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 2
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 0.5f

        val path = unit.movement.getMovementToTilesAtPosition(originTile.position, 0.5f)

        assertEquals(path.toString(), 7, path.size)
        for (neighbor in originTile.neighbors)
            assertTrue("$path expected to contain $neighbor", path.containsKey(neighbor))
    }

    @Test
    fun getMovementToTilesAtPosition_withVirtuallyNoMovement_ReturnsNeighbors() {
        assumeThat(pathfindingAlgorithm, equalTo(ClassicPathfinding)) // AStar only returns Origin
        testGame.setTileTerrain(HexCoord(0, 1), "Hill")
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 2
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 0.017f

        val path = unit.movement.getMovementToTilesAtPosition(originTile.position, 0.017f)

        assertEquals(path.toString(), 7, path.size)
        assertTrue("$path expected to contain $originTile", path.containsKey(originTile))
        for (neighbor in originTile.neighbors)
            assertTrue("$path expected to contain $neighbor", path.containsKey(neighbor))
    }

    @Test
    fun getMovementToTilesAtPosition_withZeroMovement_ReturnsOrigin() {
        testGame.setTileTerrain(HexCoord(0, 1), "Hill")
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 2
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 0f

        val path = unit.movement.getMovementToTilesAtPosition(originTile.position, 0f)

        assertEquals(path.toString(), 1, path.size)
        assertEquals(path.firstEntry().key, originTile)
    }

    @Test
    fun getMovementToTilesAtPosition_withNegativeMovement_ReturnsOrigin() {
        testGame.setTileTerrain(HexCoord(0, 1), "Hill")
        assumeThat(pathfindingAlgorithm, equalTo(AStarPathfinding)) // Classic pathfinding returns all neighbor tiles
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 2
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = -1f

        val path = unit.movement.getMovementToTilesAtPosition(originTile.position, -1f)

        assertEquals(path.toString(), 1, path.size)
        assertEquals(path.firstEntry().key, originTile)
    }

    @Test
    fun whenPathingToStartDoNotCrash() {
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)

        val target = testGame.getTile(HexCoord(0, 0))
        val path = unit.movement.getShortestPath(target)

        assertEquals(path.toString(), 1, path.size)
        assertEquals(path.toString(), HexCoord(0,0), path[0].position)
    }

    @Test
    fun whenMovementIsZeroThenCanMoveToAdjacent() {
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 0f
        val target = testGame.getTile(HexCoord(0, 1))

        assertTrue(unit.movement.canMoveTo(target))
    }

    @Test
    fun whenMovementIsZeroThenCannotReachAdjacent() {
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 0f
        val target = testGame.getTile(HexCoord(0, 1))

        assertFalse(unit.movement.canReach(target))
    }

    @Test
    fun whenPathingCostIs100ThenUseAllMovement() {
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 6
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        // River between x==1 and x==2
        for (tile in testGame.tileMap.tileList) {
            if (tile.position.x == 1) {
                val upRight = testGame.tileMap.getClockPositionNeighborTile(tile, 10)
                if (upRight != null)
                    tile.setConnectedByRiver(upRight, true, true)
                val downRight = testGame.tileMap.getClockPositionNeighborTile(tile, 12)
                if (downRight != null)
                    tile.setConnectedByRiver(downRight, true, true)
            }
        }

        val target = testGame.getTile(HexCoord(5, 0))
        val path = unit.movement.getShortestPath(target)

        // Crossing the river ends the turn, then the rest of the route is one turn
        assertEquals(path.toString(), 2, path.size)
        assertEquals(path.toString(), HexCoord(2,0), path[0].position)
        assertEquals(path.toString(), HexCoord(5,0), path[1].position)
    }

    @Test
    fun getShortestPath_avoidsUnfriendly() {
        val otherCiv = testGame.addCiv()
        civInfo.diplomacyFunctions.makeCivilizationsMeet(otherCiv)
        civInfo.getDiplomacyManager(otherCiv)!!.addModifier(DiplomaticModifiers.CapturedOurCities, -100f)
        otherCiv.getDiplomacyManager(civInfo)!!.addModifier(DiplomaticModifiers.CapturedOurCities, -100f)
        val city = testGame.addCity(otherCiv, testGame.tileMap[-5,-5])
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        originTile = testGame.tileMap[5, 0]
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentTile = originTile
        val target = testGame.getTile(HexCoord(5, 2))
        
        // find a path
        val path1 = unit.movement.getShortestPath(target)
        assertEquals(path1.toString(), 2, path1.size)
        // Warring civ now owns the middle tile in that path
        path1[0].setOwningCity(city)
        unit.movement.clearPathfindingCache()
        val path2 = unit.movement.getShortestPath(target)

        // make sure the path changed
        assertEquals(path2.toString(), 2, path1.size)
        assertNotEquals(path1[0], path2[0])
    }

    @Test
    fun getShortestPath_DoesNotAvoidAllies() {
        val otherCiv = testGame.addCiv()
        civInfo.diplomacyFunctions.makeCivilizationsMeet(otherCiv)
        civInfo.getDiplomacyManager(otherCiv)!!.addModifier(DiplomaticModifiers.BelieveSameReligion, 100f)
        civInfo.getDiplomacyManager(otherCiv)!!.hasOpenBorders = true
        val city = testGame.addCity(otherCiv, testGame.tileMap[-5,-5])
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        originTile = testGame.tileMap[5, 0]
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentTile = originTile
        val target = testGame.getTile(HexCoord(5, 2))

        // find a path
        val path1 = unit.movement.getShortestPath(target)
        assertEquals(path1.toString(), 2, path1.size)
        // Warring civ now owns the middle tile in that path
        path1[0].setOwningCity(city)
        unit.movement.clearPathfindingCache()
        val path2 = unit.movement.getShortestPath(target)

        // make sure the path changed
        assertEquals(path2.toString(), 2, path1.size)
        assertEquals(path1[0], path2[0])
    }
    
    companion object {
        @Suppress("unused")
        @Parameters
        @JvmStatic
        fun parameters(): Collection<Array<Any?>?> {
            return listOf(
                /* First execute the test with these parametrers */
                arrayOf(ClassicPathfinding),
                /* and then execute the test with these parametrers */
                arrayOf(AStarPathfinding))
        }
    }
}
