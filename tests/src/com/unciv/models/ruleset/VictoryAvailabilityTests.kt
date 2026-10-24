package com.unciv.models.ruleset

import com.unciv.Constants
import com.unciv.json.json
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

/** Uniques on a [Victory]: [Victory.isAvailable] decides who may achieve it,
 *  `Will not be chosen for new games` decides whether it is offered in the new game options.
 *
 *  Every per-civilization answer here comes from `VictoryManager.getAvailableVictories`, the single
 *  place the game's victories are filtered for a civilization. */
@RunWith(BaseTestRunner::class)
class VictoryAvailabilityTests {

    private val victoryName = "Reach the goal"

    private fun gameWithVictory(vararg uniques: String, hiddenInVictoryScreen: Boolean = false): TestGame {
        val testGame = TestGame()
        testGame.makeHexagonalMap(2)
        val uniquesJson = uniques.joinToString(", ") { "\"$it\"" }
        val victory = json().fromJson(Victory::class.java,
            """{ "name": "$victoryName", "uniques": [$uniquesJson],
                 "hiddenInVictoryScreen": $hiddenInVictoryScreen, "milestones": ["Build [Monument]"] }""")
        testGame.ruleset.victories[victory.name] = victory
        testGame.gameInfo.gameParameters.victoryTypes = arrayListOf(victory.name)
        return testGame
    }

    private fun availableVictories(civ: Civilization) = civ.victoryManager.getAvailableVictories().map { it.name }
    private fun victoriesShown(civ: Civilization) = civ.victoryManager.getVictoriesShownInVictoryScreen().map { it.name }

    /** Both civs complete the only milestone - who wins is then decided by the victory's uniques alone */
    private fun completeMilestone(testGame: TestGame, human: Boolean): String? {
        val civ = testGame.addCiv(isPlayer = human)
        val tile = if (human) testGame.getTile(2, 0) else testGame.getTile(0, 0)
        testGame.addCity(civ, tile).cityConstructions.addBuilding("Monument")
        return civ.victoryManager.getVictoryTypeAchieved()
    }

    @Test
    fun `an AI civ does not achieve a victory only available to humans`() {
        val testGame = gameWithVictory("Only available <for [Human player] Civilizations>")
        Assert.assertNull(completeMilestone(testGame, human = false))
        Assert.assertEquals(victoryName, completeMilestone(testGame, human = true))
    }

    @Test
    fun `an AI civ does not achieve a victory unavailable to AIs`() {
        val testGame = gameWithVictory("Unavailable <for [AI player] Civilizations>")
        Assert.assertNull(completeMilestone(testGame, human = false))
        Assert.assertEquals(victoryName, completeMilestone(testGame, human = true))
    }

    @Test
    fun `a victory without uniques is achieved by anyone`() {
        Assert.assertEquals(victoryName, completeMilestone(gameWithVictory(), human = false))
        Assert.assertEquals(victoryName, completeMilestone(gameWithVictory(), human = true))
    }

    @Test
    fun `a nation-specific victory is achieved only by that nation`() {
        val testGame = gameWithVictory("Only available <for [Babylon] Civilizations>")
        val babylon = testGame.addCiv(testGame.ruleset.nations["Babylon"]!!)
        val other = testGame.addCiv()
        testGame.addCity(babylon, testGame.getTile(0, 0)).cityConstructions.addBuilding("Monument")
        testGame.addCity(other, testGame.getTile(2, 0)).cityConstructions.addBuilding("Monument")

        Assert.assertEquals(victoryName, babylon.victoryManager.getVictoryTypeAchieved())
        Assert.assertNull(other.victoryManager.getVictoryTypeAchieved())
    }

    @Test
    fun `a victory is offered in the new game options unless it says otherwise`() {
        fun selectable(vararg uniques: String) =
            gameWithVictory(*uniques).ruleset.selectableVictories().map { it.name }

        Assert.assertTrue(victoryName in selectable())
        // Availability conditionals cannot be evaluated without a civilization - such a victory stays selectable
        Assert.assertTrue(victoryName in selectable("Only available <for [Human player] Civilizations>"))
        Assert.assertTrue(victoryName in selectable("Only available <for [Babylon] Civilizations>"))

        val hidden = selectable("Will not be chosen for new games")
        Assert.assertFalse(victoryName in hidden)
        Assert.assertTrue("The ruleset's own victories are still selectable", hidden.isNotEmpty())
    }

    @Test
    fun `a victory unavailable to a civ is not presented to it as its own goal`() {
        val testGame = gameWithVictory("Only available <for [Human player] Civilizations>")
        val human = testGame.addCiv(isPlayer = true)
        val ai = testGame.addCiv()

        Assert.assertEquals(listOf(victoryName), victoriesShown(human))
        Assert.assertEquals(emptyList<String>(), victoriesShown(ai))
    }

    /** A victory kept out of the victory screen is still one the civilization can achieve */
    @Test
    fun `a victory hidden in the victory screen is available but not shown`() {
        val testGame = gameWithVictory(hiddenInVictoryScreen = true)
        val civ = testGame.addCiv(isPlayer = true)

        Assert.assertEquals(listOf(victoryName), availableVictories(civ))
        Assert.assertEquals(emptyList<String>(), victoriesShown(civ))
        // ... and it is won by completing its milestones, like any other
        testGame.addCity(civ, testGame.getTile(0, 0)).cityConstructions.addBuilding("Monument")
        Assert.assertEquals(victoryName, civ.victoryManager.getVictoryTypeAchieved())
    }

    /** The guard against a future caller iterating the game's victories itself instead of asking
     *  the single filtering source: the ruleset and the game parameters both still list the victory
     *  for the AI, and only `getAvailableVictories` knows the AI cannot have it. */
    @Test
    fun `every per-civ entry point goes through the single filtering source`() {
        val testGame = gameWithVictory("Only available <for [Human player] Civilizations>")
        val human = testGame.addCiv(isPlayer = true)
        val ai = testGame.addCiv()

        Assert.assertTrue(victoryName in testGame.gameInfo.ruleset.victories)
        Assert.assertTrue(victoryName in testGame.gameInfo.gameParameters.victoryTypes)

        Assert.assertEquals(listOf(victoryName), availableVictories(human))
        Assert.assertEquals(listOf(victoryName), victoriesShown(human))
        Assert.assertEquals(listOf(victoryName), human.getPreferredVictoryTypes())

        Assert.assertEquals(emptyList<String>(), availableVictories(ai))
        Assert.assertEquals(emptyList<String>(), victoriesShown(ai))
        Assert.assertEquals(listOf(Constants.neutralVictoryType), ai.getPreferredVictoryTypes())
        Assert.assertNull(ai.victoryManager.getVictoryTypeAchieved())
    }

    @Test
    fun `an AI civ does not prefer a victory it may not achieve`() {
        val testGame = gameWithVictory("Only available <for [Human player] Civilizations>")
        val ai = testGame.addCiv()
        Assert.assertEquals(listOf(Constants.neutralVictoryType), ai.getPreferredVictoryTypes())
        Assert.assertEquals(listOf(victoryName), testGame.addCiv(isPlayer = true).getPreferredVictoryTypes())
    }

    @Test
    fun `victory uniques are validated`() {
        fun errorsFor(vararg uniques: String) = gameWithVictory(*uniques).ruleset.getErrorList()
            .filter { victoryName in it.text }

        Assert.assertTrue(errorsFor("Only available <for [Human player] Civilizations>").isEmpty())
        Assert.assertTrue(errorsFor("Will not be chosen for new games").isEmpty())
        // "Cannot be hurried" is a real unique, but not one a victory can carry
        Assert.assertTrue(errorsFor("Cannot be hurried")
            .any { it.errorSeverityToReport >= RulesetErrorSeverity.Warning && "is not allowed on its target type" in it.text })
        // A bad parameter inside a victory unique is reported, instead of being silently ignored
        Assert.assertTrue(errorsFor("Only available <for [Bogus] Civilizations>").any { "Bogus" in it.text })
    }
}
