package com.unciv.uniques

import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(GdxTestRunner::class)
class BuildingUniquesTest {
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
    }

    @Test
    fun testBuildingGrantedByUniqueGrantsResource() {
        val resourceProvider = game.createBuilding("Provides [1] [Coal]")
        val resourceProviderProvider = game.createBuilding("Gain a free [${resourceProvider.name}] [in this city]")
        city.cityConstructions.addBuilding(resourceProviderProvider.name)
        Assert.assertTrue(civInfo.getCivResourcesByName()["Coal"] == 1)
    }


}
