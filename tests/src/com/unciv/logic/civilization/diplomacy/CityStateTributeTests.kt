package com.unciv.logic.civilization.diplomacy

import com.unciv.logic.civilization.CivFlags
import com.unciv.logic.civilization.diplomacy.DiplomacyTurnManager.nextTurn
import com.unciv.logic.map.HexCoord
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class CityStateTributeTests {
    @Test
    fun nearbyMilitaryCanMakeGoldTributeAvailable() {
        val scenario = Scenario(global = 50, local = 150, withProtector = true)
        val modifiers = scenario.assertWillingness(45)
        assertEquals(-20, modifiers["Has Protector"])
        assertEquals(25, modifiers["Military Rank"])
        assertEquals(150, modifiers["Military near City-State"])
    }

    @Test
    fun militaryCanOffsetTheRecentTributePenalty() {
        val scenario = Scenario(global = 400, local = 50)
        scenario.cityState.addFlag(CivFlags.RecentlyBullied.name, 20)
        val modifiers = scenario.assertWillingness(40)
        assertEquals(-300, modifiers["Very recently paid tribute"])
        assertEquals(400, modifiers["Military Rank"])
        assertEquals(50, modifiers["Military near City-State"])
    }

    @Test
    fun zeroAllowsGoldButDoesNotMakeTheCityStateAfraid() {
        for (score in -1..1) {
            val scenario = Scenario(
                global = 0,
                local = 110 + score,
                uniques = listOf("Gain [5] Influence per turn with City-States you could demand tribute from")
            )
            scenario.assertWillingness(score)
            val diplomacy = scenario.cityState.getDiplomacyManager(scenario.demander)!!
            assertEquals(
                if (score > 0) RelationshipLevel.Afraid else RelationshipLevel.Neutral,
                diplomacy.relationshipLevel()
            )
            diplomacy.nextTurn()
            assertEquals(if (score > 0) 5f else 0f, diplomacy.getInfluence(), 0f)
            assertEquals(score > 0, diplomacy.hasFlag(DiplomacyFlags.NotifiedAfraid))
        }
    }

    @Test
    fun intimidationCanMakeGoldTributeAvailableAfterRanking() {
        val scenario = Scenario(
            global = 50, local = 100, withProtector = true,
            uniques = listOf("Units are [+25]% more effective at intimidating City-States")
        )
        val modifiers = scenario.assertWillingness(26)
        assertEquals(31, modifiers["From Uniques"])
    }

    @Test
    fun intimidationCanMakeGoldTributeAvailableBeforeRanking() {
        val scenario = Scenario(uniques = listOf("Units are [+150]% more effective at intimidating City-States"))
        scenario.cityState.addFlag(CivFlags.RecentlyBullied.name, 20)
        val modifiers = scenario.assertWillingness(90)
        assertEquals(300, modifiers["From Uniques"])
    }

    @Test
    fun workerWillingnessIncludesBothWorkerPenalties() {
        val smallCityState = Scenario(global = 50, local = 150, withProtector = true, population = 3)
        smallCityState.assertWillingness(45)
        val smallCityModifiers = smallCityState.assertWillingness(-285, demandingWorker = true)
        assertEquals(-30, smallCityModifiers["Demanding a Worker"])
        assertEquals(-300, smallCityModifiers["Demanding a Worker from small City-State"])

        val largeCityState = Scenario(global = 50, local = 150, withProtector = true, population = 4)
        largeCityState.assertWillingness(45)
        val largeCityModifiers = largeCityState.assertWillingness(15, demandingWorker = true)
        assertEquals(-30, largeCityModifiers["Demanding a Worker"])
        assertFalse(largeCityModifiers.containsKey("Demanding a Worker from small City-State"))
    }

    @Test
    fun militaryCanOffsetTheSmallCityWorkerPenaltyAtZero() {
        val scenario = Scenario(global = 0, local = 440, population = 3)
        scenario.assertWillingness(0, demandingWorker = true)
    }

    @Test
    fun stillSkipsMilitaryCalculationWhenTributeIsImpossible() {
        val recentTribute = Scenario()
        recentTribute.cityState.addFlag(CivFlags.RecentlyBullied.name, 20)
        recentTribute.assertWillingness(-210)
        val beforeRank = recentTribute.modifiers()
        assertFalse(beforeRank.containsKey("Military Rank"))
        assertFalse(beforeRank.containsKey("Military near City-State"))

        val withProtector = Scenario(global = 50, local = 100, withProtector = true)
        withProtector.assertWillingness(-5)
        val afterRank = withProtector.modifiers()
        assertEquals(25, afterRank["Military Rank"])
        assertFalse(afterRank.containsKey("Military near City-State"))
    }

    @Test
    fun negativeMilitaryConstantsUseTheFullCalculation() {
        val negativeGlobal = Scenario(global = -80, local = 180, withProtector = true)
        negativeGlobal.assertWillingness(10)

        val negativeLocal = Scenario(global = 150, local = -200, withNearbyUnit = false)
        negativeLocal.assertWillingness(40)
    }

    @Test
    fun largeMilitaryConstantsDoNotOverflowTheUpperBound() {
        val scenario = Scenario(global = 1_200_000_000, local = 1_200_000_000, withNearbyUnit = false)
        scenario.assertWillingness(1_199_999_890)
    }

    @Test
    fun civilizationOutsideTheRankingUsesTheFullCalculation() {
        val scenario = Scenario(global = 60, local = 0, ranked = false)
        val modifiers = scenario.assertWillingness(10)
        assertEquals(120, modifiers["Military Rank"])
    }

    private class Scenario(
        global: Int = 100,
        local: Int = 100,
        population: Int = 4,
        withProtector: Boolean = false,
        withNearbyUnit: Boolean = true,
        ranked: Boolean = true,
        uniques: List<String> = emptyList()
    ) {
        private val game = TestGame()
        val demander = game.addCiv(*uniques.toTypedArray(), isPlayer = true)
        val cityState = game.addCiv(cityStateType = "Cultured")

        init {
            game.makeHexagonalMap(8)
            game.ruleset.modOptions.constants.tributeGlobalModifier = global
            game.ruleset.modOptions.constants.tributeLocalModifier = local
            cityState.cityStatePersonality = CityStatePersonality.Neutral
            game.addCity(cityState, game.getTile(HexCoord.Zero), initialPopulation = population)

            if (ranked) {
                val unitTile = game.getTile(if (withNearbyUnit) 2 else 6, 0)
                game.addUnit("Giant Death Robot", demander, unitTile)
            }
            if (withProtector || !ranked) {
                val rival = game.addCiv()
                game.addUnit("Giant Death Robot", rival, game.getTile(-6, 0))
                if (withProtector) {
                    game.addUnit("Giant Death Robot", rival, game.getTile(-7, 0))
                    cityState.diplomacyFunctions.makeCivilizationsMeet(rival)
                    cityState.getDiplomacyManager(rival)!!.diplomaticStatus = DiplomaticStatus.Protector
                }
                rival.resetMilitaryMightCache()
            }
            cityState.diplomacyFunctions.makeCivilizationsMeet(demander)
            // Select the player after first contact so fixture setup does not save tutorial progress.
            game.gameInfo.currentPlayerCiv = demander
            demander.resetMilitaryMightCache()
        }

        fun modifiers() = cityState.cityStateFunctions.getTributeModifiers(demander)

        fun assertWillingness(expected: Int, demandingWorker: Boolean = false): Map<String, Int> {
            val full = cityState.cityStateFunctions.getTributeModifiers(demander, demandingWorker, requireWholeList = true)
            assertEquals("Full willingness", expected, full.values.sum())
            val willingness = cityState.cityStateFunctions.getTributeWillingness(demander, demandingWorker)
            if (expected >= 0) assertEquals("Tribute willingness", expected, willingness)
            else assertTrue("Expected negative willingness, got $willingness", willingness < 0)
            return full
        }
    }
}
