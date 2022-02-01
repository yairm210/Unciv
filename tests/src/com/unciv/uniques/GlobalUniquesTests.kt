//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.*
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

    private var ruleSet = Ruleset()
    private var gameInfo = GameInfo()
    private var tileMap = TileMap()

    companion object {
        var objectsCreated = 0
    }
    
    @Before
    fun initTheWorld() {
        // Set UncivGame.Current so that debug variables are initialized
        UncivGame.Current = UncivGame("Test")
        // And the settings can be reached for the locale used in .tr()
        UncivGame.Current.settings = GameSettings()
        
        // Create a new ruleset we can easily edit, and set the important variables of gameInfo
        RulesetCache.loadRulesets()
        ruleSet = RulesetCache.getVanillaRuleset()
        gameInfo.ruleSet = ruleSet
        gameInfo.difficultyObject = ruleSet.difficulties["Prince"]!!

        // Create a tilemap, needed for city centers
        gameInfo.tileMap = tileMap
    }

    private fun addTile(terrain: String, position: Vector2 = Vector2.Zero, features: List<String> = listOf()): TileInfo {
        val tile = TileInfo()
        tile.ruleset = ruleSet
        tile.baseTerrain = terrain
        for (feature in features) {
            tile.terrainFeatures.add(feature)
        }
        tile.tileMap = tileMap
        tile.position = position
        tile.setTransients()
        while (tileMap.tileMatrix.size < position.x + 1)
            tileMap.tileMatrix.add(ArrayList())
        while (tileMap.tileMatrix[position.x.toInt()].size < position.y + 1)
            tileMap.tileMatrix[position.x.toInt()].add(null)
        tileMap.tileMatrix[position.x.toInt()][position.y.toInt()] = tile
        return tile
    }
    
    private fun addCiv(): CivilizationInfo {
        val nationName = "Nation-$objectsCreated"
        objectsCreated++
        ruleSet.nations[nationName] = Nation().apply {
            name = nationName
            cities = arrayListOf("The Capital")
        }
        val civInfo = CivilizationInfo()
        civInfo.nation = ruleSet.nations[nationName]!!
        civInfo.gameInfo = gameInfo
        civInfo.civName = nationName
        civInfo.setTransients()
        gameInfo.civilizations.add(civInfo)
        return civInfo
    }
    
    private fun addCity(civInfo: CivilizationInfo, tile: TileInfo, replacePalace: Boolean = false): CityInfo {
        val cityInfo = CityInfo(civInfo, tile.position)
        cityInfo.population.addPopulation(-1) // Remove population
        
        if (replacePalace && civInfo.cities.size == 1) {
            // Add a capital indicator without any other stats
            val palaceWithoutStats = createBuildingWithUnique(UniqueType.IndicatesCapital.text)
            cityInfo.cityConstructions.removeBuilding("Palace")
            cityInfo.cityConstructions.addBuilding(palaceWithoutStats.name)
        }
        return cityInfo
    }
    
    private fun addUnit(name: String, civInfo: CivilizationInfo, tile: TileInfo): MapUnit {
        val baseUnit = ruleSet.units[name]!!
        baseUnit.ruleset = ruleSet
        val mapUnit = baseUnit.getMapUnit(civInfo)
        mapUnit.putInTile(tile)
        return mapUnit
    }
    
    private fun addEmptySpecialist(): String {
        val name = "specialist-${objectsCreated}"
        objectsCreated++
        ruleSet.specialists[name] = Specialist()
        return name
    }
    
    private fun createBuildingWithUnique(unique: String): Building {
        return createBuildingWithUniques(arrayListOf(unique))
    }
    
    private fun createBuildingWithUniques(uniques: ArrayList<String> = arrayListOf()): Building {
        val building = Building()
        building.uniques = uniques
        building.name = "Building-${objectsCreated}"
        objectsCreated++
        ruleSet.buildings[building.name] = building
        return building
    }
    
    
    
    @Test
    /** tests [UniqueType.Stats] */
    fun stats() {
        val civInfo = addCiv()
        val tile = addTile(Constants.desert)
        val cityInfo = addCity(civInfo, tile, true)
        val buildingName = createBuildingWithUnique("[+1 Food]").name
        
        cityInfo.cityConstructions.addBuilding(buildingName)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.equals(Stats(food=1f)))
    }
    
    @Test
    fun statsPerCity() {
        val civInfo = addCiv()
        val tile = addTile(Constants.desert)
        val cityInfo = addCity(civInfo, tile, true)
        val buildingName = createBuildingWithUnique("[+1 Production] [in this city]").name

        cityInfo.cityConstructions.addBuilding(buildingName)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.equals(Stats(production=1f)))
    }
    
    @Test
    fun statsPerSpecialist() {
        val civInfo = addCiv()
        val tile = addTile(Constants.desert)
        val cityInfo = addCity(civInfo, tile, true)
        val building = createBuildingWithUnique("[+3 Gold] from every specialist [in this city]")
        val specialistName = addEmptySpecialist()
        building.specialistSlots.add(specialistName,2)
        cityInfo.population.addPopulation(2)
        cityInfo.population.specialistAllocations[specialistName] = 2

        cityInfo.cityConstructions.addBuilding(building.name)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Specialists"]!!.equals(Stats(gold=6f)))
    }
    
    @Test
    fun statsPerPopulation() {
        val civInfo = addCiv()
        val tile = addTile(Constants.desert)
        val cityInfo = addCity(civInfo, tile, true)
        val building = createBuildingWithUnique("[+3 Gold] per [2] population [in this city]")
        cityInfo.population.addPopulation(4)

        cityInfo.cityConstructions.addBuilding(building.name)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 6f)
    }
    
    @Test
    fun statsPerXPopulation() {
        val civInfo = addCiv()
        val tile = addTile(Constants.desert)
        val cityInfo = addCity(civInfo, tile, true)
        val building = createBuildingWithUnique("[+3 Gold] in cities with [3] or more population")
        
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
        val civInfo = addCiv()
        val tile = addTile(Constants.desert)
        val cityInfo = addCity(civInfo, tile, true)
        val building = createBuildingWithUnique("[+3 Gold] in cities on [${Constants.desert}] tiles")
        cityInfo.cityConstructions.addBuilding(building.name)
        
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 3f)
        tile.baseTerrain = Constants.grassland
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.gold == 0f)
    }
    
    @Test
    fun statsSpendingGreatPeople() {
        val civInfo = addCiv()
        val tile = addTile(Constants.desert)
        val cityInfo = addCity(civInfo, tile, true)
        val unit = addUnit("Great Prophet", civInfo, tile)
        val building = createBuildingWithUnique("[+250 Gold] whenever a Great Person is expended")
        cityInfo.cityConstructions.addBuilding(building.name)
        civInfo.addGold(-civInfo.gold)
        
        UnitActions.getFoundReligionAction(unit)()
        Assert.assertTrue(civInfo.gold == 250)
    }
    
    @Test
    fun statsFromTiles() {
        val civInfo = addCiv()
        val tile = addTile(Constants.desert)
        val cityInfo = addCity(civInfo, tile, true)
        val building = createBuildingWithUnique("[+4 Gold] from [${Constants.grassland}] tiles [in all cities]")
        cityInfo.cityConstructions.addBuilding(building.name)
        
        val tile2 = addTile(Constants.grassland, Vector2(0f,1f))
        Assert.assertTrue(tile2.getTileStats(cityInfo, civInfo).gold == 4f)
    }
    
    @Test
    fun statsFromTilesWithout() {
        val civInfo = addCiv()
        val tile = addTile(Constants.desert)
        val cityInfo = addCity(civInfo, tile, true)
        val building = createBuildingWithUnique("[+4 Gold] from [${Constants.grassland}] tiles without [${Constants.forest}] [in all cities]")
        cityInfo.cityConstructions.addBuilding(building.name)

        val tile2 = addTile(Constants.grassland, Vector2(0f,1f))
        Assert.assertTrue(tile2.getTileStats(cityInfo, civInfo).gold == 4f)
        
        val tile3 = addTile(Constants.grassland, Vector2(0f, 2f), listOf(Constants.forest))
        Assert.assertFalse(tile3.getTileStats(cityInfo, civInfo).gold == 4f)
    }
    
    @Test
    fun statsFromObject() {
        val civInfo = addCiv()
        val tile = addTile(Constants.desert)
        val cityInfo = addCity(civInfo, tile, true)
        val specialist = addEmptySpecialist()
        val building = createBuildingWithUnique("[+3 Faith] from every [${specialist}]")
        
        cityInfo.cityConstructions.addBuilding(building.name)
        cityInfo.population.addPopulation(2)
        cityInfo.population.specialistAllocations[specialist] = 2
        
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Specialists"]!!.faith == 6f)

        cityInfo.cityConstructions.removeBuilding(building.name)
        val building2 = createBuildingWithUnique("[+3 Faith] from every [${Constants.grassland}]")
        cityInfo.cityConstructions.addBuilding(building2.name)
        
        val tile2 = addTile(Constants.grassland, Vector2(1f, 0f))
        Assert.assertTrue(tile2.getTileStats(cityInfo, civInfo).faith == 3f)
        
        cityInfo.cityConstructions.removeBuilding(building2.name)
        
        val emptyBuilding = createBuildingWithUniques()
        
        val building3 = createBuildingWithUnique("[+3 Faith] from every [${emptyBuilding.name}]")
        cityInfo.cityConstructions.addBuilding(emptyBuilding.name)
        cityInfo.cityConstructions.addBuilding(building3.name)
        cityInfo.cityStats.update()
        Assert.assertTrue(cityInfo.cityStats.finalStatList["Buildings"]!!.faith == 3f)
    }
}
