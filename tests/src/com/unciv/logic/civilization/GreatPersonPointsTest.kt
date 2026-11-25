package com.unciv.logic.civilization

import com.unciv.Constants
import com.unciv.logic.BackwardCompatibility.reconcileGreatPersonPoints
import com.unciv.logic.BackwardCompatibility.removeMissingModReferences
import com.unciv.logic.city.City
import com.unciv.logic.city.CityFlags
import com.unciv.logic.city.managers.CityConquestFunctions
import com.unciv.logic.civilization.managers.TurnManager
import com.unciv.logic.map.HexCoord
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class GreatPersonPointsTest {
    private val testGame = TestGame()
    private lateinit var civ: Civilization
    private lateinit var capital: City
    private lateinit var otherCity: City

    @Before
    fun setUp() {
        testGame.gameInfo.gameParameters.greatPersonPointsInCities = true
        testGame.makeHexagonalMap(4)
        civ = testGame.addCiv()
        capital = testGame.addCity(civ, testGame.getTile(HexCoord.Zero))
        otherCity = testGame.addCity(civ, testGame.getTile(3, 0))
    }

    private fun addBuildingGeneratingPoints(city: City, greatPerson: String, pointsPerTurn: Int) {
        val building = testGame.createBuilding()
        building.greatPersonPoints.add(greatPerson, pointsPerTurn)
        city.cityConstructions.addBuilding(building)
    }

    private fun unitsNamed(name: String) = civ.units.getCivUnits().filter { it.name == name }.toList()

    @Test
    fun `points accumulate in the city that generates them`() {
        addBuildingGeneratingPoints(otherCity, "Great Scientist", 5)

        civ.greatPeople.addGreatPersonPoints()

        assertEquals(5, otherCity.storedGreatPersonPoints["Great Scientist"])
        assertEquals(0, capital.storedGreatPersonPoints["Great Scientist"])
    }

    @Test
    fun `a nation's empire-wide engineer shares a cost with its city scientists`() {
        val engineer = testGame.createBaseUnit("Civilian", "Great Person - [Production]",
            "Is part of Great Person group [Great Person]", UniqueType.CivWideGreatPersonPoints.text)
        engineer.replaces = "Great Engineer"
        engineer.uniqueTo = civ.civName
        civ.cache.setTransients()
        addBuildingGeneratingPoints(capital, "Great Engineer", 60)
        addBuildingGeneratingPoints(otherCity, "Great Engineer", 50)
        addBuildingGeneratingPoints(capital, "Great Scientist", 100)
        addBuildingGeneratingPoints(otherCity, "Great Scientist", 30)

        civ.greatPeople.addGreatPersonPoints()
        assertEquals(110, civ.greatPeople.greatPersonPointsCounter["Great Engineer"])
        assertEquals(0, otherCity.storedGreatPersonPoints["Great Engineer"])
        assertEquals(100, capital.storedGreatPersonPoints["Great Scientist"])
        assertEquals(30, otherCity.storedGreatPersonPoints["Great Scientist"])

        civ.greatPeople.startTurn()
        assertEquals(1, unitsNamed(engineer.name).size)
        assertTrue(unitsNamed("Great Scientist").isEmpty())
        assertEquals(0, civ.greatPeople.greatPersonPointsCounter["Great Engineer"])
        assertEquals(100, capital.storedGreatPersonPoints["Great Scientist"])
        assertEquals(200, civ.greatPeople.getPointsRequiredForGreatPerson("Great Scientist"))

        civ.greatPeople.addGreatPersonPoints()
        civ.greatPeople.startTurn()
        assertEquals(1, unitsNamed("Great Scientist").size)
        assertEquals(60, otherCity.storedGreatPersonPoints["Great Scientist"])
        assertEquals(110, civ.greatPeople.greatPersonPointsCounter["Great Engineer"])
        assertEquals(300, civ.greatPeople.getPointsRequiredForGreatPerson("Great Engineer"))
    }

    @Test
    fun `changing a unit's point scope in a mod preserves its accumulated points on load`() {
        val pooled = testGame.createBaseUnit("Civilian", UniqueType.CivWideGreatPersonPoints.text)
        capital.storedGreatPersonPoints[pooled.name] = 15
        otherCity.storedGreatPersonPoints[pooled.name] = 30
        civ.greatPeople.greatPersonPointsCounter["Great Scientist"] = 60

        repeat(2) { testGame.gameInfo.reconcileGreatPersonPoints() }

        assertEquals(45, civ.greatPeople.greatPersonPointsCounter[pooled.name])
        assertEquals(0, capital.storedGreatPersonPoints[pooled.name])
        assertTrue(otherCity.storedGreatPersonPoints.isEmpty())
        assertEquals(60, capital.storedGreatPersonPoints["Great Scientist"])
        assertEquals(0, civ.greatPeople.greatPersonPointsCounter["Great Scientist"])
    }

    @Test
    fun `an inland city's points cannot spawn a naval unit in another city`() {
        val game = TestGame()
        game.gameInfo.gameParameters.greatPersonPointsInCities = true
        game.makeHexagonalMap(4)
        game.setTileTerrain(HexCoord(4, 0), Constants.coast)
        val owner = game.addCiv()
        val inland = game.addCity(owner, game.getTile(HexCoord.Zero))
        val port = game.addCity(owner, game.getTile(3, 0))
        inland.storedGreatPersonPoints["Great Admiral"] = 100

        owner.greatPeople.startTurn()

        assertTrue(port.isNaval())
        assertTrue(owner.units.getCivUnits().none { it.name == "Great Admiral" })
        assertEquals(100, inland.storedGreatPersonPoints["Great Admiral"])
    }

    @Test
    fun `great person is born in the city that reached the cost and other cities keep their points`() {
        capital.storedGreatPersonPoints.add("Great Scientist", 60)
        otherCity.storedGreatPersonPoints.add("Great Scientist", 130)

        civ.greatPeople.startTurn()

        assertEquals(otherCity.getCenterTile(), unitsNamed("Great Scientist").single().getTile())
        // Points beyond the cost are lost
        assertEquals(0, otherCity.storedGreatPersonPoints["Great Scientist"])
        assertEquals(60, capital.storedGreatPersonPoints["Great Scientist"])
    }

    @Test
    fun `simultaneous births in a shared group follow ruleset unit order`() {
        capital.storedGreatPersonPoints.add("Great Engineer", 100)
        capital.storedGreatPersonPoints.add("Great Scientist", 100)

        civ.greatPeople.startTurn()

        assertEquals(1, unitsNamed("Great Scientist").size)
        assertTrue(unitsNamed("Great Engineer").isEmpty())
        assertEquals(100, capital.storedGreatPersonPoints["Great Engineer"])
        assertEquals(200, civ.greatPeople.getPointsRequiredForGreatPerson("Great Engineer"))
    }

    @Test
    fun `great people in separate groups can be born in the same city on the same turn`() {
        val writer = testGame.createBaseUnit("Civilian", "Great Person - [Culture]", "Is part of Great Person group [Writer]")
        capital.storedGreatPersonPoints.add(writer.name, 100)
        capital.storedGreatPersonPoints.add("Great Scientist", 100)

        civ.greatPeople.startTurn()

        assertEquals(1, unitsNamed(writer.name).size)
        assertEquals(1, unitsNamed("Great Scientist").size)
        assertTrue(capital.storedGreatPersonPoints.isEmpty())
    }

    @Test
    fun `a city checked later in the turn needs the increased cost`() {
        // Great Scientist and Great Engineer are in the same group in Gods & Kings
        capital.storedGreatPersonPoints.add("Great Scientist", 100)
        otherCity.storedGreatPersonPoints.add("Great Engineer", 150)

        civ.greatPeople.startTurn()

        assertEquals(1, unitsNamed("Great Scientist").size)
        assertTrue(unitsNamed("Great Engineer").isEmpty())
        assertEquals(150, otherCity.storedGreatPersonPoints["Great Engineer"])
        assertEquals(200, civ.greatPeople.getPointsRequiredForGreatPerson("Great Engineer"))
    }

    @Test
    fun `each great person of a group makes the next one cost 100 more points`() {
        capital.storedGreatPersonPoints.add("Great Scientist", 100)
        civ.greatPeople.startTurn()
        capital.storedGreatPersonPoints.add("Great Engineer", 200)
        civ.greatPeople.startTurn()

        assertEquals(2, civ.units.getCivUnits().count())
        assertEquals(300, civ.greatPeople.getPointsRequiredForGreatPerson("Great Merchant"))
    }

    @Test
    fun `great person is born at the start of the turn after its city reached the cost`() {
        addBuildingGeneratingPoints(capital, "Great Scientist", 100)

        TurnManager(civ).endTurn()
        assertTrue(unitsNamed("Great Scientist").isEmpty())
        TurnManager(civ).startTurn()

        assertEquals(capital.getCenterTile(), unitsNamed("Great Scientist").single().getTile())
    }

    @Test
    fun `great person waits for the resistance in its city to end`() {
        otherCity.storedGreatPersonPoints.add("Great Scientist", 100)
        otherCity.setFlag(CityFlags.Resistance, 2)

        civ.greatPeople.startTurn()

        assertTrue(unitsNamed("Great Scientist").isEmpty())
        assertEquals(100, otherCity.storedGreatPersonPoints["Great Scientist"])
    }

    @Test
    fun `great person that cannot be placed keeps its points`() {
        val game = TestGame()
        game.gameInfo.gameParameters.greatPersonPointsInCities = true
        val blockedCiv = game.addCiv()
        val city = game.addCity(blockedCiv, game.getTile(HexCoord.Zero))
        game.addUnit("Worker", blockedCiv, city.getCenterTile()) // The only tile, so there is no room for a civilian
        city.storedGreatPersonPoints.add("Great Scientist", 100)

        blockedCiv.greatPeople.startTurn()

        assertTrue(blockedCiv.units.getCivUnits().none { it.name == "Great Scientist" })
        assertEquals(100, city.storedGreatPersonPoints["Great Scientist"])
    }

    @Test
    fun `city loses its points when it changes owner`() {
        otherCity.storedGreatPersonPoints.add("Great Scientist", 50)

        CityConquestFunctions(otherCity).moveToCiv(testGame.addCiv())

        assertTrue(otherCity.storedGreatPersonPoints.isEmpty())
    }

    @Test
    fun `cloned city has its own copy of the points`() {
        capital.storedGreatPersonPoints.add("Great Scientist", 50)

        val clone = capital.clone()

        assertNotSame(capital.storedGreatPersonPoints, clone.storedGreatPersonPoints)
        assertEquals(capital.storedGreatPersonPoints, clone.storedGreatPersonPoints)
    }

    @Test
    fun `civilization-wide mode combines points and carry overflow across successive births`() {
        testGame.gameInfo.gameParameters.greatPersonPointsInCities = false
        addBuildingGeneratingPoints(capital, "Great Scientist", 180)
        addBuildingGeneratingPoints(otherCity, "Great Scientist", 150)

        civ.greatPeople.addGreatPersonPoints()
        civ.greatPeople.startTurn()

        assertTrue(capital.storedGreatPersonPoints.isEmpty())
        assertTrue(otherCity.storedGreatPersonPoints.isEmpty())
        assertEquals(2, unitsNamed("Great Scientist").size)
        assertEquals(30, civ.greatPeople.greatPersonPointsCounter["Great Scientist"])
        assertEquals(400, civ.greatPeople.getPointsRequiredForGreatPerson("Great Engineer"))
    }

    @Test
    fun `points for units no longer in the ruleset are removed`() {
        capital.storedGreatPersonPoints.add("Removed Great Person", 50)

        testGame.gameInfo.removeMissingModReferences()

        assertTrue(capital.storedGreatPersonPoints.isEmpty())
    }
}
