//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.civilization.CityStateType
import com.unciv.logic.map.RoadStatus
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.stats.Stats
import com.unciv.testing.GdxTestRunner
import com.unciv.ui.worldscreen.unit.UnitActions
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class GlobalUniquesTests {

    private lateinit var game: TestGame

    @Before
    fun initTheWorld() {
        game = TestGame()
    }

    // region base stat bonus providing uniques

    @Test
    fun stats() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val buildingName = game.createBuilding("[+1 Food]").name

        cityInfo.cityConstructions.addBuilding(buildingName)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.equals(Stats(food=1f)))
    }

    @Test
    fun statsPerCity() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val buildingName = game.createBuilding("[+1 Production] [in this city]").name

        cityInfo.cityConstructions.addBuilding(buildingName)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.equals(Stats(production=1f)))
    }

    @Test
    fun statsPerSpecialist() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true, initialPopulation = 2)
        val building = game.createBuilding("[+3 Gold] from every specialist [in this city]")
        val specialistName = game.createSpecialist()
        building.specialistSlots.add(specialistName, 2)
        cityInfo.population.specialistAllocations[specialistName] = 2

        cityInfo.cityConstructions.addBuilding(building.name)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Specialists"]!!.equals(Stats(gold=6f)))
    }

    @Test
    fun statsPerPopulation() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true, initialPopulation = 4)
        val building = game.createBuilding("[+3 Gold] per [2] population [in this city]")

        cityInfo.cityConstructions.addBuilding(building.name)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 6f)
    }

    @Test
    fun statsPerXPopulation() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true, initialPopulation = 2)
        val building = game.createBuilding("[+3 Gold] <in cities with at least [3] [Population]>")

        cityInfo.cityConstructions.addBuilding(building.name)

        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 0f)
        cityInfo.population.setPopulation(5)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 3f)
    }

    @Test
    fun statsFromCitiesOnSpecificTiles() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val building = game.createBuilding("[+3 Gold] in cities on [${Constants.desert}] tiles")
        cityInfo.cityConstructions.addBuilding(building.name)

        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 3f)
        tile.baseTerrain = Constants.grassland
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 0f)
    }

    @Test
    fun statsFromTiles() {
        game.makeHexagonalMap(2)
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val building = game.createBuilding("[+4 Gold] from [${Constants.grassland}] tiles [in all cities]")
        cityInfo.cityConstructions.addBuilding(building.name)

        val tile2 = game.setTileFeatures(Vector2(0f,1f), Constants.grassland)
        Assert.assertTrue(tile2.getTileStats(cityInfo, civInfo).gold == 4f)
    }

    @Test
    fun statsFromTilesWithout() {
        game.makeHexagonalMap(3)
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val building = game.createBuilding("[+4 Gold] from [${Constants.grassland}] tiles without [${Constants.forest}] [in this city]")
        cityInfo.cityConstructions.addBuilding(building.name)

        val tile2 = game.setTileFeatures(Vector2(0f,1f), Constants.grassland)
        game.addTileToCity(cityInfo, tile2)
        Assert.assertTrue(tile2.getTileStats(cityInfo, civInfo).gold == 4f)

        val tile3 = game.setTileFeatures(Vector2(0f, 2f), Constants.grassland, listOf(Constants.forest))
        game.addTileToCity(cityInfo, tile3)
        Assert.assertFalse(tile3.getTileStats(cityInfo, civInfo).gold == 4f)
    }

    @Test
    fun statsFromObject() {
        game.makeHexagonalMap(1)
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true, initialPopulation = 2)
        val specialist = game.createSpecialist()
        val building = game.createBuilding("[+3 Faith] from every [${specialist}]")

        cityInfo.cityConstructions.addBuilding(building.name)
        cityInfo.population.specialistAllocations[specialist] = 2

        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Specialists"]!!.faith == 6f)

        cityInfo.cityConstructions.removeBuilding(building.name)
        val building2 = game.createBuilding("[+3 Faith] from every [${Constants.grassland}]")
        cityInfo.cityConstructions.addBuilding(building2.name)

        val tile2 = game.setTileFeatures(Vector2(0f,1f), Constants.grassland)
        Assert.assertTrue(tile2.getTileStats(cityInfo, civInfo).faith == 3f)

        cityInfo.cityConstructions.removeBuilding(building2.name)

        val emptyBuilding = game.createBuilding()

        val building3 = game.createBuilding("[+3 Faith] from every [${emptyBuilding.name}]")
        cityInfo.cityConstructions.addBuilding(emptyBuilding.name)
        cityInfo.cityConstructions.addBuilding(building3.name)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.faith == 3f)
    }

    @Test
    fun statsFromTradeRoute() {
        game.makeHexagonalMap(3)
        val civInfo = game.addCiv("[+30 Science] from each Trade Route")
        civInfo.tech.addTechnology("The Wheel") // Required to form trade routes
        val tile1 = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val tile2 = game.setTileFeatures(Vector2(0f,2f), Constants.desert)
        tile1.roadStatus = RoadStatus.Road
        tile2.roadStatus = RoadStatus.Road
        @Suppress("UNUSED_VARIABLE")
        val city1 = game.addCity(civInfo, tile1)
        val city2 = game.addCity(civInfo, tile2)
        val inBetweenTile = game.setTileFeatures(Vector2(0f, 1f), Constants.desert)
        inBetweenTile.roadStatus = RoadStatus.Road
        civInfo.transients().updateCitiesConnectedToCapital()
        city2.cityStats.update()

        Assert.assertTrue(city2.cityStats.finalStatList["Trade routes"]!!.science == 30f)
    }

    @Test
    fun statsFromGlobalCitiesFollowingReligion() {
        val civ1 = game.addCiv()
        val religion = game.addReligion(civ1)
        val belief = game.createBelief(BeliefType.Founder, "[+30 Science] for each global city following this religion")
        religion.founderBeliefs.add(belief.name)
        val civ2 = game.addCiv()
        val tile = game.getTile(Vector2(0f,0f))
        val cityOfCiv2 = game.addCity(civ2, tile, initialPopulation = 1) // Need someone to be converted
        cityOfCiv2.religion.addPressure(religion.name, 1000)

        Assert.assertTrue(cityOfCiv2.religion.getMajorityReligionName() == religion.name)

        civ1.updateStatsForNextTurn()

        Assert.assertTrue(civ1.statsForNextTurn.science == 30f)
    }

    @Test
    fun happinessFromGlobalCitiesFollowingReligion() {
        val civ1 = game.addCiv()
        val religion = game.addReligion(civ1)
        val belief = game.createBelief(BeliefType.Founder, "[+42 Happiness] for each global city following this religion")
        religion.founderBeliefs.add(belief.name)
        val civ2 = game.addCiv()
        val tile = game.getTile(Vector2(0f,0f))
        val cityOfCiv2 = game.addCity(civ2, tile, initialPopulation = 1) // Need someone to be converted
        cityOfCiv2.religion.addPressure(religion.name, 1000)

        civ1.updateStatsForNextTurn()

        val baseHappiness = civ1.getDifficulty().baseHappiness
        // Since civ1 has no cities, there are no other happiness sources
        Assert.assertTrue(civ1.happinessForNextTurn == baseHappiness + 42)
    }

    @Test
    fun statsFromGlobalFollowers() {
        val civ1 = game.addCiv()
        val religion = game.addReligion(civ1)
        val belief = game.createBelief(BeliefType.Founder, "[+30 Science] from every [3] global followers [in all cities]")
        religion.founderBeliefs.add(belief.name)
        val civ2 = game.addCiv()
        val tile = game.getTile(Vector2(0f,0f))
        val cityOfCiv2 = game.addCity(civ2, tile, initialPopulation = 9) // Need people to be converted
        cityOfCiv2.religion.addPressure(religion.name, 1000000000) // To completely overwhelm the default atheism in a city

        civ1.updateStatsForNextTurn()

        Assert.assertTrue(civ1.statsForNextTurn.science == 90f)
    }

    // endregion

    // region stat percentage bonus providing uniques

    @Test
    fun statPercentBonus() {
        val civ = game.addCiv()
        val tile = game.getTile(Vector2(0f, 0f))
        val city = game.addCity(civ, tile, true)
        val building = game.createBuilding("[+10 Science]", "[+200]% [Science]")
        city.cityConstructions.addBuilding(building.name)
        city.cityStats.update()

        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.science == 30f)
    }

    @Test
    fun statPercentBonusCities() {
        val civ = game.addCiv("[+200]% [Science] [in all cities]")
        val tile = game.getTile(Vector2(0f, 0f))
        val city = game.addCity(civ, tile, true)
        val building = game.createBuilding("[+10 Science]")
        city.cityConstructions.addBuilding(building.name)
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
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val city = game.addCity(civInfo, tile, true)
        val faithBuilding = game.createBuilding()
        faithBuilding.faith = 3f
        city.cityConstructions.addBuilding(faithBuilding.name)

        val tile2 = game.setTileFeatures(Vector2(0f,1f), Constants.grassland)
        tile2.improvement = "Farm"
        Assert.assertTrue(tile2.getTileStats(city, civInfo).faith == 9f)

        city.cityConstructions.addBuilding(emptyBuilding.name)
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
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val city = game.addCity(civInfo, tile, true)
        val faithBuilding = game.createBuilding()
        faithBuilding.faith = 3f
        city.cityConstructions.addBuilding(faithBuilding.name)

        val tile2 = game.setTileFeatures(Vector2(0f,1f), Constants.grassland)
        tile2.improvement = "Farm"
        Assert.assertTrue(tile2.getTileStats(city, civInfo).faith == 9f)

        city.cityConstructions.addBuilding(emptyBuilding.name)
        city.cityStats.update()

        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.faith == 9f)
    }

    @Test
    fun statPercentFromReligionFollowers() {
        game.makeHexagonalMap(1)
        val civInfo = game.addCiv()
        val tile = game.getTile(Vector2(0f,0f))
        val city = game.addCity(civInfo, tile, true, 1)
        val religion = game.addReligion(civInfo)
        val belief = game.createBelief(BeliefType.Follower, "[+10]% [Faith] from every follower, up to [42]%")
        religion.followerBeliefs.add(belief.name)

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
        val cityState = game.addCiv(cityState = CityStateType.Maritime)

        val tile = game.getTile(Vector2(0f,0f))
        val city = game.addCity(civInfo, tile, true)
        val cityStateTile = game.getTile(Vector2(0f, 1f))
        @Suppress("UNUSED_VARIABLE")
        val cityStateCity = game.addCity(cityState, cityStateTile, true)
        civInfo.makeCivilizationsMeet(cityState)
        cityState.getDiplomacyManager(civInfo).addInfluence(100f)

        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList[Constants.cityStates]!!.food == 3f)

        val building = game.createBuilding("[+100]% [Food] from City-States")
        city.cityConstructions.addBuilding(building.name)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList[Constants.cityStates]!!.food == 6f)
    }

    @Test
    fun statPercentFromTradeRoutes() {
        game.makeHexagonalMap(3)
        val civInfo = game.addCiv("[+30 Science] from each Trade Route", "[+100]% [Science] from Trade Routes")
        civInfo.tech.addTechnology("The Wheel") // Required to form trade routes
        val tile1 = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val tile2 = game.setTileFeatures(Vector2(0f,2f), Constants.desert)
        tile1.roadStatus = RoadStatus.Road
        tile2.roadStatus = RoadStatus.Road
        @Suppress("UNUSED_VARIABLE")
        val city1 = game.addCity(civInfo, tile1)
        val city2 = game.addCity(civInfo, tile2)
        val inBetweenTile = game.setTileFeatures(Vector2(0f, 1f), Constants.desert)
        inBetweenTile.roadStatus = RoadStatus.Road

        civInfo.transients().updateCitiesConnectedToCapital()
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
        val tile = game.getTile(Vector2(0f,0f))
        val city = game.addCity(civInfo, tile, true, 1)

        val building = game.createBuilding("Nullifies [Faith] [in this city]", "[+10 Gold, +10 Faith] [in this city]")
        city.cityConstructions.addBuilding(building.name)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.finalStatList.map { it.value.gold }.sum() >= 10f)
        Assert.assertTrue(city.cityStats.finalStatList.map { it.value.faith }.sum() == 0f)
    }

    @Test
    fun nullifiesGrowth() {
        game.makeHexagonalMap(1)
        val civInfo = game.addCiv()
        val tile = game.getTile(Vector2(0f,0f))
        val city = game.addCity(civInfo, tile, true, 1)

        val building = game.createBuilding("Nullifies Growth [in this city]", "[+10 Food, +10 Gold] [in this city]")
        city.cityConstructions.addBuilding(building.name)
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
        val tile = game.getTile(Vector2(0f,0f))
        val city = game.addCity(civInfo, tile, true, 0)

        val buildingToConstruct = game.createBuilding()
        val building = game.createBuilding("[+300]% Production when constructing [${buildingToConstruct.name}] buildings [in all cities]", "[+1 Production]")
        city.cityConstructions.addBuilding(building.name)
        city.cityConstructions.addToQueue(buildingToConstruct.name)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.statPercentBonusTree.totalStats.production == 300f)
    }

    @Test
    fun percentProductionUnits() {
        val civInfo = game.addCiv()
        val tile = game.getTile(Vector2(0f,0f))
        val city = game.addCity(civInfo, tile, true, 0)

        val unitToConstruct = game.createBaseUnit()
        val building = game.createBuilding("[+300]% Production when constructing [${unitToConstruct.name}] units [in all cities]", "[+1 Production]")
        city.cityConstructions.addBuilding(building.name)
        city.cityConstructions.addToQueue(unitToConstruct.name)
        city.cityStats.update()
        Assert.assertTrue(city.cityStats.statPercentBonusTree.totalStats.production == 300f)
    }

    @Test
    fun percentProductionWonders() {
        val civInfo = game.addCiv()
        val tile = game.getTile(Vector2(0f,0f))
        val city = game.addCity(civInfo, tile, true, 0)

        val buildingToConstruct = game.createBuilding()
        val building = game.createBuilding("[+300]% Production when constructing [${buildingToConstruct.name}] wonders [in all cities]", "[+1 Production]")
        city.cityConstructions.addBuilding(building.name)
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

        city.cityConstructions.addBuilding(buildingToConstruct.name)
        city2.cityStats.update()
        Assert.assertTrue(city2.cityStats.statPercentBonusTree.totalStats.production == 300f)
    }

    //endregion

    @Test
    fun statsSpendingGreatPeople() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val unit = game.addUnit("Great Engineer", civInfo, tile)
        val building = game.createBuilding("[+250 Gold] whenever a Great Person is expended")
        cityInfo.cityConstructions.addBuilding(building.name)

        civInfo.addGold(-civInfo.gold) // reset gold just to be sure

        unit.consume()
        Assert.assertTrue(civInfo.gold == 250)
    }

    @Test
    fun pillageYieldTest() {
        game.makeHexagonalMap(2)
        val civInfo = game.addCiv()

        val tile = game.setTileFeatures(Vector2(0f, 0f), Constants.grassland)
        val cityTile = game.setTileFeatures(Vector2(2f,0f), Constants.grassland)
        val cityInfo = game.addCity(civInfo, cityTile, true)
        cityInfo.population.foodStored = 0 // just to be sure
        civInfo.addGold(-civInfo.gold) // reset gold just to be sure

        val testImprovement = game.createTileImprovement("Pillaging this improvement yields [+20 Gold, +11 Food]")
        tile.improvement = testImprovement.name
        val unit = game.addUnit("Warrior", civInfo, tile)
        unit.currentMovement = 2f

        val pillageAction = UnitActions.getPillageAction(unit)
        pillageAction?.action?.invoke()
        Assert.assertTrue("Pillaging should transfer gold to the civ", civInfo.gold == 20)
        Assert.assertTrue("Pillaging should transfer food to the nearest city", cityInfo.population.foodStored == 11)
    }

}
