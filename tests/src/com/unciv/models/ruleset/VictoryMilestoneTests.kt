package com.unciv.models.ruleset

import com.unciv.json.json
import com.unciv.models.ruleset.validation.RulesetValidator
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/** [MilestoneType.HaveCountable] - "Have at least [amount] [countable]" */
@RunWith(BaseTestRunner::class)
class HaveCountableMilestoneTests {

    private fun victoryWithMilestones(vararg milestones: String): Victory {
        val milestoneList = milestones.joinToString(", ") { "\"$it\"" }
        return json().fromJson(Victory::class.java, """{ "name": "Test", "milestones": [ $milestoneList ] }""")
    }

    @Test
    fun `milestones of a countable victory can be completed in any order`() {
        val testGame = TestGame()
        testGame.makeHexagonalMap(2)
        val victory = victoryWithMilestones(
            "Have at least [1] [Owned [Farm] Tiles]",
            "Build [Granary]",
            "Have at least [1] [Adopted [Tradition] Policies]"
        )
        testGame.ruleset.victories[victory.name] = victory
        testGame.gameInfo.gameParameters.victoryTypes = arrayListOf(victory.name)
        val civ = testGame.addCiv()
        val city = testGame.addCity(civ, testGame.getTile(0, 0))
        val farmMilestone = victory.milestoneObjects[0]
        val policyMilestone = victory.milestoneObjects[2]

        Assert.assertFalse(farmMilestone.hasBeenCompletedBy(civ))

        // Out of the order they are declared in: the policy first, then the building, then the farm
        civ.policies.freePolicies = 1
        civ.policies.adopt(testGame.ruleset.policies["Tradition"]!!, branchCompletion = false)
        Assert.assertTrue(policyMilestone.hasBeenCompletedBy(civ))
        city.cityConstructions.addBuilding("Granary")
        Assert.assertNull("Not won yet - the farm is missing", civ.victoryManager.getVictoryTypeAchieved())

        val farmTile = testGame.getTile(1, 0)
        testGame.addTileToCity(city, farmTile)
        farmTile.setImprovement("Farm")
        Assert.assertTrue(farmMilestone.hasBeenCompletedBy(civ))
        Assert.assertEquals("Test", civ.victoryManager.getVictoryTypeAchieved())
    }

    @Test
    fun `countable milestone shows its progress`() {
        val testGame = TestGame()
        testGame.makeHexagonalMap(2)
        val victory = victoryWithMilestones("Have at least [3] [Cities]")
        val civ = testGame.addCiv()
        val milestone = victory.milestoneObjects[0]

        Assert.assertEquals("{Have at least [3] [Cities]} (0/3)", milestone.getVictoryScreenButtonHeaderText(false, civ))
        testGame.addCity(civ, testGame.getTile(0, 0))
        Assert.assertEquals("{Have at least [3] [Cities]} (1/3)", milestone.getVictoryScreenButtonHeaderText(false, civ))
        Assert.assertFalse(milestone.hasBeenCompletedBy(civ))
    }

    @Test
    fun `an invalid amount or countable is a ruleset error`() {
        val testGame = TestGame()
        val victory = victoryWithMilestones("Have at least [lots] [No Such Countable]")
        testGame.ruleset.victories[victory.name] = victory
        val errors = RulesetValidator.create(testGame.ruleset).getErrorList()
        Assert.assertTrue(errors.any { "Have at least [lots] [No Such Countable]" in it.text })
    }
}
