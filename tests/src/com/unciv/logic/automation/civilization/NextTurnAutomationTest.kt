package com.unciv.logic.automation.civilization

import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.metadata.GameSettings.PathfindingAlgorithm
import com.unciv.models.metadata.GameSettings.PathfindingAlgorithm.AStarPathfinding
import com.unciv.testing.TestGame
import com.unciv.testing.TestRunnerFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.junit.runners.Parameterized.UseParametersRunnerFactory
import org.mockito.Mockito

@RunWith(Parameterized::class)
@UseParametersRunnerFactory(TestRunnerFactory::class)
class NextTurnAutomationTest(private val algorithm: PathfindingAlgorithm) {
    companion object {
        @Suppress("unused")
        @Parameters
        @JvmStatic
        fun parameters() = TestRunnerFactory.Parameters.pathfinding
    }

    private lateinit var civInfo: Civilization

    val testGame = TestGame()

    @Before
    fun setUp() {
        UncivGame.Current.settings.useAStarPathfinding = (algorithm == AStarPathfinding)
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

    @Test
    fun `unit destroyed during an earlier promotion is not promoted or automated`() {
        // Civilian has no vanilla promotion tree, so Lead is the only promotion these units can buy.
        val actorBase = testGame.createBaseUnit("Civilian").apply { movement = 2; strength = 8 }
        val victimBase = testGame.createBaseUnit("Civilian").apply { movement = 2; strength = 8 }
        val bystanderBase = testGame.createBaseUnit("Civilian").apply { movement = 2; strength = 8 }
        // Granted, not chosen: empty unitTypes cannot be bought, but can be applied.
        val doom = testGame.createUnitPromotion("[This Unit] is destroyed")
        val lead = testGame.createUnitPromotion("[${victimBase.name}] units gain the [${doom.name}] promotion")
        lead.unitTypes = listOf("Civilian")

        val actor = testGame.addUnit(actorBase.name, civInfo, testGame.getTile(1, 0))
        val victim = testGame.addUnit(victimBase.name, civInfo, testGame.getTile(0, 1))
        val bystander = testGame.addUnit(bystanderBase.name, civInfo, testGame.getTile(0, 2))
        val victimTile = victim.currentTile
        actor.health = 10
        victim.health = 40
        bystander.health = 100
        actor.promotions.XP = 10
        victim.promotions.XP = 10
        bystander.promotions.XP = 10
        val bystanderStart = bystander.currentTile

        NextTurnAutomation.automateCivMoves(civInfo, tradeAndChangeState = false)

        assertTrue("earlier promotion ran", actor.promotions.promotions.contains(lead.name))
        assertTrue("victim was destroyed during the pass", victim.isDestroyed)
        assertTrue("destruction was the earlier unit's promotion effect", victim.promotions.promotions.contains(doom.name))
        assertFalse("destroyed unit was not promoted on its own turn", victim.promotions.promotions.contains(lead.name))
        assertTrue(victimTile.militaryUnit !== victim)
        assertTrue("bystander was still promoted", bystander.promotions.promotions.contains(lead.name))
        assertFalse(bystander.isDestroyed)
        assertTrue(
            "bystander was still automated",
            bystander.currentTile != bystanderStart || bystander.currentMovement < 2f
        )
    }

    @Test
    fun `settler transferred to barbarians during the pass is not automated`() {
        val barbarians = testGame.addBarbarianCiv()
        val spaceship = testGame.addUnit("SS Engine", civInfo, testGame.getTile(2, 0))
        spaceship.currentMovement = 0f
        val settler = testGame.addUnit("Settler", civInfo, testGame.getTile(3, 0))
        val settlerTile = settler.currentTile
        val bystander = testGame.addUnit("Warrior", civInfo, testGame.getTile(0, 2))
        val bystanderStart = bystander.currentTile

        onFirstAutomation(spaceship) { settler.capturedBy(barbarians) }

        NextTurnAutomation.automateCivMoves(civInfo, tradeAndChangeState = false)

        assertTrue("transfer happened", settler.civ.isBarbarian)
        assertSame(barbarians, settler.civ)
        assertFalse(civInfo.units.getCivUnits().any { it === settler })
        assertTrue(barbarians.units.getCivUnits().any { it === settler })
        assertSame("settler received no further move", settlerTile, settler.currentTile)
        assertEquals(1, civInfo.cities.size)
        assertTrue(
            "unaffected unit was still automated",
            bystander.currentTile != bystanderStart || bystander.currentMovement < 2f
        )
    }

    @Test
    fun `warrior gifted to another civ during the pass is not automated`() {
        val otherCiv = testGame.addCiv()
        val actorBase = testGame.createBaseUnit("Civilian")
        val actor = testGame.addUnit(actorBase.name, civInfo, testGame.getTile(2, 2))
        actor.currentMovement = 0f
        val victim = testGame.addUnit("Warrior", civInfo, testGame.getTile(2, 0))
        val victimTile = victim.currentTile
        val victimMovement = victim.currentMovement
        val bystander = testGame.addUnit("Warrior", civInfo, testGame.getTile(0, 2))
        val bystanderStart = bystander.currentTile

        onFirstAutomation(actor) { victim.gift(otherCiv) }

        NextTurnAutomation.automateCivMoves(civInfo, tradeAndChangeState = false)

        assertSame("transfer happened", otherCiv, victim.civ)
        assertTrue(otherCiv.units.getCivUnits().any { it === victim })
        assertFalse(civInfo.units.getCivUnits().any { it === victim })
        assertSame("former owner did not move the gifted unit", victimTile, victim.currentTile)
        assertEquals(victimMovement, victim.currentMovement, 0.01f)
        assertTrue(
            "unaffected unit was still automated",
            bystander.currentTile != bystanderStart || bystander.currentMovement < 2f
        )
    }

    /** Puts [unit]'s spy in the civ unit list and runs [action] the first time automation asks it for terrain damage on its current tile. */
    private fun onFirstAutomation(unit: MapUnit, action: () -> Unit) {
        val spy = replaceWithSpy(unit)
        val tile = unit.currentTile
        var done = false
        Mockito.doAnswer { invocation ->
            if (!done) {
                done = true
                action()
            }
            invocation.callRealMethod()
        }.`when`(spy).getDamageFromTerrain(tile)
    }

    private fun replaceWithSpy(unit: MapUnit): MapUnit {
        val spy = Mockito.spy(unit)
        unit.civ.units.removeUnit(unit)
        unit.civ.units.addUnit(spy, updateCivInfo = false)
        return spy
    }
}
