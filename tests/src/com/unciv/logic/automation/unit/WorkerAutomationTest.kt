package com.unciv.logic.automation.unit

import com.badlogic.gdx.math.Vector2
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.GdxTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
internal class WorkerAutomationTest {
    private val testCivilizationNames = arrayListOf("America", "Germany", "Greece","Hanoi", "Genoa")
    private lateinit var workerAutomation: WorkerAutomation
    private lateinit var civInfo: Civilization
    private lateinit var gameInfo: GameInfo
    private lateinit var ruleset: Ruleset
    private lateinit var uncivGame: UncivGame

    @Before
    fun setUp() {
        // RuleSet
        RulesetCache.loadRulesets(noMods = true)
        ruleset = RulesetCache.getVanillaRuleset()

        // GameInfo
        gameInfo = GameInfo()
        gameInfo.ruleset = ruleset

        // UncivGame
        uncivGame = UncivGame(true)
        uncivGame.settings = GameSettings()
        UncivGame.Current = uncivGame

        for (civName in testCivilizationNames)
            gameInfo.civilizations.add(Civilization(civName).apply { playerType= PlayerType.Human })
        gameInfo.tileMap = TileMap(7, ruleset)

        // Initialize test civilizations
        for (ci in gameInfo.civilizations) {
            ci.gameInfo = gameInfo
            ci.nation = Nation()
            ci.nation.name = ci.civName
        }
        gameInfo.setTransients()
        civInfo = gameInfo.civilizations.first()

        workerAutomation = WorkerAutomation(civInfo, 3)
    }

    @Test
    fun `should replace already existing improvement to enable resource`() {
        // Add the needed tech to construct the improvements below
        for (improvement in listOf(RoadStatus.Road.name, "Farm", "Mine")) {
            civInfo.tech.techsResearched.add(ruleset.tileImprovements[improvement]!!.techRequired!!)
        }
        civInfo.tech.techsResearched.add(ruleset.tileResources["Iron"]!!.revealedBy!!)

        civInfo.cities = listOf(createCity(civInfo, Vector2(0f, 0f), "Capital", true))
        val currentTile = gameInfo.tileMap[1,1]
        currentTile.setOwningCity(civInfo.cities.first())
        currentTile.improvement = "Farm" // Set existing improvement
        currentTile.resource = "Iron" // This tile also has a resource needs to be enabled by a building a Mine
        val mapUnit = addUnit("Worker", civInfo, currentTile)

        // Act
        workerAutomation.automateWorkerAction(mapUnit, setOf())

        // Assert
        assertEquals("Worker should have replaced already existing improvement 'Farm' with 'Mine' to enable 'Iron' resource",
            "Mine", currentTile.improvementInProgress)
        assertTrue(currentTile.turnsToImprovement > 0)
    }

    private fun createCity(civInfo: Civilization, position: Vector2, name: String,
                           capital: Boolean = false
    ): City {
        return City().apply {
            location = position
            if (capital)
                cityConstructions.builtBuildings.add(ruleset.buildings.values.first { it.hasUnique(
                    UniqueType.IndicatesCapital) }.name)
            this.name = name
            setTransients(civInfo)
            gameInfo.tileMap[location].setOwningCity(this)
        }
    }

    private fun addUnit(name: String, civInfo: Civilization, tile: Tile): MapUnit {
        val baseUnit = ruleset.units[name]!!
        baseUnit.ruleset = ruleset
        val mapUnit = baseUnit.getMapUnit(civInfo)
        mapUnit.putInTile(tile)
        return mapUnit
    }
}
