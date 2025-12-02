package com.unciv.logic.city

import com.unciv.logic.city.managers.CityConquestFunctions
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.fillPlaceholders
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.*
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

    private val gainFreeBuildingTestingUniqueText = UniqueType.GainFreeBuildings.text.fillPlaceholders("Monument", "in all cities")

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(3)
        attackerCiv = testGame.addCiv()
        defenderCiv = testGame.addCiv()

        defenderCity = testGame.addCity(defenderCiv, testGame.getTile(HexCoord.Zero), initialPopulation = 10)
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
        fun resetPlunderState() {
            testGame.gameInfo.turns = 50
            attackerCiv.addGold(-attackerCiv.gold)
            if (defenderCity.civ == defenderCiv) return
            cityConquestFunctions.puppetCity(defenderCiv)
            defenderCity.population.setPopulation(10)
            defenderCity.turnAcquired = 0
        }

        // Do not assume because TileBasedRandom ensures deterministic results to hardcode those
        // Ranges are from hardcoded function in getGoldForCapturingCity - all modifiers are == 1
        // Repeat test without reinitializing the CityConquestFunctions.tileBasedRandom to get coverage
        repeat(20) {
            // given
            resetPlunderState()

            // when
            cityConquestFunctions.puppetCity(attackerCiv)

            // then
            assertTrue(attackerCiv.gold in 120 until 160) // Range for city size 10 and all modifiers 1
        }
    }

    @Test
    fun `should plunder more gold the more people live in the captured city`() {
        // No repeat like `should plunder gold upon capture` - mor work and one range check might be enough

        // given
        val largeCity = testGame.addCity(defenderCiv, testGame.getTile(1,0), initialPopulation = 20)
        val smallCity = testGame.addCity(defenderCiv, testGame.getTile(0,1), initialPopulation = 5)
        val secondAttackerCiv = testGame.addCiv()
        testGame.gameInfo.turns = 50

        // when
        CityConquestFunctions(largeCity).puppetCity(attackerCiv)
        CityConquestFunctions(smallCity).puppetCity(secondAttackerCiv)

        // then
        assertTrue(attackerCiv.gold > secondAttackerCiv.gold)

        assertTrue(attackerCiv.gold in 220 until 260)  // population 20
        assertTrue(secondAttackerCiv.gold in 70 until 110)  // population 5
    }

    @Test
    fun `should plunder more gold with building uniques upon capture`() {
        // given
        val building = testGame.createBuilding(UniqueType.GoldFromCapturingCity.text.fillPlaceholders("+100"))
        val city = testGame.addCity(defenderCiv, testGame.getTile(1,0), initialPopulation = 10)
        city.cityConstructions.addBuilding(building.name)

        val secondAttackerCiv = testGame.addCiv()
        testGame.gameInfo.turns = 50

        // when
        CityConquestFunctions(city).puppetCity(secondAttackerCiv)
        cityConquestFunctions.puppetCity(attackerCiv)

        // then
        assertTrue(attackerCiv.gold < secondAttackerCiv.gold)
        assertTrue(attackerCiv.gold in 120 until 160) // Range for city size 10 and all modifiers 1
        assertTrue(secondAttackerCiv.gold in 240 until 320) // Doubled by Unique
    }

    @Test
    fun `should never destroy global wonders upon capture`() {
        // given
        val wonders = mutableListOf<String>()
        // remove randomness
        repeat(20) {
            val wonder = testGame.createWonder(UniqueType.ScienceFromResearchAgreements.text.fillPlaceholders("$it"))
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
        val largeCity = testGame.addCity(defenderCiv, testGame.getTile(1,0), initialPopulation = 20)
        val smallCity = testGame.addCity(defenderCiv, testGame.getTile(0,1), initialPopulation = 5)

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
        val onePopCity = testGame.addCity(defenderCiv, testGame.getTile(1,0), initialPopulation = 1)

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
        val smallerCity = testGame.addCity(defenderCiv, testGame.getTile(1,0), initialPopulation = 5)
        val largerCity = testGame.addCity(defenderCiv, testGame.getTile(0,1), initialPopulation = 8)

        // when
        cityConquestFunctions.moveToCiv(attackerCiv)

        // then
        assertTrue(largerCity.isCapital())
        assertFalse(smallerCity.isCapital())
    }

    @Test
    fun `should remove free buildings upon city moving`() {
        // given
        val freeBuildingCiv = testGame.addCiv(gainFreeBuildingTestingUniqueText)
        val city = testGame.addCity(freeBuildingCiv, testGame.getTile(0,1), initialPopulation = 10)

        // when
        CityConquestFunctions(city).moveToCiv(attackerCiv)

        // then
        assertFalse(city.cityConstructions.containsBuildingOrEquivalent("Monument"))
    }

    @Test
    fun `should give free buildings upon city moving`() {
        // given
        val freeBuildingCiv = testGame.addCiv(gainFreeBuildingTestingUniqueText)

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
