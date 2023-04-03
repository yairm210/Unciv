package com.unciv.logic.automation.unit

import com.unciv.logic.automation.Automation
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import kotlin.math.max
import kotlin.math.min

class CityLocationTileRanker {
    companion object {
        fun getBestTilesToFoundCity(unit: MapUnit): List<Pair<Tile, Float>> {
            val modConstants = unit.civ.gameInfo.ruleset.modOptions.constants
            val tilesNearCities = sequence {
                for (city in unit.civ.gameInfo.getCities()) {
                    val center = city.getCenterTile()
                    if (unit.civ.knows(city.civ) &&
                            // If the CITY OWNER knows that the UNIT OWNER agreed not to settle near them
                            city.civ.getDiplomacyManager(unit.civ)
                                .hasFlag(DiplomacyFlags.AgreedToNotSettleNearUs)
                    ) {
                        yieldAll(center.getTilesInDistance(6))
                        continue
                    }
                    yieldAll(center.getTilesInDistance(modConstants.minimalCityDistance)
                        .filter { it.getContinent() == center.getContinent() }
                    )
                    yieldAll(center.getTilesInDistance(modConstants.minimalCityDistanceOnDifferentContinents)
                        .filter { it.getContinent() != center.getContinent() }
                    )
                }
            }.toSet()

            // This is to improve performance - instead of ranking each tile in the area up to 19 times, do it once.
            val nearbyTileRankings = getNearbyTileRankings(unit.getTile(), unit.civ)

            val distanceFromHome = if (unit.civ.cities.isEmpty()) 0
            else unit.civ.cities.minOf { it.getCenterTile().aerialDistanceTo(unit.getTile()) }
            val range = max(
                1,
                min(5, 8 - distanceFromHome)
            ) // Restrict vision when far from home to avoid death marches

            val possibleCityLocations = unit.getTile().getTilesInDistance(range)
                .filter {
                    val tileOwner = it.getOwner()
                    it.isLand && !it.isImpassible() && (tileOwner == null || tileOwner == unit.civ) // don't allow settler to settle inside other civ's territory
                            && (unit.currentTile == it || unit.movement.canMoveTo(it))
                            && it !in tilesNearCities
                }.toList()

            val luxuryResourcesInCivArea = getLuxuryResourcesInCivArea(unit.civ)

            return possibleCityLocations
                .map {
                    Pair(
                        it,
                        rankTileAsCityCenterWithCachedValues(
                            it,
                            nearbyTileRankings,
                            luxuryResourcesInCivArea
                        )
                    )
                }
                .sortedByDescending { it.second }
        }

        fun rankTileAsCityCenter(tile: Tile, civ: Civilization): Float {
            val nearbyTileRankings = getNearbyTileRankings(tile, civ)
            val luxuryResourcesInCivArea = getLuxuryResourcesInCivArea(civ)
            return rankTileAsCityCenterWithCachedValues(tile, nearbyTileRankings, luxuryResourcesInCivArea)
        }

        private fun getNearbyTileRankings(tile: Tile, civ: Civilization): Map<Tile, Float> {
            return tile.getTilesInDistance(7)
                .associateBy({ it }, { Automation.rankTile(it, civ) })
        }

        private fun getLuxuryResourcesInCivArea(civ: Civilization): Sequence<TileResource> {
            return civ.cities.asSequence()
                .flatMap { it.getTiles().asSequence() }.filter { it.resource != null }
                .map { it.tileResource }.filter { it.resourceType == ResourceType.Luxury }
                .distinct()
        }

        private fun rankTileAsCityCenterWithCachedValues(
            tile: Tile, nearbyTileRankings: Map<Tile, Float>,
            luxuryResourcesInCivArea: Sequence<TileResource>
        ): Float {
            val bestTilesFromOuterLayer = tile.getTilesAtDistance(2)
                .sortedByDescending { nearbyTileRankings[it] }.take(2)
            val top5Tiles = (tile.neighbors + bestTilesFromOuterLayer)
                .sortedByDescending { nearbyTileRankings[it] }
                .take(5)
            var rank = top5Tiles.map { nearbyTileRankings.getValue(it) }.sum()
            if (tile.isCoastalTile()) rank += 5

            val luxuryResourcesInCityArea =
                    tile.getTilesAtDistance(2).filter { it.resource != null }
                        .map { it.tileResource }.filter { it.resourceType == ResourceType.Luxury }
                        .distinct()
            val luxuryResourcesAlreadyInCivArea =
                    luxuryResourcesInCivArea.map { it.name }.toHashSet()
            val luxuryResourcesNotYetInCiv = luxuryResourcesInCityArea
                .count { it.name !in luxuryResourcesAlreadyInCivArea }
            rank += luxuryResourcesNotYetInCiv * 10

            return rank
        }
    }
}
