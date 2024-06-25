@file:Suppress("UNUSED_VARIABLE")  // These are tests and the names serve readability

package com.unciv.logic.civilization

import com.unciv.logic.map.tile.RoadStatus
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
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
        for (tech in testGame.ruleset.technologies.keys)
            enemy.tech.addTechnology(tech)

        for (tech in testGame.ruleset.technologies.keys)
            civInfo.tech.addTechnology(tech)

        civInfo.diplomacyFunctions.makeCivilizationsMeet(enemy)
        civInfo.getDiplomacyManager(enemy)!!.declareWar()
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

        for (i in listOf(-1,0,1)) {
            val tile = testGame.tileMap[-2, i]
            tile.roadStatus = RoadStatus.Road
        }
        enemy.cache.updateCitiesConnectedToCapital()
        Assert.assertTrue(theirOtherCity.isConnectedToCapital())

        theirCapital.moveToCiv(civInfo)
        Assert.assertTrue(theirOtherCity.isCapital())
        Assert.assertTrue(!theirCapital.isCapital())
        Assert.assertTrue(theirCapital.civ == civInfo)
    }

    @Test
    fun moveCapitalToUsWhenWeHaveNoCities() {
        val theirCapital = testGame.addCity(enemy, testGame.tileMap[-2,-2])
        val theirOtherCity = testGame.addCity(enemy, testGame.tileMap[-2, 2])

        for (i in listOf(-1,0,1)) {
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

        for (i in listOf(-1,0,1)) {
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

    @Test
    fun moveTheirCityToUsWhenTheyHaveResources() {
        val theirCapital = testGame.addCity(enemy, testGame.tileMap[2,0])

        theirCapital.getCenterTile().resource = "Salt"
        theirCapital.getCenterTile().resourceAmount = 1

        val resourceTile = testGame.tileMap[0,1]
        resourceTile.resource = "Iron"
        resourceTile.resourceAmount = 3
        resourceTile.setImprovement("Mine")
        theirCapital.expansion.takeOwnership(resourceTile)

        theirCapital.moveToCiv(civInfo)
        Assert.assertTrue(theirCapital.isCapital())
        Assert.assertTrue(theirCapital.civ == civInfo)
        Assert.assertTrue(civInfo.hasResource("Salt"))
        Assert.assertEquals(civInfo.getResourceAmount("Iron"), 3)
    }
}
