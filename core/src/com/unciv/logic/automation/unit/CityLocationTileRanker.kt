package com.unciv.logic.automation.unit

import com.unciv.logic.automation.Automation
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource

class CityLocationTileRanker {
    companion object {
        fun getBestTilesToFoundCity(unit: MapUnit): Sequence<Pair<Tile, Float>> {
            val modConstants = unit.civ.gameInfo.ruleset.modOptions.constants
            val tilesNearCities = sequence {
                for (city in unit.civ.gameInfo.getCities()) {
                    val center = city.getCenterTile()
                    if (unit.civ.knows(city.civ) &&
                            // If the CITY OWNER knows that the UNIT OWNER agreed not to settle near them
                            city.civ.getDiplomacyManager(unit.civ)
                                .hasFlag(DiplomacyFlags.AgreedToNotSettleNearUs)
                    ) {
                        yieldAll(
                            center.getTilesInDistance(6)
                                .filter { canUseTileForRanking(it, unit.civ) })
                        continue
                    }
                    yieldAll(
                        center.getTilesInDistance(modConstants.minimalCityDistance)
                            .filter { canUseTileForRanking(it, unit.civ) }
                            .filter { it.getContinent() == center.getContinent() }
                    )
                    yieldAll(
                        center.getTilesInDistance(modConstants.minimalCityDistanceOnDifferentContinents)
                            .filter { canUseTileForRanking(it, unit.civ) }
                            .filter { it.getContinent() != center.getContinent() }
                    )
                }
            }.toSet()

            // This is to improve performance - instead of ranking each tile in the area up to 19 times, do it once.
            val nearbyTileRankings = getNearbyTileRankings(unit.getTile(), unit.civ)

            val distanceFromHome = if (unit.civ.cities.isEmpty()) 0
            else unit.civ.cities.minOf { it.getCenterTile().aerialDistanceTo(unit.getTile()) }
            val range = (8 - distanceFromHome).coerceIn(
                1,
                5
            ) // Restrict vision when far from home to avoid death marches

            val possibleCityLocations = unit.getTile().getTilesInDistance(range)
                .filter { canUseTileForRanking(it, unit.civ) }
                .filter {
                    val tileOwner = it.getOwner()
                    it.isLand && !it.isImpassible() && (tileOwner == null || tileOwner == unit.civ) // don't allow settler to settle inside other civ's territory
                            && (unit.currentTile == it || unit.movement.canMoveTo(it))
                            && it !in tilesNearCities
                }

            val luxuryResourcesInCivArea = getLuxuryResourcesInCivArea(unit.civ)

            return possibleCityLocations
                .map {
                    Pair(
                        it,
                        rankTileAsCityCenterWithCachedValues(
                            it,
                            nearbyTileRankings,
                            luxuryResourcesInCivArea,
                            unit.civ
                        ),
                    )
                }
                .sortedByDescending { it.second }
        }

        fun rankTileAsCityCenter(tile: Tile, civ: Civilization): Float {
            val nearbyTileRankings = getNearbyTileRankings(tile, civ)
            val luxuryResourcesInCivArea = getLuxuryResourcesInCivArea(civ)
            return rankTileAsCityCenterWithCachedValues(
                tile,
                nearbyTileRankings,
                luxuryResourcesInCivArea,
                civ
            )
        }

        private fun canUseTileForRanking(
            tile: Tile,
            civ: Civilization
        ) =
                // The AI is allowed to cheat and act like it knows the whole map.
                tile.isExplored(civ) || civ.isAI()

        private fun getNearbyTileRankings(
            tile: Tile,
            civ: Civilization
        ): Map<Tile, Float> {
            return tile.getTilesInDistance(7)
                .filter { canUseTileForRanking(it, civ) }
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
            luxuryResourcesInCivArea: Sequence<TileResource>,
            civ: Civilization
        ): Float {
            val bestTilesFromOuterLayer = tile.getTilesAtDistance(2)
                .filter { canUseTileForRanking(it, civ) }
                .sortedByDescending { nearbyTileRankings[it] }.take(2)
            val top5Tiles =
                    (tile.neighbors.filter {
                        canUseTileForRanking(
                            it,
                            civ
                        )
                    } + bestTilesFromOuterLayer)
                        .sortedByDescending { nearbyTileRankings[it] }
                        .take(5)
            var rank = top5Tiles.map { nearbyTileRankings.getValue(it) }.sum()
            if (tile.isCoastalTile()) rank += 5

            val luxuryResourcesInCityArea =
                    tile.getTilesAtDistance(2).filter { canUseTileForRanking(it, civ) }
                        .filter { it.resource != null }
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
