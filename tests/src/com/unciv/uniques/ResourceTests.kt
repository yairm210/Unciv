package com.unciv.uniques

import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(GdxTestRunner::class)
class ResourceTests {
    private val game = TestGame().apply { makeHexagonalMap(2) }
    private val civInfo = game.addCiv()
    private val city = game.addCity(civInfo, game.tileMap[0,0])

    @Test
    fun testConsumesResourceUnique() {
        val consumesCoal = game.createBuilding("Consumes [1] [Coal]")
        city.cityConstructions.addBuilding(consumesCoal.name)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == -1)
    }

    @Test
    fun testResourceProductionModifierDoesNotAffectConsumption() {
        val consumesCoal = game.createBuilding("Consumes [1] [Coal]", "Double quantity of [Coal] produced")
        city.cityConstructions.addBuilding(consumesCoal.name)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == -1)
    }

    @Test
    fun testResourceProductionAndConsumptionModifierDoesNotAffectConsumption() {
        val consumesCoal = game.createBuilding("Consumes [1] [Coal]", "Provides [1] [Coal]",
            "Double quantity of [Coal] produced")
        city.cityConstructions.addBuilding(consumesCoal.name)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 1)

        val doubleStrategic = game.createBuilding("Quantity of strategic resources produced by the empire +[100]%")
        city.cityConstructions.addBuilding(doubleStrategic.name)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 3) // Produce 4 (1*2*2), consume 1
    }

    @Test
    fun testBuildingGrantedByUniqueGrantsResource() {
        val resourceProvider = game.createBuilding("Provides [1] [Coal]")
        val resourceProviderProvider = game.createBuilding("Gain a free [${resourceProvider.name}] [in this city]")
        city.cityConstructions.addBuilding(resourceProviderProvider.name)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 1)
    }

    @Test
    fun testTileProvidesResourceOnlyWithRequiredTech() {
        val tile = game.tileMap[1,1]
        tile.resource = "Coal"
        tile.improvement = "Mine"
        tile.resourceAmount = 1

        civInfo.tech.addTechnology(game.ruleset.tileImprovements["Mine"]!!.techRequired!!)
        Assert.assertEquals(civInfo.getCivResourcesByName()["Coal"], 0)

        civInfo.tech.addTechnology(game.ruleset.tileResources["Coal"]!!.revealedBy!!)

        Assert.assertEquals(civInfo.getCivResourcesByName()["Coal"], 1)
    }

    @Test
        /** The revealing tech should not affect whether we can get the resource from improvements */
    fun testImprovementProvidesResourceEvenWithoutTech() {
        val tile = game.tileMap[1,1]
        val improvement = game.createTileImprovement("Provides [1] [Coal]", "Consumes [1] [Silver]")
        tile.improvement = improvement.name
        civInfo.cache.updateCivResources()
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 1)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Silver"] == -1)
    }

    @Test
        /** The revealing tech should not affect whether we can get the resource from improvements */
    fun testImprovementProvidesResourceWithUniqueBonuses() {
        val tile = game.tileMap[1,1]
        val improvement = game.createTileImprovement("Provides [1] [Coal]")
        tile.improvement = improvement.name
        civInfo.cache.updateCivResources()
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 1)

        val doubleCoal = game.createBuilding("Double quantity of [Coal] produced")
        city.cityConstructions.addBuilding(doubleCoal.name)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 2)

        val doubleStrategic = game.createBuilding("Quantity of strategic resources produced by the empire +[100]%")
        city.cityConstructions.addBuilding(doubleStrategic.name)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 4)
    }
}
