package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.json.json
import com.unciv.logic.map.mapunit.UnitTurnManager
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import com.unciv.ui.screens.pickerscreens.PromotionTree
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsFromUniques
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(GdxTestRunner::class)
class UnitUniquesTests {
    private lateinit var game: TestGame

    @Before
    fun initTheWorld() {
        game = TestGame()
    }

    @Test
    fun `Sweden can gift Great Persons to City States`() {
        // when
        game.makeHexagonalMap(1)
        val cityState = game.addCiv(cityStateType = "Cultured")
        val cityStateCapitalTile = game.getTile(Vector2.Zero)
        val cityStateCapital = game.addCity(cityState, cityStateCapitalTile)

        val mainCiv = game.addCiv("Gain [90] Influence with a [Great Person] gift to a City-State",
            isPlayer = true
        )

        val unitTile = game.getTile(Vector2(1f, 0f))
        cityStateCapital.expansion.takeOwnership(unitTile)

        val greatPerson = game.addUnit("Great Scientist", mainCiv, unitTile)

        // then
        val giftAction = UnitActions.getUnitActions(greatPerson, UnitActionType.GiftUnit)
            .firstOrNull { it.action != null } // This tests that the action should be enabled, too

        Assert.assertNotNull("Great Person should have a gift action", giftAction)
    }

    @Test
    fun canConstructResourceRequiringImprovement() {
        // Do this early so the uniqueObjects lazy is still un-triggered
        val requireUnique = UniqueType.ConsumesResources.text.fillPlaceholders("3", "Iron")
        // Get a clone with lazies un-tripped
        val oldImprovement = game.ruleset.tileImprovements["Manufactory"]!!
        val improvement = json().run { fromJson(TileImprovement::class.java, toJson(oldImprovement)) }
        improvement.uniques.add(requireUnique)
        Assert.assertFalse("Test preparation failed to add ConsumesResources to Manufactory",
            improvement.uniqueObjects.none { it.type == UniqueType.ConsumesResources })
        game.ruleset.tileImprovements["Manufactory"] = improvement

        game.makeHexagonalMap(1)
        val civ = game.addCiv(isPlayer = true)
        val centerTile = game.getTile(Vector2.Zero)
        val capital = game.addCity(civ, centerTile)

        // Place an Engineer and see if he could create a Manufactory
        val unitTile = game.getTile(Vector2(1f,0f))
        val unit = game.addUnit("Great Engineer", civ, unitTile)
        unit.currentMovement = unit.baseUnit.movement.toFloat()  // Required!
        val actionsWithoutIron = try {
            UnitActionsFromUniques.getImprovementConstructionActionsFromGeneralUnique(unit, unitTile)
        } catch (ex: Throwable) {
            // Give that IndexOutOfBoundsException a nicer name
            Assert.fail("getImprovementConstructionActions throws Exception ${ex.javaClass.simpleName}")
            game.ruleset.tileImprovements["Manufactory"] = oldImprovement
            return
        }.filter { it.action != null }
        Assert.assertTrue("Great Engineer should NOT be able to create a Manufactory modded to require Iron with 0 Iron",
            actionsWithoutIron.none())

        // Supply Iron
        val ironTile = game.getTile(Vector2(0f,1f))
        ironTile.resource = "Iron"
        ironTile.resourceAmount = 3
        ironTile.improvement = "Mine"
        civ.tech.addTechnology("Mining")
        civ.tech.addTechnology("Iron Working")
        // capital already owns tile, but this relinquishes first - shouldn't require manual setTerrainTransients, updateCivResources called automatically
        capital.expansion.takeOwnership(ironTile)
        val ironAvailable = civ.getResourceAmount("Iron")
        Assert.assertTrue("Test preparation failed to add Iron to Civ resources", ironAvailable >= 3)

        // See if that same Engineer could create a Manufactory NOW
        val actionsWithIron = UnitActionsFromUniques.getImprovementConstructionActionsFromGeneralUnique(unit, unitTile)
            .filter { it.action != null }
        Assert.assertFalse("Great Engineer SHOULD be able to create a Manufactory modded to require Iron once Iron is available",
            actionsWithIron.none())
        game.ruleset.tileImprovements["Manufactory"] = oldImprovement
    }

    @Test
    fun `Check Hakkapeliitta TransferMovement ability`() {
        val civ = game.addCiv(isPlayer = true)
        val centerTile = game.getTile(Vector2.Zero)

        // Hardcoded names mean test *must* run under G&K ruleset, not Vanilla, which the TestGame class ensures
        val general = game.addUnit("Great General", civ, centerTile)
        val boostingUnit = game.addUnit("Hakkapeliitta", civ, centerTile)
        boostingUnit.baseUnit.promotions.forEach { boostingUnit.promotions.addPromotion(it, true) }
        boostingUnit.updateUniques()

        val baseMovement = general.baseUnit.movement
        UnitTurnManager(general).startTurn()
        val actualMovement = general.currentMovement.roundToInt()
        val boosterMovement = boostingUnit.baseUnit.movement

        Assert.assertEquals("Great General stacked with a Hakkapeliitta should have increased movement points.", boosterMovement, actualMovement)
        // This effectively tests whether the G&K rules have not been tampered with, but won't hurt
        Assert.assertNotEquals("Great General stacked with a Hakkapeliitta should NOT have its normal movement points.", baseMovement, actualMovement)
    }

    @Test
    fun testCanGetPromotionsWithXP() {
        val civ = game.addCiv(isPlayer = true)

        val centerTile = game.getTile(Vector2.Zero)
        val unit = game.addUnit("Scout", civ, centerTile)
        val tree = PromotionTree(unit)

        Assert.assertFalse(tree.allNodes().any { !it.unreachable && tree.canBuyUpTo(it.promotion) })
        unit.promotions.XP += 10
        Assert.assertTrue(tree.allNodes().any { !it.unreachable && tree.canBuyUpTo(it.promotion) })
    }

    @Test
    fun testOneTimeUnitGetsName() {
        game.makeHexagonalMap(3)
        val civ = game.addCiv()
        val centerTile = game.getTile(Vector2.Zero)
        val capital = game.addCity(civ, centerTile)
        val unitTile = game.getTile(Vector2(1f,0f))
        val unit = game.addDefaultMeleeUnitWithUniques(civ, unitTile, "[This Unit] gets a name from the [Scientist] group")

        // TODO: Why isn't the instance name being set?
        //unit.instanceName = "Albert Einstein"

        Assert.assertTrue(unit.instanceName != null)
        Assert.assertTrue(game.ruleset.unitNameGroups["Scientist"]?.unitNames?.contains(unit.instanceName) ?: false)
    }

    @Test
    fun testPromotionTreeSetUp() {
        val civ = game.addCiv(isPlayer = true)

        //Creating the promotions
        val promotionBranch1 = game.createUnitPromotion()
        promotionBranch1.unitTypes = listOf("Scout")

        val promotionBranch2 = game.createUnitPromotion()
        promotionBranch2.unitTypes = listOf("Scout")

        val promotionTestBranchA = game.createUnitPromotion()
        promotionTestBranchA.unitTypes = listOf("Scout")
        // intentional lists a promotion twice
        promotionTestBranchA.prerequisites = listOf(promotionBranch1.name, promotionBranch2.name, promotionBranch1.name)

        val promotionTestBranchB = game.createUnitPromotion()
        promotionTestBranchB.unitTypes = listOf("Scout")
        // intentional lists a promotion twice
        promotionTestBranchB.prerequisites = listOf(promotionBranch1.name, promotionTestBranchA.name, promotionBranch2.name, promotionBranch1.name)

        // add unit
        val centerTile = game.tileMap[0,0]
        val unit = game.addUnit("Scout", civ, centerTile)
        val tree = PromotionTree(unit)
        Assert.assertFalse("We shouldn't be able to get the promotion without XP",
            tree.canBuyUpTo(promotionTestBranchB))

        unit.promotions.XP += 30
        unit.promotions.addPromotion(promotionBranch1.name)

        // The Promotion tree needs to be refreshed to check it after gaining a promotion
        tree.update()

        Assert.assertTrue("Check if we can buy the Promotion now",
            tree.canBuyUpTo(promotionTestBranchB))

        // Make sure we only have the prerequisite promotions and that it's only listed once each
        val promotionNode1 = tree.getNode(promotionTestBranchA)!!
        Assert.assertEquals(promotionNode1.parents.size, 2)
        Assert.assertTrue(
            promotionNode1.parents.any { it.promotion == promotionBranch1 } &&
            promotionNode1.parents.any { it.promotion == promotionBranch2 }
        )
        val promotionNode2 = tree.getNode(promotionTestBranchB)!!
        Assert.assertEquals(promotionNode2.parents.size, 3)
        Assert.assertTrue(
            promotionNode2.parents.any { it.promotion == promotionBranch1 } &&
            promotionNode2.parents.any { it.promotion == promotionTestBranchA } &&
            promotionNode2.parents.any { it.promotion == promotionBranch2 }
        )
    }
}
