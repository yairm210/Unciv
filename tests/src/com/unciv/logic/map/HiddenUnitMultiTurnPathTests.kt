package com.unciv.logic.map

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.models.metadata.GameSettings.PathfindingAlgorithm
import com.unciv.models.metadata.GameSettings.PathfindingAlgorithm.AStarPathfinding
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.TestGame
import com.unciv.testing.TestRunnerFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.junit.runners.Parameterized.UseParametersRunnerFactory

@RunWith(Parameterized::class)
@UseParametersRunnerFactory(TestRunnerFactory::class)
class HiddenUnitMultiTurnPathTests(private val pathfindingAlgorithm: PathfindingAlgorithm) {
    companion object {
        @Parameters
        @JvmStatic
        fun parameters() = TestRunnerFactory.Parameters.pathfinding
    }

    private lateinit var civInfo: Civilization
    private lateinit var testGame: TestGame

    @Before
    fun initTheWorld() {
        testGame = TestGame()
        UncivGame.Current.settings.useAStarPathfinding = (pathfindingAlgorithm == AStarPathfinding)
        testGame.makeHexagonalMap(6)
        civInfo = testGame.addCiv()
        civInfo.tech.techsResearched.addAll(testGame.ruleset.technologies.keys)
        civInfo.tech.embarkedUnitsCanEnterOcean = true
        civInfo.tech.unitsCanEmbark = true
    }

    @Test
    fun `hidden blocker discovered on a multi-turn route is rerouted by both pathfinding algorithms`() {
        val otherCiv = testGame.addCiv()
        civInfo.diplomacy[otherCiv.civName] = DiplomacyManager(civInfo, otherCiv)
        civInfo.getDiplomacyManager(otherCiv)!!.diplomaticStatus = DiplomaticStatus.War

        val origin = testGame.tileMap[0, 0]
        val ourUnit = testGame.addUnit("Warrior", civInfo, origin)

        // Find a destination that definitely requires more than one turn to reach.
        val destination = testGame.tileMap.tileList.first {
            it != origin && ourUnit.movement.getShortestPath(it).size > 1
        }

        // Calculate the multi-turn route before the hidden unit exists, then put the blocker
        // on its first step so that approaching it will trigger discovery.
        val initialPath = ourUnit.movement.getShortestPath(destination)
        assertTrue("The test requires a genuine multi-turn route", initialPath.size > 1)
        val hiddenTile = initialPath.first()

        val hiddenUnit = testGame.addDefaultMeleeUnitWithUniques(
            otherCiv,
            hiddenTile,
            UniqueType.Invisible.text
        )
        assertFalse("The blocker must really be invisible before discovery", hiddenUnit.isVisibleTo(civInfo))

        // Move only to the hidden tile so discovery happens through the normal movement path.
        ourUnit.currentMovement = ourUnit.getMaxMovement().toFloat()
        ourUnit.movement.moveToTile(hiddenTile)

        assertFalse("The unit must stop before entering the hidden blocker", ourUnit.currentTile == hiddenTile)
        assertTrue("The hidden blocker must be discovered by the movement attempt",
            civInfo.viewableInvisibleUnitsTiles.contains(hiddenTile))
        assertEquals("The hidden unit must not be overwritten", hiddenUnit, hiddenTile.militaryUnit)

        // This is the multi-turn regression check: after discovery, the selected pathfinding
        // algorithm must find a route to the distant destination that avoids the now-known blocker.
        val reroutedPath = ourUnit.movement.getShortestPath(destination)

        assertTrue("A multi-turn route to the destination must still exist", reroutedPath.isNotEmpty())
        assertEquals("The rerouted path must still reach the destination", destination, reroutedPath.last())
        assertFalse("The rerouted path must avoid the discovered hidden blocker",
            reroutedPath.contains(hiddenTile))
    }

    @Test
    fun `multi-turn path exploration allows an undiscovered hidden blocker as an intermediate tile`() {
        val otherCiv = testGame.addCiv()
        civInfo.diplomacy[otherCiv.civName] = DiplomacyManager(civInfo, otherCiv)
        civInfo.getDiplomacyManager(otherCiv)!!.diplomaticStatus = DiplomaticStatus.War

        // Make a one-tile-wide corridor so the hidden tile cannot simply be routed around.
        for (tile in testGame.tileMap.tileList)
            testGame.setTileTerrain(tile.position, Constants.mountain)
        val origin = testGame.setTileTerrain(HexCoord(0, 0), Constants.plains)
        val hiddenTile = testGame.setTileTerrain(HexCoord(1, 0), Constants.plains)
        val secondTurnTile = testGame.setTileTerrain(HexCoord(2, 0), Constants.plains)
        val destination = testGame.setTileTerrain(HexCoord(3, 0), Constants.plains)
        for (tile in testGame.tileMap.tileList) tile.setExplored(civInfo, true)

        val baseUnit = testGame.createBaseUnit().apply { movement = 1 }
        val ourUnit = testGame.addUnit(baseUnit.name, civInfo, origin)
        val hiddenUnit = testGame.addDefaultMeleeUnitWithUniques(
            otherCiv,
            hiddenTile,
            UniqueType.Invisible.text
        )

        assertFalse(hiddenUnit.isVisibleTo(civInfo))
        assertEquals(
            listOf(hiddenTile, secondTurnTile, destination),
            ourUnit.movement.getShortestPath(destination)
        )
        assertEquals(hiddenTile, ourUnit.movement.getTileToMoveToThisTurn(destination))

        ourUnit.movement.moveToTile(hiddenTile)

        assertEquals("Strict placement must still stop before the blocker", origin, ourUnit.currentTile)
        assertEquals("The hidden unit must not be overwritten", hiddenUnit, hiddenTile.militaryUnit)
        assertTrue("Attempting the explored path must reveal the blocker", hiddenUnit.isVisibleTo(civInfo))
    }
}
