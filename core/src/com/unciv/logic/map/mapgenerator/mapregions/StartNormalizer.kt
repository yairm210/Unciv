package com.unciv.logic.map.mapgenerator.mapregions

import com.unciv.Constants
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.resourceplacement.MapRegionResources
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import kotlin.math.abs
import kotlin.math.pow

/** Ensures that starting positions of civs have enough yield that they aren't at a disadvantage */
object StartNormalizer {

    /** Attempts to improve the start on [startTile] as needed to make it decent.
     *  Relies on startPosition having been set previously.
     *  Assumes unchanged baseline values ie citizens eat 2 food each, similar production costs
     *  If [isMinorCiv] is true, different weightings will be used. */
    fun normalizeStart(startTile: Tile, tileMap: TileMap, tileData: TileDataMap, ruleset: Ruleset, isMinorCiv: Boolean) {
        // Remove ice-like features adjacent to start
        for (tile in startTile.neighbors) {
            val lastTerrain = tile.terrainFeatureObjects.lastOrNull { it.impassable }
            if (lastTerrain != null) {
                tile.removeTerrainFeature(lastTerrain.name)
            }
        }

        if (!isMinorCiv && tileMap.mapParameters.getStrategicBalance())
            placeStrategicBalanceResources(startTile, ruleset, tileData)

        normalizeProduction(startTile, isMinorCiv, ruleset, tileData)

        val foodBonusesNeeded = calculateFoodBonusesNeeded(startTile, isMinorCiv, ruleset, tileMap)
        placeFoodBonuses(isMinorCiv, startTile, ruleset, foodBonusesNeeded)

        // Minor civs are done, go on with grassiness checks for major civs
        if (isMinorCiv) return

        addProductionBonuses(startTile, ruleset)
    }

    private fun normalizeProduction(
        startTile: Tile,
        isMinorCiv: Boolean,
        ruleset: Ruleset,
        tileData: TileDataMap
    ) {
        // evaluate production potential
        val innerProduction =
            startTile.neighbors.sumOf { getPotentialYield(it, Stat.Production).toInt() }
        val outerProduction =
            startTile.getTilesAtDistance(2).sumOf { getPotentialYield(it, Stat.Production).toInt() }
        // for very early production we ideally want tiles that also give food
        val earlyProduction = startTile.getTilesInDistanceRange(1..2).sumOf {
            if (getPotentialYield(it, Stat.Food, unimproved = true) > 0f) getPotentialYield(
                it,
                Stat.Production,
                unimproved = true
            ).toInt()
            else 0
        }

        // If terrible, try adding a hill to a dry flat tile
        if (innerProduction == 0 || (innerProduction < 2 && outerProduction < 8) || (isMinorCiv && innerProduction < 4)) {
            val hillSpot = startTile.neighbors
                .filter { it.isLand && it.terrainFeatures.isEmpty() && !it.isAdjacentTo(Constants.freshWater) && !it.isImpassible() }
                .toList().randomOrNull()
            val hillEquivalent = ruleset.terrains.values
                .firstOrNull {
                    it.type == TerrainType.TerrainFeature && it.production >= 2 && !it.hasUnique(
                        UniqueType.RareFeature
                    )
                }?.name
            if (hillSpot != null && hillEquivalent != null) {
                hillSpot.addTerrainFeature(hillEquivalent)
            }
        }

        // If bad early production, add a small strategic resource to SECOND ring (not for minors)
        if (!isMinorCiv && innerProduction < 3 && earlyProduction < 6) {
            val lastEraNumber = ruleset.eras.values.maxOf { it.eraNumber }
            val earlyEras = ruleset.eras.filterValues { it.eraNumber <= lastEraNumber / 3 }
            val validResources = ruleset.tileResources.values.filter {
                it.resourceType == ResourceType.Strategic &&
                        (it.revealedBy == null ||
                            ruleset.technologies[it.revealedBy]!!.era() in earlyEras)
            }.shuffled()
            val candidateTiles = startTile.getTilesAtDistance(2).shuffled()
            for (resource in validResources) {
                val resourcesAdded = MapRegionResources.tryAddingResourceToTiles(
                    tileData, resource, 1, candidateTiles, majorDeposit = false)
                if (resourcesAdded > 0) break
            }
        }
    }

    private fun placeStrategicBalanceResources(
        startTile: Tile,
        ruleset: Ruleset,
        tileData: TileDataMap
    ) {
        val candidateTiles =
            startTile.getTilesInDistanceRange(1..2).shuffled() + startTile.getTilesAtDistance(3)
                .shuffled()
        for (resource in ruleset.tileResources.values.filter { it.hasUnique(UniqueType.StrategicBalanceResource) }) {
            if (MapRegionResources.tryAddingResourceToTiles(
                    tileData,
                    resource,
                    1,
                    candidateTiles,
                    majorDeposit = true
                ) == 0
            ) {
                // Fallback mode - force placement, even on an otherwise inappropriate terrain. Do still respect water and impassible tiles!
                val resourceTiles =
                    if (isWaterOnlyResource(
                            resource,
                            ruleset
                        )
                    ) candidateTiles.filter { it.isWater && !it.isImpassible() }.toList()
                    else candidateTiles.filter { it.isLand && !it.isImpassible() }.toList()
                MapRegionResources.placeResourcesInTiles(
                    tileData,
                    999,
                    resourceTiles,
                    listOf(resource),
                    majorDeposit = true
                )
            }
        }
    }

    /** Check for very food-heavy starts that might still need some stone to help with production */
    private fun addProductionBonuses(startTile: Tile, ruleset: Ruleset) {
        val grassTypePlots = startTile.getTilesInDistanceRange(1..2).filter {
            it.isLand &&
                getPotentialYield(it, Stat.Food, unimproved = true) >= 2f && // Food neutral natively
                getPotentialYield(it, Stat.Production) == 0f // Production can't even be improved
        }.toMutableList()
        val plainsTypePlots = startTile.getTilesInDistanceRange(1..2).filter {
            it.isLand &&
                getPotentialYield(it, Stat.Food) >= 2f && // Something that can be improved to food neutral
                getPotentialYield(it, Stat.Production, unimproved = true) >= 1f // Some production natively
        }.toList()
        var productionBonusesNeeded = when {
            grassTypePlots.size >= 9 && plainsTypePlots.isEmpty() -> 2
            grassTypePlots.size >= 6 && plainsTypePlots.size <= 4 -> 1
            else -> 0
        }
        val productionBonuses =
            ruleset.tileResources.values.filter { it.resourceType == ResourceType.Bonus && it.production > 0 }

        if (productionBonuses.isNotEmpty()) {
            while (productionBonusesNeeded > 0 && grassTypePlots.isNotEmpty()) {
                val plot = grassTypePlots.random()
                grassTypePlots.remove(plot)

                if (plot.resource != null) continue

                val bonusToPlace = productionBonuses.filter { it.generatesNaturallyOn(plot) }.randomOrNull()
                if (bonusToPlace != null) {
                    plot.resource = bonusToPlace.name
                    productionBonusesNeeded--
                }
            }
        }
    }

    private fun calculateFoodBonusesNeeded(
        startTile: Tile,
        minorCiv: Boolean,
        ruleset: Ruleset,
        tileMap: TileMap
    ): Int {
        // evaluate food situation
        // Food²/4 because excess food is really good and lets us work other tiles or run specialists!
        // 2F is worth 1, 3F is worth 2, 4F is worth 4, 5F is worth 6 and so on
        val innerFood =
            startTile.neighbors.sumOf { (getPotentialYield(it, Stat.Food).pow(2) / 4).toInt() }
        val outerFood = startTile.getTilesAtDistance(2)
            .sumOf { (getPotentialYield(it, Stat.Food).pow(2) / 4).toInt() }
        val totalFood = innerFood + outerFood
        // we want at least some two-food tiles to keep growing
        val innerNativeTwoFood =
            startTile.neighbors.count { getPotentialYield(it, Stat.Food, unimproved = true) >= 2f }
        val outerNativeTwoFood = startTile.getTilesAtDistance(2)
            .count { getPotentialYield(it, Stat.Food, unimproved = true) >= 2f }
        val totalNativeTwoFood = innerNativeTwoFood + outerNativeTwoFood

        // Determine number of needed bonuses. Different weightings for minor and major civs.
        var bonusesNeeded = if (minorCiv) {
            when { // From 2 to 0
                totalFood < 12 || innerFood < 4 -> 2
                totalFood < 16 || innerFood < 9 -> 1
                else -> 0
            }
        } else {
            when { // From 5 to 0
                innerFood == 0 && totalFood < 4 -> 5
                totalFood < 6 -> 4
                totalFood < 8 ||
                    (totalFood < 12 && innerFood < 5) -> 3

                (totalFood < 17 && innerFood < 9) ||
                    totalNativeTwoFood < 2 -> 2

                (totalFood < 24 && innerFood < 11) ||
                    totalNativeTwoFood == 2 ||
                    innerNativeTwoFood == 0 ||
                    totalFood < 20 -> 1

                else -> 0
            }
        }
        if (tileMap.mapParameters.getLegendaryStart())
            bonusesNeeded += 2

        // Attempt to place one grassland at a plains-only spot (nor for minors)
        if (!minorCiv && bonusesNeeded < 3 && totalNativeTwoFood == 0) {
            val twoFoodTerrain =
                ruleset.terrains.values.firstOrNull { it.type == TerrainType.Land && it.food >= 2 }?.name
            val candidateInnerSpots = startTile.neighbors
                .filter { it.isLand && !it.isImpassible() && it.terrainFeatures.isEmpty() && it.resource == null }
            val candidateOuterSpots = startTile.getTilesAtDistance(2)
                .filter { it.isLand && !it.isImpassible() && it.terrainFeatures.isEmpty() && it.resource == null }
            val spot =
                candidateInnerSpots.shuffled().firstOrNull() ?: candidateOuterSpots.shuffled()
                    .firstOrNull()
            if (twoFoodTerrain != null && spot != null) {
                spot.baseTerrain = twoFoodTerrain
            } else
                bonusesNeeded = 3 // Irredeemable plains situation
        }
        return bonusesNeeded
    }

    private fun placeFoodBonuses(
        minorCiv: Boolean,
        startTile: Tile,
        ruleset: Ruleset,
        foodBonusesNeeded: Int
    ) {
        var bonusesStillNeeded = foodBonusesNeeded
        val oasisEquivalent = ruleset.terrains.values.firstOrNull {
            it.type == TerrainType.TerrainFeature &&
                it.hasUnique(UniqueType.RareFeature) &&
                it.food >= 2 &&
                it.food + it.production + it.gold >= 3 &&
                it.occursOn.any { base -> ruleset.terrains[base]!!.type == TerrainType.Land }
        }
        var canPlaceOasis =
            oasisEquivalent != null // One oasis per start is enough. Don't bother finding a place if there is no good oasis equivalent
        var placedInFirst = 0 // Attempt to put first 2 in inner ring and next 3 in second ring
        var placedInSecond = 0
        val rangeForBonuses = if (minorCiv) 2 else 3

        // Start with list of candidate plots sorted in ring order 1,2,3
        val candidatePlots = startTile.getTilesInDistanceRange(1..rangeForBonuses)
            .filter { it.resource == null && oasisEquivalent !in it.terrainFeatureObjects }
            .shuffled().sortedBy { it.aerialDistanceTo(startTile) }.toMutableList()

        // Place food bonuses (and oases) as able
        while (bonusesStillNeeded > 0 && candidatePlots.isNotEmpty()) {
            val plot = candidatePlots.first()
            candidatePlots.remove(plot) // remove the plot as it has now been tried, whether successfully or not
            if (plot.getBaseTerrain().hasUnique(
                    UniqueType.BlocksResources,
                    GameContext(attackedTile = plot)
                )
            )
                continue // Don't put bonuses on snow hills

            val validBonuses = ruleset.tileResources.values.filter {
                it.resourceType == ResourceType.Bonus &&
                    it.food >= 1 &&
                    it.generatesNaturallyOn(plot)
            }
            val goodPlotForOasis =
                canPlaceOasis && plot.lastTerrain.name in oasisEquivalent!!.occursOn

            if (validBonuses.isNotEmpty() || goodPlotForOasis) {
                if (goodPlotForOasis) {
                    plot.addTerrainFeature(oasisEquivalent!!.name)
                    canPlaceOasis = false
                } else {
                    plot.setTileResource(validBonuses.random())
                }

                if (plot.aerialDistanceTo(startTile) == 1) {
                    placedInFirst++
                    if (placedInFirst == 2) // Resort the list in ring order 2,3,1
                        candidatePlots.sortBy { abs(it.aerialDistanceTo(startTile) * 10 - 22) }
                } else if (plot.aerialDistanceTo(startTile) == 2) {
                    placedInSecond++
                    if (placedInSecond == 3) // Resort the list in ring order 3,1,2
                        candidatePlots.sortByDescending { abs(it.aerialDistanceTo(startTile) * 10 - 17) }
                }
                bonusesStillNeeded--
            }
        }
    }

    private fun getPotentialYield(tile: Tile, stat: Stat, unimproved: Boolean = false): Float {
        val baseYield = tile.stats.getTileStats(null)[stat]
        if (unimproved) return baseYield

        val bestImprovementYield = tile.tileMap.ruleset!!.tileImprovements.values
            .filter { !it.hasUnique(UniqueType.GreatImprovement) &&
                it.uniqueTo == null &&
                tile.lastTerrain.name in it.terrainsCanBeBuiltOn }
            .maxOfOrNull { it[stat] }
        return baseYield + (bestImprovementYield ?: 0f)
    }

}
