package com.unciv.uniques

import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@Suppress("unused", "UnusedVariable") // Uses variable names as comments
@RunWith(GdxTestRunner::class)
class CarrierTests {
    private val game = TestGame()
    private lateinit var civ: Civilization
    private lateinit var carrier: MapUnit
    private lateinit var bomberInCity: MapUnit
    private lateinit var fighterInCity: MapUnit

    /**
     *  We're using G&K UnitTypes here but add extra tags - filter "Fighter" would match UnitType _and_ the BaseUnit of that name,
     *  so adding a tag ensures the filter is treated as generic
     */
    @Before
    fun initCiv() {
        game.makeHexagonalMap(2)
        val waterTile = game.getTile(1,0)
        waterTile.baseTerrain = Constants.coast
        waterTile.setTerrainTransients()
        civ = game.addCiv()
        val city = civ.addCity(HexCoord.Zero)
        val fighterBase = game.createBaseUnit("Fighter", "MyFighters", "MyAircraft")
        val bomberBase = game.createBaseUnit("Bomber", "MyBombers", "MyAircraft")
        val carrierBase = game.createBaseUnit("Aircraft Carrier",
            "Can carry [1] [Aircraft] units",
            "Can carry [1] [MyFighters] units",
            "Can carry [1] [MyBombers] units")
        carrierBase.strength = 10 // Otherwise it's a civilian, and we don't look for carriers in Tile.civilianUnit
        fighterInCity = game.addUnit(fighterBase.name, civ, city.getCenterTile())
        bomberInCity = game.addUnit(bomberBase.name, civ, city.getCenterTile())
        carrier = game.addUnit(carrierBase.name, civ, waterTile)
    }

    @Test
    fun testUniformCarriedUnits() {
        val carriedFighter = game.addUnit(fighterInCity.baseUnit.name, civ, carrier.currentTile)
        val carriedFighter2 = game.addUnit(fighterInCity.baseUnit.name, civ, carrier.currentTile)
        val remainingFighterCapacity = carrier.checkCarryCapacity(fighterInCity)
        val remainingBomberCapacity = carrier.checkCarryCapacity(bomberInCity)

        // Should return capacity for a third fighter: 0
        Assert.assertEquals(0, remainingFighterCapacity)
        // Should return capacity for a bomber: 1 - the MyBombers slot
        Assert.assertEquals(1, remainingBomberCapacity)
    }

    @Test
    /** Note: This only succeeds thanks to the `unitCount`-derived specificity in [MapUnit.checkCarryCapacity] */
    fun testMixedCarriedUnits() {
        val carriedFighter = game.addUnit(fighterInCity.baseUnit.name, civ, carrier.currentTile)
        val carriedBomber = game.addUnit(bomberInCity.baseUnit.name, civ, carrier.currentTile)
        val remainingFighterCapacity = carrier.checkCarryCapacity(fighterInCity)
        val remainingBomberCapacity = carrier.checkCarryCapacity(bomberInCity)

        // Should return capacity for a second fighter: 1 (the "Aircraft" slot)
        Assert.assertEquals(1, remainingFighterCapacity)
        // Should return capacity for a second bomber: 1 (the "Aircraft" slot)
        Assert.assertEquals(1, remainingBomberCapacity)
    }

    @Test
    fun testMixedCarriedUnitsWithDifferentSelectivity() {
        civ.units.removeUnit(carrier)
        val altCarrierBase = game.createBaseUnit("Aircraft Carrier",
            "Can carry [1] [MyAircraft] units", // should more selective than Aircraft
            "Can carry [1] [Fighter] units", // should be less selective than MyFighters
            "Can carry [1] [Bomber] units") // should be less selective than MyBombers
        carrier = game.addUnit(altCarrierBase.name, civ, game.getTile(1, 0))

        testMixedCarriedUnits()
    }
}
