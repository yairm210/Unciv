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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/*
https://github.com/yairm210/Unciv/issues/9328
Worker will not replace improvement to enable strategy resources
 */

@RunWith(GdxTestRunner::class)
internal class WorkerAutomationTest {
    private val testCivilizationNames = arrayListOf("America", "Germany", "Greece","Hanoi", "Genoa")
    private lateinit var sut: WorkerAutomation
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

        sut = WorkerAutomation(civInfo, 3)
    }

    @Test
    fun `should replace existing improvement`() {
        // Arrange
        civInfo.tech.techsResearched.add(ruleset.tileImprovements[RoadStatus.Road.name]!!.techRequired!!)
        civInfo.tech.techsResearched.add(ruleset.tileImprovements["Farm"]!!.techRequired!!)
        civInfo.tech.techsResearched.add(ruleset.tileImprovements["Mine"]!!.techRequired!!)
        civInfo.tech.techsResearched.add("Iron Working")
        civInfo.cities = listOf(createCity(civInfo, Vector2(0f, 0f), "Capital", true))
        val currentTile = gameInfo.tileMap[1,1]
        currentTile.setOwningCity(civInfo.cities.first())
        currentTile.resource = "Iron"
        currentTile.improvement = "Farm"
        val mapUnit = addUnit("Worker", civInfo, currentTile)

        // Act
        sut.automateWorkerAction(mapUnit, setOf())

        // Assert
        assertEquals("Should have replaced 'Farm' with 'Mine'","Mine", currentTile.improvementInProgress)
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
