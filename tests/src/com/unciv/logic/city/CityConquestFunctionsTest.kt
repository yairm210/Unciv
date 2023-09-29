package com.unciv.logic.city

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.city.managers.CityConquestFunctions
import com.unciv.logic.civilization.Civilization
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class CityConquestFunctionsTest {

    private lateinit var attackerCiv: Civilization
    private lateinit var defenderCiv: Civilization

    private lateinit var defenderCity: City
    private lateinit var cityConquestFunctions: CityConquestFunctions

    private val testGame = TestGame()

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(3)
        attackerCiv = testGame.addCiv()
        defenderCiv = testGame.addCiv()

        defenderCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2.Zero), initialPopulation = 10)
        cityConquestFunctions = CityConquestFunctions(defenderCity)

        // otherwise test crashes when puppetying city
        testGame.gameInfo.currentPlayerCiv = testGame.addCiv()
        testGame.gameInfo.currentPlayer = testGame.gameInfo.currentPlayerCiv.civName
    }

    // Note: diplomacy repercussions for capturing/liberating cities are tested in DiplomacyManagerTest

    @Test
    fun `should change owner upon capture`() {
        // when
        cityConquestFunctions.puppetCity(attackerCiv)

        // then
        assertEquals(attackerCiv, defenderCity.civ)
        assertEquals(defenderCiv.civName, defenderCity.previousOwner)
    }

    @Test
    fun `should plunder gold upon capture`() {
        // given
        testGame.gameInfo.turns = 50

        // when
        cityConquestFunctions.puppetCity(attackerCiv)

        // then
        assertEquals(159, attackerCiv.gold)
    }

    @Test
    fun `should plunder more gold the more people live in the captured city`() {
        // given
        val largeCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2.X), initialPopulation = 20)
        val smallCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2.Y), initialPopulation = 5)
        val secondAttackerCiv = testGame.addCiv()
        testGame.gameInfo.turns = 50

        // when
        CityConquestFunctions(largeCity).puppetCity(attackerCiv)
        CityConquestFunctions(smallCity).puppetCity(secondAttackerCiv)

        // then
        assertTrue(attackerCiv.gold > secondAttackerCiv.gold)
        assertEquals(230, attackerCiv.gold)
        assertEquals(87, secondAttackerCiv.gold)
    }

    @Test
    fun `should plunder more gold with building uniques upone capture`() {
        // given
        val building = testGame.createBuilding("Doubles Gold given to enemy if city is captured")
        val city = testGame.addCity(defenderCiv, testGame.getTile(Vector2.X), initialPopulation = 10)
        city.cityConstructions.addBuilding(building.name)

        val secondAttackerCiv = testGame.addCiv()
        testGame.gameInfo.turns = 50

        // when
        CityConquestFunctions(city).puppetCity(secondAttackerCiv)
        cityConquestFunctions.puppetCity(attackerCiv)

        // then
        assertTrue(attackerCiv.gold < secondAttackerCiv.gold)
        // note: not exactly double because there is some randomness
        assertEquals(159, attackerCiv.gold)
        assertEquals(260, secondAttackerCiv.gold)
    }

    @Test
    fun `should never destroy global wonders upon capture`() {
        // given
        val wonders = mutableListOf<String>()
        // remove randomness
        repeat(20) {
            val wonder = testGame.createWonder("Science gained from research agreements [+${it}]%")
            defenderCity.cityConstructions.addBuilding(wonder.name)
            wonders.add(wonder.name)
        }

        // when
        cityConquestFunctions.puppetCity(attackerCiv)

        // then
        wonders.forEach { assertTrue(defenderCity.cityConstructions.builtBuildings.contains(it)) }
    }

    @Test
    fun `should always destroy national wonders upon capture`() {
        // given
        val wonders = mutableListOf<String>()
        // remove randomness
        repeat(20) {
            val nationalWonder = testGame.createBuilding()
            nationalWonder.isNationalWonder = true
            defenderCity.cityConstructions.addBuilding(nationalWonder.name)
            wonders.add(nationalWonder.name)
        }

        // when
        cityConquestFunctions.puppetCity(attackerCiv)

        // then
        wonders.forEach { assertFalse(defenderCity.cityConstructions.builtBuildings.contains(it)) }
    }

    @Test
    fun `should kill population upon capture`() {
        // given
        val largeCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2.X), initialPopulation = 20)
        val smallCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2.Y), initialPopulation = 5)

        // when
        cityConquestFunctions.puppetCity(attackerCiv)
        CityConquestFunctions(largeCity).puppetCity(attackerCiv)
        CityConquestFunctions(smallCity).puppetCity(attackerCiv)

        // then
        assertEquals(7, defenderCity.population.population)
        assertEquals(14, largeCity.population.population)
        assertEquals(3, smallCity.population.population)
    }

    @Test
    fun `should never reduce population below 1 upon capture`() {
        // given
        val onePopCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2.X), initialPopulation = 1)

        // when
        CityConquestFunctions(onePopCity).puppetCity(attackerCiv)

        // then
        assertEquals(1, onePopCity.population.population)
    }

    @Test
    fun `should set city in resistance upon capture`() {
        // when
        cityConquestFunctions.puppetCity(attackerCiv)

        // then
        assertTrue(defenderCity.isInResistance())
    }

    @Test
    fun `should relocate capital to largest upon city moving`() {
        // given
        val smallerCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2.X), initialPopulation = 5)
        val largerCity = testGame.addCity(defenderCiv, testGame.getTile(Vector2.Y), initialPopulation = 8)

        // when
        cityConquestFunctions.moveToCiv(attackerCiv)

        // then
        assertTrue(largerCity.isCapital())
        assertFalse(smallerCity.isCapital())
    }

    @Test
    fun `should remove free buildings upon city moving`() {
        // given
        val freeBuildingCiv = testGame.addCiv("Gain a free [Monument] [in all cities]")
        val city = testGame.addCity(freeBuildingCiv, testGame.getTile(Vector2.Y), initialPopulation = 10)

        // when
        CityConquestFunctions(city).moveToCiv(attackerCiv)

        // then
        assertFalse(city.cityConstructions.containsBuildingOrEquivalent("Monument"))
    }

    @Test
    fun `should give free buildings upon city moving`() {
        // given
        val freeBuildingCiv = testGame.addCiv("Gain a free [Monument] [in all cities]")

        // when
        cityConquestFunctions.moveToCiv(freeBuildingCiv)

        // then
        assertTrue(defenderCity.cityConstructions.containsBuildingOrEquivalent("Monument"))
    }

    @Test
    fun `should annex city`() {
        // given
        cityConquestFunctions.puppetCity(attackerCiv)

        // when
        cityConquestFunctions.annexCity()

        // then
        assertFalse(defenderCity.isPuppet)
    }
}
