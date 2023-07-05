package com.unciv.logic.civilization

import com.unciv.logic.map.tile.RoadStatus
import com.unciv.testing.GdxTestRunner
import com.unciv.uniques.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class CityMovingTests {

    private lateinit var civInfo: Civilization
    private lateinit var enemy: Civilization
    private var testGame = TestGame()

    @Before
    fun initTheWorld() {
        testGame.makeHexagonalMap(5) // enough space for lots of cities
        civInfo = testGame.addCiv()
        enemy = testGame.addCiv()

        // Required for enemy to utilize roads
        enemy.tech.techsResearched.addAll(testGame.ruleset.technologies.keys)

        civInfo.diplomacyFunctions.makeCivilizationsMeet(enemy)
        civInfo.getDiplomacyManager(enemy).declareWar()
    }

    @Test
    fun moveOtherCityToUs() {
        val ourCapital = testGame.addCity(civInfo, testGame.tileMap[2,2])
        val theirCapital = testGame.addCity(enemy, testGame.tileMap[-2,-2])
        val theirOtherCity = testGame.addCity(enemy, testGame.tileMap[-2, 2])

        theirOtherCity.moveToCiv(civInfo)
        Assert.assertTrue(!theirOtherCity.isCapital())
        Assert.assertTrue(theirOtherCity.civ == civInfo)
    }

    @Test
    fun moveCapitalToUs() {
        val ourCapital = testGame.addCity(civInfo, testGame.tileMap[2,2])
        val theirCapital = testGame.addCity(enemy, testGame.tileMap[-2,-2])
        val theirOtherCity = testGame.addCity(enemy, testGame.tileMap[-2, 2])

        for (i in listOf(-1,0,1)){
            val tile = testGame.tileMap[-2, i]
            tile.roadStatus = RoadStatus.Road
        }
        enemy.cache.updateCitiesConnectedToCapital()
        Assert.assertTrue(theirOtherCity.isConnectedToCapital())

        theirCapital.moveToCiv(civInfo)
        Assert.assertTrue(theirOtherCity.isCapital())
        Assert.assertTrue(theirCapital.isCapital())
        Assert.assertTrue(theirCapital.civ == civInfo)
    }

    @Test
    fun moveCapitalToUsWhenWeHaveNoCities() {
        val theirCapital = testGame.addCity(enemy, testGame.tileMap[-2,-2])
        val theirOtherCity = testGame.addCity(enemy, testGame.tileMap[-2, 2])

        for (i in listOf(-1,0,1)){
            val tile = testGame.tileMap[-2, i]
            tile.roadStatus = RoadStatus.Road
        }
        enemy.cache.updateCitiesConnectedToCapital()
        Assert.assertTrue(theirOtherCity.isConnectedToCapital())

        theirCapital.moveToCiv(civInfo)
        Assert.assertTrue(theirOtherCity.isCapital())
        Assert.assertTrue(theirOtherCity.civ == enemy)
        Assert.assertTrue(theirCapital.isCapital())
        Assert.assertTrue(theirCapital.civ == civInfo)
    }

    @Test
    fun moveNonCapitalToUsWhenWeHaveNoCities() {
        val theirCapital = testGame.addCity(enemy, testGame.tileMap[-2,-2])
        val theirOtherCity = testGame.addCity(enemy, testGame.tileMap[-2, 2])

        for (i in listOf(-1,0,1)){
            val tile = testGame.tileMap[-2, i]
            tile.roadStatus = RoadStatus.Road
        }
        enemy.cache.updateCitiesConnectedToCapital()
        Assert.assertTrue(theirOtherCity.isConnectedToCapital())

        theirOtherCity.moveToCiv(civInfo)
        Assert.assertTrue(theirOtherCity.isCapital())
        Assert.assertTrue(theirCapital.isCapital())
        Assert.assertTrue(theirOtherCity.civ == civInfo)
    }

    @Test
    fun moveTheirOnlyCityToUsWhenWeHaveNoCities() {
        val theirCapital = testGame.addCity(enemy, testGame.tileMap[-2,-2])
        enemy.cache.updateCitiesConnectedToCapital()

        theirCapital.moveToCiv(civInfo)
        Assert.assertTrue(theirCapital.isCapital())
        Assert.assertTrue(theirCapital.civ == civInfo)
    }
}
