//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.*
import com.unciv.testing.GdxTestRunner
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class VisibilityTests {

    private var civInfo = CivilizationInfo()
    private var enemyCivInfo = CivilizationInfo()
    private var ruleSet = Ruleset()
    private var unit = MapUnit()
    private var enemyUnit = MapUnit()
    val tileMap = TileMap()

    @Before
    fun initTheWorld() {
        RulesetCache.loadRulesets()
        ruleSet = RulesetCache.getVanillaRuleset()
        civInfo.gameInfo = GameInfo()
        civInfo.gameInfo.ruleSet = ruleSet
        civInfo.nation = Nation().apply { name = "My nation" }
        civInfo.gameInfo.civilizations.add(civInfo)
        civInfo.gameInfo.civilizations.add(enemyCivInfo)

        unit.civInfo = civInfo
        unit.owner = civInfo.civName

        enemyUnit.civInfo = enemyCivInfo
        enemyUnit.owner = enemyCivInfo.civName

        tileMap.ruleset = ruleSet
    }

    fun addTile(terrainName: String, position:Vector2) = addTile(listOf(terrainName), position)

    fun addTile(terrainNames: List<String>, position:Vector2): TileInfo {
        val tile = TileInfo()
        tile.position = position
        tile.baseTerrain = terrainNames.first()
        tile.ruleset = ruleSet
        tile.setTerrainTransients()
        tile.setTerrainFeatures(terrainNames.subList(1,terrainNames.size))
        tile.tileMap = tileMap
        tileMap.tileList.add(tile)
        tileMap.tileMatrix.clear()
        tileMap.setTransients()
        return tile
    }

    @Test
    fun canSeeNearbyForest() {
        val grassland = addTile("Grassland", Vector2(0f,0f))
        val forest = addTile(listOf("Grassland", "Forest"), Vector2(1f, 0f))
        val viewableTiles = grassland.getViewableTilesList(1)
        assert(viewableTiles.contains(forest))
    }

    @Test
    fun canSeeForestOverPlains() {
        val grassland = addTile("Grassland", Vector2(0f,0f))
        addTile("Plains", Vector2(1f,0f))
        val forest = addTile(listOf("Grassland", "Forest"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(viewableTiles.contains(forest))
    }

    @Test
    fun cannotSeePlainsOverForest() {
        val grassland = addTile("Grassland", Vector2(0f,0f))
        addTile(listOf("Grassland", "Forest"), Vector2(1f, 0f))
        val plains = addTile("Plains", Vector2(2f,0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(!viewableTiles.contains(plains))
    }

    @Test
    fun cannotSeeForestOverForest() {
        val grassland = addTile("Grassland", Vector2(0f,0f))
        addTile(listOf("Grassland", "Forest"), Vector2(1f, 0f))
        val plains = addTile(listOf("Plains", "Forest"), Vector2(2f,0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(!viewableTiles.contains(plains))
    }

    @Test
    fun canSeeHillOverPlains() {
        val grassland = addTile("Grassland", Vector2(0f,0f))
        addTile("Plains", Vector2(1f,0f))
        val hill = addTile(listOf("Grassland", "Hill"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(viewableTiles.contains(hill))
    }

    @Test
    fun cannotSeePlainsOverHill() {
        val grassland = addTile("Grassland", Vector2(0f,0f))
        addTile(listOf("Grassland", "Hill"), Vector2(1f, 0f))
        val plains = addTile("Plains", Vector2(2f,0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(!viewableTiles.contains(plains))
    }

    @Test
    fun cannotSeeHillOverHill() {
        val grassland = addTile("Grassland", Vector2(0f,0f))
        addTile(listOf("Grassland", "Hill"), Vector2(1f,0f))
        val hill = addTile(listOf("Grassland", "Hill"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(!viewableTiles.contains(hill))
    }


    @Test
    fun cannotSeeHillOverForest() {
        val grassland = addTile("Grassland", Vector2(0f,0f))
        addTile(listOf("Grassland", "Forest"), Vector2(1f,0f))
        val hill = addTile(listOf("Grassland", "Hill"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(!viewableTiles.contains(hill))
    }

    @Test
    fun cannotSeeForestOverHill() {
        val grassland = addTile("Grassland", Vector2(0f,0f))
        addTile(listOf("Grassland", "Hill"), Vector2(1f,0f))
        val hill = addTile(listOf("Grassland", "Forest"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(!viewableTiles.contains(hill))
    }

    @Test
    fun canSeeHillForestOverHill() {
        val grassland = addTile("Grassland", Vector2(0f,0f))
        addTile(listOf("Grassland", "Forest"), Vector2(1f,0f))
        val hill = addTile(listOf("Grassland", "Hill", "Forest"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(viewableTiles.contains(hill))
    }

    @Test
    fun canSeeMountainOverHill() {
        val grassland = addTile("Grassland", Vector2(0f,0f))
        addTile(listOf("Grassland", "Hill"), Vector2(1f,0f))
        val hill = addTile(listOf("Mountain"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(viewableTiles.contains(hill))
    }

    @Test
    fun cannotSeeMountainOverHillForest() {
        val grassland = addTile("Grassland", Vector2(0f,0f))
        addTile(listOf("Grassland", "Hill", "Forest"), Vector2(1f,0f))
        val hill = addTile(listOf("Mountain"), Vector2(2f, 0f))
        val viewableTiles = grassland.getViewableTilesList(2)
        assert(!viewableTiles.contains(hill))
    }

}
