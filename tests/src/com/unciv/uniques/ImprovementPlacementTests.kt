//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.RoadStatus
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stats
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class ImprovementPlacementTests {

    private lateinit var game: TestGame
    private lateinit var testCiv: CivilizationInfo

    @Before
    fun initTheWorld() {
        game = TestGame()
        game.makeHexagonalMap(3)
        testCiv = game.addCiv()
        game.addCity(testCiv, game.getTile(Vector2.Zero))
    }
    
    // region stat uniques

    val defaultImprovementTileLocation = Vector2(0f,1f)
            
    @Test
    /** Does not check removal improvements, or Moai-like 'can only be built on [tileFilter] tiles' uniques */
    fun canBuildImprovementOnRegularAllowedTerrain() {
        var success = true
        for (improvement in game.ruleset.tileImprovements.values) {
            if (improvement.hasUnique(UniqueType.Unbuildable)) continue
            // I can't be bothered to add the special check for moai, gimme a break
            if (improvement.hasUnique(UniqueType.CanOnlyBeBuiltOnTile)) continue
            if (improvement.uniqueTo != null) testCiv.civName = improvement.uniqueTo!!

            val terrainsCanBeBuiltOn = improvement.terrainsCanBeBuiltOn
                .map {
                    if (it == "Land") game.ruleset.terrains.values
                        .first { terrain -> terrain.type == TerrainType.Land }
                    else game.ruleset.terrains[it]
                }

            for (terrain in terrainsCanBeBuiltOn) {
                if (terrain == null) {
                    println("Improvement $improvement has unknown terrain")
                    success = false
                    continue
                }
                val tile = if (terrain.type == TerrainType.TerrainFeature) {
                    game.setTileFeatures(
                        defaultImprovementTileLocation,
                        terrain.occursOn.first(),
                        listOf(terrain.name)
                    )
                } else game.setTileFeatures(defaultImprovementTileLocation, terrain.name)

                tile.resource = null
                if (improvement.hasUnique(UniqueType.CanOnlyImproveResource)) {
                    val resourceToAdd = game.ruleset.tileResources.values
                        .filter { it.getImprovements().contains(improvement.name) }
                        .first { it.terrainsCanBeFoundOn.contains(tile.getLastTerrain().name) }
                    tile.resource = resourceToAdd.name
                }

                if (improvement.techRequired != null && !testCiv.tech.isResearched(improvement.techRequired!!))
                    testCiv.tech.addTechnology(improvement.techRequired!!)
                if (!tile.canBuildImprovement(improvement, testCiv)) {
                    tile.canBuildImprovement(improvement, testCiv)
                    success = false
                    println("Improvement $improvement cannot be built on $terrain")
                }
            }
        }

        assert(success)
    }
    
    // endregion
}
