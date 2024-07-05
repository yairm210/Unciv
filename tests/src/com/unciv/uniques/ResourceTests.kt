package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Assert.assertEquals
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
        tile.setImprovement("Mine")

        civInfo.tech.addTechnology(game.ruleset.tileImprovements["Mine"]!!.techRequired!!)
        assertEquals(civInfo.getCivResourcesByName()["Coal"], 0)

        civInfo.tech.addTechnology(game.ruleset.tileResources["Coal"]!!.revealedBy!!)

        assertEquals(civInfo.getCivResourcesByName()["Coal"], 1)
    }


    @Test
    fun testTileDoesNotProvideResourceWithPillagedImprovement() {
        val tile = game.tileMap[1,1]
        tile.resource = "Coal"
        tile.resourceAmount = 1
        tile.setImprovement("Mine")

        civInfo.tech.addTechnology(game.ruleset.tileImprovements["Mine"]!!.techRequired!!)
        civInfo.tech.addTechnology(game.ruleset.tileResources["Coal"]!!.revealedBy!!)
        assertEquals(civInfo.getCivResourcesByName()["Coal"], 1)

        tile.setPillaged()
        assertEquals(civInfo.getCivResourcesByName()["Coal"], 0)
    }

    @Test
        /** The revealing tech should not affect whether we can get the resource from improvements */
    fun testImprovementProvidesResourceEvenWithoutTech() {
        val tile = game.tileMap[1,1]
        val improvement = game.createTileImprovement("Provides [1] [Coal]", "Consumes [1] [Silver]")
        tile.setImprovement(improvement.name, civInfo)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 1)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Silver"] == -1)
    }

    @Test
        /** The revealing tech should not affect whether we can get the resource from improvements */
    fun testImprovementProvidesResourceWithUniqueBonuses() {
        val tile = game.tileMap[1,1]
        val improvement = game.createTileImprovement("Provides [1] [Coal]")
        tile.setImprovement(improvement.name, civInfo)
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
        assertEquals(2f, city.cityStats.currentCityStats.faith)
    }


    // Resource tests
    @Test
    fun `should get resources from tiles`() {
        // given
        civInfo.tech.addTechnology("Iron Working")
        civInfo.tech.addTechnology("Mining")

        val tile = game.getTile(Vector2(1f, 1f))
        tile.resource = "Iron"
        tile.resourceAmount = 4
        tile.improvement = "Mine"

        // when
        val cityResources = city.getResourcesGeneratedByCity(civInfo.getResourceModifiers())

        // then
        assertEquals(1, cityResources.size)
        assertEquals("4 Iron from Tiles", cityResources[0].toString())
    }

    @Test
    fun `should get resources from unique buildings`() {
        // given
        val building = game.createBuilding("Provides [4] [Iron]")
        city.cityConstructions.addBuilding(building)

        // when
        val resources = civInfo.detailedCivResources

        // then
        assertEquals(1, resources.size)
        assertEquals("4 Iron from Buildings", resources[0].toString())
    }

    @Test
    fun `should reduce resources due to buildings`() {
        // given
        city.cityConstructions.addBuilding("Factory")

        // when
        val resources = civInfo.detailedCivResources

        // then
        assertEquals(1, resources.size)
        assertEquals("-1 Coal from Buildings", resources[0].toString())
    }

    @Test
    fun `Civ-wide resources from building uniques propagate between cities`() {
        // given
        val building = game.createBuilding("Provides [4] [Coal]")
        city.cityConstructions.addBuilding(building)

        val otherCity = civInfo.addCity(Vector2(2f,2f))

        // when
        val resourceAmountInOtherCity = otherCity.getAvailableResourceAmount("Coal")

        // then
        assertEquals(4, resourceAmountInOtherCity)
    }


    @Test
    fun `City-wide resources from building uniques propagate between cities`() {
        // given
        val resource = game.createResource(UniqueType.CityResource.text)
        val building = game.createBuilding("Provides [4] [${resource.name}]")
        city.cityConstructions.addBuilding(building)

        val otherCity = civInfo.addCity(Vector2(2f,2f))

        // when
        val resourceAmountInOtherCity = otherCity.getAvailableResourceAmount(resource.name)

        // then
        assertEquals(4, resourceAmountInOtherCity)
    }

    @Test
    fun `City-wide resources not double-counted in same city`() {
        // given
        val resource = game.createResource(UniqueType.CityResource.text)
        val building = game.createBuilding("Provides [4] [${resource.name}]")
        city.cityConstructions.addBuilding(building)


        // when
        val resourceAmountInCapital = city.getAvailableResourceAmount(resource.name)

        // then
        assertEquals(4, resourceAmountInCapital)
    }

    @Test
    fun `Civ-wide resources can come from follower beliefs, and affect all cities`() {
        // given
        val religion = game.addReligion(civInfo)
        val belief = game.createBelief(BeliefType.Follower, "Provides [1] [Iron]")
        religion.addBeliefs(listOf(belief))
        city.population.setPopulation(1)
        city.religion.addPressure(religion.name, 1000)
        val otherCity = civInfo.addCity(Vector2(2f,2f)) // NOT religionized

        // when
        val resourceAmountInCapital = city.getAvailableResourceAmount("Iron")
        val resourceAmountInOtherCity = otherCity.getAvailableResourceAmount("Iron")

        // then
        assertEquals(1, resourceAmountInCapital)
        assertEquals(1, resourceAmountInOtherCity)
    }


    @Test
    fun CityResourcesWorkWithConditional() {
        // given
        val resource = game.createResource(UniqueType.CityResource.text)
        val resourceAndConditionalBuilding = game.createBuilding("Provides [2] [${resource.name}]",
            "[+1 Faith] <when above [1] [${resource.name}]>")

        // when
        city.cityConstructions.addBuilding(resourceAndConditionalBuilding)
        val faith = city.cityStats.currentCityStats.faith

        // then
        assertEquals(1f, faith)
    }

    @Test
    fun CityResourcesFromImprovementWithConditional() {
        // given
        val resource = game.createResource(UniqueType.CityResource.text)
        val resourceImprovement = game.createTileImprovement("Provides [2] [${resource.name}] <in [non-[Fresh water]] tiles>")
        game.getTile(1,1).addTerrainFeature("Oasis")

        // when
        game.getTile(1,1).setImprovement(resourceImprovement.name)

        // then
        val resourceAmountInCapital = city.getAvailableResourceAmount(resource.name)
        assert(resourceAmountInCapital == 0)
    }
}
