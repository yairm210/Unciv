package com.unciv.uniques

import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
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
        city.cityConstructions.addBuilding(consumesCoal)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == -1)
    }

    @Test
    fun testResourceProductionModifierDoesNotAffectConsumption() {
        val consumesCoal = game.createBuilding("Consumes [1] [Coal]")
        val doubleCoal = game.createBuilding("Double quantity of [Coal] produced")
        val doubleStrategic = game.createBuilding("Quantity of strategic resources produced by the empire +[100]%")

        city.cityConstructions.addBuilding(consumesCoal)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == -1)

        city.cityConstructions.addBuilding(doubleCoal)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == -1)

        city.cityConstructions.addBuilding(doubleStrategic)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == -1)
    }

    @Test
    fun testResourceProductionAndConsumptionModifierDoesNotAffectConsumption() {
        val consumesCoal = game.createBuilding("Consumes [1] [Coal]")
        val providesCoal = game.createBuilding("Provides [1] [Coal]")
        val doubleCoal = game.createBuilding("Double quantity of [Coal] produced")
        val doubleStrategic = game.createBuilding("Quantity of strategic resources produced by the empire +[100]%")

        city.cityConstructions.addBuilding(providesCoal)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 1)

        city.cityConstructions.addBuilding(doubleCoal)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 2)

        city.cityConstructions.addBuilding(doubleStrategic)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 4)

        city.cityConstructions.addBuilding(consumesCoal)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 3) // Produce 4 (1*2*2), consume 1
    }

    @Test
    fun testBuildingGrantedByUniqueGrantsResource() {
        val resourceProvider = game.createBuilding("Provides [1] [Coal]")
        val resourceProviderProvider = game.createBuilding("Gain a free [${resourceProvider.name}] [in this city]")
        city.cityConstructions.addBuilding(resourceProviderProvider)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 1)
    }

    @Test
    fun testTileProvidesResourceOnlyWithRequiredTech() {
        val tile = game.tileMap[1,1]
        tile.resource = "Coal"
        tile.resourceAmount = 1
        tile.changeImprovement("Mine")

        civInfo.tech.addTechnology(game.ruleset.tileImprovements["Mine"]!!.techRequired!!)
        Assert.assertEquals(civInfo.getCivResourcesByName()["Coal"], 0)

        civInfo.tech.addTechnology(game.ruleset.tileResources["Coal"]!!.revealedBy!!)

        Assert.assertEquals(civInfo.getCivResourcesByName()["Coal"], 1)
    }


    @Test
    fun testTileDoesNotProvideResourceWithPillagedImprovement() {
        val tile = game.tileMap[1,1]
        tile.resource = "Coal"
        tile.resourceAmount = 1
        tile.changeImprovement("Mine")

        civInfo.tech.addTechnology(game.ruleset.tileImprovements["Mine"]!!.techRequired!!)
        civInfo.tech.addTechnology(game.ruleset.tileResources["Coal"]!!.revealedBy!!)
        Assert.assertEquals(civInfo.getCivResourcesByName()["Coal"], 1)

        tile.setPillaged()
        Assert.assertEquals(civInfo.getCivResourcesByName()["Coal"], 0)
    }

    @Test
        /** The revealing tech should not affect whether we can get the resource from improvements */
    fun testImprovementProvidesResourceEvenWithoutTech() {
        val tile = game.tileMap[1,1]
        val improvement = game.createTileImprovement("Provides [1] [Coal]", "Consumes [1] [Silver]")
        tile.changeImprovement(improvement.name, civInfo)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 1)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Silver"] == -1)
    }

    @Test
        /** The revealing tech should not affect whether we can get the resource from improvements */
    fun testImprovementProvidesResourceWithUniqueBonuses() {
        val tile = game.tileMap[1,1]
        val improvement = game.createTileImprovement("Provides [1] [Coal]")
        tile.changeImprovement(improvement.name, civInfo)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 1)

        val doubleCoal = game.createBuilding("Double quantity of [Coal] produced")
        city.cityConstructions.addBuilding(doubleCoal)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 2)

        val doubleStrategic = game.createBuilding("Quantity of strategic resources produced by the empire +[100]%")
        city.cityConstructions.addBuilding(doubleStrategic)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 4)
    }

    @Test
    fun testPerCountableForGlobalAndLocalResources() {
        // one coal provided locally
        val consumesCoal = game.createBuilding("Provides [1] [Coal]")
        city.cityConstructions.addBuilding(consumesCoal)
        // one globally
        UniqueTriggerActivation.triggerUnique(Unique("Provides [1] [Coal] <for [2] turns>"), civInfo)
        val providesFaithPerCoal = game.createBuilding("[+1 Faith] [in this city] <for every [Coal]>")
        city.cityConstructions.addBuilding(providesFaithPerCoal)
        Assert.assertEquals(2f, city.cityStats.currentCityStats.faith)
    }
}
