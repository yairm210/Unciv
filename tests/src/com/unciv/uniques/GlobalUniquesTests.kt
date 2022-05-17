//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.map.RoadStatus
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.stats.Stats
import com.unciv.testing.GdxTestRunner
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
    
    // region stat uniques

    @Test
    fun stats() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val buildingName = game.createBuildingWithUnique("[+1 Food]").name
        
        cityInfo.cityConstructions.addBuilding(buildingName)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.equals(Stats(food=1f)))
    }
    
    @Test
    fun statsPerCity() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val buildingName = game.createBuildingWithUnique("[+1 Production] [in this city]").name

        cityInfo.cityConstructions.addBuilding(buildingName)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.equals(Stats(production=1f)))
    }
    
    @Test
    fun statsPerSpecialist() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val building = game.createBuildingWithUnique("[+3 Gold] from every specialist [in this city]")
        val specialistName = game.addEmptySpecialist()
        building.specialistSlots.add(specialistName,2)
        cityInfo.population.addPopulation(2)
        cityInfo.population.specialistAllocations[specialistName] = 2

        cityInfo.cityConstructions.addBuilding(building.name)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Specialists"]!!.equals(Stats(gold=6f)))
    }
    
    @Test
    fun statsPerPopulation() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val building = game.createBuildingWithUnique("[+3 Gold] per [2] population [in this city]")
        cityInfo.population.addPopulation(4)

        cityInfo.cityConstructions.addBuilding(building.name)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 6f)
    }
    
    @Test
    fun statsPerXPopulation() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val building = game.createBuildingWithUnique("[+3 Gold] in cities with [3] or more population")
        
        cityInfo.population.addPopulation(2)
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
        val building = game.createBuildingWithUnique("[+3 Gold] in cities on [${Constants.desert}] tiles")
        cityInfo.cityConstructions.addBuilding(building.name)
        
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 3f)
        tile.baseTerrain = Constants.grassland
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 0f)
    }
    
    @Test
    fun statsSpendingGreatPeople() {
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val unit = game.addUnit("Great Engineer", civInfo, tile)
        val building = game.createBuildingWithUnique("[+250 Gold] whenever a Great Person is expended")
        cityInfo.cityConstructions.addBuilding(building.name)
        civInfo.addGold(-civInfo.gold)

        civInfo.addGold(-civInfo.gold) // reset gold just to be sure
        unit.consume()
        Assert.assertTrue(civInfo.gold == 250)
    }
    
    @Test
    fun statsFromTiles() {
        game.makeHexagonalMap(2)
        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val building = game.createBuildingWithUnique("[+4 Gold] from [${Constants.grassland}] tiles [in all cities]")
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
        val building = game.createBuildingWithUnique("[+4 Gold] from [${Constants.grassland}] tiles without [${Constants.forest}] [in this city]")
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
        val cityInfo = game.addCity(civInfo, tile, true)
        val specialist = game.addEmptySpecialist()
        val building = game.createBuildingWithUnique("[+3 Faith] from every [${specialist}]")
        
        cityInfo.cityConstructions.addBuilding(building.name)
        cityInfo.population.addPopulation(2)
        cityInfo.population.specialistAllocations[specialist] = 2
        
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Specialists"]!!.faith == 6f)

        cityInfo.cityConstructions.removeBuilding(building.name)
        val building2 = game.createBuildingWithUnique("[+3 Faith] from every [${Constants.grassland}]")
        cityInfo.cityConstructions.addBuilding(building2.name)

        val tile2 = game.setTileFeatures(Vector2(0f,1f), Constants.grassland)
        Assert.assertTrue(tile2.getTileStats(cityInfo, civInfo).faith == 3f)
        
        cityInfo.cityConstructions.removeBuilding(building2.name)
        
        val emptyBuilding = game.createBuildingWithUniques()
        
        val building3 = game.createBuildingWithUnique("[+3 Faith] from every [${emptyBuilding.name}]")
        cityInfo.cityConstructions.addBuilding(emptyBuilding.name)
        cityInfo.cityConstructions.addBuilding(building3.name)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.faith == 3f)
    }
    
    @Test
    fun statsFromTradeRoute() {
        game.makeHexagonalMap(3)
        val civInfo = game.addCiv(uniques = listOf("[+30 Science] from each Trade Route"))
        civInfo.tech.addTechnology("The Wheel") // Required to form trade routes
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val tile2 = game.setTileFeatures(Vector2(0f,2f), Constants.desert)
        tile.roadStatus = RoadStatus.Road
        tile2.roadStatus = RoadStatus.Road
        val cityInfo = game.addCity(civInfo, tile)
        val city2 = game.addCity(civInfo, tile2)
        val inBetweenTile = game.setTileFeatures(Vector2(0f, 1f), Constants.desert)
        inBetweenTile.roadStatus = RoadStatus.Road
        civInfo.transients().updateCitiesConnectedToCapital()
        city2.cityStats.update()
        println(city2.isConnectedToCapital())
        println(city2.cityStats.finalStatList)
        
        Assert.assertTrue(city2.cityStats.finalStatList["Trade routes"]!!.science == 30f)
    }
    
    @Test
    fun statsFromGlobalCitiesFollowingReligion() {
        val civ1 = game.addCiv()
        val religion = game.addReligion(civ1)
        val belief = game.addBelief(BeliefType.Founder, "[+30 Science] for each global city following this religion")
        religion.founderBeliefs.add(belief.name)
        val civ2 = game.addCiv()
        val tile = game.getTile(Vector2(0f,0f))
        val cityOfCiv2 = game.addCity(civ2, tile)
        cityOfCiv2.population.setPopulation(1) // Need someone to be converted
        cityOfCiv2.religion.addPressure(religion.name, 1000)
        
        Assert.assertTrue(cityOfCiv2.religion.getMajorityReligionName() == religion.name)
        
        civ1.updateStatsForNextTurn()
        
        Assert.assertTrue(civ1.statsForNextTurn.science == 30f)
    }
    
    @Test
    fun statsFromGlobalFollowers() {
        val civ1 = game.addCiv()
        val religion = game.addReligion(civ1)
        val belief = game.addBelief(BeliefType.Founder, "[+30 Science] from every [3] global followers [in all cities]")
        religion.founderBeliefs.add(belief.name)
        val civ2 = game.addCiv()
        val tile = game.getTile(Vector2(0f,0f))
        val cityOfCiv2 = game.addCity(civ2, tile)
        cityOfCiv2.population.setPopulation(9) // Need people to be converted
        cityOfCiv2.religion.addPressure(religion.name, 1000000000) // To completely overwhelm the default atheism in a city

        civ1.updateStatsForNextTurn()
        
        println(civ1.statsForNextTurn.science)

        Assert.assertTrue(civ1.statsForNextTurn.science == 90f)
    }
    
    // endregion
    
    // region stat percentage bonus providing uniques
    
    @Test
    fun statPercentBonus() {
        val civ = game.addCiv()
        val tile = game.getTile(Vector2(0f, 0f))
        val city = game.addCity(civ, tile, true)
        val building = game.createBuildingWithUniques(arrayListOf("[+10 Science]", "[+200]% [Science]"))
        city.cityConstructions.addBuilding(building.name)
        city.cityStats.update()
        
        println(city.cityStats.finalStatList)
        
        Assert.assertTrue(city.cityStats.finalStatList["Buildings"]!!.science == 30f)
    }
    
    
    // endregion
}
