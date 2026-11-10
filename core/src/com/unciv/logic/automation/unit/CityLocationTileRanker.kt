package com.unciv.logic.automation.unit

import com.unciv.Constants
import com.unciv.logic.automation.Automation
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.map.HexMath
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.utils.DebugUtils
import com.unciv.view.CivView
import com.unciv.view.ForeignCityView
import com.unciv.view.MapUnitView
import com.unciv.view.getTerrainMatchingUniques
import com.unciv.view.hasResource
import com.unciv.view.isAdjacentToCoast
import com.unciv.view.isHill
import com.unciv.view.isNaturalWonder
import com.unciv.view.terrainHasUnique
import com.unciv.view.TileView
import yairm210.purity.annotations.Readonly

object CityLocationTileRanker {

    class BestTilesToFoundCity {
        var tileRankMap: HashMap<TileView, Float> = HashMap()
        var bestTile: TileView? = null
        var bestTileRank: Float = 0f
    }

    /**
     * Returns a hashmap of tiles to their ranking plus the a the highest value tile and its value
     */
    fun getBestTilesToFoundCity(unit: MapUnitView, distanceToSearch: Int? = null, minimumValue: Float): BestTilesToFoundCity {
        val civ = unit.civ()
        val unitTile = unit.getTile()
        val distanceModifier = 3f // percentage penalty per aerial distance from unit (Settler)
        val range = if (distanceToSearch != null) distanceToSearch else {
            val distanceFromHome = if (civ.cities().isEmpty()) 0
            else civ.cities().minOf { it.getCenterTile().aerialDistanceTo(unitTile) }
            (8 - distanceFromHome).coerceIn(1, 5) // Restrict vision when far from home to avoid death marches
        }
        // Only cities on tiles we have explored are known to us
        val nearbyCities = unitTile.getExploredTilesInDistance(7 + range).mapNotNull { it.getCity() }.toList()

        // Tile-specific conditionals of the founding uniques can't be checked through the view, so we only know whether the unit can found cities at all
        val canFoundCity = (unit.getMatchingUniques(UniqueType.FoundCity) + unit.getMatchingUniques(UniqueType.FoundPuppetCity)).any()
        val possibleCityLocations = unitTile.getExploredTilesInDistance(range)
            .filter { canFoundCity }
            .filter { canSettleTile(it, civ, nearbyCities) && (unitTile == it || unit.canMoveTo(it)) }
        val bestTilesToFoundCity = BestTilesToFoundCity()
        val baseTileMap = HashMap<TileView, Float>()

        // Assume unexplored tiles are worth the average of the explored tiles around us
        val throwawayLuxuries = HashSet<TileResource>()
        // onCoast doesn't matter here, it does not change baseTileMap
        unitTile.getExploredTilesInDistance(range + 2).forEach { tile ->
            rankTile(tile, civ, false, throwawayLuxuries, baseTileMap)
        }
        val unexploredTilePrior = if (baseTileMap.isEmpty()) 0f else baseTileMap.values.average().toFloat()

        val possibleTileLocationsWithRank = possibleCityLocations
            .map {
                var tileValue = rankTileToSettle(it, civ, nearbyCities, baseTileMap, unexploredTilePrior)
                val distanceScore = (unitTile.aerialDistanceTo(it) * distanceModifier).coerceIn(0f, 99f)
                tileValue *= (100 - distanceScore) / 100
                if (tileValue >= minimumValue)
                    bestTilesToFoundCity.tileRankMap[it] = tileValue

                Pair(it, tileValue)
            }.filter { it.second >= minimumValue }
            .sortedByDescending { it.second }
            .toList()

        val bestReachableTile = possibleTileLocationsWithRank.firstOrNull { unit.canReach(it.first) }
        if (bestReachableTile != null){
            bestTilesToFoundCity.bestTile = bestReachableTile.first
            bestTilesToFoundCity.bestTileRank = bestReachableTile.second
        }

        if (DebugUtils.SHOW_SETTLER_SCORES)
            DebugUtils.SETTLER_SCORES = bestTilesToFoundCity.tileRankMap.entries.associate { it.key.position() to it.value }

        return bestTilesToFoundCity
    }

    @Readonly
    private fun canSettleTile(tile: TileView, civ: CivView, nearbyCities: List<ForeignCityView>): Boolean {
        val modConstants = civ.ruleset.modOptions.constants
        if (!tile.isLand || tile.isImpassible()) return false
        val owner = tile.getOwner()
        if (owner != null && owner != civ) return false
        for (city in nearbyCities) {
            val distance = city.getCenterTile().aerialDistanceTo(tile)
            // todo: AgreedToNotSettleNearUs is hardcoded for now but it may be better to softcode it below in getDistanceToCityModifier
            val cityCiv = city.owningCiv()
            if (distance <= 6 && civ.knows(cityCiv)
                && !civ.isAtWarWith(cityCiv)
                // If the CITY OWNER knows that the UNIT OWNER agreed not to settle near them
                && cityCiv.getDiplomacyManagerWith(civ)!!
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

    private fun rankTileToSettle(newCityTile: TileView, civ: CivView, nearbyCities: List<ForeignCityView>,
                                 baseTileMap: HashMap<TileView, Float>, unexploredTilePrior: Float = 0f): Float {
        var tileValue = 0f
        tileValue += getDistanceToCityModifier(newCityTile, nearbyCities, civ)

        val onCoast = newCityTile.isAdjacentToCoast()
        val onHill = newCityTile.isHill()
        val isNextToMountain = newCityTile.isAdjacentTo(Constants.mountain)
        val unImprovable = newCityTile.getTerrainMatchingUniques(UniqueType.RestrictedBuildableImprovements)
            .any { it.params[0] == Constants.allRoad }

        // Only count a luxury resource that we don't have yet as unique once
        val newUniqueLuxuryResources = HashSet<TileResource>()

        if (onCoast) tileValue += 3
        // Hills are free production and defence
        if (onHill) tileValue += 14
        // Observatories are good, but current implementation not mod-friendly
        if (isNextToMountain) tileValue += 5
        // This bonus for settling on river is a bit outsized for the importance, but otherwise they have a habit of settling 1 tile away
        if (newCityTile.isAdjacentToRiver()) tileValue += 20
        // We want to found the city on an oasis because it can't be improved otherwise
        if (unImprovable) tileValue += 3

        val lastTerrain = newCityTile.getAllTerrains().last()
        val resource = newCityTile.getViewableResource(civ)
        if (resource != null) {
            tileValue -= 4
            // Settling on bonus resources tends to waste a food
            if (resource.resourceType == ResourceType.Bonus) tileValue -= 8
            // Build on jungle luxuries for tempo
            if (resource.resourceType == ResourceType.Luxury &&
                lastTerrain.hasUnique(UniqueType.Vegetation) &&
                !lastTerrain.hasUnique(UniqueType.ProductionBonusWhenRemoved)
                ) tileValue += 10
        }

        var tiles = 0
        for (i in 0..2) {
            //Ideally, we shouldn't really count the center tile, as it's converted into 1 production 2 food anyways with special cases treated above, but doing so can lead to AI moving settler back and forth until forever
            val tilesAtDistance = if (i == 0) sequenceOf(newCityTile) else newCityTile.getExploredTilesAtDistance(i)
            for (nearbyTile in tilesAtDistance) {
                tiles++
                tileValue += rankTile(nearbyTile, civ, onCoast, newUniqueLuxuryResources, baseTileMap) * (3f / (i + 1))
                //Tiles close to the city can be worked more quickly, and thus should gain higher weight.
            }
            // Unexplored tiles are unknown - estimate them as an average tile instead of reading the actual map
            val unexploredTiles = if (i == 0) 0 else newCityTile.estimateUnexploredTilesAtDistance(i)
            tiles += unexploredTiles
            tileValue += unexploredTilePrior * unexploredTiles * (3f / (i + 1))
        }

        // Placing cities on the edge of the map is bad, we can't even build improvements on them!
        tileValue -= (HexMath.getNumberOfTilesInHexagon(2) - tiles) * 2.4f
        return tileValue
    }

    @Readonly
    private fun getDistanceToCityModifier(newCityTile: TileView, nearbyCities: List<ForeignCityView>, civ: CivView): Float {
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
             if (city.owningCiv() == civ) { 
                distanceToCityModifier *= if (city.isCapital()) 2 else 1
                modifier += distanceToCityModifier
            }
        }
        return modifier
    }

    private fun rankTile(rankTile: TileView, civ: CivView, onCoast: Boolean, newUniqueLuxuryResources: HashSet<TileResource>,
                         baseTileMap: HashMap<TileView, Float>): Float {
        if (rankTile.getCity() != null) return -1f
        var locationSpecificTileValue = 0f
        // Don't settle near but not on the coast
        if (rankTile.isWater && !onCoast) locationSpecificTileValue -= 1
        // Check if there are any new unique luxury resources
        val resource = rankTile.getViewableResource(civ)
        if (resource != null &&
            resource.resourceType == ResourceType.Luxury &&
            !civ.hasResource(resource) &&
            !newUniqueLuxuryResources.contains(resource)
        ) {
            locationSpecificTileValue += 10
            newUniqueLuxuryResources.add(resource)
        }

        // Check if everything else has been calculated, if so return it
        if (baseTileMap.containsKey(rankTile)) return locationSpecificTileValue + baseTileMap[rankTile]!!
        val owner = rankTile.getOwner()
        if (owner != null && owner != civ) return 0f

        var rankTileValue = Automation.rankStatsValue(rankTile.getTileStats(civ), civ)

        if (resource != null) {
            rankTileValue += when (resource.resourceType) {
                ResourceType.Bonus -> 1f
                ResourceType.Strategic -> 2f
                ResourceType.Luxury -> 10f //very important for humans who might want to conquer the AI
            }
        }
        if (rankTile.terrainHasUnique(UniqueType.FreshWater)) rankTileValue += 0.5f 
        //Taking into account freshwater farm food, maybe less important in baseruleset mods
        if (rankTile.terrainFeatures.isNotEmpty() && rankTile.getAllTerrains().last().hasUnique(UniqueType.ProductionBonusWhenRemoved)) rankTileValue += 0.7f
        //Taking into account yields from forest chopping

        if (rankTile.isNaturalWonder()) rankTileValue += 4

        baseTileMap[rankTile] = rankTileValue

        return rankTileValue + locationSpecificTileValue
    }

}
