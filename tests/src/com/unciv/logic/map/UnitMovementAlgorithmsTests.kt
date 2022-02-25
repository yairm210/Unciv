//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic.map

import com.unciv.Constants
import com.unciv.logic.GameInfo
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
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
        civInfo.nation = Nation().apply {
            name = "My nation"
            cities = arrayListOf("The Capital")
        }
        civInfo.gameInfo = GameInfo()
        civInfo.gameInfo.ruleSet = ruleSet
        unit.civInfo = civInfo


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
            unit.currentTile = tile
            
            Assert.assertFalse("$type must not enter occupied tile", unit.movement.canPassThrough(tile))
        }
        // ranged check
        otherUnit.baseUnit.rangedStrength = 1 // make non-Civilian ranged
        tile.militaryUnit = otherUnit

        for (type in ruleSet.unitTypes) {
            unit.baseUnit = BaseUnit().apply { unitType = type.key; ruleset = ruleSet }
            unit.currentTile = tile
            
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
}