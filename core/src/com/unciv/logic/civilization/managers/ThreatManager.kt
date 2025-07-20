package com.unciv.logic.civilization.managers

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.screens.victoryscreen.RankingType

/**
 * Handles optimised operations related to finding threats or allies in an area.
 */
class ThreatManager(val civInfo: Civilization) {

    class ClosestEnemyTileData(
        /** The farthest radius in which we have checked tiles for enemies.
         * A value of 2 means all enemies at a radius of 2 are in tilesWithEnemies. */
        var distanceSearched: Int,
        /** Stores the location of the enemy tiles that we saw with the distance at which we saw them.
         * Tiles are sorted by distance in increasing order.
         * This allows us to quickly check if they are still alive and if we should search farther. 
         * It is not guaranteed that each tile in this list has an enemy (since they may have died).*/
        var tilesWithEnemies: MutableList<Pair<Tile,Int>>
    )

    private val distanceToClosestEnemyTiles = HashMap<Tile, ClosestEnemyTileData>()

    /**
     * Gets the distance to the closest visible enemy unit or city.
     * The result value is cached and since it is called each turn in NextTurnAutomation.getUnitPriority 
     * each subsequent calls are likely to be free.
     */
    fun getDistanceToClosestEnemyUnit(tile: Tile, maxDist: Int, takeLargerValues: Boolean = true): Int {
        val tileData = distanceToClosestEnemyTiles[tile]
        // Needs to be a high value, but not the max value so we can still add to it. Example: nextTurnAutomation sorting
        val notFoundDistance = if (takeLargerValues) 500000 else maxDist
        var minDistanceToSearch = 1
        // Look if we can return the cache or if we can reduce our search
        if (tileData != null) {
            val tilesWithEnemies = tileData.tilesWithEnemies
            // Check the tiles where we have previously found an enemy, if so it must be the closest
            while (tilesWithEnemies.isNotEmpty()) {
                val enemyTile = tilesWithEnemies.first()
                if (doesTileHaveMilitaryEnemy(enemyTile.first)) {
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
            for (searchTile in tile.getTilesAtDistance(i)) {
                if (doesTileHaveMilitaryEnemy(searchTile)) {
                    tilesWithEnemyAtDistance.add(Pair(searchTile, i))
                }
            }
            if (tilesWithEnemyAtDistance.isNotEmpty()) {
                distanceToClosestEnemyTiles[tile] = ClosestEnemyTileData(i, tilesWithEnemyAtDistance)
                return i
            }
        }
        distanceToClosestEnemyTiles[tile] = ClosestEnemyTileData(maxDist, mutableListOf())
        return notFoundDistance
    }

    /**
     * Returns all tiles with enemy units on them in distance.
     * Every tile is guaranteed to have an enemy.
     * May be quicker than a manual search because of caching.
     * Also ends up calculating and caching [getDistanceToClosestEnemyUnit].
     */
    fun getTilesWithEnemyUnitsInDistance(tile: Tile, maxDist: Int): MutableList<Tile> {
        val tileData = distanceToClosestEnemyTiles[tile]

        // The list of tiles that we will return
        val tilesWithEnemies: MutableList<Tile> = mutableListOf()
        // The list of tiles with distance that will be stored in distanceToClosestEnemyTiles
        val tileDataTilesWithEnemies: MutableList<Pair<Tile,Int>> = if (tileData?.tilesWithEnemies != null) tileData.tilesWithEnemies else mutableListOf()

        if (tileData != null && tileData.distanceSearched >= maxDist) {
            // Add all tiles that we have previously found
            val tilesWithEnemiesIterator = tileDataTilesWithEnemies.listIterator()
            for (tileWithDistance in tilesWithEnemiesIterator) {
                // Check if the next tile is out of our search range, if so lets stop here
                if (tileWithDistance.second > maxDist) return tilesWithEnemies
                // Check if the threat on the tile is still present
                if (doesTileHaveMilitaryEnemy(tileWithDistance.first))
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
            for (searchTile in tile.getTilesAtDistance(i)) {
                if (doesTileHaveMilitaryEnemy(searchTile)) {
                    tilesWithEnemies.add(searchTile)
                    tileDataTilesWithEnemies.add(Pair(searchTile, i))
                }
            }
        }
        if (tileData != null) {
            tileData.distanceSearched = maxOf(tileData.distanceSearched, maxDist)
        } else {
            // Cache our results for later
            distanceToClosestEnemyTiles[tile] = ClosestEnemyTileData(maxDist, tileDataTilesWithEnemies)
        }
        return tilesWithEnemies
    }

    /**
     * Returns all enemy military units within maxDistance of the tile.
     */
    fun getEnemyMilitaryUnitsInDistance(tile: Tile, maxDist: Int): List<MapUnit> =
        getEnemyUnitsOnTiles(getTilesWithEnemyUnitsInDistance(tile, maxDist))

    /**
     * Returns all enemy units on tiles
     *
     * @param includeCivilians If true enemy civilians are included
     */
    fun getEnemyUnitsOnTiles(tilesWithEnemyUnitsInDistance:List<Tile>, includeCivilians: Boolean = false): List<MapUnit> =
        tilesWithEnemyUnitsInDistance.flatMap { enemyTile -> enemyTile.getUnits()
            .filter { civInfo.isAtWarWith(it.civ) && if (includeCivilians) true else it.isMilitary()  } }
    
    fun getDangerousTiles(unit: MapUnit, distance: Int = 3): HashSet<Tile> {
        val tilesWithEnemyUnits = getTilesWithEnemyUnitsInDistance(unit.getTile(), distance)
        val nearbyRangedEnemyUnits = getEnemyUnitsOnTiles(tilesWithEnemyUnits)

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
    fun doesTileHaveMilitaryEnemy(tile: Tile): Boolean {
        if (!tile.isExplored(civInfo)) return false
        if (tile.isCityCenter() && tile.getCity()!!.civ.isAtWarWith(civInfo)) return true
        if (!tile.isVisible(civInfo)) return false
        if (tile.getUnits().any { it.isMilitary()
            && it.civ.isAtWarWith(civInfo)
            && !it.isInvisible(civInfo) })
            return true
        return false
    }

    /** @return a sequence of pairs of cities, the first city is our city and the second city is a nearby city that is not from our civ. */
    fun getNeighboringCitiesOfOtherCivs(): Sequence<Pair<City,City>> = civInfo.cities.flatMap {
        ourCity -> ourCity.neighboringCities.filter { it.civ != civInfo }.map { Pair(ourCity, it) } 
    }.asSequence()

    fun getNeighboringCivilizations(): Set<Civilization> = civInfo.cities.flatMap { it.neighboringCities }.filter { it.civ != civInfo && civInfo.knows(it.civ) }.map { it.civ }.toSet()

    fun getCombinedForceOfWarringCivs(): Int = civInfo.getCivsAtWarWith().sumOf { it.getStatForRanking(RankingType.Force) } 

    fun clear() {
        distanceToClosestEnemyTiles.clear()
    }
}
