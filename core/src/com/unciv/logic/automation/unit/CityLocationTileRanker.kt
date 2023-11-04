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
    /**
     * Returns a hashmap of tiles to their ranking plus the a the highest value tile and its value
     */
    fun getBestTilesToFoundCity(unit: MapUnit, distanceToSearch: Int? = null): Pair<HashMap<Tile, Float>, Pair<Tile?, Float>> {
        val range =  if (distanceToSearch != null) distanceToSearch else {
            val distanceFromHome = if (unit.civ.cities.isEmpty()) 0
            else unit.civ.cities.minOf { it.getCenterTile().aerialDistanceTo(unit.getTile()) }
            (8 - distanceFromHome).coerceIn(1, 5) // Restrict vision when far from home to avoid death marches
        }
        val nearbyCities = unit.civ.gameInfo.getCities()
            .filter { it.getCenterTile().aerialDistanceTo(unit.getTile()) <= 3 + range }

        val possibleCityLocations = unit.getTile().getTilesInDistance(range)
            .filter { canSettleTile(it, unit.civ, nearbyCities) && (unit.getTile() == it || unit.movement.canMoveTo(it)) }
        val uniqueCache = LocalUniqueCache()
        val baseTileMap = HashMap<Tile, Float>()
        val tileRankMap = HashMap<Tile, Float>()
        var maxValueTile: Tile? = null
        var maxValueRank: Float = 0f
        for (tile in possibleCityLocations) {
            val tileValue = rankTileToSettle(tile, unit.civ, nearbyCities, baseTileMap, uniqueCache)
            if (tileValue > maxValueRank) {
                maxValueTile = tile
                maxValueRank = tileValue
            }
            tileRankMap[tile] = tileValue
        }
        return Pair(tileRankMap, Pair(maxValueTile, maxValueRank))
    }

    private fun canSettleTile(tile: Tile, civ: Civilization, nearbyCities: Sequence<City>): Boolean {
        val modConstants = civ.gameInfo.ruleset.modOptions.constants
        // The AI is allowed to cheat and act like it knows the whole map.
        if (!tile.isExplored(civ) && !civ.isAI()) return false
        if (!tile.isLand || tile.isImpassible()) return false
        if (tile.getOwner() != null && tile.getOwner() != civ) return false
        if (nearbyCities.any {
                it.getCenterTile().aerialDistanceTo(tile) <=
                    if (tile.getContinent() == it.getCenterTile().getContinent()) modConstants.minimalCityDistance
                    else modConstants.minimalCityDistanceOnDifferentContinents
            }) return false
        return true
    }

    private fun rankTileToSettle(newCityTile: Tile, civ: Civilization, nearbyCities: Sequence<City>,
                                 baseTileMap: HashMap<Tile, Float>, uniqueCache: LocalUniqueCache): Float {
        var tileValue = 0f
        for (city in nearbyCities) {
            val distanceToCity = newCityTile.aerialDistanceTo(city.getCenterTile())
            var distanceToCityModifier = when {
                distanceToCity == 5 -> 3
                distanceToCity == 4 -> 6
                distanceToCity == 3 -> 9
                distanceToCity < 3 -> 15 // Even if it is a mod that lets us settle closer, lets still not do it
                else -> 0
            }
            // Bigger cities will expand more so we want to stay away from them
            // Reduces the chance that we don't settle at the begining
            distanceToCityModifier *= when {
                city.population.population >= 12 -> 4
                city.population.population >= 8 -> 3
                city.population.population >= 3 -> 2
                else -> 1
            }
            // It is worse to settle cities near our own compare to near another civ
            // Do not settle near our capital unless really necessary
            // Having a strong capital is esential to constructing wonders
            if (city.civ == civ && city.isCapital())
                distanceToCityModifier *= 2
            tileValue -= distanceToCityModifier
        }

        val onCoast = newCityTile.isCoastalTile()

        fun rankTile(rankTile: Tile): Float {
            var locationSpecificTileValue = 0f
            // Don't settle near but not on the coast
            if (rankTile.isCoastalTile() && !onCoast) locationSpecificTileValue -= 10
            // Apply the effect of having a lighthouse, since we can probably assume that we will build it
            if (onCoast && rankTile.isOcean) locationSpecificTileValue += 1
            // Check if everything else has been calculated, if so return it
            if (baseTileMap.containsKey(rankTile)) return locationSpecificTileValue + baseTileMap[rankTile]!!
            if (rankTile.getOwner() != null && rankTile.getOwner() != civ) return 0f

            var rankTileValue = Automation.rankStatsValue(rankTile.stats.getTileStats(null, civ, uniqueCache), civ)

            if (rankTile.resource != null) {
                rankTileValue += when (rankTile.tileResource.resourceType) {
                    ResourceType.Bonus -> 2f
                    ResourceType.Strategic -> 1.2f * rankTile.resourceAmount
                    ResourceType.Luxury ->
                        // For all normal maps, lets just assume resourceAmmount = 1
                        if (civ.hasResource(rankTile.resource!!)) 3f * rankTile.resourceAmount
                        else 8f + 3f * (rankTile.resourceAmount - 1)
                }
            }

            if (rankTile.isNaturalWonder()) rankTileValue += 10

            baseTileMap[rankTile] = rankTileValue

            return rankTileValue + locationSpecificTileValue
        }

        if (onCoast) tileValue += 10
        if (newCityTile.isAdjacentToRiver()) tileValue += 10
        if (newCityTile.terrainHasUnique(UniqueType.FreshWater)) tileValue += 5
        // We want to found the city on an oasis because it can't be improved otherwise
        if (newCityTile.terrainHasUnique(UniqueType.Unbuildable)) tileValue += 3
        // If we build the city on a resource tile, then we can't build any special improvements on it
        if (newCityTile.resource != null) tileValue -= 4

        for (i in 0..3) {
            for (nearbyTile in newCityTile.getTilesAtDistance(i)) {
                tileValue += rankTile(nearbyTile)
            }
        }
        return tileValue
    }
}
