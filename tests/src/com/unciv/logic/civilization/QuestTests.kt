package com.unciv.logic.civilization

import com.unciv.json.json
import com.unciv.logic.city.City
import com.unciv.logic.civilization.managers.quests.QuestManager
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.QuestName
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class QuestTests {
    private lateinit var testGame: TestGame
    private lateinit var civ: Civilization
    private lateinit var city: City
    private lateinit var cityState: Civilization

    @Before
    fun setUp() {
        testGame = TestGame()
        civ = testGame.addCiv()
        cityState = testGame.addCiv(cityStateType = "Maritime")
        civ.diplomacyFunctions.makeCivilizationsMeet(cityState)
        testGame.makeHexagonalMap(3)
        city = testGame.addCity(civ, testGame.getTile(-2, 0))
        testGame.addCity(cityState, testGame.getTile(2, 0))
        testGame.gameInfo.turns = QuestManager.INDIVIDUAL_QUEST_FIRST_POSSIBLE_TURN
    }

    companion object {
        const val testRouteAndGPJson = "{\"assignedQuests\":[{\"questName\":\"Acquire Great Person\",\"assigner\":\"Nation-1\",\"assignee\":\"Nation-0\",\"assignedOnTurn\":37,\"data1\":\"Great Artist\"},{\"questName\":\"Route\",\"assigner\":\"Nation-1\",\"assignee\":\"Nation-0\",\"assignedOnTurn\":42}],\"globalQuestCountdown\":33,\"individualQuestCountdown\":{\"Nation-0\":12}}"
        const val testClearBarbarianJson = "{\"assignedQuests\":[{\"questName\":\"Clear Barbarian Camp\",\"assigner\":\"Nation-1\",\"assignee\":\"Nation-0\",\"assignedOnTurn\":37,\"data1\":0,\"data2\":0}],\"globalQuestCountdown\":3,\"individualQuestCountdown\":{\"Nation-0\":3}}"
    }

    private val manager get() = cityState.questManager
    private fun loadManager(testJson: String = testRouteAndGPJson) {
        cityState.questManager = json().fromJson(QuestManager::class.java, testJson)
        manager.setTransients(cityState)
    }
    private fun assignedquests() = manager.getAssignedQuestsFor(civ).toList()

    private fun setupBarbarianCamp(): Pair<Tile, Float> {
        val campTile = testGame.getTile(0, 0)
        testGame.gameInfo.barbarians.setTransients(testGame.gameInfo)
        testGame.gameInfo.barbarians.createNewCamp(campTile)
        loadManager(testClearBarbarianJson)
        val influence = assignedquests()[0].quest.influence
        return campTile to influence
    }

    private fun checkFulfillment(questName: QuestName, influence: Float) {
        manager.endTurn()
        assertTrue(
            "$questName quest should have been removed",
            assignedquests().none { it.questNameInstance == questName }
        )
        assertEquals(
            "Should have gained influence by fulfilling the $questName quest",
            influence,
            cityState.getDiplomacyManager(civ)!!.getInfluence()
        )
    }

    @Test
    fun testSerializeQuestManager() {
        // Arrange
        repeat(QuestManager.INDIVIDUAL_QUEST_FIRST_POSSIBLE_TURN_RAND) {
            manager.endTurn()
            testGame.gameInfo.turns++
        }
        // The state-based random in QuestManager is not actually deterministic, so we can't expect an entire json string
        val mustContain = listOf("\"assignedQuests\":[{\"questName\":\"", "\"assigner\":\"Nation-1\",\"assignee\":\"Nation-0\"", "\"individualQuestCountdown\":{\"Nation-0\":")

        // Act
        val data = json().toJson(manager)
        // Assert
        val actual = mustContain.all { it in data }
        assertTrue("Serialized json for QuestManager must contain all expected fragments", actual)
    }

    @Test
    fun testDeserializeQuestManager() {
        // Arrange/Act
        loadManager()
        // Assert
        val assignedQuests = assignedquests()
        assertEquals(2, assignedQuests.size)
        assertEquals(QuestName.GreatPerson, assignedQuests[0].questNameInstance)
        assertEquals("Great Artist", assignedQuests[0].data1)
        assertEquals(QuestName.Route, assignedQuests[1].questNameInstance)
    }

    @Test
    fun testFulfillGreatPeson() {
        // Arrange
        loadManager()
        val influence = assignedquests()[0].quest.influence
        // Act
        testGame.addUnit("Great Artist", civ, testGame.getTile(-2, 0))
        // Assert
        checkFulfillment(QuestName.GreatPerson, influence)
    }

    @Test
    fun testFulfillRoute() {
        // Arrange
        loadManager()
        val influence = assignedquests()[1].quest.influence
        civ.tech.addTechnology("The Wheel", false)
        // Act
        testGame.getTile(-1, 0).setImprovement("Road", civ)
        testGame.getTile(0, 0).setImprovement("Road", civ)
        testGame.getTile(1, 0).setImprovement("Road", civ)
        civ.cache.updateCitiesConnectedToCapital()
        // Assert
        checkFulfillment(QuestName.Route, influence)
    }

    @Test
    fun testFulfillClearBarbarianCamp() {
        // Arrange
        val (campTile, influence) = setupBarbarianCamp()
        val clearer = testGame.addUnit("Warrior", civ, testGame.getTile(-1, 0))
        // Act
        clearer.movement.headTowards(campTile)
        // Assert
        checkFulfillment(QuestName.ClearBarbarianCamp, influence)
    }

    @Test
    fun testClearBarbarianCampObsoletedByExpansion() {
        // Arrange
        val (campTile, _) = setupBarbarianCamp()
        assertTrue(campTile.owningCity == null)
        civ.playerType = PlayerType.Human // Let's test the notification too in this case
        civ.addGold(city.expansion.getGoldCostOfTile(campTile))
        // Act
        city.expansion.buyTile(campTile)
        // Assert
        assertTrue(campTile.owningCity == city)
        assertFalse("Expansion should have cleared the ClearBarbarianCamp quest", manager.haveQuestsFor(civ))
        val questMentionedInNotifications = civ.notifications.any { QuestName.ClearBarbarianCamp.value in it.text }
        assertTrue("The player should have been notified ClearBarbarianCamp got obsolete", questMentionedInNotifications)
    }
}
