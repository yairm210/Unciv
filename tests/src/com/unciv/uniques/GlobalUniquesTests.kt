//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.*
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stats
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class GlobalUniquesTests {

    private var ruleSet = Ruleset()
//    private var unit = MapUnit()
    private var gameInfo = GameInfo()
    private var tileMap = TileMap()

    companion object {
        var objectsCreated = 0
    }
    
    @Before
    fun initTheWorld() {
        // Set UncivGame.Current so that debug variables are initialized
        UncivGame.Current = UncivGame("Test")
        // And the settings can be reached for the locale used in .tr()
        UncivGame.Current.settings = GameSettings()
        
        // Create a new ruleset we can easily edit, and set the important variables of gameInfo
        RulesetCache.loadRulesets()
        ruleSet = RulesetCache.getBaseRuleset()
        gameInfo.ruleSet = ruleSet
        gameInfo.difficultyObject = ruleSet.difficulties["Prince"]!!

        // Create a tilemap, needed for city centers
        gameInfo.tileMap = tileMap
    }

    private fun addTile(terrain: String, position: Vector2 = Vector2.Zero): TileInfo {
        val tile = TileInfo()
        tile.ruleset = ruleSet
        tile.baseTerrain = terrain
        tile.tileMap = tileMap
        tile.position = position
        tile.setTransients()
        while (tileMap.tileMatrix.size < position.y + 1)
            tileMap.tileMatrix.add(ArrayList<TileInfo?>())
        while (tileMap.tileMatrix[position.y.toInt()].size < position.x + 1)
            tileMap.tileMatrix[position.y.toInt()].add(null)
        tileMap.tileMatrix[position.y.toInt()][position.x.toInt()] = tile
        return tile
    }
    
    private fun addCiv(): CivilizationInfo {
        val nationName = "Nation-$objectsCreated"
        objectsCreated++
        ruleSet.nations[nationName] = Nation().apply {
            name = nationName
            cities = arrayListOf("The Capital")
        }
        val civInfo = CivilizationInfo()
        civInfo.nation = ruleSet.nations[nationName]!!
        civInfo.gameInfo = gameInfo
        civInfo.civName = nationName
        civInfo.setTransients()
        gameInfo.civilizations.add(civInfo)
        return civInfo
    }
    
    private fun addCity(civInfo: CivilizationInfo, tile: TileInfo, replacePalace: Boolean = false): CityInfo {
        val cityInfo = CityInfo(civInfo, tile.position)
        cityInfo.population.addPopulation(-1) // Remove population
        
        if (replacePalace && civInfo.cities.size == 1) {
            // Add a capital indicator without any other stats
            val palaceWithoutStats = Building()
            palaceWithoutStats.name = "PalaceWithoutStats"
            palaceWithoutStats.uniques.add("Indicates the capital city")
            ruleSet.buildings[palaceWithoutStats.name] = palaceWithoutStats
            cityInfo.cityConstructions.removeBuilding("Palace")
            cityInfo.cityConstructions.addBuilding(palaceWithoutStats.name)
        }
        return cityInfo
    }
    
    }
}
