//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
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
            val terrain = improvement.terrainsCanBeBuiltOn.firstOrNull() ?: continue
            val tile = getTile()
            tile.baseTerrain = terrain
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
                    .firstOrNull { it.improvement == improvement.name }?.name
            if (tile.resource == null) continue
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
            if (!improvement.getMatchingUniques(UniqueType.CanOnlyBeBuiltOn).any { it.params[0] == "Coastal" } ) continue
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
            if (!improvement.getMatchingUniques(UniqueType.CanOnlyBeBuiltOn).any { it.params[0] == "Coastal" } ) continue
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
            if (resource.improvement == null) continue
            val improvement = ruleSet.tileImprovements[resource.improvement]!!
            if (improvement.terrainsCanBeBuiltOn.isNotEmpty()) continue
            val wrongResource = ruleSet.tileResources.values.firstOrNull { 
                it != resource && it.improvement != improvement.name 
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
        tile.terrainFeatures.add("Hill")
        tile.resource = "Sheep"
        tile.setTransients()
        civInfo.civName = "Inca"

        for (improvement in ruleSet.tileImprovements.values) {
            if (!improvement.uniques.contains("Cannot be built on [Bonus resource] tiles")) continue
            val canBeBuilt = tile.canBuildImprovement(improvement, civInfo)
            Assert.assertFalse(improvement.name, canBeBuilt)
        }
    }
}
