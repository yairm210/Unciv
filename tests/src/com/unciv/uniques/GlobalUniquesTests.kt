//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.stats.Stats
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(GdxTestRunner::class)
class GlobalUniquesTests {

    private lateinit var game: TestGame
    private val epsilon = 0.01 // for float comparisons

    @Before
    fun initTheWorld() {
        game = TestGame()
    }

    // region base stat bonus providing uniques

    @Test
    fun statsOnBuilding() {
        val civInfo = game.addCiv()
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true)
        val building = game.createBuilding("[+1 Food]")

        city.cityConstructions.addBuilding(building)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.equals(Stats(food=1f)))
    }


    @Test
    fun statsNotOnBuilding() {
        val civInfo = game.addCiv("[+2 Gold]")
        civInfo.updateStatsForNextTurn()
        Assert.assertTrue(civInfo.stats.statsForNextTurn.equals(Stats(gold=2f)))
    }

    @Test
    fun statsHappinessNotOnBuilding() {
        val civInfo = game.addCiv("[+7 Happiness]")
        civInfo.updateStatsForNextTurn()
        Assert.assertTrue(civInfo.stats.happiness == civInfo.getDifficulty().baseHappiness + 7)
    }


    @Test
    fun statsPerCity() {
        val civInfo = game.addCiv()
        val city = game.addCity(civInfo,  game.getTile(Vector2.Zero), true)
        val building = game.createBuilding("[+1 Production] [in this city]")

        city.cityConstructions.addBuilding(building)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.equals(Stats(production=1f)))
    }

    @Test
    fun statsPerSpecialist() {
        val civInfo = game.addCiv()
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true, initialPopulation = 2)
        val building = game.createBuilding("[+3 Gold] from every specialist [in this city]")
        val specialistName = game.createSpecialist()
        building.specialistSlots.add(specialistName, 2)
        city.population.specialistAllocations[specialistName] = 2

        city.cityConstructions.addBuilding(building)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList["Specialists"]!!.equals(Stats(gold=6f)))
    }

    @Test
    fun statsPerPopulation() {
        val civInfo = game.addCiv()
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true, initialPopulation = 4)
        val building = game.createBuilding("[+3 Gold] per [2] population [in this city]")

        city.cityConstructions.addBuilding(building)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.gold == 6f)
    }

    @Test
    fun statsPerXPopulation() {
        val civInfo = game.addCiv()
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true, initialPopulation = 2)
        val building = game.createBuilding("[+3 Gold] <in cities with at least [3] [Population]>")

        city.cityConstructions.addBuilding(building)

        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.gold == 0f)
        city.population.setPopulation(5)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.gold == 3f)
    }

    @Test
    fun statsFromCitiesOnSpecificTiles() {
        val civInfo = game.addCiv()
        val tile = game.setTileTerrain(Vector2.Zero, Constants.desert)
        val city = game.addCity(civInfo, tile, true)
        val building = game.createBuilding("[+3 Gold] in cities on [${Constants.desert}] tiles")
        city.cityConstructions.addBuilding(building)

        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.gold == 3f)
        tile.baseTerrain = Constants.grassland
        tile.setTransients()
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.gold == 0f)
    }

    @Test
    fun statsFromTiles() {
        game.makeHexagonalMap(2)
        val civInfo = game.addCiv()
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true)
        val building = game.createBuilding("[+4 Gold] from [${Constants.grassland}] tiles [in all cities]")
        city.cityConstructions.addBuilding(building)

        val tile2 = game.setTileTerrain(Vector2(0f,1f), Constants.grassland)
        Assert.assertTrue(tile2.stats.getTileStats(city, civInfo).gold == 4f)
    }

    @Test
    fun statsFromTilesMultifilter() {
        game.makeHexagonalMap(2)
        val civInfo = game.addCiv()
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true)
        val building = game.createBuilding("[+4 Gold] from [{${Constants.grassland}} {Farm}] tiles [in all cities]")
        city.cityConstructions.addBuilding(building)

        val tile2 = game.setTileTerrain(Vector2(0f,1f), Constants.grassland)
        tile2.setImprovement("Farm")
        Assert.assertTrue(tile2.stats.getTileStats(city, civInfo).gold == 4f)
    }
    
    @Test
    fun statsFromTilesWithout() {
        game.makeHexagonalMap(3)
        val civInfo = game.addCiv()
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true)
        val building = game.createBuilding("[+4 Gold] from [${Constants.grassland}] tiles without [${Constants.forest}] [in this city]")
        city.cityConstructions.addBuilding(building)

        val tile2 = game.setTileTerrain(Vector2(0f,1f), Constants.grassland)
        game.addTileToCity(city, tile2)
        Assert.assertTrue(tile2.stats.getTileStats(city, civInfo).gold == 4f)

        val tile3 = game.setTileTerrainAndFeatures(Vector2(0f, 2f), Constants.grassland, Constants.forest)
        game.addTileToCity(city, tile3)
        Assert.assertFalse(tile3.stats.getTileStats(city, civInfo).gold == 4f)
    }

    @Test
    fun statsFromObject() {
        game.makeHexagonalMap(1)
        val civInfo = game.addCiv()
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true, initialPopulation = 2)
        val specialist = game.createSpecialist()
        val building = game.createBuilding("[+3 Faith] from every [${specialist}]")

        city.cityConstructions.addBuilding(building)
        city.population.specialistAllocations[specialist] = 2

        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList["Specialists"]!!.faith == 6f)

        city.cityConstructions.removeBuilding(building)
        val building2 = game.createBuilding("[+3 Faith] from every [${Constants.grassland}]")
        city.cityConstructions.addBuilding(building2)

        val tile2 = game.setTileTerrain(Vector2(0f,1f), Constants.grassland)
        Assert.assertTrue(tile2.stats.getTileStats(city, civInfo).faith == 3f)

        city.cityConstructions.removeBuilding(building2)

        val emptyBuilding = game.createBuilding()

        val building3 = game.createBuilding("[+3 Faith] from every [${emptyBuilding.name}]")
        city.cityConstructions.addBuilding(emptyBuilding)
        city.cityConstructions.addBuilding(building3)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.faith == 3f)
    }

    @Test
    fun statsFromTradeRoute() {
        game.makeHexagonalMap(3)
        val civInfo = game.addCiv("[+30 Science] from each Trade Route")
        civInfo.tech.addTechnology("The Wheel") // Required to form trade routes
        val tile1 = game.getTile(Vector2.Zero)
        val tile2 = game.getTile(Vector2(0f, 2f))
        tile1.roadStatus = RoadStatus.Road
        tile2.roadStatus = RoadStatus.Road
        @Suppress("UNUSED_VARIABLE")
        val city1 = game.addCity(civInfo, tile1)
        val city2 = game.addCity(civInfo, tile2)
        val inBetweenTile = game.getTile(Vector2(0f, 1f))
        inBetweenTile.roadStatus = RoadStatus.Road
        civInfo.cache.updateCitiesConnectedToCapital()
        city2.cityStats.update()

        Assert.assertTrue(city2.cityStats.finalStatList["Trade routes"]!!.science == 30f)
    }

    @Test
    fun statsFromPolicies() {
        game.makeHexagonalMap(3)
        val civInfo = game.addCiv("[+30 Science] per [2] social policies adopted")
        val policiesToAdopt = listOf("Tradition", "Aristocracy", "Legalism")
        civInfo.policies.freePolicies = 3
        for (policyName in policiesToAdopt) {
            val policy = game.ruleset.policies[policyName]!!
            civInfo.policies.adopt(policy)
        }
        Assert.assertTrue(civInfo.stats.getStatMapForNextTurn()["Policies"]!!.science == 30f)
    }

    @Test
    fun statsFromGlobalCitiesFollowingReligion() {
        val civ1 = game.addCiv()
        val religion = game.addReligion(civ1)
        val belief = game.createBelief(BeliefType.Founder, "[+30 Science] for each global city following this religion")
        religion.addBeliefs(listOf(belief))
        val civ2 = game.addCiv()
        val tile = game.getTile(Vector2.Zero)
        val cityOfCiv2 = game.addCity(civ2, tile, initialPopulation = 1) // Need someone to be converted
        cityOfCiv2.religion.addPressure(religion.name, 1000)

        Assert.assertTrue(cityOfCiv2.religion.getMajorityReligionName() == religion.name)

        civ1.updateStatsForNextTurn()

        Assert.assertTrue(civ1.stats.statsForNextTurn.science == 30f)
    }

    @Test
    fun happinessFromGlobalCitiesFollowingReligion() {
        val civ1 = game.addCiv()
        val religion = game.addReligion(civ1)
        val belief = game.createBelief(BeliefType.Founder, "[+42 Happiness] for each global city following this religion")
        religion.addBeliefs(listOf(belief))
        val civ2 = game.addCiv()
        val tile = game.getTile(Vector2.Zero)
        val cityOfCiv2 = game.addCity(civ2, tile, initialPopulation = 1) // Need someone to be converted
        cityOfCiv2.religion.addPressure(religion.name, 1000)

        civ1.updateStatsForNextTurn()

        val baseHappiness = civ1.getDifficulty().baseHappiness
        // Since civ1 has no cities, there are no other happiness sources
        Assert.assertTrue(civ1.stats.happiness == baseHappiness + 42)
    }

    @Test
    fun statsFromGlobalFollowers() {
        val civ1 = game.addCiv()
        val religion = game.addReligion(civ1)
        val belief = game.createBelief(BeliefType.Founder, "[+30 Science] from every [3] global followers [in all cities]")
        religion.addBeliefs(listOf(belief))
        val civ2 = game.addCiv()
        val tile = game.getTile(Vector2.Zero)
        val cityOfCiv2 = game.addCity(civ2, tile, initialPopulation = 9) // Need people to be converted
        cityOfCiv2.religion.addPressure(religion.name, 1000000000) // To completely overwhelm the default atheism in a city

        civ1.updateStatsForNextTurn()

        Assert.assertTrue(civ1.stats.statsForNextTurn.science == 90f)
    }

    // endregion

    // region stat percentage bonus providing uniques

    @Test
    fun statPercentBonus() {
        val civ = game.addCiv()
        val tile = game.getTile(Vector2.Zero)
        val city = game.addCity(civ, tile, true)
        val building = game.createBuilding("[+10 Science]", "[+200]% [Science]")
        city.cityConstructions.addBuilding(building)
        city.cityStats.update()

        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.science == 30f)
    }

    @Test
    fun statPercentBonusCities() {
        val civ = game.addCiv("[+200]% [Science] [in all cities]")
        val tile = game.getTile(Vector2.Zero)
        val city = game.addCity(civ, tile, true)
        val building = game.createBuilding("[+10 Science]")
        city.cityConstructions.addBuilding(building)
        city.cityStats.update()

        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.science == 30f)
    }

    @Test
    fun statPercentFromObject() {
        game.makeHexagonalMap(1)
        val emptyBuilding = game.createBuilding()
        val civInfo = game.addCiv(
                "[+3 Faith] from every [Farm]",
                "[+200]% [Faith] from every [${emptyBuilding.name}]",
                "[+200]% [Faith] from every [Farm]",
            )
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true)
        val faithBuilding = game.createBuilding()
        faithBuilding.faith = 3f
        city.cityConstructions.addBuilding(faithBuilding)

        val tile2 = game.setTileTerrain(Vector2(0f,1f), Constants.grassland)
        tile2.setImprovement("Farm")
        Assert.assertTrue(tile2.stats.getTileStats(city, civInfo).faith == 9f)

        city.cityConstructions.addBuilding(emptyBuilding)
        city.cityStats.update()

        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.faith == 9f)
    }

    @Test
    fun allStatsPercentFromObject() {
        game.makeHexagonalMap(1)
        val emptyBuilding = game.createBuilding()
        val civInfo = game.addCiv(
                "[+3 Faith] from every [Farm]",
                "[+200]% Yield from every [${emptyBuilding.name}]",
                "[+200]% Yield from every [Farm]",
            )
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true)
        val faithBuilding = game.createBuilding()
        faithBuilding.faith = 3f
        city.cityConstructions.addBuilding(faithBuilding)

        val tile2 = game.setTileTerrain(Vector2(0f,1f), Constants.grassland)
        tile2.setImprovement("Farm")
        Assert.assertTrue(tile2.stats.getTileStats(city, civInfo).faith == 9f)

        city.cityConstructions.addBuilding(emptyBuilding)
        city.cityStats.update()

        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.faith == 9f)
    }

    @Test
    fun statPercentFromReligionFollowers() {
        game.makeHexagonalMap(1)
        val civInfo = game.addCiv()
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true, 1)
        val religion = game.addReligion(civInfo)
        val belief = game.createBelief(BeliefType.Follower, "[+10]% [Faith] from every follower, up to [42]%")
        religion.addBeliefs(listOf(belief))

        city.religion.addPressure(religion.name, 1000000000)

        Assert.assertTrue(city.religion.getMajorityReligionName() == religion.name)

        city.cityStats.update()
        Assert.assertTrue(city.cityStats.statPercentBonusTree.totalStats.faith == 10f)

        city.population.setPopulation(10)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.statPercentBonusTree.totalStats.faith == 42f)
    }

    @Test
    fun bonusStatsFromCityStates() {
        game.makeHexagonalMap(1)
        val civInfo = game.addCiv()
        val cityState = game.addCiv(cityStateType = "Maritime")

        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true)
        val cityStateTile = game.getTile(Vector2(0f, 1f))
        @Suppress("UNUSED_VARIABLE")
        val cityStateCity = game.addCity(cityState, cityStateTile, true)
        civInfo.diplomacyFunctions.makeCivilizationsMeet(cityState)
        cityState.getDiplomacyManager(civInfo)!!.addInfluence(100f)

        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList[Constants.cityStates]!!.food == 3f)

        val building = game.createBuilding("[+100]% [Food] from City-States")
        city.cityConstructions.addBuilding(building)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList[Constants.cityStates]!!.food == 6f)
    }

    @Test
    fun statPercentFromTradeRoutes() {
        game.makeHexagonalMap(3)
        val civInfo = game.addCiv("[+30 Science] from each Trade Route", "[+100]% [Science] from Trade Routes")
        civInfo.tech.addTechnology("The Wheel") // Required to form trade routes
        val tile1 = game.getTile(Vector2.Zero)
        val tile2 =  game.getTile(Vector2(0f, 2f))
        tile1.roadStatus = RoadStatus.Road
        tile2.roadStatus = RoadStatus.Road
        @Suppress("UNUSED_VARIABLE")
        val city1 = game.addCity(civInfo, tile1)
        val city2 = game.addCity(civInfo, tile2)
        val inBetweenTile =  game.getTile(Vector2(0f, 1f))
        inBetweenTile.roadStatus = RoadStatus.Road

        civInfo.cache.updateCitiesConnectedToCapital()
        Assert.assertTrue(city2.cityStats.isConnectedToCapital(RoadStatus.Road))

        city2.cityStats.update()
        Assert.assertTrue(city2.cityStats.finalStatList["Trade routes"]!!.science == 60f)
    }


    // endregion

    // region stat nullifying uniques

    @Test
    fun nullifiesStat() {
        game.makeHexagonalMap(1)
        val civInfo = game.addCiv()
        val tile = game.getTile(Vector2.Zero)
        val city = game.addCity(civInfo, tile, true, 1)

        val building = game.createBuilding("Nullifies [Faith] [in this city]", "[+10 Gold, +10 Faith] [in this city]")
        city.cityConstructions.addBuilding(building)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList.map { it.value.gold }.sum() >= 10f)
        Assert.assertTrue(city.cityStats.finalStatList.map { it.value.faith }.sum() == 0f)
    }

    @Test
    fun nullifiesGrowth() {
        game.makeHexagonalMap(1)
        val civInfo = game.addCiv()
        val tile = game.getTile(Vector2.Zero)
        val city = game.addCity(civInfo, tile, true, 1)

        val building = game.createBuilding("Nullifies Growth [in this city]", "[+10 Food, +10 Gold] [in this city]")
        city.cityConstructions.addBuilding(building)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList.map { it.value.gold }.sum() >= 10f)
        Assert.assertTrue(city.cityStats.finalStatList.map { it.value.food }.sum() == 0f)

        city.population.addPopulation(1)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList.map { it.value.food }.sum() == 0f)
    }

    // endregion

    //region production percentage bonus providing uniques based on production

    @Test
    fun percentProductionBuildings() {
        val civInfo = game.addCiv()
        val tile = game.getTile(Vector2.Zero)
        val city = game.addCity(civInfo, tile, true, 0)

        val buildingToConstruct = game.createBuilding()
        val building = game.createBuilding("[+300]% Production when constructing [${buildingToConstruct.name}] buildings [in all cities]", "[+1 Production]")
        city.cityConstructions.addBuilding(building)
        city.cityConstructions.addToQueue(buildingToConstruct.name)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.statPercentBonusTree.totalStats.production == 300f)
    }

    @Test
    fun percentProductionUnits() {
        val civInfo = game.addCiv()
        val tile = game.getTile(Vector2.Zero)
        val city = game.addCity(civInfo, tile, true, 0)

        val unitToConstruct = game.createBaseUnit()
        val building = game.createBuilding("[+300]% Production when constructing [${unitToConstruct.name}] units [in all cities]", "[+1 Production]")
        city.cityConstructions.addBuilding(building)
        city.cityConstructions.addToQueue(unitToConstruct.name)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.statPercentBonusTree.totalStats.production == 300f)
    }

    @Test
    fun percentProductionWonders() {
        val civInfo = game.addCiv()
        val tile = game.getTile(Vector2.Zero)
        val city = game.addCity(civInfo, tile, true, 0)

        val buildingToConstruct = game.createBuilding()
        val building = game.createBuilding("[+300]% Production when constructing [${buildingToConstruct.name}] wonders [in all cities]", "[+1 Production]")
        city.cityConstructions.addBuilding(building)
        city.cityConstructions.addToQueue(buildingToConstruct.name)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.statPercentBonusTree.totalStats.production == 0f)

        buildingToConstruct.isWonder = true
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.statPercentBonusTree.totalStats.production == 300f)
    }

    @Test
    fun percentProductionBuildingsInCapital() {
        game.makeHexagonalMap(3)
        val civInfo = game.addCiv("[+300]% Production towards any buildings that already exist in the Capital")
        val tile = game.getTile(Vector2(0f,2f))
        val city = game.addCity(civInfo, tile, true, 0)
        val city2 = game.addCity(civInfo, game.getTile(Vector2(0f, -2f)), initialPopulation = 0)

        val buildingToConstruct = game.createBuilding()
        city2.cityConstructions.addToQueue(buildingToConstruct.name)
        city2.cityStats.update()
        Assert.assertTrue(city2.cityStats.statPercentBonusTree.totalStats.production == 0f)

        city.cityConstructions.addBuilding(buildingToConstruct)
        city2.cityStats.update()
        Assert.assertTrue(city2.cityStats.statPercentBonusTree.totalStats.production == 300f)
    }

    //endregion

    // region Other Global Uniques

    // region growth

    @Test
    fun growthPercentBonusTest() {
        val civInfo = game.addCiv()
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true)
        // City has 2 food from center -2 from pop, so total of 0
        val building = game.createBuilding("[+100]% growth [in all cities]", "[+2 Food]")
        city.cityConstructions.addBuilding(building)

        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList["[Buildings] ([Growth])"]!!.equals(Stats(food=2f)))
    }

    @Test
    fun carryOverFoodTest() {
        val civInfo = game.addCiv("[50]% Food is carried over after population increases [in all cities]")
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true)

        val foodNecessary = city.population.getFoodToNextPopulation()
        city.population.nextTurn(foodNecessary)

        Assert.assertTrue(city.population.foodStored == (foodNecessary * 0.5f).toInt())
    }


    @Test
    fun foodConsumptionBySpecialistsTest() {
        val civInfo = game.addCiv("[-50]% Food consumption by specialists [in all cities]")
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true, initialPopulation = 1)

        val building = game.createBuilding()
        val specialistName = game.createSpecialist()
        building.specialistSlots.add(specialistName, 1)
        city.population.specialistAllocations[specialistName] = 1

        city.cityStats.update()
        print(city.cityStats.finalStatList)
        Assert.assertTrue(city.cityStats.finalStatList["Population"]!!.food == -1f)
    }

    // endregion growth

    // region happiness

    @Test
    fun unhappinessFromCitiesPercentageTest() {
        val civInfo = game.addCiv("[+100]% unhappiness from the number of cities")
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true)

        city.cityStats.update()
        println(city.cityStats.happinessList)
        Assert.assertTrue(abs(city.cityStats.happinessList["Cities"]!! - -6f) < epsilon) // Float rounding errors
    }

    @Test
    fun unhappinessFromPopulationTypePercentageChangeTest() {
        val civInfo = game.addCiv("[-50]% Unhappiness from [Population] [in all cities]")
        val city = game.addCity(civInfo, game.getTile(Vector2.Zero), true, initialPopulation = 4)

        city.cityStats.update()
        println(city.cityStats.happinessList)
        Assert.assertTrue(abs(city.cityStats.happinessList["Population"]!! - -2f) < epsilon)

        val building = game.createBuilding("[-50]% Unhappiness from [Specialists] [in all cities]")
        val specialist = game.createSpecialist()
        building.specialistSlots[specialist] = 2
        city.population.specialistAllocations[specialist] = 2
        city.cityConstructions.addBuilding(building)

        city.cityStats.update()
        println(city.cityStats.happinessList)
        Assert.assertTrue(abs(city.cityStats.happinessList["Population"]!! - -1f) < epsilon)
    }


    // endregion happiness

    // region Conditionals

    @Test
    fun conditionalDifficulty() {
        val civInfo = game.addCiv()
        val tile = game.getTile(Vector2.Zero)
        val city = game.addCity(civInfo, tile, true)

        val tests = listOf(
            "<on [Settler] difficulty>" to 0,
            "<on [Chieftain] difficulty>" to 0,
            "<on [Prince] difficulty>" to 1, // TestGame is Prince
            "<on [King] difficulty>" to 0,
            "<on [Emperor] difficulty>" to 0,
            "<on [Settler] difficulty or higher>" to 0,
            "<on [Chieftain] difficulty or higher>" to 0,
            "<on [Prince] difficulty or higher>" to 1,
            "<on [King] difficulty or higher>" to 1,
            "<on [Emperor] difficulty or higher>" to 1,
            "<on [Not Found] difficulty>" to 0,
            "<on [Not Found] difficulty or higher>" to 0,
        )

        Assert.assertEquals(civInfo.gold, 0)
        for ((test, expected) in tests) {
            val building = game.createBuilding("Gain [1] [Gold] $test")
            city.cityConstructions.addBuilding(building)
            Assert.assertEquals("Conditional `$test` should be: $expected", civInfo.gold, expected)
            civInfo.addGold(-civInfo.gold) // Reset the gold
        }
    }

    // endregion

    // region Great Persons
    @Test
    fun statsSpendingGreatPeople() {
        val civInfo = game.addCiv()
        val tile = game.getTile(Vector2.Zero)
        val city = game.addCity(civInfo, tile, true)
        val unit = game.addUnit("Great Engineer", civInfo, tile)
        val building = game.createBuilding("Gain [250] [Gold] <upon expending a [Great Person] unit>")
        city.cityConstructions.addBuilding(building)

        civInfo.addGold(-civInfo.gold) // reset gold just to be sure

        unit.consume()
        Assert.assertTrue(civInfo.gold == 250)
    }
    // endregion

    // region Gain control over tiles

    @Test
    fun takeOverTilesInCity() {
        game.makeHexagonalMap(5)
        val civInfo = game.addCiv()
        val tile = game.getTile(Vector2.Zero)
        val city = game.addCity(civInfo, tile, true)
        Assert.assertEquals(7, city.getTiles().count())
        val building = game.createBuilding("Gain control over [8] tiles [in this city]")
        city.cityConstructions.addBuilding(building)
        Assert.assertEquals(15, city.getTiles().count())
    }

    // endregion

    // region Mark [tagTarget] as [tag]

    @Test
    fun markTargetAsTag() {
        val civInfo = game.addCiv()
        val tile = game.getTile(Vector2.Zero)
        val city = game.addCity(civInfo, tile, true)
        city.cityConstructions.addBuilding(game.createBuilding("Mark [Nation] as [Communist]"))
        val building = game.createBuilding("Gain [100] [Gold] <upon expending a [Great Person] unit> <for [Communist] Civilizations>")
        city.cityConstructions.addBuilding(building)

        civInfo.addGold(-civInfo.gold) // Reset the gold
        Assert.assertEquals(0, civInfo.gold)

        // Expend the unit
        game.addUnit("Great Engineer", civInfo, tile).consume()
        Assert.assertEquals(100, civInfo.gold)

        // Remove the tag
        city.cityConstructions.addBuilding(game.createBuilding("Mark [Nation] as not [Communist]"))
        game.addUnit("Great Engineer", civInfo, tile).consume()
        Assert.assertEquals(100, civInfo.gold)
    }

    // endregion

    // endregion

}
