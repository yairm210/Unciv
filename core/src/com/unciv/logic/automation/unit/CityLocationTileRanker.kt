package com.unciv.logic.automation.unit

import com.unciv.logic.automation.Automation
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType

object CityLocationTileRanker {
    fun getBestTilesToFoundCity(unit: MapUnit): Sequence<Pair<Tile, Float>> {
        val distanceFromHome = if (unit.civ.cities.isEmpty()) 0
        else unit.civ.cities.minOf { it.getCenterTile().aerialDistanceTo(unit.getTile()) }
        val range = (8 - distanceFromHome).coerceIn(1, 5) // Restrict vision when far from home to avoid death marches
        val nearbyCities = unit.civ.gameInfo.getCities()
            .filter { it.getCenterTile().aerialDistanceTo(unit.getTile()) > 3 + range }

        val possibleCityLocations = unit.getTile().getTilesInDistance(range)
            .filter { canSettleTile(it, unit.civ, nearbyCities) && unit.movement.canMoveTo(it) }
        val uniqueCache = LocalUniqueCache()
        val baseTileMap = HashMap<Tile, Float>()
        return possibleCityLocations.map {
            it to rankTileToSettle(it, unit.civ, nearbyCities, baseTileMap, uniqueCache)}.sortedByDescending { it.second }
    }

    private fun canSettleTile(tile: Tile, civ: Civilization, nearbyCities: Sequence<City>): Boolean {
        val modConstants = civ.gameInfo.ruleset.modOptions.constants
        // The AI is allowed to cheat and act like it knows the whole map.
        if (!(tile.isExplored(civ) || civ.isAI())) return false
        if (!tile.isLand) return false
        if (!(tile.getOwner() == null || tile.getOwner() == civ)) return false
        if (!nearbyCities.any {
                it.getCenterTile().aerialDistanceTo(tile) <
                    if (tile.getContinent() == it.getCenterTile().getContinent()) modConstants.minimalCityDistance
                    else modConstants.minimalCityDistanceOnDifferentContinents
            }) return false
        return true
    }

    private fun rankTileToSettle(tile: Tile, civ: Civilization, nearbyCities: Sequence<City>, 
                                 baseTileMap: HashMap<Tile, Float>, uniqueCache: LocalUniqueCache): Float {
        var tileValue = 0f
        for (city in nearbyCities) {
            val distanceToCity = tile.aerialDistanceTo(city.getCenterTile())
            var distanceToCityModifier = when {
                distanceToCity == 5 -> 20
                distanceToCity == 4 -> 30
                distanceToCity == 3 -> 50
                distanceToCity < 3 -> 100 // Even if it is a mod that lets us settle closer, lets still not do it
                else -> 0
            }
            // It is worse to settle cities near our own compare to near another civ
            if (city.civ == civ)
            // Do not settle near our capital unless really necessary
                distanceToCityModifier *= if (city.isCapital()) 5 else 2
            tileValue -= distanceToCityModifier
        }

        val onCoast = tile.isCoastalTile()

        fun rankTile(rankTile: Tile): Float {
            var locationSpecificTileValue = 0f
            // Don't settle near by not on the coast
            if (tile.isWater && !onCoast) locationSpecificTileValue -= 20
            // Check if everything else has been calculated, if so return it
            if (baseTileMap.containsKey(rankTile)) return locationSpecificTileValue + baseTileMap[rankTile]!!
            if (rankTile.getOwner() != null && rankTile.getOwner() != civ) return 0f
            
            var rankTileValue = Automation.rankStatsValue(rankTile.stats.getTileStats(null, civ, uniqueCache), civ)

            if (rankTile.resource != null) {
                if (rankTile.tileResource.resourceType == ResourceType.Strategic) {
                    rankTileValue += 3 * rankTile.resourceAmount
                } else if (rankTile.tileResource.resourceType == ResourceType.Luxury) {
                    rankTileValue += if (civ.hasResource(rankTile.resource!!)) 10 * rankTile.resourceAmount
                    else 30 + 10 * (rankTile.resourceAmount - 1)
                }
            }
            
            if (rankTile.isNaturalWonder()) rankTileValue += 15

            baseTileMap[rankTile] = rankTileValue

            return rankTileValue + locationSpecificTileValue
        }

        if (onCoast) tileValue += 10
        if (tile.isAdjacentToRiver()) tileValue += 5
        if (tile.terrainHasUnique(UniqueType.FreshWater)) tileValue += 5
        // We want to found the city on an oasis because it can't be improved otherwise
        if (tile.terrainHasUnique(UniqueType.Unbuildable)) tileValue += 5

        for (i in 0..3) {
            for (nearbyTile in tile.getTilesInDistanceRange(IntRange(i, i))) {
                tileValue += rankTile(tile)
            }
        }
        return tileValue
    }
}
