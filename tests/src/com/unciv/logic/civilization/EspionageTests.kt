package com.unciv.logic.civilization

import com.badlogic.gdx.math.Vector2
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class EspionageTests {
    private val testGame = TestGame()

    val civA = testGame.addCiv()
    val civB = testGame.addCiv()
    @Before
    fun setup() {
        testGame.gameInfo.gameParameters.espionageEnabled = true
        civA.diplomacyFunctions.makeCivilizationsMeet(civB)
        testGame.makeHexagonalMap(3)
    }

    @Test
    fun `Espionage manager add spy`() {
        val espionageManagerA = civA.espionageManager
        assertEquals(0, espionageManagerA.spyList.size)
        espionageManagerA.addSpy()
        assertEquals(1, espionageManagerA.spyList.size)
    }

    @Test
    fun `Espionage check spy effectiveness reduction unique`() {
        val espionageManagerA = civA.espionageManager
        val spy = espionageManagerA.addSpy()
        val city = civB.addCity(Vector2(1f,1f))
        spy.moveTo(city)
        assertEquals(1.0, spy.getEfficiencyModifier(), 0.1)
        city.cityConstructions.addBuilding("Constabulary")
        assertEquals(0.75, spy.getEfficiencyModifier(), 0.1)
    }

    @Test
    fun `Spy effectiveness can't go below zero`() {
        val espionageManagerA = civA.espionageManager
        val spy = espionageManagerA.addSpy()
        val city = civB.addCity(Vector2(1f,1f))
        spy.moveTo(city)
        city.cityConstructions.addBuilding("Constabulary")
        city.cityConstructions.addBuilding("Police Station")
        city.cityConstructions.addBuilding("National Intelligence Agency")
        city.cityConstructions.addBuilding("Great Firewall")
        assertTrue(spy.getEfficiencyModifier() >= 0)
    }

}
