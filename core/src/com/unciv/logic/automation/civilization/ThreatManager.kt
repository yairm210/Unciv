package com.unciv.logic.automation.civilization

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.screens.victoryscreen.RankingType
import com.unciv.view.MapUnitView
import yairm210.purity.annotations.Readonly

/**
 * Handles optimised operations related to finding threats or allies in an area.
 * Cached results live in the [AutomationCache] that is passed in.
 */
object ThreatManager {

    /**
     * Gets the distance to the closest visible enemy unit or city.
     * The result value is cached and since it is called each turn in NextTurnAutomation.getUnitPriority 
     * each subsequent calls are likely to be free.
     */
    @Readonly @Suppress("purity") //tilesWithEnemies is implicit cache
    fun getDistanceToClosestEnemyUnit(civInfo: Civilization, cache: AutomationCache, tile: Tile, maxDist: Int, takeLargerValues: Boolean = true): Int {
        val tileData = cache.distanceToClosestEnemyTiles[tile]
        // Needs to be a high value, but not the max value so we can still add to it. Example: nextTurnAutomation sorting
        val notFoundDistance = if (takeLargerValues) 500000 else maxDist
        var minDistanceToSearch = 1
        // Look if we can return the cache or if we can reduce our search
        if (tileData != null) {
            val tilesWithEnemies = tileData.tilesWithEnemies
            // Check the tiles where we have previously found an enemy, if so it must be the closest
            while (tilesWithEnemies.isNotEmpty()) {
                val enemyTile = tilesWithEnemies.first()
                if (doesTileHaveMilitaryEnemy(civInfo, enemyTile.first)) {
                    return if (takeLargerValues) enemyTile.second
                    else enemyTile.second.coerceAtMost(maxDist)
                } else {
                    // This tile is no longer valid
                    tilesWithEnemies.removeAt(0)
                }
            }

            if (tileData.distanceSearched > maxDist) {
                // We have already searched past the range we want to search and haven't found any enemies
                return if (takeLargerValues) notFoundDistance else maxDist
            }

            // Only search the tiles that we haven't searched yet
            minDistanceToSearch = (tileData.distanceSearched + 1).coerceAtLeast(1)
        }


        if (tileData != null && tileData.tilesWithEnemies.isNotEmpty()) throw IllegalStateException("There must be no elements in tile.data.tilesWithEnemies at this point")
        val tilesWithEnemyAtDistance: MutableList<Pair<Tile,Int>> = mutableListOf()
        // Search for nearby enemies and store the results
        for (i in minDistanceToSearch..maxDist) {
            tile.forEachTileAtDistance(i) { searchTile ->
                if (doesTileHaveMilitaryEnemy(civInfo, searchTile)) {
                    tilesWithEnemyAtDistance.add(Pair(searchTile, i))
                }
            }
            if (tilesWithEnemyAtDistance.isNotEmpty()) {
                cache.distanceToClosestEnemyTiles[tile] = AutomationCache.ClosestEnemyTileData(i, tilesWithEnemyAtDistance)
                return i
            }
        }
        cache.distanceToClosestEnemyTiles[tile] = AutomationCache.ClosestEnemyTileData(maxDist, mutableListOf())
        return notFoundDistance
    }

    /**
     * Returns all tiles with enemy units on them in distance.
     * Every tile is guaranteed to have an enemy.
     * May be quicker than a manual search because of caching.
     * Also ends up calculating and caching [getDistanceToClosestEnemyUnit].
     */
    @Readonly @Suppress("purity") // actually mutates cached data :think:
    fun getTilesWithEnemyUnitsInDistance(civInfo: Civilization, cache: AutomationCache, tile: Tile, maxDist: Int): MutableList<Tile> {
        val tileData = cache.distanceToClosestEnemyTiles[tile]

        // The list of tiles that we will return
        val tilesWithEnemies: MutableList<Tile> = mutableListOf()
        // The list of tiles with distance that will be stored in cache.distanceToClosestEnemyTiles
        val tileDataTilesWithEnemies: MutableList<Pair<Tile,Int>> = if (tileData?.tilesWithEnemies != null) tileData.tilesWithEnemies else mutableListOf()

        if (tileData != null) {
            // Add all tiles that we have previously found
            val tilesWithEnemiesIterator = tileDataTilesWithEnemies.listIterator()
            for (tileWithDistance in tilesWithEnemiesIterator) {
                // Check if the next tile is out of our search range, if so lets stop here
                if (tileWithDistance.second > maxDist) return tilesWithEnemies
                // Check if the threat on the tile is still present
                if (doesTileHaveMilitaryEnemy(civInfo, tileWithDistance.first))
                    tilesWithEnemies.add(tileWithDistance.first)
                else tilesWithEnemiesIterator.remove()
            }
        }

        // We don't need to search for anything more if we have previously searched past maxDist
        if (tileData != null && maxDist <= tileData.distanceSearched)
            return tilesWithEnemies


        // Search all tiles that haven't been searched yet up until madDist
        val minDistanceToSearch = (tileData?.distanceSearched?.coerceAtLeast(0) ?: 0) + 1

        for (i in minDistanceToSearch..maxDist) {
            tile.forEachTileAtDistance(i) { searchTile ->
                if (doesTileHaveMilitaryEnemy(civInfo, searchTile)) {
                    tilesWithEnemies.add(searchTile)
                    tileDataTilesWithEnemies.add(Pair(searchTile, i))
                }
            }
        }
        if (tileData != null) {
            tileData.distanceSearched = maxOf(tileData.distanceSearched, maxDist)
        } else {
            // Cache our results for later
            cache.distanceToClosestEnemyTiles[tile] = AutomationCache.ClosestEnemyTileData(maxDist, tileDataTilesWithEnemies)
        }
        return tilesWithEnemies
    }

    /**
     * Returns all enemy military units on tiles
     */
    @Readonly
    fun getEnemyUnitsOnTiles(civInfo: Civilization, tilesWithEnemyUnitsInDistance:List<Tile>): List<MapUnit> =
        tilesWithEnemyUnitsInDistance.flatMap { enemyTile -> enemyTile.getUnits()
            .filter { it.isMilitary() && civInfo.isAtWarWith(it.civ) } }

    @Readonly
    fun getDangerousTiles(cache: AutomationCache, unitView: MapUnitView, distance: Int = 3): HashSet<Tile> {
        // Temporary - tile logic below still works on MapUnit
        val unit = unitView.getUnit()
        val tilesWithEnemyUnits = getTilesWithEnemyUnitsInDistance(unit.civ, cache, unit.getTile(), distance)
        val nearbyRangedEnemyUnits = getEnemyUnitsOnTiles(unit.civ, tilesWithEnemyUnits)

        val tilesInRangeOfAttack = nearbyRangedEnemyUnits
            .flatMap { it.getTile().getTilesInDistance(it.getRange()) }

        val tilesWithinBombardmentRange = tilesWithEnemyUnits
            .filter { it.isCityCenter() && it.getCity()!!.civ.isAtWarWith(unit.civ) }
            .flatMap { it.getTilesInDistance(it.getCity()!!.getBombardRange()) }

        val tilesWithTerrainDamage = unit.currentTile.getTilesInDistance(distance)
            .filter { unit.getDamageFromTerrain(it) > 0 }

        return (tilesInRangeOfAttack + tilesWithinBombardmentRange + tilesWithTerrainDamage).toHashSet()
    }

    /**
     * Returns true if the tile has a visible enemy, otherwise returns false.
     */
    @Readonly
    fun doesTileHaveMilitaryEnemy(civInfo: Civilization, tile: Tile): Boolean {
        if (!tile.isExplored(civInfo)) return false
        if (tile.isCityCenter() && tile.getCity()!!.civ.isAtWarWith(civInfo)) return true
        if (!tile.isVisible(civInfo)) return false
        if (tile.getUnits().any { it.isMilitary()
            && it.civ.isAtWarWith(civInfo)
            && it.isVisibleTo(civInfo) })
            return true
        return false
    }

    /** @return a sequence of pairs of cities, the first city is our city and the second city is a nearby city that is not from our civ. */
    @Readonly
    fun getNeighboringCitiesOfOtherCivs(civInfo: Civilization): Sequence<Pair<City,City>> = civInfo.cities.flatMap {
        ourCity -> ourCity.getNeighboringCities().filter { it.civ != civInfo }.map { Pair(ourCity, it) }
    }.asSequence()

    @Readonly fun getNeighboringCivilizations(civInfo: Civilization): Set<Civilization> = civInfo.cities
        .flatMap { it.getNeighboringCities() }
        .filter { it.civ != civInfo && civInfo.knows(it.civ) }
        .map { it.civ }.toSet()

    @Readonly fun getCombinedForceOfWarringCivs(civInfo: Civilization): Int = civInfo.getCivsAtWarWith().sumOf { it.getStatForRanking(RankingType.Force) } 
}
