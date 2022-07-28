package com.unciv.logic.civilization

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.GameInfo
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.GdxTestRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.After
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

    private val mockGameInfo = mockk<GameInfo>()
    private val slot = slot<String>()

    private val testCivilizationNames = arrayListOf("America", "Germany", "Greece")
    private val civilizations = testCivilizationNames.associateWith { CivilizationInfo(it) }
    private val ourCiv = civilizations.values.first()
    private val tilesMap = TileMap().apply { tileMatrix = ArrayList() }
    private var rules = Ruleset()

    @Before
    fun setup() {
        RulesetCache.loadRulesets()
        rules = RulesetCache.getVanillaRuleset()
        // Setup the GameInfo mock
        every { mockGameInfo.getCivilization(capture(slot)) } answers { civilizations.getValue(slot.captured) }
        every { mockGameInfo.civilizations } answers { civilizations.values.toMutableList() }
        every { mockGameInfo.tileMap } returns tilesMap
        every { mockGameInfo.ruleSet } returns rules
        every { mockGameInfo.getCities() } answers { civilizations.values.asSequence().flatMap { it.cities } }
        // Needs for founding cities
        every { mockGameInfo.turns } returns 1

        // Initialize test civilizations so they pass certain criteria
        civilizations.values.forEach {
            it.gameInfo = mockGameInfo
            it.nation = Nation()
            it.nation.name = it.civName // for isBarbarian()
            it.tech.techsResearched.add(rules.tileImprovements[RoadStatus.Road.name]!!.techRequired!!)
            it.tech.techsResearched.add(rules.tileImprovements[RoadStatus.Railroad.name]!!.techRequired!!)
        }
    }

    @After
    fun tearDown() {
        (tilesMap.values as ArrayList<TileInfo>).clear()
        for (civ in civilizations.values) {
            civ.cities = emptyList()
            civ.diplomacy.clear()
        }
    }

    private fun createLand(from: Int, to: Int) {
        // create map
        val tiles = tilesMap.tileMatrix
        tilesMap.bottomY = from
        tiles.add(ArrayList())
        for (y in from..to)
            tiles.last().add(TileInfo().apply { tileMap = tilesMap
                position = Vector2(tiles.size-1f, y.toFloat())
                baseTerrain = rules.terrains.values.first { it.type == TerrainType.Land }.name })
    }

    private fun createWater(from: Int, to: Int) {
        // create map
        val tiles = tilesMap.tileMatrix
        tilesMap.bottomY = from
        // here we assume the row with a land is already created
        tiles.add(ArrayList())
        for (y in from..to)
            tiles.last().add(TileInfo().apply { tileMap = tilesMap
                position = Vector2(tiles.size-1f, y.toFloat())
                isWater = true
                baseTerrain = rules.terrains.values.first { it.type == TerrainType.Water }.name })
    }

    private fun createMedium(from:Int, to: Int, type: RoadStatus) {
        val tiles = tilesMap.tileMatrix
        for (tile in tiles.last())
            if (tile != null && tile.position.y > from && tile.position.y < to)
                tile.roadStatus = type
    }

    private fun createCity(civInfo: CivilizationInfo, position: Vector2, name: String, capital: Boolean = false, hasHarbor: Boolean = false): CityInfo {
        return CityInfo().apply {
            this.civInfo = civInfo
            location = position
            if (capital)
                cityConstructions.builtBuildings.add(rules.buildings.values.first { it.hasUnique(UniqueType.IndicatesCapital) }.name)
            if (hasHarbor)
                cityConstructions.builtBuildings.add(rules.buildings.values.first { it.hasUnique(UniqueType.ConnectTradeRoutes) }.name)
            this.name = name
            setTransients()
            tilesMap[location].setOwningCity(this)
        }
    }

    private fun meetCivAndSetBorders(name: String, areBordersOpen: Boolean) {
        ourCiv.diplomacy[name] = DiplomacyManager(ourCiv, name)
            .apply { diplomaticStatus = DiplomaticStatus.Peace }
        ourCiv.diplomacy[name]!!.hasOpenBorders = areBordersOpen
    }

    @Test
    fun `Own cities are connected by road`() {
        // Map: C-A N
        createLand(-2,2)
        ourCiv.cities = listOf( createCity(ourCiv, Vector2(0f, 0f), "Capital", true),
                                createCity(ourCiv, Vector2(0f, -2f), "Connected"),
                                createCity(ourCiv, Vector2(0f, 2f), "Not connected"))
        createMedium(-2, 0, RoadStatus.Road)

        val connectionsFinder = CapitalConnectionsFinder(ourCiv)
        val res = connectionsFinder.find()

        Assert.assertTrue(res.keys.any { it.name == "Connected" } && !res.keys.any { it.name == "Not connected" }  )
    }

    @Test
    fun `Own cities are connected by railroad`() {
        // Map: N A=C
        createLand(-2,2)
        ourCiv.cities = listOf( createCity(ourCiv, Vector2(0f, 0f), "Capital", true),
            createCity(ourCiv, Vector2(0f, 2f), "Connected"),
            createCity(ourCiv, Vector2(0f, -2f), "Not connected"))
        createMedium(0, 2, RoadStatus.Railroad)

        val connectionsFinder = CapitalConnectionsFinder(ourCiv)
        val res = connectionsFinder.find()

        Assert.assertTrue(res.keys.any { it.name == "Connected" } && !res.keys.any { it.name == "Not connected" }  )
    }

    @Test
    fun `Own cities are connected by road and harbor`() {
        // Map: N A-C C
        //      ~~~~~~~
        createLand(-2,4)
        createMedium(0,2, RoadStatus.Road)
        createWater(-2,4)
        ourCiv.cities = listOf( createCity(ourCiv, Vector2(0f, 0f), "Capital", true),
            createCity(ourCiv, Vector2(0f, -2f), "Not connected"),
            createCity(ourCiv, Vector2(0f, 2f), "Connected1", capital = false, hasHarbor = true),
            createCity(ourCiv, Vector2(0f, 4f), "Connected2", capital = false, hasHarbor = true))

        val connectionsFinder = CapitalConnectionsFinder(ourCiv)
        val res = connectionsFinder.find()

        Assert.assertTrue(res.keys.count { it.name.startsWith("Connected") } == 2 && !res.keys.any { it.name == "Not connected" }  )
    }

    @Test
    fun `Cities are connected by roads via Open Borders`() {
        // Map: N-X=A-O=C
        createLand(-4,4)
        ourCiv.cities = listOf( createCity(ourCiv, Vector2(0f, 0f), "Capital", true),
            createCity(ourCiv, Vector2(0f, -4f), "Not connected"),
            createCity(ourCiv, Vector2(0f, 4f), "Connected"))

        val openCiv = civilizations["Germany"]!!
        openCiv.cities = listOf( createCity(openCiv, Vector2(0f, 2f), "Berlin", true))
        meetCivAndSetBorders("Germany", true)

        val closedCiv = civilizations["Greece"]!!
        closedCiv.cities = listOf( createCity(closedCiv, Vector2(0f, -2f), "Athens", true))
        meetCivAndSetBorders("Greece", false)

        createMedium(-4,-2, RoadStatus.Road)
        createMedium(-2,0, RoadStatus.Railroad)
        createMedium(0,2, RoadStatus.Road)
        createMedium(2,4, RoadStatus.Railroad)
        // part of the railroad (Berlin-Connected) goes through other civilization territory
        tilesMap.tileMatrix[0][7]!!.setOwningCity(openCiv.cities.first())


        val connectionsFinder = CapitalConnectionsFinder(ourCiv)
        val res = connectionsFinder.find()

        Assert.assertTrue(res.keys.any { it.name == "Connected" } && !res.keys.any { it.name == "Not connected" }  )
    }

    @Test
    fun `Cities are connected via own harbors only`() {
        // Map: A
        //      ~~~~~
        //      C O-N=N
        createLand(-4,-4) // capital is on an island
        createWater(-4,0)
        createLand(-4,2) // some land without access to ocean
        ourCiv.cities = listOf( createCity(ourCiv, Vector2(0f, -4f), "Capital", true, hasHarbor = true),
            createCity(ourCiv, Vector2(2f, 2f), "Not connected1", capital = false, hasHarbor = true), // cannot reach ocean
            createCity(ourCiv, Vector2(2f, 0f), "Not connected2"), // has no harbor, has road to Berlin
            createCity(ourCiv, Vector2(2f, -4f), "Connected", capital = false, hasHarbor = true))

        val openCiv = civilizations["Germany"]!!
        openCiv.cities = listOf( createCity(openCiv, Vector2(2f, -2f), "Berlin", true, hasHarbor = true))
        meetCivAndSetBorders("Germany", true)

        createMedium(-2,0, RoadStatus.Road)
        createMedium(0,2, RoadStatus.Railroad)

        val connectionsFinder = CapitalConnectionsFinder(ourCiv)
        val res = connectionsFinder.find()

        Assert.assertTrue(res.keys.any { it.name == "Connected" } && !res.keys.any { it.name.startsWith("Not connected") } )
    }

    @Test
    fun `Cities are connected by roads via City-States`() {
        // Map: N=X-A=O-C
        createLand(-4,4)
        ourCiv.cities = listOf( createCity(ourCiv, Vector2(0f, 0f), "Capital", true),
            createCity(ourCiv, Vector2(0f, -4f), "Not connected"),
            createCity(ourCiv, Vector2(0f, 4f), "Connected"))

        val openCiv = civilizations["Germany"]!!
        openCiv.nation.cityStateType = CityStateType.Cultured
        openCiv.cities = listOf( createCity(openCiv, Vector2(0f, 2f), "Berlin", true))
        ourCiv.diplomacy["Germany"] = DiplomacyManager(ourCiv, "Germany")
            .apply { diplomaticStatus = DiplomaticStatus.Peace }

        val closedCiv = civilizations["Greece"]!!
        closedCiv.nation.cityStateType = CityStateType.Cultured
        closedCiv.cities = listOf( createCity(closedCiv, Vector2(0f, -2f), "Athens", true))
        ourCiv.diplomacy["Greece"] = DiplomacyManager(ourCiv, "Greece")
            .apply { diplomaticStatus = DiplomaticStatus.War }


        createMedium(-4,-2, RoadStatus.Railroad)
        createMedium(-2,0, RoadStatus.Road)
        createMedium(0,2, RoadStatus.Railroad)
        createMedium(2,4, RoadStatus.Road)
        // part of the railroad (Berlin-Connected) goes through other civilization territory
        tilesMap.tileMatrix[0][7]!!.setOwningCity(openCiv.cities.first())


        val connectionsFinder = CapitalConnectionsFinder(ourCiv)
        val res = connectionsFinder.find()

        Assert.assertTrue(res.keys.any { it.name == "Connected" } && !res.keys.any { it.name == "Not connected" }  )

    }
}
