package com.unciv.logic.civilization

import com.unciv.json.json
import com.unciv.logic.civilization.managers.quests.QuestManager
import com.unciv.models.ruleset.QuestName
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class QuestTests {
    private lateinit var testGame: TestGame
    private lateinit var civ: Civilization
    private lateinit var cityState: Civilization

    @Before
    fun setUp() {
        testGame = TestGame()
        civ = testGame.addCiv()
        cityState = testGame.addCiv(cityStateType = "Maritime")
        civ.diplomacyFunctions.makeCivilizationsMeet(cityState)
        testGame.makeHexagonalMap(3)
        testGame.addCity(civ, testGame.getTile(-2, 0))
        testGame.addCity(cityState, testGame.getTile(2, 0))
        testGame.gameInfo.turns = QuestManager.INDIVIDUAL_QUEST_FIRST_POSSIBLE_TURN
    }

    companion object {
        const val testJson = "{\"assignedQuests\":[{\"questName\":\"Acquire Great Person\",\"assigner\":\"Nation-1\",\"assignee\":\"Nation-0\",\"assignedOnTurn\":37,\"data1\":\"Great Artist\"},{\"questName\":\"Route\",\"assigner\":\"Nation-1\",\"assignee\":\"Nation-0\",\"assignedOnTurn\":42}],\"globalQuestCountdown\":33,\"individualQuestCountdown\":{\"Nation-0\":12}}"
    }

    private val manager get() = cityState.questManager
    private fun loadManager() {
        cityState.questManager = json().fromJson(QuestManager::class.java, testJson)
        manager.setTransients(cityState)
    }
    private fun assignedquests() = manager.getAssignedQuestsFor(civ).toList()

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
        repeat(QuestManager.INDIVIDUAL_QUEST_FIRST_POSSIBLE_TURN_RAND) {
            manager.endTurn()
            testGame.gameInfo.turns++
        }
        // The state-based random in QuestManager is not actually deterministic, so we can't expect an entire json string
        val mustContain = listOf("\"assignedQuests\":[{\"questName\":\"", "\"assigner\":\"Nation-1\",\"assignee\":\"Nation-0\"", "\"individualQuestCountdown\":{\"Nation-0\":")

        val data = json().toJson(manager)
        val actual = mustContain.all { it in data }
        assertTrue("Serialized json for QuestManager must contain all expected fragments", actual)
    }

    @Test
    fun testDeserializeQuestManager() {
        loadManager()
        val assignedQuests = assignedquests()
        assertEquals(2, assignedQuests.size)
        assertEquals(QuestName.GreatPerson, assignedQuests[0].questNameInstance)
        assertEquals("Great Artist", assignedQuests[0].data1)
        assertEquals(QuestName.Route, assignedQuests[1].questNameInstance)
    }

    @Test
    fun testFulfillGreatPeson() {
        loadManager()
        val influence = assignedquests()[0].quest.influence
        testGame.addUnit("Great Artist", civ, testGame.getTile(-2, 0))
        checkFulfillment(QuestName.GreatPerson, influence)
    }

    @Test
    fun testFulfillRoute() {
        loadManager()
        val influence = assignedquests()[1].quest.influence
        civ.tech.addTechnology("The Wheel", false)
        testGame.getTile(-1, 0).setImprovement("Road", civ)
        testGame.getTile(0, 0).setImprovement("Road", civ)
        testGame.getTile(1, 0).setImprovement("Road", civ)
        civ.cache.updateCitiesConnectedToCapital()
        checkFulfillment(QuestName.Route, influence)
    }
}
