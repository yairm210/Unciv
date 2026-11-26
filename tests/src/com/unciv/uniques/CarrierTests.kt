package com.unciv.uniques

import com.unciv.Constants
import com.unciv.json.json
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
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
    fun landUnitCanBoardAndDisembarkFromCarrier() {
        val waterTile = carrier.currentTile
        carrier = replaceCarrierWithLandCarrier(waterTile)

        val landNeighbors = carrier.currentTile.neighbors.filter { it.isLand }.toList()
        val landOrigin = landNeighbors.first()
        val nextWater = landNeighbors.first { it != landOrigin && it.getUnits().none() }
        val secondLandOrigin = landNeighbors.first {
            it != landOrigin && it != nextWater && it.getUnits().none()
        }
        nextWater.baseTerrain = Constants.coast
        nextWater.setTerrainTransients()
        val passenger = createLandPassenger(landOrigin)

        Assert.assertTrue(carrier.isTransportTypeOf(passenger))
        Assert.assertTrue(carrier.canTransport(passenger))
        Assert.assertTrue(
            "Cannot board carrier: ${passenger.movement.getCannotMoveToReason(carrier.currentTile)}",
            passenger.movement.canMoveTo(carrier.currentTile)
        )
        passenger.movement.moveToTile(carrier.currentTile)

        Assert.assertSame(carrier, carrier.currentTile.militaryUnit)
        Assert.assertTrue(passenger.isTransported)
        Assert.assertTrue(carrier.currentTile.transportedUnits.contains(passenger))
        Assert.assertEquals(0f, passenger.currentMovement, 0.001f)

        val secondPassenger = createLandPassenger(secondLandOrigin)
        Assert.assertFalse(secondPassenger.movement.canMoveTo(carrier.currentTile))

        carrier.movement.moveToTile(nextWater)
        Assert.assertSame(nextWater, carrier.currentTile)
        Assert.assertSame(nextWater, passenger.currentTile)
        Assert.assertTrue(nextWater.transportedUnits.contains(passenger))

        val disembarkTile = nextWater.neighbors.first {
            it.isLand && it != landOrigin && it != secondLandOrigin && it.getUnits().none()
        }
        passenger.currentMovement = 2f
        passenger.movement.moveToTile(disembarkTile)

        Assert.assertFalse(passenger.isTransported)
        Assert.assertSame(passenger, disembarkTile.militaryUnit)
        Assert.assertFalse(carrier.currentTile.transportedUnits.contains(passenger))
    }

    @Test
    fun landPassengerPersistsInCarrierAfterSaveLoad() {
        carrier = replaceCarrierWithLandCarrier()
        val passenger = boardLandPassenger(
            carrier,
            carrier.currentTile.neighbors.first { it.isLand && it.getUnits().none() }
        )
        val savedTile = carrier.currentTile
        val loadedGame = json().fromJson(GameInfo::class.java, json().toJson(game.gameInfo))
        val loadedTile = loadedGame.tileMap.values.single {
            it.transportedUnits.any { unit -> unit.name == passenger.name }
        }
        val loadedCarrier = loadedTile.militaryUnit!!
        val loadedPassenger = loadedTile.transportedUnits.single()

        Assert.assertEquals(carrier.name, loadedCarrier.name)
        Assert.assertEquals(civ.civID, loadedCarrier.owner)
        Assert.assertEquals(civ.civID, loadedPassenger.owner)
        Assert.assertEquals(savedTile.position, loadedTile.position)
        Assert.assertTrue(loadedPassenger.isTransported)
    }

    @Test
    fun giftingCarrierAlsoGiftsLandPassenger() {
        carrier = replaceCarrierWithLandCarrier()
        val passenger = boardLandPassenger(carrier, carrier.currentTile.neighbors.first { it.isLand && it.getUnits().none() })
        val recipient = game.addCiv()

        carrier.gift(recipient)

        Assert.assertSame(recipient, carrier.civ)
        Assert.assertSame(recipient, passenger.civ)
        Assert.assertTrue(passenger.isTransported)
        Assert.assertSame(carrier.currentTile, passenger.currentTile)
    }

    @Test
    fun destroyingCarrierAlsoDestroysLandPassenger() {
        carrier = replaceCarrierWithLandCarrier()
        val passenger = boardLandPassenger(carrier, carrier.currentTile.neighbors.first { it.isLand && it.getUnits().none() })
        val carrierTile = carrier.currentTile

        carrier.destroy()

        Assert.assertTrue(carrier.isDestroyed)
        Assert.assertTrue(passenger.isDestroyed)
        Assert.assertFalse(carrierTile.transportedUnits.contains(passenger))
    }

    @Test
    fun disbandingCarrierEvacuatesLandPassengerWithMovement() {
        carrier = replaceCarrierWithLandCarrier()
        val passenger = boardLandPassenger(carrier, carrier.currentTile.neighbors.first { it.isLand && it.getUnits().none() })
        val carrierTile = carrier.currentTile
        passenger.currentMovement = passenger.getMaxMovement().toFloat()

        carrier.disband()

        Assert.assertTrue(carrier.isDestroyed)
        Assert.assertFalse(passenger.isDestroyed)
        Assert.assertFalse(passenger.isTransported)
        Assert.assertNotSame(carrierTile, passenger.currentTile)
        Assert.assertTrue(passenger.currentTile.isLand)
    }

    @Test
    fun disbandingCarrierAlsoDisbandsPassengerWithoutMovement() {
        carrier = replaceCarrierWithLandCarrier()
        val passenger = boardLandPassenger(carrier, carrier.currentTile.neighbors.first { it.isLand && it.getUnits().none() })

        carrier.disband()

        Assert.assertTrue(carrier.isDestroyed)
        Assert.assertTrue(passenger.isDestroyed)
    }

    @Test
    fun producingLandUnitDoesNotReplaceLoadedCarrierInCity() {
        carrier = replaceCarrierWithLandCarrier(capacity = 2)
        val passenger = boardLandPassenger(
            carrier,
            carrier.currentTile.neighbors.first { it.isLand && it.getUnits().none() }
        )
        val city = civ.cities.first()
        val cityTile = city.getCenterTile()
        carrier.movement.moveToTile(cityTile)

        val producedBase = game.createBaseUnit(
            "Melee",
            "Can be carried by [All] units"
        ).apply {
            movement = 2
            strength = 5
        }
        val producedUnit = civ.units.addUnit(producedBase, city)

        Assert.assertNotNull(producedUnit)
        Assert.assertSame(carrier, cityTile.militaryUnit)
        Assert.assertTrue(passenger.isTransported)
        Assert.assertSame(cityTile, passenger.currentTile)
        Assert.assertNotSame(cityTile, producedUnit!!.currentTile)
        Assert.assertFalse(producedUnit.isTransported)
    }

    @Test
    fun swappingLoadedCarriersMovesLandPassengersWithThem() {
        val firstWater = carrier.currentTile
        carrier = replaceCarrierWithLandCarrier(firstWater)
        val secondWater = firstWater.neighbors.first { it.getUnits().none() }.apply {
            baseTerrain = Constants.coast
            setTerrainTransients()
        }
        val secondCarrier = createLandCarrier(secondWater)
        val firstPassenger = boardLandPassenger(
            carrier,
            firstWater.neighbors.first { it.isLand && it.getUnits().none() }
        )
        val secondPassenger = boardLandPassenger(
            secondCarrier,
            secondWater.neighbors.first { it.isLand && it.getUnits().none() && it != firstPassenger.currentTile }
        )

        Assert.assertTrue(carrier.movement.canUnitSwapTo(secondWater))
        carrier.movement.swapMoveToTile(secondWater)

        Assert.assertSame(secondWater, carrier.currentTile)
        Assert.assertSame(secondWater, firstPassenger.currentTile)
        Assert.assertSame(firstWater, secondCarrier.currentTile)
        Assert.assertSame(firstWater, secondPassenger.currentTile)
        Assert.assertTrue(firstPassenger.isTransported)
        Assert.assertTrue(secondPassenger.isTransported)
    }

    @Test
    fun cannotBeCarriedByOverridesCanBeCarriedBy() {
        val blockedPassengerBase = game.createBaseUnit(
            "Melee",
            "Can be carried by [All] units",
            "Cannot be carried by [All] units"
        )
        val blockedPassenger = game.addUnit(blockedPassengerBase.name, civ, null)

        Assert.assertFalse(carrier.isTransportTypeOf(blockedPassenger))
        Assert.assertFalse(carrier.canTransport(blockedPassenger))
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

    @Test
    fun testOverAllocation() {
        // Fill up carrier to the brim
        val carriedFighter = game.addUnit(fighterInCity.baseUnit.name, civ, carrier.currentTile)
        val carriedFighter2 = game.addUnit(fighterInCity.baseUnit.name, civ, carrier.currentTile)
        val carriedBomber = game.addUnit(bomberInCity.baseUnit.name, civ, carrier.currentTile)

        // We need to cram units into the carrier beyond what is allowed...
        val extraFighter = game.addUnit(fighterInCity.baseUnit.name, civ, game.getTile(0, 0))
        for (unit in listOf(extraFighter, fighterInCity, bomberInCity)) {
            unit.removeFromTile()
            //unit.putInTile checks capacity, so avoid
            carrier.getTile().transportedUnits.add(unit)
            unit.isTransported = true
        }
        Assert.assertEquals(6, carrier.getTile().transportedUnits.size)

        val remainingFighterCapacity = carrier.checkCarryCapacity(fighterInCity) // The unit is already on the carrier, but the function doesn't care, only filter matching is relevant
        val remainingBomberCapacity = carrier.checkCarryCapacity(bomberInCity)

        // Should return we got two fighters too many (the "Aircraft" slot is not relevant)
        Assert.assertEquals(-2, remainingFighterCapacity)
        // Should return there's a bomber over the allowed limit
        Assert.assertEquals(-1, remainingBomberCapacity)
    }

    private fun replaceCarrierWithLandCarrier(tile: Tile = carrier.currentTile, capacity: Int = 1): MapUnit {
        carrier.removeFromTile()
        civ.units.removeUnit(carrier)
        return createLandCarrier(tile, capacity).also { carrier = it }
    }

    private fun createLandCarrier(tile: Tile, capacity: Int = 1): MapUnit {
        val base = game.createBaseUnit("Aircraft Carrier", "Can carry [$capacity] [All] units").apply {
            movement = 2
            strength = 10
        }
        return game.addUnit(base.name, civ, tile)
    }

    private fun createLandPassenger(tile: Tile): MapUnit {
        val base = game.createBaseUnit("Melee", "Can be carried by [All] units").apply {
            movement = 2
            strength = 5
        }
        return game.addUnit(base.name, civ, tile)
    }

    private fun boardLandPassenger(carrier: MapUnit, landTile: Tile): MapUnit {
        val passenger = createLandPassenger(landTile)
        passenger.movement.moveToTile(carrier.currentTile)
        Assert.assertTrue("Passenger should have boarded the carrier", passenger.isTransported)
        return passenger
    }
}
