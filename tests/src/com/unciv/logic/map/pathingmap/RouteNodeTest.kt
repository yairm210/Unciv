package com.unciv.logic.map.pathingmap

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class RouteNodeTest {
    @Test
    fun bitsInRouteNodeRoundTrip() {
        val testGame = TestGame()
        testGame.makeHexagonalMap(209) //209 is smallest radius that uses all bits in tile index
        val zeroBasedIndex = (1 shl 17) or 1 // 18 bits. value is 131073
        val tile = testGame.tileMap.tileList[zeroBasedIndex]
        val relationship = RelationshipLevel.Unforgivable // 3 bits, value is 8
        val pbmMoveThisTurn =
            FixedPointMovement.fpmFromFixedPointBits((1 shl 8) or 1) //9 bits. value is 257 aka 13.00
        val moveThisTurn =
            FixedPointMovement.fpmFromFixedPointBits((1 shl 8) or 1) //9 bits. value is 257 aka 13.00
        val turns = (1 shl 5) or 1 // 6 bits. value is 33
        val parentTile = testGame.tileMap.getClockPositionNeighborTile(tile, 12)!!
        val damagingTiles = 3
        val underestimatedTotal =
            FixedPointMovement.fpmFromFixedPointBits((1 shl 13) or 1) //15 bits. value is 8193 aka 409.65move

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

        Assert.assertEquals(tile.zeroBasedIndex, node.tileIdx)
        Assert.assertEquals(tile, node.tile(testGame.tileMap))
        Assert.assertEquals(12, node.parentClockDir)
        Assert.assertEquals(parentTile, node.parentTile(testGame.tileMap))
        Assert.assertEquals(false, node.canStopOn)
        Assert.assertEquals(moveThisTurn, node.moveUsedThisTurn)
        Assert.assertEquals(pbmMoveThisTurn, node.moveSinceStoppable)
        Assert.assertEquals(turns, node.turns)
        Assert.assertEquals(damagingTiles, node.damagingTiles)
        Assert.assertEquals(false, node.endTurnWithoutMoreDamage)
        Assert.assertEquals(relationship, node.relationshipLevel)
        Assert.assertEquals(true, node.initialized)

        val prioritized = PrioritizedNode(node, underestimatedTotal)
        Assert.assertEquals(tile.zeroBasedIndex, prioritized.tileIdx)
        Assert.assertEquals(underestimatedTotal, prioritized.underestimatedTotal)
        
        val reRouteNode = RouteNode(prioritized.bits)
        Assert.assertEquals(tile.zeroBasedIndex, reRouteNode.tileIdx)
        Assert.assertEquals(tile, reRouteNode.tile(testGame.tileMap))
        // PrioritizedNode drops parentTile
        Assert.assertEquals(moveThisTurn, reRouteNode.moveUsedThisTurn)
        Assert.assertEquals(pbmMoveThisTurn, reRouteNode.moveSinceStoppable)
        Assert.assertEquals(turns, reRouteNode.turns)
        Assert.assertEquals(damagingTiles, reRouteNode.damagingTiles)
        Assert.assertEquals(relationship, reRouteNode.relationshipLevel)
        Assert.assertEquals(true, reRouteNode.initialized)
    }
}
