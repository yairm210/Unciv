//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.civilization.CivFlags
import com.unciv.logic.map.RoadStatus
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
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

    fun setRulesetTransients() {
        // TODO this shouldn't be here - OR in GameInfo.setTransients, but in ruleset code
        for (baseUnit in game.ruleset.units.values)
            baseUnit.ruleset = game.ruleset
    }

    // region stat uniques

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
        val specialistName = game.addEmptySpecialist()
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
        val building = game.createBuilding("[+3 Gold] in cities with [3] or more population")

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
        val specialist = game.addEmptySpecialist()
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

    // endregion
    // region stat uniques - religion-specific

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

    // endregion

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

    // region happiness uniques

    @Test
    fun testGrowthPercentBonus() {
        val globalUniques = game.ruleset.globalUniques
        val unique = globalUniques.getMatchingUniques(UniqueType.GrowthPercentBonus, StateForConditionals.IgnoreConditionals).firstOrNull()
        val conditional = unique?.conditionals?.firstOrNull { it.type == UniqueType.ConditionalBetweenHappiness }
        val mustHaveConditional = conditional != null
        Assert.assertTrue("GrowthPercentBonus must exist in globalUniques and have a ConditionalBetweenHappiness", mustHaveConditional)

        val civInfo = game.addCiv()
        val stateForConditionals = StateForConditionals(civInfo)
        civInfo.happinessForNextTurn = 0
        val activeAtZero = globalUniques.getMatchingUniques(UniqueType.GrowthPercentBonus, stateForConditionals).any()
        civInfo.happinessForNextTurn = -1
        val activeAtMinus1 = globalUniques.getMatchingUniques(UniqueType.GrowthPercentBonus, stateForConditionals).any()
        civInfo.happinessForNextTurn = -9
        val activeAtMinus9 = globalUniques.getMatchingUniques(UniqueType.GrowthPercentBonus, stateForConditionals).any()
        civInfo.happinessForNextTurn = -10
        val activeAtMinus10 = globalUniques.getMatchingUniques(UniqueType.GrowthPercentBonus, stateForConditionals).any()

        val success = !activeAtZero && activeAtMinus1 && activeAtMinus9 && !activeAtMinus10
        Assert.assertTrue("GrowthPercentBonus must obey conditional (activeAtZero=$activeAtZero, activeAtMinus1=$activeAtMinus1, activeAtMinus9=$activeAtMinus9, activeAtMinus10=$activeAtMinus10)", success)

        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val city = game.addCity(civInfo, tile, initialPopulation = 1)
        civInfo.updateStatsForNextTurn()
        // At this point the city will be working 1 Grassland giving a total of 4 food tile yield and consume 2 for 1 pop.
        val foodAt1 = city.cityStats.currentCityStats.food
        civInfo.happinessForNextTurn = -5
        city.cityStats.update()
        val foodAtMinus5 = city.cityStats.currentCityStats.food
        val reduction = ((1f - foodAtMinus5 / foodAt1) * 100f).toInt()
        Assert.assertTrue("GrowthPercentBonus effective reduction must be 75%, found $reduction%", reduction == 75)
    }

    @Test
    fun testNullifiesGrowth() {
        val globalUniques = game.ruleset.globalUniques
        val unique = globalUniques.getMatchingUniques(UniqueType.NullifiesGrowth, StateForConditionals.IgnoreConditionals).firstOrNull()
        val conditional = unique?.conditionals?.firstOrNull { it.type == UniqueType.ConditionalBelowHappiness }
        val mustHaveConditional = conditional != null
        Assert.assertTrue("NullifiesGrowth must exist in globalUniques and have a ConditionalBelowHappiness", mustHaveConditional)

        val civInfo = game.addCiv()
        val stateForConditionals = StateForConditionals(civInfo)
        civInfo.happinessForNextTurn = -1
        val activeAtMinus1 = globalUniques.getMatchingUniques(UniqueType.NullifiesGrowth, stateForConditionals).any()
        civInfo.happinessForNextTurn = -9
        val activeAtMinus9 = globalUniques.getMatchingUniques(UniqueType.NullifiesGrowth, stateForConditionals).any()
        civInfo.happinessForNextTurn = -10
        val activeAtMinus10 = globalUniques.getMatchingUniques(UniqueType.NullifiesGrowth, stateForConditionals).any()

        val success = !activeAtMinus1 && !activeAtMinus9 && activeAtMinus10
        Assert.assertTrue("NullifiesGrowth must obey conditional (activeAtMinus1=$activeAtMinus1, activeAtMinus9=$activeAtMinus9, activeAtMinus10=$activeAtMinus10)", success)

        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        game.setTileFeatures(Vector2(1f,1f), features = listOf("Oasis"))
        game.setTileFeatures(Vector2(-1f,-1f), features = listOf("Oasis"))
        val city = game.addCity(civInfo, tile, initialPopulation = 2)
        civInfo.updateStatsForNextTurn()
        // At this point the city will be working two oases giving a total of 8 food tile yield and consume 4 for 2 pop.
        val foodAt1 = city.cityStats.currentCityStats.food
        Assert.assertTrue("NullifiesGrowth must find >0 food at default happiness, found $foodAt1", foodAt1 > 0f)
        civInfo.happinessForNextTurn = -15
        city.cityStats.update()
        val foodAtMinus15 = city.cityStats.currentCityStats.food
        Assert.assertTrue("NullifiesGrowth must leave 0 food, found $foodAtMinus15", foodAtMinus15 == 0f)
    }

    @Test
    fun testCannotBuildUnits() {
        val unitName = "Settler"

        val globalUniques = game.ruleset.globalUniques
        val unique = globalUniques.getMatchingUniques(UniqueType.CannotBuildUnits, StateForConditionals.IgnoreConditionals).firstOrNull()
        val conditional = unique?.conditionals?.firstOrNull { it.type == UniqueType.ConditionalBelowHappiness }
        val mustHaveUnique = unique?.params?.get(0) == unitName && conditional != null
        Assert.assertTrue("CannotBuildUnits must exist in globalUniques for the Settler unit with ConditionalBelowHappiness", mustHaveUnique)

        val civInfo = game.addCiv()
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val city = game.addCity(civInfo, tile, initialPopulation = 6)
        civInfo.updateStatsForNextTurn()

        setRulesetTransients()
        city.cityConstructions.addToQueue(unitName)
        val isBuildingSettlerAtBase = city.cityConstructions.isBeingConstructed(unitName)

        city.cityConstructions.constructionQueue.clear()
        civInfo.happinessForNextTurn = -15
        city.cityConstructions.addToQueue(unitName)
        val isBuildingSettlerAtMinus15 = city.cityConstructions.isBeingConstructed(unitName)

        val baseUnit = game.ruleset.units[unitName]
        val message = baseUnit?.getRejectionReasons(city.cityConstructions)?.getMostImportantRejectionReason()
        val displayOK = baseUnit?.shouldBeDisplayed(city.cityConstructions) == true && message?.contains(unitName) == true

        val success = isBuildingSettlerAtBase && !isBuildingSettlerAtMinus15
        Assert.assertTrue("CannotBuildUnits must prevent Settler from being queued at -15 happiness but allow otherwise", success)
        Assert.assertTrue("Unbuildable Settler at -15 happiness should still be displayed with reason", displayOK == true)
    }

    @Test
    fun testSpawnRebels() {
        val globalUniques = game.ruleset.globalUniques
        val unique = globalUniques.getMatchingUniques(UniqueType.SpawnRebels, StateForConditionals.IgnoreConditionals).firstOrNull()
        val conditional = unique?.conditionals?.firstOrNull { it.type == UniqueType.ConditionalBelowHappiness }
        val mustHaveUnique = conditional != null
        Assert.assertTrue("SpawnRebels must exist in globalUniques with ConditionalBelowHappiness", mustHaveUnique)

        val civInfo = game.addCiv(isPlayer = true)
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val city = game.addCity(civInfo, tile, initialPopulation = 1)
        val barbarians = game.addBarbarians()

        setRulesetTransients()
        civInfo.startTurn()
        val hasFlagAtBase = civInfo.hasFlag(CivFlags.RevoltSpawning.name)

        city.population.addPopulation(80)  // Need happiness below -20, startTurn recalculates civInfo.happinessForNextTurn

        civInfo.startTurn()
        val hasFlagAtMinus25 = civInfo.hasFlag(CivFlags.RevoltSpawning.name)
        Assert.assertTrue("SpawnRebels must set the RevoltSpawning flag at -25, but not at base happiness", !hasFlagAtBase && hasFlagAtMinus25)

        civInfo.removeFlag(CivFlags.RevoltSpawning.name)
        civInfo.addFlag(CivFlags.RevoltSpawning.name, 1)
        game.tileMap.gameInfo = game.gameInfo
        civInfo.startTurn()
        val didRevolt = civInfo.notifications.any { "revolt" in it.text } && barbarians.getCivUnits().any()
        Assert.assertTrue("SpawnRebels must cause actual rebels and a notification to appear", didRevolt)
    }

    @Test
    fun testHappinessProductionMalus() {
        val globalUniques = game.ruleset.globalUniques
        val unique = globalUniques.getMatchingUniques(UniqueType.StatPercentBonusCities, StateForConditionals.IgnoreConditionals).firstOrNull()
        val conditional = unique?.conditionals?.firstOrNull { it.type == UniqueType.ConditionalBelowHappiness }
        val mustHaveUnique = conditional != null && unique?.params?.get(1) == "Production"
        Assert.assertTrue("StatPercentBonusCities for Production must exist in globalUniques with ConditionalBelowHappiness", mustHaveUnique)

        val civInfo = game.addCiv(isPlayer = true)
        val tile = game.setTileFeatures(Vector2(0f,0f), Constants.desert)
        val city = game.addCity(civInfo, tile, initialPopulation = 36)
        civInfo.happinessForNextTurn = 0  // addCity updates this but we want the state after loading a game
        game.gameInfo.setTransients()
        val productionBefore = city.cityStats.currentCityStats.production
        civInfo.updateStatsForNextTurn()
        city.cityStats.update()
        val productionAfter = city.cityStats.currentCityStats.production
        Assert.assertTrue("Production after GameInfo.setTransients ($productionBefore) must equal Production after updating happiness and effects directly ($productionAfter)", productionBefore == productionAfter)
    }

    // endregion
}
