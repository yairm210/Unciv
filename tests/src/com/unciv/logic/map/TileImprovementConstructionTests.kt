//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic.map

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stats
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
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
        for (tech in testGame.ruleset.technologies.values)
            civInfo.tech.addTechnology(tech.name)
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

            if (improvement.uniqueTo != null) {
                civInfo = testGame.addCiv(improvement.uniqueTo!!)
                for (tech in testGame.ruleset.technologies.values)
                    civInfo.tech.addTechnology(tech.name)
                city.civ = civInfo
            }

            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
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
            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
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
            if (improvement.uniqueTo != null) {
                civInfo = testGame.addCiv(improvement.uniqueTo!!)
                for (tech in testGame.ruleset.technologies.values)
                    civInfo.tech.addTechnology(tech.name)
                city.civ = civInfo
            }
            val canBeBuilt = coastalTile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
            Assert.assertTrue(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun coastalImprovementsCanNOTBeBuiltInland() {
        val tile = tileMap[1,1]

        for (improvement in testGame.ruleset.tileImprovements.values) {
            if (!improvement.uniques.contains("Can only be built on [Coastal] tiles")) continue
            civInfo.setNameForUnitTests(improvement.uniqueTo ?: "OtherCiv")
            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
            Assert.assertFalse(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun uniqueToOtherImprovementsCanNOTBeBuilt() {
        for (improvement in testGame.ruleset.tileImprovements.values) {
            if (improvement.uniqueTo == null) continue
            civInfo.setNameForUnitTests("OtherCiv")
            val tile = tileMap[1,1]
            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
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
            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
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

        tile.setImprovement("Landmark")
        Assert.assertEquals(tile.terrainFeatures, listOf("Hill"))
    }

    @Test
    fun citadelTakesOverAdjacentTiles() {
        val tile = tileMap[1,1]
        Assert.assertFalse(tile.neighbors.all { it.owningCity == city })
        tile.setImprovement("Citadel", civInfo)
        Assert.assertTrue(tile.neighbors.all { it.owningCity == city })
    }

    @Test
    fun terraceFarmCanNOTBeBuiltOnBonus() {
        val tile = tileMap[1,1]
        tile.resource = "Sheep"
        tile.setTransients()
        tile.addTerrainFeature("Hill")
        civInfo.setNameForUnitTests("Inca")

        for (improvement in testGame.ruleset.tileImprovements.values) {
            if (!improvement.uniques.contains("Cannot be built on [Bonus resource] tiles")) continue
            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
            Assert.assertFalse(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun buildingRoadBuildsARoad() {
        val tile = tileMap[1,1]
        tile.improvementFunctions.setImprovement("Road")
        assert(tile.roadStatus == RoadStatus.Road)
    }

    @Test
    fun removingRoadRemovesRoad() {
        val tile = tileMap[1,1]
        tile.roadStatus = RoadStatus.Road
        tile.improvementFunctions.setImprovement("Remove Road")
        assert(tile.roadStatus == RoadStatus.None)
    }

    @Test
    fun removingForestRemovesForestAndLumbermill() {
        val tile = tileMap[1,1]
        tile.addTerrainFeature("Forest")
        tile.improvementFunctions.setImprovement("Lumber mill")
        assert(tile.getTileImprovement()!!.name == "Lumber mill")
        tile.improvementFunctions.setImprovement("Remove Forest")
        assert(tile.terrainFeatures.isEmpty())
        assert(tile.improvement == null) // Lumber mill can ONLY be on Forest, and is therefore removed
    }

    @Test
    fun removingForestRemovesForestButNotCamp() {
        val tile = tileMap[1,1]
        tile.addTerrainFeature("Forest")
        tile.resource = "Deer"
        tile.baseTerrain = "Plains"
        tile.improvementFunctions.setImprovement("Camp")
        assert(tile.getTileImprovement()!!.name == "Camp")
        tile.improvementFunctions.setImprovement("Remove Forest")
        assert(tile.terrainFeatures.isEmpty())
        assert(tile.improvement == "Camp") // Camp can be both on Forest AND on Plains, so not removed
    }

    @Test
    fun improvementCannotBuildWhenNotAllowed() {
        val tile = tileMap[1,1]
        tile.baseTerrain ="Grassland"
        tile.addTerrainFeature("Forest")

        val improvement = testGame.createTileImprovement()
        Assert.assertFalse("Forest doesn't allow building unless allowed",
            tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state))


        val allowedImprovement = testGame.createTileImprovement()
        allowedImprovement.terrainsCanBeBuiltOn += "Forest"
        Assert.assertTrue("Forest should allow building when allowed",
            tile.improvementFunctions.canBuildImprovement(allowedImprovement, civInfo.state))
        tile.setImprovement(allowedImprovement.name)
        Assert.assertTrue(tile.improvement == allowedImprovement.name)
        Assert.assertTrue("Forest should not be removed with this improvement", tile.terrainFeatures.contains("Forest"))
    }

    @Test
    fun improvementDoesntNeedRemovalCanBuildHere() {
        val tile = tileMap[1,1]
        tile.baseTerrain ="Grassland"
        tile.addTerrainFeature("Forest")

        val improvement = testGame.createTileImprovement("Does not need removal of [Forest]")
        Assert.assertTrue(tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state))
        tile.setImprovement(improvement.name)
        Assert.assertTrue(tile.improvement == improvement.name)
        Assert.assertTrue("Forest should not be removed with this improvement", tile.terrainFeatures.contains("Forest"))
    }

    @Test
    fun statsDiffFromRemovingForestTakesRemovedLumberMillIntoAccount() {
        val tile = tileMap[1,1]
        tile.baseTerrain = "Grassland"
        tile.addTerrainFeature("Forest")

        val lumberMill = testGame.ruleset.tileImprovements["Lumber mill"]!!
        tile.improvementFunctions.setImprovement(lumberMill.name)
        assert(tile.getTileImprovement() == lumberMill)

        // 1f 1p from forest, 2p from lumber mill since all techs are researched
        val tileStats = tile.stats.getTileStats(civInfo)
        assert(tileStats.equals(Stats(production = 3f, food = 1f)))

        val statsDiff = tile.stats.getStatDiffForImprovement(testGame.ruleset.tileImprovements["Remove Forest"]!!, civInfo, null)

        // We'll be reverting back to grassland stats - 2f only
        assert(statsDiff.equals(Stats(food = +1f, production = -3f)))
    }
}
