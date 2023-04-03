package com.unciv.logic.civilization

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.GameInfo
import com.unciv.logic.city.City
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.transients.CapitalConnectionsFinder
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.GdxTestRunner
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

    private var gameInfo = GameInfo()
    private val testCivilizationNames = arrayListOf("America", "Germany", "Greece","Hanoi", "Genoa")
    private var rules = Ruleset()

    private fun ourCiv() = gameInfo.civilizations.first()

    @Before
    fun setup() {
        RulesetCache.loadRulesets(noMods = true)
        rules = RulesetCache.getVanillaRuleset()

        gameInfo = GameInfo()
        gameInfo.ruleset = rules

        for (civName in testCivilizationNames)
            gameInfo.civilizations.add(Civilization(civName).apply { playerType=PlayerType.Human })
        gameInfo.tileMap = TileMap(4, rules)

        // Initialize test civilizations so they pass certain criteria
        gameInfo.civilizations.forEach {
            it.gameInfo = gameInfo
            it.nation = Nation()
            it.nation.name = it.civName // for isBarbarian()
            it.tech.techsResearched.add(rules.tileImprovements[RoadStatus.Road.name]!!.techRequired!!)
            it.tech.techsResearched.add(rules.tileImprovements[RoadStatus.Railroad.name]!!.techRequired!!)
        }

        gameInfo.setTransients()
    }

    private fun createMedium(from:Int, to: Int, type: RoadStatus) {
        for (i in from..to){
            val tile = gameInfo.tileMap[0, i]
            tile.roadStatus = type
        }
    }

    private fun createCity(civInfo: Civilization, position: Vector2, name: String, capital: Boolean = false, hasHarbor: Boolean = false): City {
        return City().apply {
            location = position
            if (capital)
                cityConstructions.builtBuildings.add(rules.buildings.values.first { it.hasUnique(UniqueType.IndicatesCapital) }.name)
            if (hasHarbor)
                cityConstructions.builtBuildings.add(rules.buildings.values.first { it.hasUnique(UniqueType.ConnectTradeRoutes) }.name)
            this.name = name
            setTransients(civInfo)
            gameInfo.tileMap[location].setOwningCity(this)
        }
    }

    private fun meetCivAndSetBorders(name: String, areBordersOpen: Boolean) {
        ourCiv().diplomacy[name] = DiplomacyManager(ourCiv(), name)
            .apply { diplomaticStatus = DiplomaticStatus.Peace }
        ourCiv().diplomacy[name]!!.hasOpenBorders = areBordersOpen
    }

    @Test
    fun `Own cities are connected by road`() {
        // Map: C-A N
//         createLand(-2,2)
        ourCiv().cities = listOf( createCity(ourCiv(), Vector2(0f, 0f), "Capital", true),
                                createCity(ourCiv(), Vector2(0f, -2f), "Connected"),
                                createCity(ourCiv(), Vector2(0f, 2f), "Not connected"))
        createMedium(-2, 0, RoadStatus.Road)

        val connectionsFinder = CapitalConnectionsFinder(ourCiv())
        val res = connectionsFinder.find()

        Assert.assertTrue(res.keys.any { it.name == "Connected" } && !res.keys.any { it.name == "Not connected" }  )
    }

    @Test
    fun `Own cities are connected by railroad`() {
        // Map: N A=C
        ourCiv().cities = listOf( createCity(ourCiv(), Vector2(0f, 0f), "Capital", true),
            createCity(ourCiv(), Vector2(0f, 2f), "Connected"),
            createCity(ourCiv(), Vector2(0f, -2f), "Not connected"))
        createMedium(0, 2, RoadStatus.Railroad)

        val connectionsFinder = CapitalConnectionsFinder(ourCiv())
        val res = connectionsFinder.find()

        Assert.assertTrue(res.keys.any { it.name == "Connected" } && !res.keys.any { it.name == "Not connected" }  )
    }
//
//     @Test
//     fun `Own cities are connected by road and harbor`() {
//         // Map: N A-C C
//         //      ~~~~~~~
//         createMedium(0,2, RoadStatus.Road)
// //         createWater(-2,4)
//         ourCiv().cities = listOf( createCity(ourCiv(), Vector2(0f, 0f), "Capital", true),
//             createCity(ourCiv(), Vector2(0f, -2f), "Not connected"),
//             createCity(ourCiv(), Vector2(0f, 2f), "Connected1", capital = false, hasHarbor = true),
//             createCity(ourCiv(), Vector2(0f, 4f), "Connected2", capital = false, hasHarbor = true))
//
//         val connectionsFinder = CapitalConnectionsFinder(ourCiv())
//         val res = connectionsFinder.find()
//
//         Assert.assertTrue(res.keys.count { it.name.startsWith("Connected") } == 2 && !res.keys.any { it.name == "Not connected" }  )
//     }

    @Test
    fun `Cities are connected by roads via Open Borders`() {
        // Map: N-X=A-O=C
        ourCiv().cities = listOf( createCity(ourCiv(), Vector2(0f, 0f), "Capital", true),
            createCity(ourCiv(), Vector2(0f, -4f), "Not connected"),
            createCity(ourCiv(), Vector2(0f, 4f), "Connected"))

        val openCiv = gameInfo.getCivilization("Germany")
        openCiv.cities = listOf( createCity(openCiv, Vector2(0f, 2f), "Berlin", true))
        meetCivAndSetBorders("Germany", true)

        // The path to "not connected" goes through closed territory
        val closedCiv = gameInfo.getCivilization("Greece")
        closedCiv.cities = listOf( createCity(closedCiv, Vector2(0f, -2f), "Athens", true))
        meetCivAndSetBorders("Greece", false)

        createMedium(-4,-2, RoadStatus.Road)
        createMedium(-2,0, RoadStatus.Railroad)
        createMedium(0,2, RoadStatus.Road)
        createMedium(2,4, RoadStatus.Railroad)

        val connectionsFinder = CapitalConnectionsFinder(ourCiv())
        val res = connectionsFinder.find()

        Assert.assertTrue(res.keys.any { it.name == "Connected" } && !res.keys.any { it.name == "Not connected" }  )
    }

//     @Test
//     fun `Cities are connected via own harbors only`() {
//         // Map: A
//         //      ~~~~~
//         //      C O-N=N
//         createLand(-4,-4) // capital is on an island
//         createWater(-4,0)
//         createLand(-4,2) // some land without access to ocean
//         ourCiv().cities = listOf( createCity(ourCiv(), Vector2(0f, -4f), "Capital", true, hasHarbor = true),
//             createCity(ourCiv(), Vector2(2f, 2f), "Not connected1", capital = false, hasHarbor = true), // cannot reach ocean
//             createCity(ourCiv(), Vector2(2f, 0f), "Not connected2"), // has no harbor, has road to Berlin
//             createCity(ourCiv(), Vector2(2f, -4f), "Connected", capital = false, hasHarbor = true))
//
//         val openCiv = gameInfo.getCivilization("Germany")
//         openCiv.cities = listOf( createCity(openCiv, Vector2(2f, -2f), "Berlin", true, hasHarbor = true))
//         meetCivAndSetBorders("Germany", true)
//
//         createMedium(-2,0, RoadStatus.Road)
//         createMedium(0,2, RoadStatus.Railroad)
//
//         val connectionsFinder = CapitalConnectionsFinder(ourCiv())
//         val res = connectionsFinder.find()
//
//         Assert.assertTrue(res.keys.any { it.name == "Connected" } && !res.keys.any { it.name.startsWith("Not connected") } )
//     }

    @Test
    fun `Cities are connected by roads via City-States`() {
        // Map: N=X-A=O-C
        ourCiv().cities = listOf( createCity(ourCiv(), Vector2(0f, 0f), "Capital", true),
            createCity(ourCiv(), Vector2(0f, -4f), "Not connected"),
            createCity(ourCiv(), Vector2(0f, 4f), "Connected"))

        val openCiv = gameInfo.getCivilization("Hanoi")
//         openCiv.nation.cityStateType = "Cultured"
        openCiv.cities = listOf( createCity(openCiv, Vector2(0f, 2f), "Berlin", true))
        ourCiv().diplomacy["Hanoi"] = DiplomacyManager(ourCiv(), "Hanoi")
            .apply { diplomaticStatus = DiplomaticStatus.Peace }

        val closedCiv = gameInfo.getCivilization("Genoa")
        closedCiv.nation.cityStateType = "Cultured"
        closedCiv.cities = listOf( createCity(closedCiv, Vector2(0f, -2f), "Athens", true))
        ourCiv().diplomacy["Genoa"] = DiplomacyManager(ourCiv(), "Genoa")
            .apply { diplomaticStatus = DiplomaticStatus.War }


        createMedium(-4,-2, RoadStatus.Railroad)
        createMedium(-2,0, RoadStatus.Road)
        createMedium(0,2, RoadStatus.Railroad)
        createMedium(2,4, RoadStatus.Road)


        val connectionsFinder = CapitalConnectionsFinder(ourCiv())
        val res = connectionsFinder.find()

        Assert.assertTrue(res.keys.any { it.name == "Connected" } && !res.keys.any { it.name == "Not connected" }  )

    }
}
