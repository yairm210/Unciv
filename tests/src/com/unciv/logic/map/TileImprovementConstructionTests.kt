//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic.map

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.GdxTestRunner
import com.unciv.uniques.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class TileImprovementConstructionTests {

    private lateinit var civInfo: Civilization
    private lateinit var tileMap: TileMap
    private lateinit var city: City

    val testGame = TestGame()


    @Before
    fun initTheWorld() {
        testGame.makeHexagonalMap(4)
        tileMap = testGame.tileMap
        civInfo = testGame.addCiv()
        civInfo.tech.researchedTechnologies.addAll(testGame.ruleset.technologies.values)
        civInfo.tech.techsResearched.addAll(testGame.ruleset.technologies.keys)
        city = testGame.addCity(civInfo, tileMap[0,0])
    }


    @Test
    fun allTerrainSpecificImprovementsCanBeBuilt() {
        for (improvement in testGame.ruleset.tileImprovements.values) {
            var terrain = improvement.terrainsCanBeBuiltOn.firstOrNull() ?: continue
            if (terrain == "Land") terrain = testGame.ruleset.terrains.values.first { it.type == TerrainType.Land }.name
            if (terrain == "Water") terrain = testGame.ruleset.terrains.values.first { it.type == TerrainType.Water }.name
            // If this improvement requires additional conditions to be true,
            // its too complex to handle all of them, so just skip it and hope its fine
            // I would like some comments on whether this approach is fine or if it's better if I handle every single unique here as well
            if (improvement.hasUnique(UniqueType.CanOnlyBeBuiltOnTile, StateForConditionals.IgnoreConditionals)) continue
            if (improvement.hasUnique(UniqueType.Unbuildable, StateForConditionals.IgnoreConditionals)) continue

            val tile = tileMap[1,1]
            tile.baseTerrain = terrain
            tile.resource = null
            if (improvement.hasUnique(UniqueType.CanOnlyImproveResource, StateForConditionals.IgnoreConditionals)) {
                tile.resource = testGame.ruleset.tileResources.values.firstOrNull { it.isImprovedBy(improvement.name) }?.name ?: continue
            }
            tile.setTransients()

            if (improvement.uniqueTo != null){
                civInfo.civName = improvement.uniqueTo!!
            }

            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo)
            Assert.assertTrue(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun allResourceImprovementsCanBeBuilt() {

        for (improvement in testGame.ruleset.tileImprovements.values) {
            val tile = tileMap[1,1]
            tile.resource = testGame.ruleset.tileResources.values
                .firstOrNull { it.isImprovedBy(improvement.name) }?.name
            if (tile.resource == null) continue
            // If this improvement requires additional conditions to be true,
            // its too complex to handle all of them, so just skip it and hope its fine
            if (improvement.hasUnique(UniqueType.CanOnlyBeBuiltOnTile, StateForConditionals.IgnoreConditionals)) continue

            tile.setTransients()
            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo)
            Assert.assertTrue(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun coastalImprovementsCanBeBuilt() {
        val coastTile = tileMap[1,2]
        coastTile.baseTerrain = "Coast"
        coastTile.setTransients()

        val coastalTile = tileMap[1,1]

        for (improvement in testGame.ruleset.tileImprovements.values) {
            if (!improvement.uniques.contains("Can only be built on [Coastal] tiles")) continue
            civInfo.civName = improvement.uniqueTo ?: "OtherCiv"
            val canBeBuilt = coastalTile.improvementFunctions.canBuildImprovement(improvement, civInfo)
            Assert.assertTrue(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun coastalImprovementsCanNOTBeBuiltInland() {
        val tile = tileMap[1,1]

        for (improvement in testGame.ruleset.tileImprovements.values) {
            if (!improvement.uniques.contains("Can only be built on [Coastal] tiles")) continue
            civInfo.civName = improvement.uniqueTo ?: "OtherCiv"
            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo)
            Assert.assertFalse(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun uniqueToOtherImprovementsCanNOTBeBuilt() {
        for (improvement in testGame.ruleset.tileImprovements.values) {
            if (improvement.uniqueTo == null) continue
            civInfo.civName = "OtherCiv"
            val tile = tileMap[1,1]
            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo)
            Assert.assertFalse(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun improvementsCanNOTBeBuiltOnWrongResource() {
        for (resource in testGame.ruleset.tileResources.values) {
            if (resource.getImprovements().isEmpty()) continue
            val improvement = testGame.ruleset.tileImprovements[resource.getImprovements().first()]!!
            if (!improvement.hasUnique(UniqueType.CanOnlyImproveResource)) continue
            val wrongResource = testGame.ruleset.tileResources.values.firstOrNull {
                it != resource && !it.isImprovedBy(improvement.name)
            } ?: continue
            val tile = tileMap[1,1]
            tile.baseTerrain = "Plains"
            tile.resource = wrongResource.name
            tile.setTransients()
            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo)
            Assert.assertFalse(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun buildingGreatImprovementRemovesFeatures() {
        val tile = tileMap[1,1]
        tile.baseTerrain = "Plains"
        tile.addTerrainFeature("Hill")
        tile.addTerrainFeature("Forest")
        Assert.assertEquals(tile.terrainFeatures, listOf("Hill", "Forest"))

        tile.changeImprovement("Landmark")
        Assert.assertEquals(tile.terrainFeatures, listOf("Hill"))
    }

    @Test
    fun citadelTakesOverAdjacentTiles() {
        val tile = tileMap[1,1]
        Assert.assertFalse(tile.neighbors.all { it.owningCity == city })
        tile.changeImprovement("Citadel", civInfo)
        Assert.assertTrue(tile.neighbors.all { it.owningCity == city })
    }

    @Test
    fun terraceFarmCanNOTBeBuiltOnBonus() {
        val tile = tileMap[1,1]
        tile.resource = "Sheep"
        tile.setTransients()
        tile.addTerrainFeature("Hill")
        civInfo.civName = "Inca"

        for (improvement in testGame.ruleset.tileImprovements.values) {
            if (!improvement.uniques.contains("Cannot be built on [Bonus resource] tiles")) continue
            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo)
            Assert.assertFalse(improvement.name, canBeBuilt)
        }
    }
}
