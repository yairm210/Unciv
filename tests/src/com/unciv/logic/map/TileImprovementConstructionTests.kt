//  Taken from https://github.com/TomGrill/gdx-testing
package com.unciv.logic.map

import com.unciv.Constants
import com.unciv.logic.GameInfo
import com.unciv.logic.city.City
import com.unciv.logic.city.City.Companion.NO_ID
import com.unciv.logic.city.City.Companion.pseudoRandomId
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.TurnManager
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stats
import com.unciv.models.translations.fillPlaceholders
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestCase
import com.unciv.testing.TestGame
import com.unciv.testing.runTestParcours
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class TileImprovementConstructionTests {

    private lateinit var civInfo: Civilization
    private lateinit var tileMap: TileMap
    private lateinit var city: City

    val testGame = TestGame()


    @Before
    fun initTheWorld() {
        testGame.makeHexagonalMap(4)
        tileMap = testGame.tileMap
        civInfo = testGame.addCiv()
        for (tech in testGame.ruleset.technologies.values)
            civInfo.tech.addTechnology(tech.name)
        city = testGame.addCity(civInfo, tileMap[0,0])
    }

    @Test
    fun allTerrainSpecificImprovementsCanBeBuilt() {
        for (improvement in testGame.ruleset.tileImprovements.values) {
            var terrain = improvement.terrainsCanBeBuiltOn.firstOrNull() ?: continue
            if (terrain == "Land") terrain = testGame.ruleset.terrains.values.first { it.type == TerrainType.Land }.name
            if (terrain == "Water") terrain = testGame.ruleset.terrains.values.first { it.type == TerrainType.Water }.name
            // If this improvement requires additional conditions to be true,
            // its too complex to handle all of them, so just skip it and hope its fine
            // I would like some comments on whether this approach is fine or if it's better if I handle every single unique here as well
            if (improvement.hasUnique(UniqueType.CanOnlyBeBuiltOnTile, GameContext.IgnoreConditionals)) continue
            if (improvement.hasUnique(UniqueType.Unbuildable, GameContext.IgnoreConditionals)) continue

            val tile = tileMap[1,1]
            tile.baseTerrain = terrain
            tile.tileResource = null
            if (improvement.hasUnique(UniqueType.CanOnlyImproveResource, GameContext.IgnoreConditionals)) {
                tile.tileResource = testGame.ruleset.tileResources.values.firstOrNull { it.isImprovedBy(improvement.name) } ?: continue
            }
            tile.setTransients()

            if (improvement.uniqueTo != null) {
                civInfo = testGame.addCiv(improvement.uniqueTo!!)
                for (tech in testGame.ruleset.technologies.values)
                    civInfo.tech.addTechnology(tech.name)
                city.civ = civInfo
                city.id = if (city.id != NO_ID) city.id else pseudoRandomId(civInfo)
            }

            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
            Assert.assertTrue(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun allResourceImprovementsCanBeBuilt() {
        testResourceImprovements("Improvements improving a resource can be built on that resource as long as the domain matches -", wrongDomain = false)
    }

    @Test
    fun resourceImprovementsOnWrongDomainCannotBeBuilt() {
        testResourceImprovements("Improvements improving a resource cannot be built on that resource when the domain does not match -", wrongDomain = true)
    }

    private fun testResourceImprovements(title: String, wrongDomain: Boolean) {
        val landTile = tileMap[1,1]
        val coastTile = tileMap[1,2]
        coastTile.baseTerrain = Constants.coast
        coastTile.setTransients()
        coastTile.setOwningCity(city) // Yes ownership is tested

        val items: Array<TestCase<Pair<TileImprovement, TileResource>, Boolean>> =
            testGame.ruleset.tileImprovements.values
            .flatMap { improvement ->
                // Test all combinations - e.g. both Oil well and Offshore Platform for Oil
                testGame.ruleset.tileResources.values
                    .filter { it.isImprovedBy(improvement.name) }
                    .map { improvement to it }
            }
            .map { TestCase(it, !wrongDomain) }
            .toTypedArray()

        fun testImprovement(improvement: TileImprovement, tileResource: TileResource, tile: Tile): Boolean {
            tile.tileResource = tileResource
            tile.setTransients()
            return tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
        }
        fun testImprovement(improvement: TileImprovement, tileResource: TileResource): Boolean {
            // This relies on our builtin rulesets not using more complex filter in these uniques
            val landOK = landTile.improvementFunctions.extendedDomainCheck(improvement) &&
                improvement.getMatchingUniques(UniqueType.CanOnlyBeBuiltOnTile).all { it.params[0] == "Land" } &&
                improvement.getMatchingUniques(UniqueType.CannotBuildOnTile).none { it.params[0] == "Land" }
            val coastOK = coastTile.improvementFunctions.extendedDomainCheck(improvement) &&
                improvement.getMatchingUniques(UniqueType.CanOnlyBeBuiltOnTile).all { it.params[0] == "Water" } &&
                improvement.getMatchingUniques(UniqueType.CannotBuildOnTile).none { it.params[0] == "Water" }
            if (wrongDomain && landOK && coastOK) return false // Can't test wrong domain as the improvement has CanOnlyImproveResource which always ignores domain
            if (landOK != wrongDomain && !testImprovement(improvement, tileResource, landTile)) return false
            if (coastOK != wrongDomain && !testImprovement(improvement, tileResource, coastTile)) return false
            return true
        }

        runTestParcours(title, *items) { testImprovement(it.first, it.second) }
    }

    @Test
    fun coastalImprovementsCanBeBuilt() {
        val coastTile = tileMap[1,2]
        coastTile.baseTerrain = Constants.coast
        coastTile.setTransients()

        val coastalTile = tileMap[1,1]

        for (improvement in testGame.ruleset.tileImprovements.values) {
            if (!improvement.uniques.contains("Can only be built on [Coastal] tiles")) continue
            if (improvement.uniqueTo != null) {
                civInfo = testGame.addCiv(improvement.uniqueTo!!)
                for (tech in testGame.ruleset.technologies.values)
                    civInfo.tech.addTechnology(tech.name)
                city.civ = civInfo
                city.id = if (city.id != NO_ID) city.id else pseudoRandomId(civInfo)
            }
            val canBeBuilt = coastalTile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
            Assert.assertTrue(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun coastalImprovementsCanNOTBeBuiltInland() {
        val tile = tileMap[1,1]

        for (improvement in testGame.ruleset.tileImprovements.values) {
            if (!improvement.uniques.contains("Can only be built on [Coastal] tiles")) continue
            civInfo.setNameForUnitTests(improvement.uniqueTo ?: "OtherCiv")
            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
            Assert.assertFalse(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun uniqueToOtherImprovementsCanNOTBeBuilt() {
        for (improvement in testGame.ruleset.tileImprovements.values) {
            if (improvement.uniqueTo == null) continue
            civInfo.setNameForUnitTests("OtherCiv")
            val tile = tileMap[1,1]
            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
            Assert.assertFalse(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun improvementsCanNOTBeBuiltOnWrongResource() {
        for (resource in testGame.ruleset.tileResources.values) {
            if (resource.getImprovements().isEmpty()) continue
            val improvement = testGame.ruleset.tileImprovements[resource.getImprovements().first()]!!
            if (!improvement.hasUnique(UniqueType.CanOnlyImproveResource)) continue
            val wrongResource = testGame.ruleset.tileResources.values.firstOrNull {
                it != resource && !it.isImprovedBy(improvement.name)
            } ?: continue
            val tile = tileMap[1,1]
            tile.baseTerrain = "Plains"
            tile.tileResource = wrongResource
            tile.setTransients()
            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
            Assert.assertFalse(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun buildingGreatImprovementRemovesFeatures() {
        val tile = tileMap[1,1]
        tile.baseTerrain = "Plains"
        tile.addTerrainFeature("Hill")
        tile.addTerrainFeature("Forest")
        Assert.assertEquals(tile.terrainFeatures, listOf("Hill", "Forest"))

        tile.setImprovement("Landmark")
        Assert.assertEquals(tile.terrainFeatures, listOf("Hill"))
    }

    @Test
    fun buildingImprovementWithCivAppliesTerrainChange() {
        val tile = tileMap[1,1]
        val improvement = testGame.createTileImprovement("Turn this tile into a [Coast] tile")

        tile.setImprovement(improvement, civInfo)

        Assert.assertEquals("Coast", tile.baseTerrain)
    }

    @Test
    fun citadelTakesOverAdjacentTiles() {
        val tile = tileMap[1,1]
        Assert.assertFalse(tile.neighbors.all { it.owningCity == city })
        tile.setImprovement("Citadel", civInfo)
        Assert.assertTrue(tile.neighbors.all { it.owningCity == city })
    }

    @Test
    fun terraceFarmCanNOTBeBuiltOnBonus() {
        val tile = tileMap[1,1]
        tile.setTileResource("Sheep")
        tile.setTransients()
        tile.addTerrainFeature("Hill")
        civInfo.setNameForUnitTests("Inca")

        for (improvement in testGame.ruleset.tileImprovements.values) {
            if (!improvement.uniques.contains("Cannot be built on [Bonus resource] tiles")) continue
            val canBeBuilt = tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state)
            Assert.assertFalse(improvement.name, canBeBuilt)
        }
    }

    @Test
    fun buildingRoadBuildsARoad() {
        val tile = tileMap[1,1]
        tile.setImprovement("Road")
        assert(tile.roadStatus == RoadStatus.Road)
    }

    @Test
    fun removingRoadRemovesRoad() {
        val tile = tileMap[1,1]
        tile.roadStatus = RoadStatus.Road
        tile.setImprovement("Remove Road")
        assert(tile.roadStatus == RoadStatus.None)
    }

    @Test
    fun removingForestRemovesForestAndLumbermill() {
        val tile = tileMap[1,1]
        tile.addTerrainFeature("Forest")
        tile.setImprovement("Lumber mill")
        assert(tile.tileImprovement!!.name == "Lumber mill")
        tile.setImprovement("Remove Forest")
        assert(tile.terrainFeatures.isEmpty())
        assert(tile.improvement == null) // Lumber mill can ONLY be on Forest, and is therefore removed
    }

    @Test
    fun removingForestRemovesForestButNotCamp() {
        val tile = tileMap[1,1]
        tile.addTerrainFeature("Forest")
        tile.setTileResource("Deer")
        tile.baseTerrain = "Plains"
        tile.setImprovement("Camp")
        assert(tile.tileImprovement!!.name == "Camp")
        tile.setImprovement("Remove Forest")
        assert(tile.terrainFeatures.isEmpty())
        assert(tile.improvement == "Camp") // Camp can be both on Forest AND on Plains, so not removed
    }

    @Test
    fun improvementCannotBuildWhenNotAllowed() {
        val tile = tileMap[1,1]
        tile.baseTerrain ="Grassland"
        tile.addTerrainFeature("Forest")

        val improvement = testGame.createTileImprovement()
        Assert.assertFalse("Forest doesn't allow building unless allowed",
            tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state))


        val allowedImprovement = testGame.createTileImprovement()
        allowedImprovement.terrainsCanBeBuiltOn += "Forest"
        Assert.assertTrue("Forest should allow building when allowed",
            tile.improvementFunctions.canBuildImprovement(allowedImprovement, civInfo.state))
        tile.setImprovement(allowedImprovement)
        Assert.assertTrue(tile.tileImprovement == allowedImprovement)
        Assert.assertTrue("Forest should not be removed with this improvement", tile.terrainFeatures.contains("Forest"))
    }

    @Test
    fun improvementDoesntNeedRemovalCanBuildHere() {
        val tile = tileMap[1,1]
        tile.baseTerrain ="Grassland"
        tile.addTerrainFeature("Forest")

        val improvement = testGame.createTileImprovement("Does not need removal of [Forest]")
        Assert.assertTrue(tile.improvementFunctions.canBuildImprovement(improvement, civInfo.state))
        tile.setImprovement(improvement)
        Assert.assertTrue(tile.tileImprovement == improvement)
        Assert.assertTrue("Forest should not be removed with this improvement", tile.terrainFeatures.contains("Forest"))
    }

    @Test
    fun statsDiffFromRemovingForestTakesRemovedLumberMillIntoAccount() {
        val tile = tileMap[1,1]
        tile.baseTerrain = "Grassland"
        tile.addTerrainFeature("Forest")

        val lumberMill = testGame.ruleset.tileImprovements["Lumber mill"]!!
        tile.setImprovement(lumberMill)
        assert(tile.tileImprovement == lumberMill)

        // 1f 1p from forest, 2p from lumber mill since all techs are researched
        val tileStats = tile.stats.getTileStats(civInfo)
        assert(tileStats.equals(Stats(production = 3f, food = 1f)))

        val statsDiff = tile.stats.getStatDiffForImprovement(testGame.ruleset.tileImprovements["Remove Forest"]!!, civInfo, null)

        // We'll be reverting back to grassland stats - 2f only
        assert(statsDiff.equals(Stats(food = +1f, production = -3f)))
    }

    @Test
    fun cityFoundingDoesNotRegisterCenterAsNeutralRoad() {
        val (_, civ, city) = roadMaintenanceGame()
        val center = city.getCenterTile()

        Assert.assertEquals(RoadStatus.Road, center.roadStatus)
        Assert.assertFalse(civ.neutralRoads.contains(center.position))
        assertTransportationUpkeep(civ, 0f)

        center.setRoadStatus(RoadStatus.Road, civ)
        Assert.assertFalse(civ.neutralRoads.contains(center.position))
        assertTransportationUpkeep(civ, 0f)

        civ.tech.addTechnology("Railroads", false)
        center.setRoadStatus(RoadStatus.Railroad, civ)
        Assert.assertEquals(RoadStatus.Railroad, center.roadStatus)
        Assert.assertFalse(civ.neutralRoads.contains(center.position))
        assertTransportationUpkeep(civ, 0f)
    }

    @Test
    fun ownedRoadIsChargedOnceNotAsNeutral() {
        val (_, civ, city) = roadMaintenanceGame(
            roadUnique = UniqueType.ImprovementAllMaintenance.text.fillPlaceholders("100", "Gold")
        )
        val ownedTile = ownedNonCenterTile(city)
        Assert.assertEquals(civ, ownedTile.getOwner())
        Assert.assertEquals(civ, ownedTile.getRoadOwner())

        ownedTile.setImprovement("Road", civ)
        Assert.assertEquals(RoadStatus.Road, ownedTile.roadStatus)
        Assert.assertFalse(civ.neutralRoads.contains(ownedTile.position))
        assertTransportationUpkeep(civ, -100f)

        ownedTile.setImprovement("Road", civ)
        Assert.assertFalse(civ.neutralRoads.contains(ownedTile.position))
        assertTransportationUpkeep(civ, -100f)

        ownedTile.setImprovement("Remove Road", civ)
        Assert.assertEquals(RoadStatus.None, ownedTile.roadStatus)
        Assert.assertFalse(civ.neutralRoads.contains(ownedTile.position))
        assertTransportationUpkeep(civ, 0f)
    }

    @Test
    fun unownedRoadIsNeutralAndClaimingTransfersToTerritory() {
        val (_, civ, city) = roadMaintenanceGame(
            roadUnique = UniqueType.ImprovementAllMaintenance.text.fillPlaceholders("100", "Gold")
        )
        val tiles = unownedTiles(city).iterator()
        val removable = tiles.next()
        val pillaged = tiles.next()
        val claimed = tiles.next()

        removable.setImprovement("Road", civ)
        Assert.assertTrue(civ.neutralRoads.contains(removable.position))
        assertTransportationUpkeep(civ, -100f)

        removable.setImprovement("Remove Road", civ)
        Assert.assertFalse(civ.neutralRoads.contains(removable.position))
        assertTransportationUpkeep(civ, 0f)

        pillaged.setImprovement("Road", civ)
        Assert.assertTrue(civ.neutralRoads.contains(pillaged.position))
        assertTransportationUpkeep(civ, -100f)
        pillaged.setPillaged()
        Assert.assertTrue(pillaged.roadIsPillaged)
        assertTransportationUpkeep(civ, 0f)

        claimed.setImprovement("Road", civ)
        Assert.assertTrue(civ.neutralRoads.contains(claimed.position))
        assertTransportationUpkeep(civ, -100f)
        city.expansion.takeOwnership(claimed)
        Assert.assertFalse(civ.neutralRoads.contains(claimed.position))
        Assert.assertEquals(civ, claimed.getOwner())
        assertTransportationUpkeep(civ, -100f)
    }

    @Test
    fun territorialMaintenanceIsFreeOnNeutralAndChargedOnceWhenOwned() {
        val territorial = UniqueType.ImprovementMaintenance.text.fillPlaceholders("100", "Gold")
        val (_, civ, city) = roadMaintenanceGame(roadUnique = territorial)
        val ownedTile = ownedNonCenterTile(city)
        val unownedTile = unownedTiles(city).first()

        unownedTile.setImprovement("Road", civ)
        Assert.assertTrue(civ.neutralRoads.contains(unownedTile.position))
        assertTransportationUpkeep(civ, 0f)

        ownedTile.setImprovement("Road", civ)
        Assert.assertFalse(civ.neutralRoads.contains(ownedTile.position))
        assertTransportationUpkeep(civ, -100f)
    }

    @Test
    fun defaultRulesetRoadAndRailroadMaintenance() {
        val (_, civ, city) = roadMaintenanceGame()
        val ownedRoad = ownedNonCenterTile(city)
        val ownedRailroad = city.getCenterTile().neighbors.first { it != ownedRoad }
        val unownedRoad = unownedTiles(city).first()

        assertTransportationUpkeep(civ, 0f)

        ownedRoad.setImprovement("Road", civ)
        assertTransportationUpkeep(civ, -1f)

        ownedRailroad.setImprovement("Railroad", civ)
        assertTransportationUpkeep(civ, -3f)

        unownedRoad.setImprovement("Road", civ)
        Assert.assertTrue(civ.neutralRoads.contains(unownedRoad.position))
        assertTransportationUpkeep(civ, -4f)
        Assert.assertFalse(civ.neutralRoads.contains(ownedRoad.position))
        Assert.assertFalse(civ.neutralRoads.contains(ownedRailroad.position))
        Assert.assertFalse(civ.neutralRoads.contains(city.getCenterTile().position))
    }

    @Test
    fun transportationUpkeepMatchesStatsTransientsAndTreasury() {
        val (game, civ, city) = roadMaintenanceGame(
            roadUnique = UniqueType.ImprovementAllMaintenance.text.fillPlaceholders("100", "Gold"),
            isPlayer = true
        )
        ownedNonCenterTile(city).setImprovement("Road", civ)
        unownedTiles(city).first().setImprovement("Road", civ)

        val breakdown = civ.stats.getStatMapForNextTurn()
        Assert.assertEquals(-200f, breakdown["Transportation upkeep"]!!.gold, 0f)

        civ.updateStatsForNextTurn()
        val summed = Stats()
        for (stats in breakdown.values) summed.add(stats)
        Assert.assertTrue(summed.equals(civ.stats.statsForNextTurn))

        val upkeepBeforeTransients = transportationUpkeepGold(civ)
        rebuildNeutralRoadTransients(game.gameInfo)
        Assert.assertEquals(upkeepBeforeTransients, transportationUpkeepGold(civ), 0f)
        Assert.assertFalse(civ.neutralRoads.contains(city.getCenterTile().position))
        Assert.assertFalse(civ.neutralRoads.contains(ownedNonCenterTile(city).position))
        Assert.assertTrue(civ.neutralRoads.contains(unownedTiles(city).first().position))

        civ.addGold(10_000)
        civ.updateStatsForNextTurn()
        val goldBeforeTurn = civ.gold
        val predictedGoldDelta = civ.stats.statsForNextTurn.gold.toInt()
        TurnManager(civ).endTurn()
        Assert.assertEquals(goldBeforeTurn + predictedGoldDelta, civ.gold)
    }

    private data class RoadMaintenanceSetup(val game: TestGame, val civ: Civilization, val city: City)

    private fun roadMaintenanceGame(
        roadUnique: String? = null,
        railroadUnique: String? = null,
        isPlayer: Boolean = false
    ): RoadMaintenanceSetup {
        val game = TestGame()
        if (roadUnique != null) replaceImprovementMaintenance(game.ruleset, RoadStatus.Road.name, roadUnique)
        if (railroadUnique != null) replaceImprovementMaintenance(game.ruleset, RoadStatus.Railroad.name, railroadUnique)
        game.makeHexagonalMap(3)
        val civ = game.addCiv(isPlayer = isPlayer)
        civ.tech.addTechnology("The Wheel", false)
        val city = game.addCity(civ, game.getTile(HexCoord.Zero))
        return RoadMaintenanceSetup(game, civ, city)
    }

    private fun replaceImprovementMaintenance(ruleset: Ruleset, improvementName: String, unique: String) {
        val original = ruleset.tileImprovements[improvementName]!!
        val replacement = TileImprovement()
        replacement.name = original.name
        replacement.terrainsCanBeBuiltOn = original.terrainsCanBeBuiltOn
        replacement.turnsToBuild = original.turnsToBuild
        replacement.techRequired = original.techRequired
        replacement.uniques.addAll(original.uniques.filterNot { it.startsWith("Costs [") })
        replacement.uniques.add(unique)
        ruleset.tileImprovements[improvementName] = replacement
    }

    private fun ownedNonCenterTile(city: City) = city.getCenterTile().neighbors.first()

    private fun unownedTiles(city: City): List<Tile> {
        val tiles = ArrayList<Tile>()
        city.getCenterTile().forEachTileAtDistance(2) { tiles.add(it) }
        return tiles
    }

    private fun transportationUpkeepGold(civ: Civilization) =
        civ.stats.getStatMapForNextTurn()["Transportation upkeep"]!!.gold

    private fun assertTransportationUpkeep(civ: Civilization, expectedGold: Float) {
        Assert.assertEquals(expectedGold, transportationUpkeepGold(civ), 0f)
    }

    private fun rebuildNeutralRoadTransients(gameInfo: GameInfo) {
        for (civ in gameInfo.civilizations)
            civ.neutralRoads.clear()
        gameInfo.tileMap.setNeutralTransients()
    }
}
