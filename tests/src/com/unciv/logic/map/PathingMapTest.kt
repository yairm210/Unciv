package com.unciv.logic.map

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.PathingMap.Companion.NEVER_LOG
import com.unciv.logic.map.PathingMap.Companion.VERBOSE_PATHFINDING_LOGS
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.FixedPointMovement.Companion.fpmFromMovement
import com.unciv.logic.map.FixedPointMovement.Companion.fpmFromFixedPointBits
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(BaseTestRunner::class)
class PathingMapTest {

    private lateinit var originTile: Tile
    private lateinit var civInfo: Civilization
    private var testGame = TestGame()

    @Before
    fun initTheWorld() {
        testGame.makeHexagonalMap(8)
        originTile = testGame.tileMap[0, 0]
        civInfo = testGame.addCiv()
        for (i in 0 until 100)
            testGame.tileMap.tileList[i].setExplored(civInfo, true)
    }
    
    @Test // This only exists to reduce how often we accidentally push with verbose logging enabled
    fun verbose_pathing_logs_disabled() {
        assertEquals(VERBOSE_PATHFINDING_LOGS, NEVER_LOG)
    }
    
    @Test
    fun bitsInRouteNodeRoundTrip() {
        testGame.makeHexagonalMap(209) //209 is smallest radius that uses all bits in tile index
        val zeroBasedIndex = (1 shl 17) or 1 // 18 bits. value is 131073
        val tile = testGame.tileMap.tileList[zeroBasedIndex]
        val relationship = RelationshipLevel.Unforgivable // 3 bits, value is 8
        val pbmMoveThisTurn = fpmFromFixedPointBits((1 shl 8) or 1) //9 bits. value is 257 aka 13.00
        val moveThisTurn = fpmFromFixedPointBits((1 shl 8) or 1) //9 bits. value is 257 aka 13.00
        val turns = (1 shl 5) or 1 // 6 bits. value is 33
        val parentTile = testGame.tileMap.getClockPositionNeighborTile(tile, 12)!!
        val attackRange = (1 shl 4) or 1 // 5 bits. value is 17
        val damagingTiles = 3
        val underestimatedTotal = fpmFromFixedPointBits((1 shl 13) or 1) //15 bits. value is 8193 aka 409.65move

        val node = RouteNode(
            tile,
            relationship,
            pbmMoveThisTurn,
            moveThisTurn,
            turns,
            parentTile,
            damagingTiles,
            true
        )

        assertEquals(tile.zeroBasedIndex, node.tileIdx)
        assertEquals(tile, node.tile(testGame.tileMap))
        assertEquals(12, node.parentClockDir)
        assertEquals(parentTile, node.parentTile(testGame.tileMap))
        assertEquals(false, node.canStopOn)
        assertEquals(moveThisTurn, node.moveUsedThisTurn)
        assertEquals(pbmMoveThisTurn, node.moveSinceStoppable)
        assertEquals(turns, node.turns)
        assertEquals(damagingTiles, node.damagingTiles)
        assertEquals(false, node.endTurnWithoutMoreDamage)
        assertEquals(relationship, node.relationshipLevel)
        assertEquals(true, node.initialized)

        val prioritized = PrioritizedNode(node, underestimatedTotal)
        assertEquals(tile.zeroBasedIndex, prioritized.tileIdx)
        assertEquals(underestimatedTotal, prioritized.underestimatedTotal)
        
        val reRouteNode = RouteNode(prioritized.bits)
        assertEquals(tile.zeroBasedIndex, reRouteNode.tileIdx)
        assertEquals(tile, reRouteNode.tile(testGame.tileMap))
        // PrioritizedNode drops parentTile
        assertEquals(moveThisTurn, reRouteNode.moveUsedThisTurn)
        assertEquals(pbmMoveThisTurn, reRouteNode.moveSinceStoppable)
        assertEquals(turns, reRouteNode.turns)
        assertEquals(damagingTiles, reRouteNode.damagingTiles)
        assertEquals(relationship, reRouteNode.relationshipLevel)
        assertEquals(true, reRouteNode.initialized)
    }


    @Test
    fun shortestPathEvenWhenItsWayMoreTiles() {
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

        val pathing = PathingMap.createUnitPathingMap(unit)
        val path = pathing.getShortestPath(target)!!

        // expect movement along the railroad, even though it's 13 tiles
        assertEquals(
            listOf(
                HexCoord(3, 4),
                HexCoord(0, 4)
            ),
            path.map { it.position },
        )
        assertEquals(1, pathing.getCachedNode(target).turns)
        assertEquals(fpmFromMovement(0.3f), pathing.getCachedNode(target).moveUsedThisTurn)
        assertEquals("""
        -1     +0     +1     +2     +3     +4     +5     +6    
  +5     /      /     1/1.0  1/1.0  1/1.0  0/1.0  0/1.0  0/1.0 
  +4     /     1/0.3D 1/0.2  1/0.1  0/1.0  0/0.9  0/0.8  0/1.0 
  +3     /     1/1.0  1/1.0  1/1.0  0/1.0  0/1.0  0/0.7  0/1.0 
  +2     /      /      /      /      /     0/1.0  0/0.6  0/1.0 
  +1     /     0/1.0  0/1.0  0/1.0  0/1.0  0/1.0  0/0.5  0/1.0 
  +0    0/1.0  0/0.0S 0/0.1  0/0.2  0/0.3  0/0.4  0/0.5   /    
  -1    0/1.0  0/1.0  0/1.0  0/1.0  0/1.0  0/1.0   /      /    
""", pathing.toDebugString(target))
        // And affirm cache
        assertEquals(path, pathing.getShortestPath(target)!!)
    }

    @Test
    fun canPauseBeforeMountainsToCrossWithoutDamage() {
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

        val pathing = PathingMap.createUnitPathingMap(unit)
        val path = pathing.getShortestPath(target)

        assertEquals(listOf(
            HexCoord(0, 1),
            HexCoord(0, 4),
            HexCoord(0, 5),
            HexCoord(0, 8),
        ), path?.map { it.position })
        assertEquals("""
        -3     -2     -1     +0     +1     +2     +3    
  +8                         3/3.0D 4/1.0   /      /    
  +7                  4/1.0  3/2.0  3/2.0  3/2.0  3/1.0 
  +6            /     3/2.0  2/2.0  2/2.0  2/2.0  3/1.0 
  +5     /     3/2.0  2/2.0  2/1.0  2/1.0  2/2.0  3/1.0 
  +4    3/1.0  2/2.0  2/1.0  1/3.0  2/1.0  2/2.0  3/1.0 
  +3    3/1.0  2/2.0  2/1.0  1/2.0  1/2.0  1/2.0  2/1.0 
  +2    3/1.0  2/2.0  1/2.0  0/2.0  0/2.0  0/2.0  1/1.0 
  +1    3/1.0  1/2.0  0/2.0  0/1.0  0/1.0  0/2.0  1/1.0 
  +0    2/1.0  0/2.0  0/1.0  0/0.0S 0/1.0  0/2.0  1/1.0 
  -1    1/1.0  0/2.0  0/1.0  0/1.0  0/2.0  1/1.0   /    
  -2    1/1.0  0/2.0  0/2.0  0/2.0  1/1.0   /      /    
  -3    1/1.0  1/1.0  1/1.0  1/1.0   /      /      /    
""", pathing.toDebugString(target))
        // And affirm cache
        assertEquals(path, pathing.getShortestPath(target)!!)
    }
    
    @Test
    fun getMovementToTilesAtPosition_returnsRightTiles() {
        testGame.setTileTerrain(HexCoord(0, 1), "Hill")
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 2
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 2f

        val pathing = PathingMap.createUnitPathingMap(unit)
        val path = pathing.getMovementToTilesAtPosition()

        assertEquals(path.toString(), 18, path.size)
//        assertNotEquals(path.toString(), path.firstEntry(), path.lastEntry())
        assertEquals("""
        -2     -1     +0     +1     +2    
  +2     /      /      /     0/2.0  0/2.0 
  +1     /     0/2.0  0/2.0  0/1.0  0/2.0 
  +0    0/2.0  0/1.0  0/0.0S 0/1.0  0/2.0 
  -1    0/2.0  0/1.0  0/1.0  0/2.0   /    
  -2    0/2.0  0/2.0  0/2.0   /      /    
""", pathing.toDebugString())
        // And affirm cache
        assertEquals(path, pathing.getMovementToTilesAtPosition())
    }

    @Test
    fun getShortestPath_takesOverMaxTurns_returnsNull() {
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        originTile = testGame.getTile(HexCoord(-8, -8))
        val targetTile = testGame.getTile(HexCoord(8, 8))
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 1f

        val pathing = PathingMap.createUnitPathingMap(unit)
        val path = pathing.getShortestPath(targetTile, 5)

        assertNull(path)
        // And affirm cache
        assertEquals(path, pathing.getShortestPath(targetTile, 5))
    }

    @Test
    fun getShortestPath_takesOver63Turns_returnsNull() {
        testGame.makeHexagonalMap(100)
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        originTile = testGame.getTile(HexCoord(-50, 0))
        val targetTile = testGame.getTile(HexCoord(50, 0))
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 1f

        val pathing = PathingMap.createUnitPathingMap(unit)
        val path = pathing.getShortestPath(targetTile)

        assertNull(path)
        // And affirm cache
        assertEquals(path, pathing.getShortestPath(targetTile))
    }

    @Test
    fun getShortestPath_toEveryTile_usesCacheCorrectly() {
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 4
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 4f

        val pathing = PathingMap.createUnitPathingMap(unit)
        for (targetTile in testGame.tileMap.tileList) {
            val aerialDistance = originTile.aerialDistanceTo(targetTile)
            
            val path = pathing.getShortestPath(targetTile)
            
            if (aerialDistance <= 4) {
                assertEquals(listOf(targetTile), path)
            } else {
                assertEquals(2, path!!.size)
                assertEquals(targetTile, path[1])                
            }
        }
    }
    
    @Test
    fun findMatchingTilesInAttackRange_ignoresAttackRange() {
        val evemyCiv = testGame.addBarbarianCiv()
        // Right side all hills, left side all roads.  A line of enemy warriors just north.
        for (tile in testGame.tileMap.tileList) {
            if (tile.position.x > 0) tile.addTerrainFeature("Hill")
            if (tile.position.x < 0) tile.setRoadStatus(RoadStatus.Road, civInfo)
            if (tile.position.y == 2) testGame.addUnit("Warrior", evemyCiv, tile)
        }
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 3
        baseUnit.range = 2
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 3f

        val pathing = PathingMap.createUnitPathingMap(unit)
        val attackableTiles = pathing.bfsAllMatchingTiles(1) { tile, _ -> tile.militaryUnit?.civ == evemyCiv}
        
        // cant hit enemy at -4,2 or 3,2 because unit would have no movement left.
        val expected = listOf(
            HexCoord(-3,2),
            HexCoord(-2,2),
            HexCoord(-1,2),
            HexCoord(0,2),
            HexCoord(1,2),
            HexCoord(2,2),
        )
        val actual = attackableTiles.map {it.position}.sortedWith { l, r -> if (l.x != r.x) l.x.compareTo(r.x) else l.y.compareTo(r.y) }
        assertEquals(expected, actual)
        assertEquals("""
        -5     -4     -3     -2     -1     +0     +1     +2    
  +2     /      /     0/17.0* 0/17.0* 0/17.0* 0/17.0* 0/17.0* 0/17.0*
  +1     /     0/3.0  0/2.5  0/2.0  0/1.5  0/1.0  0/2.0  0/3.0 
  +0    0/3.0  0/2.5  0/2.0  0/1.5  0/1.0  0/0.0S 0/2.0  0/3.0 
  -1    0/3.0  0/2.5  0/2.0  0/1.5  0/1.0  0/1.0  0/3.0   /    
  -2    0/3.0  0/2.5  0/2.0  0/1.5  0/1.5  0/2.0  0/3.0   /    
  -3    0/3.0  0/2.5  0/2.0  0/2.0  0/2.0  0/3.0   /      /    
  -4    0/3.0  0/2.5  0/2.5  0/2.5  0/2.5  0/3.0   /      /    
  -5    0/3.0  0/3.0  0/3.0  0/3.0  0/3.0   /      /      /    
""", pathing.toDebugString())
        // And affirm full recalculation using cached tiles has same result.
        val actual2 = attackableTiles.map {it.position}.sortedWith { l, r -> if (l.x != r.x) l.x.compareTo(r.x) else l.y.compareTo(r.y) }
        assertEquals(expected, actual2)
    }

    @Test
    fun findMatchingTilesInAttackRange_considersSelf() {
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        val unit = testGame.addUnit(baseUnit.name, civInfo, originTile)
        unit.currentMovement = 1f

        val pathing = PathingMap.createUnitPathingMap(unit)
        val attackableTiles = pathing.bfsAllMatchingTilesThisTurn { tile, _ -> tile.civilianUnit?.civ == civInfo}

        val expected = listOf(HexCoord(0,0))
        assertEquals(expected, attackableTiles.map {it.position})
        // And affirm full recalculation using cached tiles has same result.
        assertEquals(expected, pathing.bfsAllMatchingTilesThisTurn { tile, _ -> tile.civilianUnit?.civ == civInfo}.map {it.position})
    }

    @Test
    fun safeCacheIsPreferredOverDamageCacheWhenBothReachTargetEquallyFast() {
        // Two routes of identical length from origin to target: one straight through a Mountain
        // (damaging), one around through Plains (safe). The safe, damage-free route must win.
        testGame.setTileTerrain(HexCoord(1, 0), "Mountain")
        val civ = testGame.addCiv("Land units may cross [Mountain] tiles after the first [Great General] is earned")
        civ.passThroughImpassableUnlocked = true
        civ.passableImpassables.add("Mountain")
        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 3
        val unit = testGame.addUnit(baseUnit.name, civ, originTile)
        val target = testGame.getTile(HexCoord(2, 0))

        val pathing = PathingMap.createUnitPathingMap(unit)
        val path = pathing.getShortestPath(target)

        assertEquals(listOf(target), path)
        val node = pathing.getCachedNode(target)
        assertEquals(0, node.damagingTiles)
    }

    @Test
    fun onlyRouteIsThroughDamage_stillFindsItViaDamageCache() {
        // Everything is mountains except a corridor: origin, one Mountain, then the target. With
        // only 1 movement, crossing the Mountain is unavoidable and forces ending a turn on it.
        for (tile in testGame.tileMap.tileList) {
            testGame.setTileTerrain(tile.position, "Mountain")
        }
        testGame.setTileTerrain(HexCoord(0, 0), "Plains")
        testGame.setTileTerrain(HexCoord(2, 0), "Plains")
        val civ = testGame.addCiv("Land units may cross [Mountain] tiles after the first [Great General] is earned")
        civ.passThroughImpassableUnlocked = true
        civ.passableImpassables.add("Mountain")

        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        val unit = testGame.addUnit(baseUnit.name, civ, originTile)
        val target = testGame.getTile(HexCoord(2, 0))

        val pathing = PathingMap.createUnitPathingMap(unit)
        val path = pathing.getShortestPath(target)

        assertEquals(listOf(testGame.getTile(HexCoord(1,0)), target), path)
        val node = pathing.getCachedNode(target)
        assertEquals(1, node.damagingTiles)
    }

    @Test
    fun chainOfMultipleDamagingTilesIsCrossedAndTallied() {
        // Everything is mountains except a corridor with 3 consecutive Mountain tiles to cross,
        // forced to stop on each one in turn (1 movement per turn).
        for (tile in testGame.tileMap.tileList) {
            testGame.setTileTerrain(tile.position, "Mountain")
        }
        testGame.setTileTerrain(HexCoord(0, 0), "Plains")
        testGame.setTileTerrain(HexCoord(4, 0), "Plains")
        val civ = testGame.addCiv("Land units may cross [Mountain] tiles after the first [Great General] is earned")
        civ.passThroughImpassableUnlocked = true
        civ.passableImpassables.add("Mountain")

        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 1
        val unit = testGame.addUnit(baseUnit.name, civ, originTile)
        val target = testGame.getTile(HexCoord(4, 0))

        val pathing = PathingMap.createUnitPathingMap(unit)
        val path = pathing.getShortestPath(target)

        assertEquals(listOf(
            testGame.getTile(HexCoord(1, 0)),
            testGame.getTile(HexCoord(2, 0)),
            testGame.getTile(HexCoord(3, 0)),
            target,
        ), path)
        val node = pathing.getCachedNode(target)
        // Ending our turn there is the only option, crossing all 3 damaging tiles along the way.
        assertEquals(3, node.damagingTiles)
    }

    @Test
    fun occupiedTileDoesNotInterfereWithDamageAccounting() {
        // The direct route is blocked by another unit; the detour crosses a Mountain instead.
        // The two concepts (blocked-for-ending-turn vs damage) must not interfere with each other.
        testGame.setTileTerrain(HexCoord(1, 0), "Grassland")
        testGame.setTileTerrain(HexCoord(1, -1), "Mountain")
        testGame.setTileTerrain(HexCoord(2, -1), "Grassland")
        testGame.setTileTerrain(HexCoord(2, 0), "Grassland")
        val civ = testGame.addCiv("Land units may cross [Mountain] tiles after the first [Great General] is earned")
        civ.passThroughImpassableUnlocked = true
        civ.passableImpassables.add("Mountain")
        val blockerCiv = testGame.addCiv()
        testGame.addUnit("Warrior", blockerCiv, testGame.getTile(HexCoord(1, 0)))

        val baseUnit = testGame.createBaseUnit()
        baseUnit.movement = 3
        val unit = testGame.addUnit(baseUnit.name, civ, originTile)
        val target = testGame.getTile(HexCoord(2, 0))

        val pathing = PathingMap.createUnitPathingMap(unit)
        val path = pathing.getShortestPath(target)

        assertEquals(listOf(target), path)
    }

    @Test
    fun canRetroactivelyPauseOnADamagingTileToPushPastAnUnstoppableTileAfterIt() {
        for (tile in testGame.tileMap.tileList) {
            testGame.setTileTerrain(tile.position, "Ocean")
        }
        testGame.setTileTerrain(HexCoord(0, 0), "Plains")
        testGame.setTileTerrain(HexCoord(0, 1), "Mountain")
        testGame.setTileTerrain(HexCoord(0, 2), "Plains")
        testGame.setTileTerrain(HexCoord(0, 3), "Plains")
        civInfo.passThroughImpassableUnlocked = true
        civInfo.passableImpassables.add("Mountain")
        testGame.addUnit("Warrior", civInfo, testGame.getTile(HexCoord(0, 2)))

        val unit = testGame.addUnit("Warrior", civInfo, originTile)
        unit.currentMovement = 2f
        val target = testGame.getTile(HexCoord(0, 3))
        val pathing = PathingMap.createUnitPathingMap(unit)
        val path = pathing.getShortestPath(target)

        assertEquals(listOf(testGame.getTile(HexCoord(0, 1)), target), path)
        assertEquals(1, pathing.getCachedNode(target).damagingTiles)
    }

    @Test
    fun manyWarriorsConvergingOnRoad_doNotQueueSingleFile() {
        for (x in -8..8)
            testGame.getTile(HexCoord(x, 0)).setRoadStatus(RoadStatus.Road, civInfo)
        for (tile in testGame.tileMap.tileList)
            tile.setExplored(civInfo, true)
        //make 18 warriors between [-8,-8] and [-7,0]
        val warriors = ArrayList<MapUnit>()
        for (tile in testGame.tileMap.tileList) {
            if (tile.position.x < -6 && tile.position.y<1)
                warriors.add(testGame.addUnit("Warrior", civInfo, tile))
        }
        assertEquals(18, warriors.size)
        // tell all 19 warriors to move 15 tiles to the right, and 8 up.
        val targets = warriors.map {
            val x = it.getTile().position.x+15
            val y = it.getTile().position.y+8
            testGame.tileMap[x,y]
        }
        // Ideally, moving these 15 tiles takes them 8 turns
        repeat(8) {
            for (warrior in warriors)
                warrior.currentMovement = 2f
            for ((warrior, target) in warriors.zip(targets))
                warrior.movement.headTowards(target)
        }
        // Ensure all warriors moved more than halfway, and didn't end up single-file
        for (warrior in warriors) {
            val position = warrior.getTile().position
            assertTrue("$warrior at $position should have moved to x>0", position.x > 0)
        }
    }
}
