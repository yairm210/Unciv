package com.unciv.models.ruleset

import com.unciv.json.json
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/** [Victory.humanOnly] - a victory only a human player can achieve, and that is not a new game option */
@RunWith(BaseTestRunner::class)
class HumanOnlyVictoryTests {

    private fun gameWithVictory(humanOnly: Boolean): TestGame {
        val testGame = TestGame()
        testGame.makeHexagonalMap(2)
        val victory = json().fromJson(Victory::class.java,
            """{ "name": "Reach the goal", "humanOnly": $humanOnly, "milestones": ["Build [Monument]"] }""")
        testGame.ruleset.victories[victory.name] = victory
        testGame.gameInfo.gameParameters.victoryTypes = arrayListOf(victory.name)
        return testGame
    }

    @Test
    fun `an AI civ does not achieve a humanOnly victory`() {
        val testGame = gameWithVictory(humanOnly = true)
        val aiCiv = testGame.addCiv()
        val humanCiv = testGame.addCiv(isPlayer = true)
        testGame.addCity(aiCiv, testGame.getTile(0, 0)).cityConstructions.addBuilding("Monument")
        testGame.addCity(humanCiv, testGame.getTile(2, 0)).cityConstructions.addBuilding("Monument")

        Assert.assertNull(aiCiv.victoryManager.getVictoryTypeAchieved())
        Assert.assertEquals("Reach the goal", humanCiv.victoryManager.getVictoryTypeAchieved())
    }

    @Test
    fun `an AI civ still achieves a normal victory`() {
        val testGame = gameWithVictory(humanOnly = false)
        val aiCiv = testGame.addCiv()
        testGame.addCity(aiCiv, testGame.getTile(0, 0)).cityConstructions.addBuilding("Monument")

        Assert.assertEquals("Reach the goal", aiCiv.victoryManager.getVictoryTypeAchieved())
    }

    @Test
    fun `humanOnly victories are not offered in the new game options`() {
        val selectable = gameWithVictory(humanOnly = true).ruleset.selectableVictories().map { it.name }
        Assert.assertFalse("Reach the goal" in selectable)
        Assert.assertTrue("The ruleset's own victories are still selectable", selectable.isNotEmpty())

        val normal = gameWithVictory(humanOnly = false).ruleset.selectableVictories().map { it.name }
        Assert.assertTrue("Reach the goal" in normal)
    }
}
