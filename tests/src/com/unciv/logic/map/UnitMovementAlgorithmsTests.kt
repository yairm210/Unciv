//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic.map

import com.unciv.Constants
import com.unciv.logic.GameInfo
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.models.ruleset.*
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class UnitMovementAlgorithmsTests {

    private var tile = TileInfo()
    private var civInfo = CivilizationInfo()
    private var ruleSet = Ruleset()
    private var unit = MapUnit()

    @Before
    fun initTheWorld() {
        RulesetCache.loadRulesets()
        ruleSet = RulesetCache.getVanillaRuleset()
        tile.ruleset = ruleSet
        tile.baseTerrain = Constants.grassland
        civInfo.tech.techsResearched.addAll(ruleSet.technologies.keys)
        civInfo.tech.embarkedUnitsCanEnterOcean = true
        civInfo.tech.unitsCanEmbark = true
        civInfo.gameInfo = GameInfo()
        civInfo.gameInfo.ruleSet = ruleSet
        civInfo.gameInfo.difficultyObject = Difficulty()
        civInfo.gameInfo.speed = ruleSet.speeds[Speed.DEFAULTFORSIMULATION]!!
        civInfo.nation = Nation().apply { name = "My nation" }
        civInfo.gameInfo.civilizations.add(civInfo)
        unit.civInfo = civInfo
        unit.owner = civInfo.civName

        // Needed for convertHillToTerrainFeature to not crash
        val tileMap = TileMap()
        tileMap.tileMatrix.add(ArrayList<TileInfo?>().apply { add(tile) })
        tile.tileMap = tileMap
        tile.setTransients()
    }

    @Test
    fun canPassThroughPassableTerrains() {
        for (terrain in ruleSet.terrains.values) {
            tile.baseTerrain = terrain.name
            tile.setTerrainFeatures(listOf())
            tile.setTransients()

            unit.baseUnit = BaseUnit().apply { unitType = "Sword"; ruleset = ruleSet }

            Assert.assertTrue(terrain.name, terrain.impassable != unit.movement.canPassThrough(tile))
        }
    }

    @Test
    fun unitCanEnterTheCity() {

        val map = TileMap()
        val cityTile = tile.clone() // reset, so that the isCoastalTile won't be carried over from previous tests
        cityTile.baseTerrain = Constants.grassland
        cityTile.tileMap = map
        cityTile.ruleset = ruleSet
        cityTile.setTransients()
        map.tileMatrix.add(arrayListOf(cityTile)) // needed for tile.setTransients()

        val otherTile = tile.clone()
        otherTile.baseTerrain = Constants.coast
        otherTile.position.y = 1f
        map.tileMatrix[0].add(otherTile)

        val city = CityInfo()
        city.location = cityTile.position
        city.civInfo = civInfo
        cityTile.setOwningCity(city)

        for (type in ruleSet.unitTypes)
        {
            unit.owner = civInfo.civName
            unit.baseUnit = BaseUnit().apply { unitType = type.key; ruleset = ruleSet }
            if(!unit.movement.canPassThrough(cityTile))
                unit.movement.canPassThrough(cityTile)
            Assert.assertTrue(type.key, unit.movement.canPassThrough(cityTile))
        }
    }

    @Test
    fun waterUnitCanNOTEnterLand() {
        for (terrain in ruleSet.terrains.values) {
            if (terrain.impassable) continue
            tile.baseTerrain = terrain.name
            tile.setTransients()

            for (type in ruleSet.unitTypes) {
                unit.baseUnit = BaseUnit().apply { unitType = type.key; ruleset = ruleSet }
                Assert.assertTrue("%s cannot be at %s".format(type.key, terrain.name),
                        (unit.baseUnit.isWaterUnit() && tile.isLand) != unit.movement.canPassThrough(tile))
            }
        }
    }

    @Test
    fun canNOTEnterIce() {
        tile.baseTerrain = Constants.ocean
        tile.setTerrainFeatures(listOf(Constants.ice))
        tile.setTransients()

        for (type in ruleSet.unitTypes) {
            unit.baseUnit = BaseUnit().apply { unitType = type.key; ruleset = ruleSet }
            unit.updateUniques(ruleSet)

            Assert.assertTrue(
                "$type cannot be in Ice",
                unit.movement.canPassThrough(tile) == (
                    type.value.uniques.contains("Can enter ice tiles")
                    || type.value.uniques.contains("Can pass through impassable tiles")
                )
            )
        }
    }

    @Test
    fun canNOTEnterNaturalWonder() {
        tile.baseTerrain = Constants.plains
        tile.naturalWonder = "Mount Fuji"
        tile.setTransients()

        for (type in ruleSet.unitTypes) {
            unit.baseUnit = BaseUnit().apply { unitType = type.key; ruleset = ruleSet }

            Assert.assertFalse("$type must not enter Wonder tile", unit.movement.canPassThrough(tile))
        }
    }

    @Test
    fun canNOTEnterCoastUntilProperTechIsResearched() {

        civInfo.tech.unitsCanEmbark = false

        tile.baseTerrain = Constants.coast
        tile.setTransients()

        for (type in ruleSet.unitTypes) {
            unit.baseUnit = BaseUnit().apply { unitType = type.key; ruleset = ruleSet }

            Assert.assertTrue("$type cannot be in Coast",
                    unit.baseUnit.isLandUnit() != unit.movement.canPassThrough(tile))
        }
    }

    @Test
    fun canNOTEnterOceanUntilProperTechIsResearched() {

        civInfo.tech.embarkedUnitsCanEnterOcean = false

        tile.baseTerrain = Constants.ocean
        tile.setTransients()

        for (type in ruleSet.unitTypes) {
            unit.baseUnit = BaseUnit().apply { unitType = type.key; ruleset = ruleSet }

            Assert.assertTrue("$type cannot be in Ocean",
                    unit.baseUnit.isLandUnit() != unit.movement.canPassThrough(tile))
        }
    }

    @Test
    fun canNOTEnterOceanWithLimitations() {

        tile.baseTerrain = Constants.ocean
        tile.setTransients()

        for (type in ruleSet.unitTypes) {
            unit.baseUnit = BaseUnit().apply {
                unitType = type.key
                ruleset = ruleSet
                if (this.isMelee())
                    uniques.add("Cannot enter ocean tiles")
                if (this.isRanged())
                    uniques.add("Cannot enter ocean tiles <before researching [Astronomy]>")
            }
            unit.updateUniques(ruleSet)

            Assert.assertTrue("$type cannot be in Ocean",
                    (unit.baseUnit.isMelee()) != unit.movement.canPassThrough(tile))

            civInfo.tech.techsResearched.remove("Astronomy")

            Assert.assertTrue("$type cannot be in Ocean until Astronomy",
                    (unit.baseUnit.isMelee() || unit.baseUnit.isRanged())
                                != unit.movement.canPassThrough(tile))

            civInfo.tech.techsResearched.add("Astronomy")
        }
    }

    @Test
    fun canNOTPassThroughTileWithEnemyUnits() {
        tile.baseTerrain = Constants.grassland
        tile.setTransients()

        unit.currentTile = tile

        val otherCiv = CivilizationInfo()
        otherCiv.civName = Constants.barbarians // they are always enemies
        otherCiv.nation = Nation().apply { name = Constants.barbarians }
        val otherUnit = MapUnit()
        otherUnit.civInfo = otherCiv
        otherUnit.baseUnit = BaseUnit()
        // melee check
        otherUnit.baseUnit.strength = 1
        tile.militaryUnit = otherUnit

        for (type in ruleSet.unitTypes) {
            unit.baseUnit = BaseUnit().apply { unitType = type.key; ruleset = ruleSet }

            Assert.assertFalse("$type must not enter occupied tile", unit.movement.canPassThrough(tile))
        }
        // ranged check
        otherUnit.baseUnit.rangedStrength = 1 // make non-Civilian ranged
        tile.militaryUnit = otherUnit

        for (type in ruleSet.unitTypes) {
            unit.baseUnit = BaseUnit().apply { unitType = type.key; ruleset = ruleSet }

            Assert.assertFalse("$type must not enter occupied tile", unit.movement.canPassThrough(tile))
        }
    }

    @Test
    fun canNOTPassForeignTiles() {
        tile.baseTerrain = Constants.desert
        tile.setTransients()

        val otherCiv = CivilizationInfo()
        otherCiv.civName = "Other civ"
        otherCiv.nation = Nation().apply { name = "Other nation" }

        val city = CityInfo()
        city.location = tile.position.cpy().add(1f,1f)
        city.civInfo = otherCiv
        tile.setOwningCity(city)

        unit.baseUnit = BaseUnit().apply { unitType = ruleSet.unitTypes.keys.first(); ruleset = ruleSet }
        unit.owner = civInfo.civName

        Assert.assertFalse("Unit must not enter other civ tile", unit.movement.canPassThrough(tile))

        city.location = tile.position

        Assert.assertFalse("Unit must not enter other civ city", unit.movement.canPassThrough(tile))

        city.hasJustBeenConquered = true
        civInfo.diplomacy["Other civ"] = DiplomacyManager(otherCiv, "Other civ")
        civInfo.getDiplomacyManager(otherCiv).diplomaticStatus = DiplomaticStatus.War

        Assert.assertTrue("Unit can capture other civ city", unit.movement.canPassThrough(tile))
    }

    /**
     * Creates an [amount] of tiles connected to each other of the same type and ownership as initial one.
     * Remember to set the ownership of the initial tile _before_ calling this method.
     */
    private fun generateTileCopies(amount: Int): ArrayList<TileInfo> {
        val newTiles = arrayListOf<TileInfo>()
        for (i in 1..amount) {
            tile.clone().apply {
                position.set(0f, i.toFloat())
                tile.tileMap.tileMatrix.last().add(this)
                newTiles.add(this)
            }
        }
        // allow this tile to be teleported to
        newTiles.last().setOwningCity(null)
        return newTiles
    }

    private fun createOpponentCiv(namePrefix: String, relations: DiplomaticStatus): CivilizationInfo {
        val otherCiv = CivilizationInfo()
        otherCiv.civName = "$namePrefix civ"
        otherCiv.nation = Nation().apply { name = "$namePrefix nation" }
        otherCiv.gameInfo = civInfo.gameInfo
        otherCiv.gameInfo.civilizations.add(otherCiv)
        civInfo.diplomacy[otherCiv.civName] = DiplomacyManager(otherCiv, otherCiv.civName).apply {
            diplomaticStatus = relations
            hasOpenBorders = false
        }
        return otherCiv
    }

    // primary purpose of the method to set the ownership of the tile to be evacuated from
    private fun createOpponentCivAndCity() {

        val otherCiv = createOpponentCiv("Other", DiplomaticStatus.Peace)

        val city = CityInfo()
        city.location = tile.position.cpy().add(5f, 5f) // random shift to avoid of being in city
        city.civInfo = otherCiv
        tile.setOwningCity(city)
    }

    private fun setupMilitaryUnitInTheCurrentTile(type: String) {
        // "strength = 1" to indicate it is military unit
        unit.baseUnit = BaseUnit().apply { unitType = type; strength = 1; ruleset = ruleSet }
        unit.currentTile = tile
        tile.militaryUnit = unit
        unit.name = "Unit"
    }

    @Test
    fun `can teleport land unit`() {
        // this is needed for unit.putInTile(), unit.moveThroughTile() to avoid using Uncivgame.Current.viewEntireMapForDebug
        civInfo.nation.name = Constants.spectator

        tile.baseTerrain = Constants.grassland
        tile.position.set(0f, 0f)
        tile.setTransients()
        createOpponentCivAndCity()
        val newTiles = generateTileCopies(2)

        setupMilitaryUnitInTheCurrentTile("Sword")

        unit.movement.teleportToClosestMoveableTile()

        Assert.assertTrue("Unit must be teleported to new location", unit.currentTile == newTiles.last())
    }

    @Test
    fun `can teleport water unit`() {
        // this is needed for unit.putInTile(), unit.moveThroughTile() to avoid using Uncivgame.Current.viewEntireMapForDebug
        civInfo.nation.name = Constants.spectator

        tile.baseTerrain = Constants.ocean
        tile.position.set(0f, 0f)
        tile.setTransients()
        createOpponentCivAndCity()
        val newTiles = generateTileCopies(3)

        setupMilitaryUnitInTheCurrentTile("Melee Water")

        unit.movement.teleportToClosestMoveableTile()

        Assert.assertTrue("Unit must be teleported to new location", unit.currentTile == newTiles.last())
    }

    @Test
    fun `can teleport water unit over other unit`() {
        // this is needed for unit.putInTile(), unit.moveThroughTile() to avoid using Uncivgame.Current.viewEntireMapForDebug
        civInfo.nation.name = Constants.spectator

        tile.baseTerrain = Constants.ocean
        tile.position.set(0f, 0f)
        tile.setTransients()
        createOpponentCivAndCity()
        val newTiles = generateTileCopies(3)

        // Other unit on the way
        val otherUnit = MapUnit()
        otherUnit.civInfo = civInfo
        otherUnit.owner = civInfo.civName
        otherUnit.baseUnit = BaseUnit().apply { unitType = "Melee Water"; strength = 1; ruleset = ruleSet }
        otherUnit.currentTile = newTiles[0]
        newTiles[0].militaryUnit = otherUnit
        otherUnit.name = "Friend Unit"

        setupMilitaryUnitInTheCurrentTile("Melee Water")

        unit.movement.teleportToClosestMoveableTile()

        Assert.assertTrue("Unit must be teleported to new location", unit.currentTile == newTiles.last())
    }

    @Test
    fun `can teleport air unit`() {
        // this is needed for unit.putInTile(), unit.moveThroughTile() to avoid using Uncivgame.Current.viewEntireMapForDebug
        civInfo.nation.name = Constants.spectator

        tile.baseTerrain = Constants.grassland
        tile.position.set(0f, 0f)
        tile.setTransients()
        createOpponentCivAndCity()
        val newTiles = generateTileCopies(2)

        setupMilitaryUnitInTheCurrentTile("Sword")

        unit.movement.teleportToClosestMoveableTile()

        Assert.assertTrue("Unit must be teleported to new location", unit.currentTile == newTiles.last())
    }

    @Test
    fun `can teleport land unit to city`() {
        // this is needed for unit.putInTile(), unit.moveThroughTile() to avoid using Uncivgame.Current.viewEntireMapForDebug
        civInfo.nation.name = Constants.spectator

        tile.baseTerrain = Constants.grassland
        tile.position.set(0f, 0f)
        tile.setTransients()
        createOpponentCivAndCity()
        val newTiles = generateTileCopies(7)
        // create obstacle
        newTiles[3].baseTerrain = "Grand Mesa"
        newTiles[3].setTransients()
        // create our city
        CityInfo().apply {
            this.civInfo = this@UnitMovementAlgorithmsTests.civInfo
            location = newTiles.last().position.cpy()
            tiles.add(location)
            tiles.add(newTiles[5].position)
            tileMap = tile.tileMap
            civInfo.cities = listOf(this)
            newTiles[5].setOwningCity(this)
            newTiles.last().setOwningCity(this)
        }

        setupMilitaryUnitInTheCurrentTile("Sword")

        unit.movement.teleportToClosestMoveableTile()

        Assert.assertTrue("Unit must be teleported to the city", unit.currentTile == newTiles[5])
    }

    @Test
    fun `can NOT teleport water unit over the land`() {
        // this is needed for unit.putInTile(), unit.moveThroughTile() to avoid using Uncivgame.Current.viewEntireMapForDebug
        civInfo.nation.name = Constants.spectator

        tile.baseTerrain = Constants.ocean
        tile.position.set(0f, 0f)
        tile.setTransients()
        createOpponentCivAndCity()
        val newTiles = generateTileCopies(3)
        // create obstacle
        newTiles[1].baseTerrain = Constants.grassland
        newTiles[1].setTransients()

        setupMilitaryUnitInTheCurrentTile("Melee Water")

        unit.movement.teleportToClosestMoveableTile()

        Assert.assertTrue("Unit must not be teleported but destroyed",
            unit.currentTile == tile && unit.isDestroyed)
    }

    @Test
    fun `can teleport land unit over civilian and capture it`() {
        // this is needed for unit.putInTile(), unit.moveThroughTile() to avoid using Uncivgame.Current.viewEntireMapForDebug
        civInfo.nation.name = Constants.spectator

        tile.baseTerrain = Constants.grassland
        tile.position.set(0f, 0f)
        tile.setTransients()
        createOpponentCivAndCity()
        val newTiles = generateTileCopies(2)

        setupMilitaryUnitInTheCurrentTile("Sword")

        val thirdCiv = createOpponentCiv("Third", DiplomaticStatus.War)
        val otherUnit = MapUnit()
        otherUnit.baseUnit = BaseUnit().apply { unitType = "Civilian"; ruleset = ruleSet }
        otherUnit.currentTile = newTiles.last()
        newTiles.last().civilianUnit = otherUnit
        otherUnit.name = "Worker"
        otherUnit.civInfo = thirdCiv
        otherUnit.owner = thirdCiv.civName

        unit.movement.teleportToClosestMoveableTile()

        Assert.assertTrue("Civilian unit must be captured by teleported unit",
            unit.currentTile == newTiles.last() && otherUnit.civInfo == unit.civInfo)
    }

    @Test
    fun `can teleport transport and its transported units to the same tile`() {
        civInfo.nation.name = Constants.spectator

        tile.baseTerrain = Constants.ocean
        tile.position.set(0f, 0f)
        tile.setTransients()
        createOpponentCivAndCity()
        val newTiles = generateTileCopies(3)

        setupMilitaryUnitInTheCurrentTile("Aircraft Carrier")
        unit.owner = civInfo.civName
        unit.civInfo = civInfo
        unit.baseUnit.uniques.add("Can carry [2] [Aircraft] units")
        unit.updateUniques(ruleSet)
        civInfo.addUnit(unit, false)

        val fighters = ArrayList<MapUnit>()
        for (i in 0..1) {
            val newFighter = MapUnit()
            newFighter.baseUnit = BaseUnit().apply { unitType = "Fighter"; ruleset = ruleSet }
            newFighter.owner = civInfo.civName
            newFighter.civInfo = civInfo
            newFighter.currentTile = unit.getTile()
            tile.airUnits += newFighter
            newFighter.name = "Fighter"
            newFighter.isTransported = true
            civInfo.addUnit(newFighter, false)
            fighters += newFighter
        }

        // simulate ejecting all units within foreign territory
        for (unit in civInfo.getCivUnits()) unit.movement.teleportToClosestMoveableTile()
        Assert.assertTrue("Transport and transported units must be teleported to the same tile",
            civInfo.getCivUnits().toList().size == 3 && civInfo.getCivUnits().all { it.getTile() == newTiles.last() })
    }

}
