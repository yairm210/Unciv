package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import com.unciv.testing.GdxTestRunner
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions.getImprovementConstructionActions
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
        val cityStateCapitalTile = game.getTile(Vector2(0f, 0f))
        val cityStateCapital = game.addCity(cityState, cityStateCapitalTile)

        val mainCiv = game.addCiv("Gain [90] Influence with a [Great Person] gift to a City-State",
            isPlayer = true
        )

        val unitTile = game.getTile(Vector2(1f, 0f))
        cityStateCapital.expansion.takeOwnership(unitTile)

        val greatPerson = game.addUnit("Great Scientist", mainCiv, unitTile)

        // then
        val giftAction = UnitActions.getGiftAction(greatPerson, unitTile)

        Assert.assertNotNull("Great Person should have a gift action", giftAction)
    }
    @Test
    fun CanConstructResourceRequiringImprovement() {
        // Do this early so the uniqueObjects lazy is still un-triggered
        val improvement = game.ruleset.tileImprovements["Manufactory"]!!
        val requireUnique = UniqueType.ConsumesResources.text.fillPlaceholders("3", "Iron")
        improvement.uniques.add(requireUnique)
        Assert.assertFalse("Test preparation failed to add ConsumesResources to Manufactory",
            improvement.uniqueObjects.none { it.type == UniqueType.ConsumesResources })

        val civ = game.addCiv(isPlayer = true)
        val centerTile = game.getTile(Vector2.Zero)
        val capital = game.addCity(civ, centerTile)

        // Place an Engineer and see if he could create a Manufactory
        val unitTile = game.getTile(Vector2(1f,0f))
        val unit = game.addUnit("Great Engineer", civ, unitTile)
        unit.currentMovement = unit.baseUnit.movement.toFloat()  // Required!
        val actionsWithoutIron = try {
            getImprovementConstructionActions(unit, unitTile)
        } catch (ex: Throwable) {
            // Give that IndexOutOfBoundsException a nicer name
            Assert.fail("getImprovementConstructionActions throws Exception ${ex.javaClass.simpleName}")
            return
        }.filter { it.action != null }
        Assert.assertTrue("Great Engineer should NOT be able to create a Manufactory modded to require Iron with 0 Iron",
            actionsWithoutIron.isEmpty())

        // Supply Iron
        val ironTile = game.getTile(Vector2(0f,1f))
        ironTile.resource = "Iron"
        ironTile.resourceAmount = 3
        ironTile.improvement = "Mine"
        civ.tech.addTechnology("Mining")
        civ.tech.addTechnology("Iron Working")
        // capital already owns tile, but this relinquishes first - shouldn't require manual setTerrainTransients, updateCivResources called automatically
        capital.expansion.takeOwnership(ironTile)
        val ironAvailable = civ.getCivResourcesByName()["Iron"] ?: 0
        Assert.assertTrue("Test preparation failed to add Iron to Civ resources", ironAvailable >= 3)

        // See if that same Engineer could create a Manufactory NOW
        val actionsWithIron = getImprovementConstructionActions(unit, unitTile)
            .filter { it.action != null }
        Assert.assertFalse("Great Engineer SHOULD be able to create a Manufactory modded to require Iron once Iron is available",
            actionsWithIron.isEmpty())
    }
}
