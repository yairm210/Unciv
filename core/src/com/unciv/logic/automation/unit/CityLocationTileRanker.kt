package com.unciv.logic.automation.unit

import com.unciv.logic.automation.Automation
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType

object CityLocationTileRanker {

    class BestTilesToFoundCity {
        var tileRankMap: HashMap<Tile, Float> = HashMap()
        var bestTile: Tile? = null
        var bestTileRank: Float = 0f
    }

    /**
     * Returns a hashmap of tiles to their ranking plus the a the highest value tile and its value
     */
    fun getBestTilesToFoundCity(unit: MapUnit, distanceToSearch: Int? = null, minimumValue: Float): BestTilesToFoundCity {
        val range =  if (distanceToSearch != null) distanceToSearch else {
            val distanceFromHome = if (unit.civ.cities.isEmpty()) 0
            else unit.civ.cities.minOf { it.getCenterTile().aerialDistanceTo(unit.getTile()) }
            (8 - distanceFromHome).coerceIn(1, 5) // Restrict vision when far from home to avoid death marches
        }
        val nearbyCities = unit.civ.gameInfo.getCities()
            .filter { it.getCenterTile().aerialDistanceTo(unit.getTile()) <= 7 + range }

        val uniques = unit.getMatchingUniques(UniqueType.FoundCity)
        val possibleCityLocations = unit.getTile().getTilesInDistance(range)
            // Filter out tiles that we can't actually found on
            .filter { tile -> uniques.any { it.conditionalsApply(StateForConditionals(unit = unit, tile = tile)) } }
            .filter { canSettleTile(it, unit.civ, nearbyCities) && (unit.getTile() == it || unit.movement.canMoveTo(it)) }
        val uniqueCache = LocalUniqueCache()
        val bestTilesToFoundCity = BestTilesToFoundCity()
        val baseTileMap = HashMap<Tile, Float>()

        val possibleTileLocationsWithRank = possibleCityLocations
            .map {
                val tileValue = rankTileToSettle(it, unit.civ, nearbyCities, baseTileMap, uniqueCache)
                if (tileValue >= minimumValue)
                    bestTilesToFoundCity.tileRankMap[it] = tileValue

                Pair(it, tileValue)
            }.filter { it.second >= minimumValue }
            .sortedByDescending { it.second }

        val bestReachableTile = possibleTileLocationsWithRank.firstOrNull { unit.movement.canReach(it.first) }
        if (bestReachableTile != null){
            bestTilesToFoundCity.bestTile = bestReachableTile.first
            bestTilesToFoundCity.bestTileRank = bestReachableTile.second
        }

        return bestTilesToFoundCity
    }

    private fun canSettleTile(tile: Tile, civ: Civilization, nearbyCities: Sequence<City>): Boolean {
        val modConstants = civ.gameInfo.ruleset.modOptions.constants
        if (!tile.isLand || tile.isImpassible()) return false
        if (tile.getOwner() != null && tile.getOwner() != civ) return false
        for (city in nearbyCities) {
            val distance = city.getCenterTile().aerialDistanceTo(tile)
            // todo: AgreedToNotSettleNearUs is hardcoded for now but it may be better to softcode it below in getDistanceToCityModifier
            if (distance <= 6 && civ.knows(city.civ)
                && !civ.isAtWarWith(city.civ)
                // If the CITY OWNER knows that the UNIT OWNER agreed not to settle near them
                && city.civ.getDiplomacyManager(civ)!!
                    .hasFlag(DiplomacyFlags.AgreedToNotSettleNearUs))
                return false
            if (tile.getContinent() == city.getCenterTile().getContinent()) {
                if (distance <= modConstants.minimalCityDistance) return false
            } else {
                if (distance <= modConstants.minimalCityDistanceOnDifferentContinents) return false
            }
        }
        return true
    }

    private fun rankTileToSettle(newCityTile: Tile, civ: Civilization, nearbyCities: Sequence<City>,
                                 baseTileMap: HashMap<Tile, Float>, uniqueCache: LocalUniqueCache): Float {
        var tileValue = 0f
        tileValue += getDistanceToCityModifier(newCityTile, nearbyCities, civ)

        val onCoast = newCityTile.isCoastalTile()
        val onHill = newCityTile.isHill()
        val isNextToMountain = newCityTile.isAdjacentTo("Mountain")
        // Only count a luxary resource that we don't have yet as unique once
        val newUniqueLuxuryResources = HashSet<String>()

        if (onCoast) tileValue += 3
        // Hills are free production and defence
        if (onHill) tileValue += 7
        // Observatories are good, but current implementation no mod-friendly
        if (isNextToMountain) tileValue += 5
        if (newCityTile.isAdjacentToRiver()) tileValue += 14
        if (newCityTile.terrainHasUnique(UniqueType.FreshWater)) tileValue += 5
        // We want to found the city on an oasis because it can't be improved otherwise
        if (newCityTile.terrainHasUnique(UniqueType.Unbuildable)) tileValue += 3
        // If we build the city on a resource tile, then we can't build any special improvements on it
        if (newCityTile.resource != null) tileValue -= 4

        var tiles = 0
        for (i in 0..3) {
            for (nearbyTile in newCityTile.getTilesAtDistance(i)) {
                tiles++
                tileValue += rankTile(nearbyTile, civ, onCoast, newUniqueLuxuryResources, baseTileMap, uniqueCache)
            }
        }

        // Placing cities on the edge of the map is bad, we can't even build improvements on them!
        tileValue -= (HexMath.getNumberOfTilesInHexagon(3) - tiles) * 3
        return tileValue
    }

    private fun getDistanceToCityModifier(newCityTile: Tile,nearbyCities: Sequence<City>, civ: Civilization): Float {
        var modifier = 0f
        for (city in nearbyCities) {
            val distanceToCity = newCityTile.aerialDistanceTo(city.getCenterTile())
            var distanceToCityModifier = when {
                // NOTE: the line it.getCenterTile().aerialDistanceTo(unit.getTile()) <= X + range
                // above MUST have the constant X that is added to the range be higher or equal to the highest distance here + 1
                // If it is not higher the settler may get stuck when it ranks the same tile differently
                // as it moves away from the city and doesn't include it in the calculation
                // and values it higher than when it moves closer to the city
                distanceToCity == 7 -> 2f
                distanceToCity == 6 -> 4f
                distanceToCity == 5 -> 8f // Settling further away sacrifices tempo
                distanceToCity == 4 -> 6f
                distanceToCity == 3 -> -25f
                distanceToCity < 3 -> -30f // Even if it is a mod that lets us settle closer, lets still not do it
                else -> 0f
            }
            // We want a defensive ring around our capital
            if (city.civ == civ) distanceToCityModifier *= if (city.isCapital()) 2 else 1
            modifier += distanceToCityModifier
        }
        return modifier
    }

    private fun rankTile(rankTile: Tile, civ: Civilization, onCoast: Boolean, newUniqueLuxuryResources: HashSet<String>,
                         baseTileMap: HashMap<Tile, Float>, uniqueCache: LocalUniqueCache): Float {
        if (rankTile.getCity() != null) return -1f
        var locationSpecificTileValue = 0f
        // Don't settle near but not on the coast
        if (rankTile.isCoastalTile() && !onCoast) locationSpecificTileValue -= 2
        // Apply the effect of having a lighthouse, since we can probably assume that we will build it
        if (onCoast && rankTile.isOcean) locationSpecificTileValue += 1
        // Check if there are any new unique luxury resources
        if (rankTile.resource != null && rankTile.tileResource.resourceType == ResourceType.Luxury
            && !(civ.hasResource(rankTile.resource!!) || newUniqueLuxuryResources.contains(rankTile.resource))) {
            locationSpecificTileValue += 10
            newUniqueLuxuryResources.add(rankTile.resource!!)
        }

        // Check if everything else has been calculated, if so return it
        if (baseTileMap.containsKey(rankTile)) return locationSpecificTileValue + baseTileMap[rankTile]!!
        if (rankTile.getOwner() != null && rankTile.getOwner() != civ) return 0f

        var rankTileValue = Automation.rankStatsValue(rankTile.stats.getTileStats(null, civ, uniqueCache), civ)
        // We can't build improvements on water tiles without resources
        if (!rankTile.isLand) rankTileValue -= 1

        if (rankTile.resource != null) {
            rankTileValue += when (rankTile.tileResource.resourceType) {
                ResourceType.Bonus -> 2f
                ResourceType.Strategic -> 1.2f * rankTile.resourceAmount
                ResourceType.Luxury -> 5f * rankTile.resourceAmount
            }
        }

        if (rankTile.isNaturalWonder()) rankTileValue += 10

        baseTileMap[rankTile] = rankTileValue

        return rankTileValue + locationSpecificTileValue
    }

}
