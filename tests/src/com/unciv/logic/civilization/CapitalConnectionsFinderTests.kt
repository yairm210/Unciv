package com.unciv.logic.civilization

import com.unciv.logic.map.tile.RoadStatus
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Legend to maps in the tests below:
 *  A - capital
 *    - land without roads
 *  - - road
 *  = - railroad
 *  C - connected city
 *  N - not connected city
 *  O - open borders
 *  X - closed borders
 *  ~ - water
 */

@RunWith(GdxTestRunner::class)
class CapitalConnectionsFinderTests {

    private var testGame = TestGame()
    private lateinit var ourCiv: Civilization

    @Before
    fun setup() {
        testGame.makeHexagonalMap(5)
        ourCiv = testGame.addCiv()

        //Add techs to utilize roads
        ourCiv.tech.addTechnology(testGame.ruleset.tileImprovements[RoadStatus.Road.name]?.techRequired!!)
        ourCiv.tech.addTechnology(testGame.ruleset.tileImprovements[RoadStatus.Railroad.name]?.techRequired!!)
    }

    private fun createMedium(from: Int, to: Int, type: RoadStatus) {
        for (i in from..to) {
            val tile = testGame.tileMap[0, i]
            tile.roadStatus = type
        }
    }

    private fun meetCivAndSetBorders(civ: Civilization, areBordersOpen: Boolean) {
        ourCiv.getDiplomacyManager(civ).makePeace()
        ourCiv.getDiplomacyManager(civ).hasOpenBorders = areBordersOpen
    }

    @Test
    fun `Own cities are connected by road`() {
        // Map: C-A N
//         createLand(-2,2)
        val capital = testGame.addCity(ourCiv, testGame.tileMap[0, 0])
        val connectedCity = testGame.addCity(ourCiv, testGame.tileMap[0, -2])
        val notConnectedCity = testGame.addCity(ourCiv, testGame.tileMap[0, 2])

        createMedium(-2, 0, RoadStatus.Road)
        ourCiv.cache.updateCitiesConnectedToCapital()

        Assert.assertTrue(connectedCity.isConnectedToCapital())
        Assert.assertFalse(notConnectedCity.isConnectedToCapital())
    }

    @Test
    fun `Own cities are connected by railroad`() {
        // Map: N A=C
        val capital = testGame.addCity(ourCiv, testGame.tileMap[0, 0])
        val connectedCity = testGame.addCity(ourCiv, testGame.tileMap[0, 2])
        val notConnectedCity = testGame.addCity(ourCiv, testGame.tileMap[0, -2])
        createMedium(0, 2, RoadStatus.Railroad)


        ourCiv.cache.updateCitiesConnectedToCapital()

        Assert.assertTrue(connectedCity.isConnectedToCapital())
        Assert.assertFalse(notConnectedCity.isConnectedToCapital())
    }

//     @Test
//     fun `Own cities are connected by road and harbor`() {
//         // Map: N A-C C
//         //      ~~~~~~~
//         createMedium(0,2, RoadStatus.Road)
//         //createWater(-2,4)
//         val capital = testGame.addCity(ourCiv, testGame.tileMap[0, 0])
//         val connectedCity1 = testGame.addCity(ourCiv, testGame.tileMap[0, 2])
//         val connectedCity2 = testGame.addCity(ourCiv, testGame.tileMap[0, 4])
//         val notConnectedCity = testGame.addCity(ourCiv, testGame.tileMap[0, -2])
//         connectedCity1.cityConstructions.addBuilding("Harbor")
//         connectedCity2.cityConstructions.addBuilding("Harbor")
//
//         ourCiv.cache.updateCitiesConnectedToCapital()
//
//         Assert.assertTrue(connectedCity1.isConnectedToCapital())
//         Assert.assertTrue(connectedCity2.isConnectedToCapital())
//         Assert.assertFalse(notConnectedCity.isConnectedToCapital())
//     }

    @Test
    fun `Cities are connected by roads via Open Borders`() {
        // Map: N-X=A-O=C
        val capital = testGame.addCity(ourCiv, testGame.tileMap[0, 0])
        val connectedCity = testGame.addCity(ourCiv, testGame.tileMap[0, 4])
        val notConnectedCity = testGame.addCity(ourCiv, testGame.tileMap[0, -4])

        val openCiv = testGame.addCiv()
        val openCivCapital = testGame.addCity(openCiv, testGame.tileMap[0, 2])
        meetCivAndSetBorders(openCiv, true)

        // The path to "not connected" goes through closed territory
        val closedCiv = testGame.addCiv()
        val closedCivCapital = testGame.addCity(closedCiv, testGame.tileMap[0, -2])
        meetCivAndSetBorders(closedCiv, false)

        createMedium(-4,-2, RoadStatus.Road)
        createMedium(-2,0, RoadStatus.Railroad)
        createMedium(0,2, RoadStatus.Road)
        createMedium(2,4, RoadStatus.Railroad)

        ourCiv.cache.updateCitiesConnectedToCapital()

        Assert.assertTrue(connectedCity.isConnectedToCapital())
        Assert.assertFalse(notConnectedCity.isConnectedToCapital())
    }

//     @Test
//     fun `Cities are connected via own harbors only`() {
//         // Map: A
//         //      ~~~~~
//         //      C O-N=N
//         //createLand(-4,-4) // capital is on an island
//         //createWater(-4,0)
//         //createLand(-4,2) // some land without access to ocean
//         val capital = testGame.addCity(ourCiv, testGame.tileMap[0, -4])
//         val connectedCity = testGame.addCity(ourCiv, testGame.tileMap[2, -4])
//         val notConnectedCity1 = testGame.addCity(ourCiv, testGame.tileMap[2, 2])
//         val notConnectedCity2 = testGame.addCity(ourCiv, testGame.tileMap[2, 0])
//         capital.cityConstructions.addBuilding("Harbor")
//         notConnectedCity1.cityConstructions.addBuilding("Harbor")
//         connectedCity.cityConstructions.addBuilding("Harbor")
//
//         val openCiv = testGame.addCiv()
//         val openCivCapital = testGame.addCity(openCiv, testGame.tileMap[2, -2])
//         openCivCapital.cityConstructions.addBuilding("Harbor")
//         meetCivAndSetBorders(openCiv, true)
//
//         createMedium(-2,0, RoadStatus.Road)
//         createMedium(0,2, RoadStatus.Railroad)
//
//         ourCiv.cache.updateCitiesConnectedToCapital()
//
//         Assert.assertTrue(connectedCity.isConnectedToCapital())
//         Assert.assertFalse(notConnectedCity1.isConnectedToCapital())
//         Assert.assertFalse(notConnectedCity2.isConnectedToCapital())
//     }

    @Test
    fun `Cities are connected by roads via City-States`() {
        // Map: N=X-A=O-C
        val capital = testGame.addCity(ourCiv, testGame.tileMap[0, 0])
        val connectedCity = testGame.addCity(ourCiv, testGame.tileMap[0, 4])
        val notConnectedCity = testGame.addCity(ourCiv, testGame.tileMap[0, -4])

        val openCiv = testGame.addCiv(cityStateType = "Cultured")
        val openCivCapital = testGame.addCity(openCiv, testGame.tileMap[0, 2])
        ourCiv.getDiplomacyManager(openCiv).makePeace()

        val closedCiv = testGame.addCiv(cityStateType = "Cultured")
        val closedCivCapital = testGame.addCity(closedCiv, testGame.tileMap[0, -2])
        ourCiv.diplomacyFunctions.makeCivilizationsMeet(closedCiv)
        ourCiv.getDiplomacyManager(closedCiv).declareWar()


        createMedium(-4,-2, RoadStatus.Railroad)
        createMedium(-2,0, RoadStatus.Road)
        createMedium(0,2, RoadStatus.Railroad)
        createMedium(2,4, RoadStatus.Road)


        ourCiv.cache.updateCitiesConnectedToCapital()

        Assert.assertTrue(connectedCity.isConnectedToCapital())
        Assert.assertFalse(notConnectedCity.isConnectedToCapital())
    }
}
