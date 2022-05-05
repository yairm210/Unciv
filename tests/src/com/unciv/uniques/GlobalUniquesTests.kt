//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.models.ruleset.unique.UniqueType
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


    @Test
    /** tests [UniqueType.Stats] */
    fun stats() {
        val civInfo = game.addCiv()
        val tile = game.addTile(Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val buildingName = game.createBuildingWithUnique("[+1 Food]").name
        
        cityInfo.cityConstructions.addBuilding(buildingName)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.equals(Stats(food=1f)))
    }
    
    @Test
    fun statsPerCity() {
        val civInfo = game.addCiv()
        val tile = game.addTile(Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val buildingName = game.createBuildingWithUnique("[+1 Production] [in this city]").name

        cityInfo.cityConstructions.addBuilding(buildingName)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.equals(Stats(production=1f)))
    }
    
    @Test
    fun statsPerSpecialist() {
        val civInfo = game.addCiv()
        val tile = game.addTile(Constants.desert)
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
        val tile = game.addTile(Constants.desert)
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
        val tile = game.addTile(Constants.desert)
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
        val tile = game.addTile(Constants.desert)
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
        val tile = game.addTile(Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val unit = game.addUnit("Great Prophet", civInfo, tile)
        val building = game.createBuildingWithUnique("[+250 Gold] whenever a Great Person is expended")
        cityInfo.cityConstructions.addBuilding(building.name)
        civInfo.addGold(-civInfo.gold)
        
        UnitActions.getFoundReligionAction(unit)()
        Assert.assertTrue(civInfo.gold == 250)
    }
    
    @Test
    fun statsFromTiles() {
        val civInfo = game.addCiv()
        val tile = game.addTile(Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val building = game.createBuildingWithUnique("[+4 Gold] from [${Constants.grassland}] tiles [in all cities]")
        cityInfo.cityConstructions.addBuilding(building.name)
        
        val tile2 = game.addTile(Constants.grassland, Vector2(0f,1f))
        Assert.assertTrue(tile2.getTileStats(cityInfo, civInfo).gold == 4f)
    }
    
    @Test
    fun statsFromTilesWithout() {
        val civInfo = game.addCiv()
        val tile = game.addTile(Constants.desert)
        val cityInfo = game.addCity(civInfo, tile, true)
        val building = game.createBuildingWithUnique("[+4 Gold] from [${Constants.grassland}] tiles without [${Constants.forest}] [in all cities]")
        cityInfo.cityConstructions.addBuilding(building.name)

        val tile2 = game.addTile(Constants.grassland, Vector2(0f,1f))
        Assert.assertTrue(tile2.getTileStats(cityInfo, civInfo).gold == 4f)
        
        val tile3 = game.addTile(Constants.grassland, Vector2(0f, 2f), listOf(Constants.forest))
        Assert.assertFalse(tile3.getTileStats(cityInfo, civInfo).gold == 4f)
    }
    
    @Test
    fun statsFromObject() {
        val civInfo = game.addCiv()
        val tile = game.addTile(Constants.desert)
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
        
        val tile2 = game.addTile(Constants.grassland, Vector2(1f, 0f))
        Assert.assertTrue(tile2.getTileStats(cityInfo, civInfo).faith == 3f)
        
        cityInfo.cityConstructions.removeBuilding(building2.name)
        
        val emptyBuilding = game.createBuildingWithUniques()
        
        val building3 = game.createBuildingWithUnique("[+3 Faith] from every [${emptyBuilding.name}]")
        cityInfo.cityConstructions.addBuilding(emptyBuilding.name)
        cityInfo.cityConstructions.addBuilding(building3.name)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.faith == 3f)
    }
}
