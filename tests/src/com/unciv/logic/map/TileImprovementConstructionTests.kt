//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class TileImprovementConstructionTests {

    private var civInfo = CivilizationInfo()
    private var city = CityInfo()
    private var ruleSet = Ruleset()
    private val tileMap = TileMap()

    private fun getTile() = TileInfo().apply {
        baseTerrain = "Plains"
        ruleset = ruleSet
        position = Vector2(1f, 1f) // so that it's not on the same position as the city
        setOwningCity(city)
        this@apply.tileMap = this@TileImprovementConstructionTests.tileMap
    }

    @Before
    fun initTheWorld() {
        RulesetCache.loadRulesets()
        ruleSet = RulesetCache.getVanillaRuleset()
        civInfo.tech.researchedTechnologies.addAll(ruleSet.technologies.values)
        civInfo.tech.techsResearched.addAll(ruleSet.technologies.keys)
        city.civInfo = civInfo
    }


    @Test
    fun allTerrainSpecificImprovementsCanBeBuilt() {

        for (improvement in ruleSet.tileImprovements.values) {
            var terrain = improvement.terrainsCanBeBuiltOn.firstOrNull() ?: continue
            if (terrain == "Land") terrain = ruleSet.terrains.values.first { it.type == TerrainType.Land }.name
            if (terrain == "Water") terrain = ruleSet.terrains.values.first { it.type == TerrainType.Water }.name
            // If this improvement requires additional conditions to be true,
            // its too complex to handle all of them, so just skip it and hope its fine
            // I would like some comments on whether this approach is fine or if it's better if I handle every single unique here as well
            if (improvement.hasUnique(UniqueType.CanOnlyBeBuiltOnTile, StateForConditionals.IgnoreConditionals)) continue
            if (improvement.hasUnique(UniqueType.Unbuildable, StateForConditionals.IgnoreConditionals)) continue

            val tile = getTile()
            tile.baseTerrain = terrain
            if (improvement.hasUnique(UniqueType.CanOnlyImproveResource, StateForConditionals.IgnoreConditionals)) {
                tile.resource = ruleSet.tileResources.values.firstOrNull { it.isImprovedBy(improvement.name) }?.name ?: continue
            }
            tile.setTransients()
            if (improvement.uniqueTo != null) civInfo.civName = improvement.uniqueTo!!

            val canBeBuilt = tile.canBuildImprovement(improvement, civInfo)
            Assert.assertTrue(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun allResourceImprovementsCanBeBuilt() {
        for (improvement in ruleSet.tileImprovements.values) {
            val tile = getTile()
            tile.resource = ruleSet.tileResources.values
                .firstOrNull { it.isImprovedBy(improvement.name) }?.name
            if (tile.resource == null) continue
            // If this improvement requires additional conditions to be true,
            // its too complex to handle all of them, so just skip it and hope its fine
            if (improvement.hasUnique(UniqueType.CanOnlyBeBuiltOnTile, StateForConditionals.IgnoreConditionals)) continue

            tile.setTransients()
            val canBeBuilt = tile.canBuildImprovement(improvement, civInfo)
            Assert.assertTrue(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun coastalImprovementsCanBeBuilt() {
        val map = TileMap()
        val tile = getTile()
        tile.tileMap = map
        tile.setTransients()

        val otherTile = tile.clone()
        otherTile.baseTerrain = "Coast"
        otherTile.position.y = 1f

        map.tileMatrix.add(arrayListOf(tile, otherTile))

        for (improvement in ruleSet.tileImprovements.values) {
            if (!improvement.uniques.contains("Can only be built on [Coastal] tiles")) continue
            civInfo.civName = improvement.uniqueTo ?: "OtherCiv"
            val canBeBuilt = tile.canBuildImprovement(improvement, civInfo)
            Assert.assertTrue(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun coastalImprovementsCanNOTBeBuiltInland() {
        val tile = getTile()
        tile.setTransients()

        for (improvement in ruleSet.tileImprovements.values) {
            if (!improvement.uniques.contains("Can only be built on [Coastal] tiles")) continue
            civInfo.civName = improvement.uniqueTo ?: "OtherCiv"
            val canBeBuilt = tile.canBuildImprovement(improvement, civInfo)
            Assert.assertFalse(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun uniqueToOtherImprovementsCanNOTBeBuilt() {
        for (improvement in ruleSet.tileImprovements.values) {
            if (improvement.uniqueTo == null) continue
            civInfo.civName = "OtherCiv"
            val tile = getTile()
            tile.setTransients()
            val canBeBuilt = tile.canBuildImprovement(improvement, civInfo)
            Assert.assertFalse(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun improvementsCanNOTBeBuiltOnWrongResource() {
        civInfo.civName = "OtherCiv"

        for (resource in ruleSet.tileResources.values) {
            if (resource.getImprovements().isEmpty()) continue
            val improvement = ruleSet.tileImprovements[resource.getImprovements().first()]!!
            if (!improvement.hasUnique(UniqueType.CanOnlyImproveResource)) continue
            val wrongResource = ruleSet.tileResources.values.firstOrNull {
                it != resource && !it.isImprovedBy(improvement.name)
            } ?: continue
            val tile = getTile()
            tile.baseTerrain = "Plains"
            tile.resource = wrongResource.name
            tile.setTransients()
            val canBeBuilt = tile.canBuildImprovement(improvement, civInfo)
            Assert.assertFalse(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun terraceFarmCanNOTBeBuiltOnBonus() {
        val tile = getTile()
        tile.resource = "Sheep"
        tile.setTransients()
        tile.addTerrainFeature("Hill")
        civInfo.civName = "Inca"

        for (improvement in ruleSet.tileImprovements.values) {
            if (!improvement.uniques.contains("Cannot be built on [Bonus resource] tiles")) continue
            val canBeBuilt = tile.canBuildImprovement(improvement, civInfo)
            Assert.assertFalse(improvement.name, canBeBuilt)
        }
    }
}
