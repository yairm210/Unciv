package com.unciv.logic.city

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.RejectionReasonType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.concurrent.thread

/** "Limited to [n] per Civilization" counts every queued copy in every city, an entry that is queued never counts against itself,
 *  and the queue survives concurrent edits. */
@RunWith(BaseTestRunner::class)
class ConstructionQueueLimitTests {
    private val testGame = TestGame()
    private lateinit var civ: Civilization
    private lateinit var cityA: City
    private lateinit var cityB: City
    private lateinit var cityC: City
    private lateinit var limited: BaseUnit
    private lateinit var limitedBuilding: Building

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(8)
        civ = testGame.addCiv()
        cityA = testGame.addCity(civ, testGame.getTile(HexCoord.Zero))
        cityB = testGame.addCity(civ, testGame.getTile(4, 0))
        cityC = testGame.addCity(civ, testGame.getTile(0, 4))
        limited = testGame.createBaseUnit(testGame.createUnitType().name, "Limited to [3] per Civilization")
        limited.cost = 100
        limitedBuilding = testGame.createBuilding("Limited to [2] per Civilization")
        limitedBuilding.cost = 100
    }

    private fun City.queued(name: String = limited.name) = cityConstructions.constructionQueue.count { it == name }
    private fun City.enqueue(times: Int) = repeat(times) { cityConstructions.addToQueue(limited) }

    @Test
    fun `one city cannot queue more copies than the limit`() {
        cityA.enqueue(5)
        assertEquals(3, cityA.queued())
    }

    @Test
    fun `copies queued in another city count against the limit`() {
        cityA.enqueue(2)
        cityB.enqueue(3)
        assertEquals(2, cityA.queued())
        assertEquals("only one more fits", 1, cityB.queued())
    }

    @Test
    fun `built units count against the limit together with queued copies`() {
        testGame.addUnit(limited.name, civ, testGame.getTile(1, 0))
        cityA.enqueue(5)
        assertEquals(2, cityA.queued())
        assertFalse(cityB.cityConstructions.canAddToQueue(limited))
    }

    @Test
    fun `a full limit is reported and hidden in the city that has the copies as in any other`() {
        cityA.enqueue(3)
        for (city in listOf(cityA, cityC)) {
            assertTrue(limited.getRejectionReasons(city.cityConstructions).any { it.type == RejectionReasonType.MaxNumberBuildable })
            assertFalse(limited.shouldBeDisplayed(city.cityConstructions))
        }
    }

    @Test
    fun `limited buildings are counted per city`() {
        cityA.cityConstructions.addToQueue(limitedBuilding)
        cityA.cityConstructions.addToQueue(limitedBuilding)
        cityB.cityConstructions.addToQueue(limitedBuilding)
        assertEquals(1, cityA.queued(limitedBuilding.name))
        assertFalse(cityC.cityConstructions.canAddToQueue(limitedBuilding))
    }

    @Test
    fun `entries within the limit stay in the queue when the queue is validated`() {
        cityA.enqueue(2)
        cityB.enqueue(1)
        cityA.cityConstructions.constructIfEnough()
        cityB.cityConstructions.constructIfEnough()
        assertEquals(2, cityA.queued())
        assertEquals(1, cityB.queued())
    }

    @Test
    fun `a queue that is over the limit, as old saves can have it, is trimmed to the limit by validation`() {
        cityA.cityConstructions.editQueue { repeat(5) { add(limited.name) } }
        cityA.cityConstructions.constructIfEnough()
        assertEquals(3, cityA.queued())
    }

    @Test
    fun `a queued limited unit can be bought although it fills the limit`() {
        cityA.enqueue(3)
        assertTrue(limited.isPurchasable(cityA.cityConstructions))
    }

    @Test
    fun `progress on a queued limited unit is not refunded because the queue fills the limit`() {
        val other = testGame.createBaseUnit(testGame.createUnitType().name)
        other.cost = 100
        cityA.cityConstructions.addToQueue(other)
        cityA.enqueue(3)
        cityA.cityConstructions.inProgressConstructions[limited.name] = 40
        cityA.cityConstructions.validateInProgressConstructions()
        assertEquals(40, cityA.cityConstructions.inProgressConstructions[limited.name])
    }

    @Test
    fun `concurrent queue edits are not lost`() {
        val before = cityA.cityConstructions.constructionQueue.size
        val threads = (1..8).map { t -> thread { repeat(250) { cityA.cityConstructions.editQueue { add("entry $t") } } } }
        threads.forEach { it.join() }
        assertEquals(before + 8 * 250, cityA.cityConstructions.constructionQueue.size)
    }

    @Test
    fun `removing an index that is no longer valid does nothing`() {
        cityA.enqueue(1)
        val before = cityA.cityConstructions.constructionQueue
        cityA.cityConstructions.removeFromQueue(99, false)
        assertEquals(before, cityA.cityConstructions.constructionQueue)
    }

    @Test
    fun `checks use the queue snapshot they are given instead of reading the field`() {
        val fullSnapshot = List(3) { limited.name }
        // The live queue is empty, but the snapshot the caller took has the limit reached
        assertFalse(cityA.cityConstructions.canAddToQueue(limited, fullSnapshot))
        assertFalse(limited.shouldBeDisplayed(cityA.cityConstructions, fullSnapshot))
        assertTrue(cityA.cityConstructions.canAddToQueue(limited))
    }

    @Test
    fun `the list of constructable units leaves out a unit whose limit the queue reaches`() {
        assertTrue(cityA.cityConstructions.getConstructableUnits().any { it.name == limited.name })
        cityA.enqueue(3)
        assertFalse(cityA.cityConstructions.getConstructableUnits().any { it.name == limited.name })
    }
}
