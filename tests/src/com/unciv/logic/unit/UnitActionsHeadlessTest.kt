package com.unciv.logic.unit

import com.unciv.GUI
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.civilization.Civilization
import com.unciv.models.UnitActionType
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the two things the headless MCP agent relies on: enumerating unit actions with no
 *  world screen loaded, and picking a combat target the same way the `attack` tool does. */
@RunWith(GdxTestRunner::class)
class UnitActionsHeadlessTest {
    private lateinit var attackerCiv: Civilization
    private lateinit var defenderCiv: Civilization

    private val testGame = TestGame()

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(6)
        attackerCiv = testGame.addCiv(isPlayer = true)
        defenderCiv = testGame.addCiv()
        attackerCiv.diplomacyFunctions.makeCivilizationsMeet(defenderCiv)
        attackerCiv.diplomacy[defenderCiv.civName]?.declareWar()
    }

    @Test
    fun `enumerates unit actions headless and includes Promote and DisbandUnit`() {
        assertFalse("Test should run with no world screen loaded", GUI.isWorldLoaded())

        val promotion = testGame.createUnitPromotion()
        val unit = testGame.addUnit("Warrior", attackerCiv, testGame.getTile(0, 0))
        promotion.unitTypes = listOf(unit.type.name)
        unit.promotions.XP = 999 // enough XP to be promotable regardless of ruleset cost

        val actionTypes = UnitActions.getUnitActions(unit).map { it.type }.toList()

        assertTrue(UnitActionType.Promote in actionTypes)
        assertTrue(UnitActionType.DisbandUnit in actionTypes)
    }

    @Test
    fun `attack tool target matching finds an adjacent enemy`() {
        val attackerUnit = testGame.addUnit("Warrior", attackerCiv, testGame.getTile(0, 0))
        attackerUnit.currentMovement = attackerUnit.getMaxMovement().toFloat()
        val defenderTile = testGame.getTile(1, 0)
        testGame.addUnit("Warrior", defenderCiv, defenderTile)

        val targets = TargetHelper.getAttackableEnemies(attackerUnit, attackerUnit.movement.getDistanceToTiles())
        // Same lookup the `attack` MCP tool does with the caller-supplied (x, y).
        val target = targets.firstOrNull { it.tileToAttack.position.x == defenderTile.position.x && it.tileToAttack.position.y == defenderTile.position.y }

        assertEquals(defenderTile, target?.tileToAttack)
    }
}
