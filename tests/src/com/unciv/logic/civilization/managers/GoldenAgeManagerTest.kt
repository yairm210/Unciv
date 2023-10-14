package com.unciv.logic.civilization.managers

import com.badlogic.gdx.math.Vector2
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class GoldenAgeManagerTest {

    val testGame = TestGame()
    val civ = testGame.addCiv()
    val goldenAgeManager = GoldenAgeManager()

    @Before
    fun setUp() {
        goldenAgeManager.civInfo = civ
    }

    @Test
    fun `should gain golden age points when happy`() {
        // when
        goldenAgeManager.endTurn(10)

        // then
        assertEquals(10, goldenAgeManager.storedHappiness)
    }

    @Test
    fun `should lose golden age points when unhappy`() {
        // given
        goldenAgeManager.storedHappiness = 100

        // when
        goldenAgeManager.endTurn(-10)

        // then
        assertEquals(90, goldenAgeManager.storedHappiness)
    }

    @Test
    fun `should not go into negative golden age points`() {
        // given
        goldenAgeManager.storedHappiness = 5

        // when
        goldenAgeManager.endTurn(-10)

        // then
        assertEquals(0, goldenAgeManager.storedHappiness)
    }

    @Test
    fun `should not store excess happiness when already in golden age`() {
        // given
        goldenAgeManager.storedHappiness = 5
        goldenAgeManager.enterGoldenAge()

        // when
        goldenAgeManager.endTurn(10)

        // then
        assertEquals(5, goldenAgeManager.storedHappiness)
    }

    @Test
    fun `should decrease golden age duration on next turn`() {
        // given
        goldenAgeManager.enterGoldenAge(10)

        // when
        goldenAgeManager.endTurn(0)

        // then
        assertEquals(9, goldenAgeManager.turnsLeftForCurrentGoldenAge)
    }

    @Test
    fun `should go in golden age with enough happiness`() {
        // given
        goldenAgeManager.storedHappiness = 700

        // when
        goldenAgeManager.endTurn(0)

        // then
        assertTrue(goldenAgeManager.isGoldenAge())
    }

    @Test
    fun `should increase golden age cost each time is triggered by happiness`() {
        // given
        val happinessRequiredForFirstGoldenAge = goldenAgeManager.happinessRequiredForNextGoldenAge()
        goldenAgeManager.storedHappiness = happinessRequiredForFirstGoldenAge

        // when
        goldenAgeManager.endTurn(10)

        // then
        assertTrue(goldenAgeManager.happinessRequiredForNextGoldenAge() > happinessRequiredForFirstGoldenAge)
    }

    @Test
    fun `should not increase golden age cost when triggered by outside factors`() {
        // given
        val happinessRequiredForFirstGoldenAge = goldenAgeManager.happinessRequiredForNextGoldenAge()
        goldenAgeManager.enterGoldenAge(10)

        // when
        goldenAgeManager.endTurn(10)

        // then
        assertEquals(happinessRequiredForFirstGoldenAge, goldenAgeManager.happinessRequiredForNextGoldenAge())
    }

    @Test
    fun `should increase golden age cost with more cities`() {
        // given
        testGame.makeHexagonalMap(1)
        testGame.addCity(civ, testGame.getTile(Vector2.Zero), initialPopulation = 10)
        val happinessRequiredForGoldenAgeOneCity = goldenAgeManager.happinessRequiredForNextGoldenAge()

        // when
        testGame.addCity(civ, testGame.getTile(Vector2.X), initialPopulation = 10)

        // then
        val happinessRequiredForGoldenAgeTwoCities = goldenAgeManager.happinessRequiredForNextGoldenAge()
        assertTrue(happinessRequiredForGoldenAgeOneCity < happinessRequiredForGoldenAgeTwoCities)
    }

    @Test
    fun `should increase golden age lenght due to uniques`() {
        // given
        val civ = testGame.addCiv("[+50]% Golden Age length")
        goldenAgeManager.civInfo = civ

        // when
        goldenAgeManager.enterGoldenAge(10)

        // then
        assertEquals(15, goldenAgeManager.turnsLeftForCurrentGoldenAge)
    }
}
