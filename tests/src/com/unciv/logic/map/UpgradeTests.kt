@file:Suppress("UNUSED_VARIABLE")  // These are tests and the names serve readability

package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.models.UpgradeUnitAction
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsUpgrade
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class UpgradeTests {

    val testGame = TestGame()

    @Before
    fun initTest() {
        testGame.makeHexagonalMap(5)
    }

    @Test
    fun ruinsUpgradeToSpecialUnit() {
        val unitToUpgradeTo = testGame.createBaseUnit()
        val testUnit = testGame.createBaseUnit(uniques = arrayOf("May upgrade to [${unitToUpgradeTo.name}] through ruins-like effects"))
        testUnit.upgradesTo = "Warrior"

        val civ = testGame.addCiv()
        var unit1 = testGame.addUnit(testUnit.name, civ, testGame.getTile(Vector2.Zero))
        val triggerUnique = Unique("[This Unit] upgrades for free including special upgrades")
        UniqueTriggerActivation.triggerUnique(triggerUnique, unit1)
        unit1 = testGame.getTile(Vector2.Zero).getFirstUnit()!!

        Assert.assertTrue("Unit should upgrade to special unit, not warrior", unit1.baseUnit == unitToUpgradeTo)
    }

    @Test
    fun ruinsUpgradeToNormalUnitWithoutUnique() {
        val unitToUpgradeTo = testGame.createBaseUnit()
        val testUnit = testGame.createBaseUnit()
        testUnit.upgradesTo = "Warrior"

        val civ = testGame.addCiv()
        var unit1 = testGame.addUnit(testUnit.name, civ, testGame.getTile(Vector2.Zero))
        val triggerUnique = Unique("[This Unit] upgrades for free including special upgrades")
        UniqueTriggerActivation.triggerUnique(triggerUnique, unit1)
        unit1 = testGame.getTile(Vector2.Zero).getFirstUnit()!!

        Assert.assertTrue("Unit should upgrade to Warrior without unique", unit1.baseUnit.name == "Warrior")
    }

    @Test
    fun regularUpgradeCannotUpgradeToSpecialUnit() {
        val unitToUpgradeTo = testGame.createBaseUnit()
        val testUnit = testGame.createBaseUnit(uniques = arrayOf("May upgrade to [${unitToUpgradeTo.name}] through ruins-like effects"))
        testUnit.upgradesTo = "Warrior"

        val civ = testGame.addCiv()
        var unit1 = testGame.addUnit(testUnit.name, civ, testGame.getTile(Vector2.Zero))
        val upgradeActions = UnitActionsUpgrade.getFreeUpgradeAction(unit1)

        Assert.assertTrue(upgradeActions.count() == 1)
        Assert.assertFalse("Unit should not be able to upgrade to special unit",
            upgradeActions.any { (it as UpgradeUnitAction).unitToUpgradeTo == unitToUpgradeTo })

        val triggerUnique = Unique("[This Unit] upgrades for free")
        UniqueTriggerActivation.triggerUnique(triggerUnique, unit1)
        unit1 = testGame.getTile(Vector2.Zero).getFirstUnit()!!

        Assert.assertTrue(unit1.baseUnit.name == "Warrior")
    }

    @Test
    fun canUpgradeToMultipleWithUnique() {
        val unitToUpgradeTo = testGame.createBaseUnit()
        val testUnit = testGame.createBaseUnit(uniques = arrayOf(
            "Can upgrade to [${unitToUpgradeTo.name}]",
            "Can upgrade to [Warrior]",
        ))

        val civ = testGame.addCiv()
        var unit1 = testGame.addUnit(testUnit.name, civ, testGame.getTile(Vector2.Zero))
        val upgradeActions = UnitActionsUpgrade.getFreeUpgradeAction(unit1)

        Assert.assertTrue(upgradeActions.count() == 2)

        val triggerUnique = Unique("[This Unit] upgrades for free")
        UniqueTriggerActivation.triggerUnique(triggerUnique, unit1)
        unit1 = testGame.getTile(Vector2.Zero).getFirstUnit()!!

        Assert.assertFalse(unit1.baseUnit == testUnit)
    }

    @Test
    fun cannotUpgradeWithoutGold() {
        val unitToUpgradeTo = testGame.createBaseUnit()
        val testUnit = testGame.createBaseUnit()
        testUnit.upgradesTo = unitToUpgradeTo.name

        val civ = testGame.addCiv()
        testGame.addCity(civ, testGame.getTile(Vector2.Zero)) // We need to own the tile to be able to upgrade here

        val unit1 = testGame.addUnit(testUnit.name, civ, testGame.getTile(Vector2.Zero))
        var upgradeActions = UnitActionsUpgrade.getUpgradeActionAnywhere(unit1)

        Assert.assertTrue("We should need gold to upgrade here", upgradeActions.all { it.action == null })

        civ.addGold(unit1.upgrade.getCostOfUpgrade(unitToUpgradeTo))

        upgradeActions = UnitActionsUpgrade.getUpgradeActionAnywhere(unit1)

        Assert.assertTrue(upgradeActions.count() == 1)
        Assert.assertTrue(upgradeActions.none { it.action == null })
    }
}
