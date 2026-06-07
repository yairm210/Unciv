package com.unciv.uniques

import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.HexCoord
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.RedirectOutput
import com.unciv.testing.RedirectPolicy
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random


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
        val doubleStrategicProduction = game.createBuilding("[+100]% [Strategic] resource production")

        city.cityConstructions.addBuilding(consumesCoal)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == -1)

        city.cityConstructions.addBuilding(doubleCoal)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == -1)

        city.cityConstructions.addBuilding(doubleStrategic)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == -1)

        city.cityConstructions.addBuilding(doubleStrategicProduction)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == -1)
    }

    @Test
    fun testResourceProductionAndConsumptionModifierDoesNotAffectConsumption() {
        val consumesCoal = game.createBuilding("Consumes [1] [Coal]")
        val providesCoal = game.createBuilding("Provides [1] [Coal]")
        val doubleStrategicProduction = game.createBuilding("[+100]% [Strategic] resource production")

        city.cityConstructions.addBuilding(providesCoal)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 1)

        city.cityConstructions.addBuilding(doubleStrategicProduction)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 2)

        city.cityConstructions.addBuilding(consumesCoal)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 1) // Produce 2, consume 1
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
        tile.setTileResource("Coal")
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
        tile.setTileResource("Coal")
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
        tile.setImprovement(improvement, civInfo)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 1)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Silver"] == -1)
    }

    @Test
        /** The revealing tech should not affect whether we can get the resource from improvements */
    fun testImprovementProvidesResourceWithUniqueBonuses() {
        val tile = game.tileMap[1,1]
        val improvement = game.createTileImprovement("Provides [1] [Coal]")
        tile.setImprovement(improvement, civInfo)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 1)

        val doubleCoal = game.createBuilding("[+100]% [Coal] resource production")
        city.cityConstructions.addBuilding(doubleCoal)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 2)

        val doubleStrategic = game.createBuilding("[+100]% [Strategic] resource production")
        city.cityConstructions.addBuilding(doubleStrategic)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 3)

        val doubleStrategicProduction = game.createBuilding("[+100]% [Strategic] resource production")
        city.cityConstructions.addBuilding(doubleStrategicProduction)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 4)
    }

    @Test
    fun stringtoint(){
        assert(1f == "1".toFloat())
    }


    // Resource tests
    @Test
    fun `should get resources from tiles`() {
        // given
        civInfo.tech.addTechnology("Iron Working")
        civInfo.tech.addTechnology("Mining")

        val tile = game.getTile(1,1)
        tile.setTileResource("Iron")
        tile.resourceAmount = 4
        tile.setImprovementBasic("Mine")

        // when
        val cityResources = city.getResourcesGeneratedByCity()

        // then
        assertEquals(1, cityResources.size)
        assertEquals("4 Iron from Tiles", cityResources.first().toString())
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
        assertEquals("4 Iron from Buildings", resources.first().toString())
    }

    @Test
    fun `should handle StatPercentFromObjectToResource with a buildingFilter`() {
        city.cityConstructions.addBuilding("Monument")
        val building = game.createBuilding("[300]% of [Culture] from every [Monument] in the city added to [Iron]")
        city.cityConstructions.addBuilding(building)
        assertEquals(6, city.getAvailableResourceAmount("Iron")) // 2 Culture * 3
    }

    @Test
    fun `should handle StatPercentFromObjectToResource with a improvementFilter`() {
        val tile = game.tileMap[1,1]
        tile.setTileResource("Wheat")
        tile.resourceAmount = 1
        tile.setImprovement("Farm")
        city.population.addPopulation(5) // Add population, since the tile needs to be worked
        val building = game.createBuilding("[300]% of [Food] from every [Farm] in the city added to [Iron]")
        city.cityConstructions.addBuilding(building)
        assertEquals(3, city.getAvailableResourceAmount("Iron"))
    }

    @Test
    fun `should reduce resources due to buildings`() {
        // given
        city.cityConstructions.addBuilding("Factory")

        // when
        val resources = civInfo.detailedCivResources

        // then
        assertEquals(1, resources.size)
        assertEquals("-1 Coal from Buildings", resources.first().toString())
    }

    @Test
    fun `Civ-wide resources from building uniques propagate between cities`() {
        // given
        val building = game.createBuilding("Provides [4] [Coal]")
        city.cityConstructions.addBuilding(building)

        val otherCity = civInfo.addCity(HexCoord(2,2))

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

        val otherCity = civInfo.addCity(HexCoord(2,2))

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
        val otherCity = civInfo.addCity(HexCoord(2,2)) // NOT religionized

        // when
        val resourceAmountInCapital = city.getAvailableResourceAmount("Iron")
        val resourceAmountInOtherCity = otherCity.getAvailableResourceAmount("Iron")

        // then
        assertEquals(1, resourceAmountInCapital)
        assertEquals(1, resourceAmountInOtherCity)
    }


    @Test
    fun cityResourcesWorkWithConditional() {
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
    fun cityResourcesFromImprovementWithConditional() {
        // given
        val resource = game.createResource(UniqueType.CityResource.text)
        val resourceImprovement = game.createTileImprovement("Provides [2] [${resource.name}] <in [non-[Fresh water]] tiles>")
        game.getTile(1,1).addTerrainFeature("Oasis")

        // when
        game.getTile(1,1).setImprovement(resourceImprovement)

        // then
        val resourceAmountInCapital = city.getAvailableResourceAmount(resource)
        assert(resourceAmountInCapital == 0)
    }


    @Test
    fun stockpiledResourcesConsumedWhenConstructionStarts() {
        // given
        val resource = game.createResource(UniqueType.Stockpiled.text)
        val building = game.createBuilding("Instantly provides [2] [${resource.name}]")
        city.cityConstructions.addBuilding(building)
        assert(civInfo.getCivResourcesByName()[resource.name] == 2)

        val consumingBuilding = game.createBuilding("Costs [1] [${resource.name}]")
        assert(civInfo.getCivResourcesByName()[resource.name] == 2) // no change yet
        city.cityConstructions.setCurrentConstruction(consumingBuilding.name)
        civInfo.playerType = PlayerType.Human // to not loop endlessly on "next turn"
        game.gameInfo.currentPlayer = civInfo.civID
        game.gameInfo.currentPlayerCiv = civInfo
        game.gameInfo.nextTurn()
        assert(civInfo.getCivResourcesByName()[resource.name] == 1) // 1 was consumed because production started
    }

    @Test
    fun constructionsRequiringStockpiledResourcesUnconstructableWithoutThem() {
        // given
        val resource = game.createResource(UniqueType.Stockpiled.text)
        val consumingBuilding = game.createBuilding("Costs [1] [${resource.name}]")
        assert(!consumingBuilding.isBuildable(city.cityConstructions))


        val building = game.createBuilding("Instantly provides [2] [${resource.name}]")
        city.cityConstructions.addBuilding(building)
        assert(consumingBuilding.isBuildable(city.cityConstructions))
    }

    @Test
    fun `Set stockpile to countable`() {
        // given
        val resource = game.createResource(UniqueType.Stockpiled.text)
        val building = game.createBuilding("Instantly provides [2] [${resource.name}]")
        city.cityConstructions.addBuilding(building)
        assertEquals(2, civInfo.getCivResourcesByName()[resource.name])

        // when
        UniqueTriggerActivation.triggerUnique(
            Unique("Set [${resource.name}] to [1+1*2]"),
            civInfo
        )

        // then
        assertEquals(3, civInfo.getCivResourcesByName()[resource.name])
    }

    @Test
    //@Ignore("For performance testing, not CI")
    @RedirectOutput(RedirectPolicy.Show)
    fun stressTestResourceSupplyList() {
        val resourceCount = 32
        val originCount = 12
        val totalTime = 10000L

        val rng = Random(42)
        val resources = (1..resourceCount).map {
            val type = ResourceType.entries.random(rng)
            val isStockpile = if (it % 3 == 0) arrayOf(UniqueType.Stockpiled.text) else emptyArray()
            val resource = game.createResource(*isStockpile)
            resource.resourceType = type
            resource
        }
        val origins = (1..originCount).map { "${rng.nextInt(4242)} Origin #$it" }
        val data = ResourceSupplyList()
        var loops = 0

        fun someResource() = resources.random(rng)
        fun someOrigin() = origins.random(rng)
        fun someAmount() = rng.nextInt(-5, 6)
        fun exercise1() {
            repeat(42) {
                data.add(someResource(), someOrigin(), someAmount())
            }
        }
        fun exercise2() {
            repeat(6) {
                val other = ResourceSupplyList()
                repeat(7) {
                    other.add(someResource(), someOrigin(), someAmount())
                }
                data.add(other)
            }
        }
        fun exercise3() {
            var output = ""
            repeat(4) {
                val resource = someResource()
                val total = data.sumBy(resource)
                output = data.listBy(resource).joinToString(prefix = "${resource.name} (", postfix = ") total=$total") { "${it.origin}: ${it.amount}" }
            }
            if (loops % 10000 == 0) println(output)
            repeat(4) {
                val origin = someOrigin()
                val total = data.sumByOrigin(origin)
                output = data.listBy(origin).joinToString(prefix = "$origin (", postfix = ") total=$total") { "${it.resource.name}: ${it.amount}" }
            }
            if (loops % 10000 == 5000) println(output)
        }

        val startTime = System.currentTimeMillis()
        val maxTime = startTime + totalTime

        while (System.currentTimeMillis() < maxTime) {
            loops++
            exercise1()
            exercise2()
            exercise3()
        }
        val elapsedTime = System.currentTimeMillis() - startTime
        println("$loops loops done in ${elapsedTime}ms, ${loops * 1000 / elapsedTime} loops/s")
    }
}
