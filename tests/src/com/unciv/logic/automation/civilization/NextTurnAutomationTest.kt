package com.unciv.logic.automation.civilization

import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.automation.civilization.NextTurnAutomationTest.Companion.PathfindingAlgorithm.Classic
import com.unciv.logic.automation.civilization.NextTurnAutomationTest.Companion.PathfindingAlgorithm.AStar
import com.unciv.testing.GdxTestRunnerFactory
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.junit.runners.Parameterized.UseParametersRunnerFactory

@RunWith(Parameterized::class)
@UseParametersRunnerFactory(GdxTestRunnerFactory::class)
class NextTurnAutomationTest(private val algorithm: PathfindingAlgorithm) {

    private lateinit var civInfo: Civilization

    val testGame = TestGame()

    @Before
    fun setUp() {
        UncivGame.Current.settings.useAStarPathfinding = (algorithm == AStar)
        testGame.makeHexagonalMap(7)
        civInfo = testGame.addCiv()
        val capital = testGame.addCity(civInfo, testGame.tileMap[0,0])
        assertTrue(capital.isCapital())
    }

    @Test
    fun `automateSettlerEscorting replaces low hp escort`() {
        val settler = testGame.addUnit("Settler", civInfo, testGame.tileMap[0,2])
        val highHpWarrior = testGame.addUnit("Warrior", civInfo, testGame.tileMap[0,1])
        val lowHpWarrior = testGame.addUnit("Warrior", civInfo, testGame.tileMap[0,2])
        lowHpWarrior.takeDamage(90)
        lowHpWarrior.startEscorting()
        assertTrue(settler.isEscorting())

        // Act
        NextTurnAutomation.automateSettlerEscorting(civInfo)
        assertEquals("settler should not have moved, else test is invalid", testGame.tileMap[0,2], settler.currentTile)
        
        // Assert
        assertEquals("high hp warrior have taken the place of low hp escort of settler", testGame.tileMap[0,2], highHpWarrior.currentTile)
        assertEquals("high hp warrior have taken the place of low hp escort of settler", testGame.tileMap[0,1], lowHpWarrior.currentTile)
        assertEquals("high hp warrior have taken the place of low hp escort of settler", highHpWarrior, settler.getOtherEscortUnit())
        assertEquals("high hp warrior have taken the place of low hp escort of settler", settler, highHpWarrior.getOtherEscortUnit())
        assertEquals("high hp warrior have taken the place of low hp escort of settler", null, lowHpWarrior.getOtherEscortUnit())
    }

    @Test
    fun `automateSettlerEscorting replaces low hp escort even with no movement`() {
        // AStar fails this because UnitMovement#canUnitSwapToReachableTile
        // calls escortedUnit.movement.canMoveTo(includeOtherEscortUnit = false) 
        // and escortedUnit.movement.canUnitSwapToReachableTile(checkEscorted = false)
        // but AStar assumes includeOtherEscortUnit always true.
        val settler1 = testGame.addUnit("Settler", civInfo, testGame.tileMap[0,2])
        val settler2 = testGame.addUnit("Settler", civInfo, testGame.tileMap[0,1])
        val highHpWarrior = testGame.addUnit("Warrior", civInfo, testGame.tileMap[0,1])
        val lowHpWarrior = testGame.addUnit("Warrior", civInfo, testGame.tileMap[0,2])
        lowHpWarrior.takeDamage(90)
        lowHpWarrior.startEscorting()
        settler2.currentMovement = 0f
        assertEquals(lowHpWarrior, settler1.getOtherEscortUnit())
        assertEquals(highHpWarrior, settler2.getOtherEscortUnit())

        // Act
        NextTurnAutomation.automateSettlerEscorting(civInfo)
        assertEquals("settlers should not have moved, else test is invalid", testGame.tileMap[0,2], settler1.currentTile)
        assertEquals("settlers should not have moved, else test is invalid", testGame.tileMap[0,1], settler2.currentTile)

        // Assert
        assertEquals("high hp warrior have taken the place of low hp escort of settler", testGame.tileMap[0,2], highHpWarrior.currentTile)
        assertEquals("high hp warrior have taken the place of low hp escort of settler", testGame.tileMap[0,1], lowHpWarrior.currentTile)
        assertEquals("high hp warrior have taken the place of low hp escort of settler", highHpWarrior, settler1.getOtherEscortUnit())
        assertEquals("high hp warrior have taken the place of low hp escort of settler", settler1, highHpWarrior.getOtherEscortUnit())
        assertEquals("high hp warrior have taken the place of low hp escort of settler", lowHpWarrior, settler2.getOtherEscortUnit())
        assertEquals("high hp warrior have taken the place of low hp escort of settler", settler2, lowHpWarrior.getOtherEscortUnit())
    }

    companion object {
        enum class PathfindingAlgorithm {
            Classic,
            AStar
        }

        @Suppress("unused")
        @Parameters
        @JvmStatic
        fun parameters(): Collection<Array<Any?>?> {
            return listOf( arrayOf(Classic), arrayOf(AStar))
        }
    }
}
