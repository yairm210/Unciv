package com.unciv.logic.civilization.managers

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile

class ThreatManager(val civInfo: Civilization) {

    class ClosestEnemyTileData(
        /** The farthest radius in which we have checked all the tiles for enemies.
         * A value of 2 means there are no enemies in a radius of 2. */
        var distanceSearched: Int,
        /** It is guaranteed that there is no enemy within a radius of D-1.
         * The enemy that we saw might have been killed.
         * so we have to check the tileWithEnemy to see if we need to search again. */
        var distanceToClosestEnemy: Int? = null,
        /** Stores the location of the enemy that we saw.
        * This allows us to quickly check if they are still alive.
        * and if we should search farther. */
        var tileWithEnemy: Tile? = null
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
            if (tileData.distanceToClosestEnemy == null) {
                if (tileData.distanceSearched >= maxDist)
                    return notFoundDistance
                // else: we need to search more we didn't search as far as we are looking for now
            } else if (doesTileHaveMilitaryEnemy(tileData.tileWithEnemy!!)) {
                // The enemy is still there
                return if (tileData.distanceToClosestEnemy!! <= maxDist || takeLargerValues)
                    tileData.distanceToClosestEnemy!!
                else notFoundDistance
            }
            // Only search the tiles that we haven't searched yet
            minDistanceToSearch = (tileData.distanceSearched + 1).coerceAtLeast(1)
        }

        // Search for nearby enemies and store the results
        for (i in minDistanceToSearch..maxDist) {
            for (searchTile in tile.getTilesAtDistance(i)) {
                if (doesTileHaveMilitaryEnemy(searchTile)) {
                    // We have only completely searched a radius of  i - 1 
                    distanceToClosestEnemyTiles[tile] = ClosestEnemyTileData(i - 1, i, searchTile)
                    return i
                }
            }
        }
        distanceToClosestEnemyTiles[tile] = ClosestEnemyTileData(maxDist, null, null)
        return notFoundDistance
    }

    /**
     * Returns all tiles with enemy units on them in distance.
     * May be quicker than a manual search because of caching.
     * Also ends up calculating and caching [getDistanceToClosestEnemyUnit].
     */
    fun getTilesWithEnemyUnitsInDistance(tile: Tile, maxDist: Int): MutableList<Tile> {
        val tileData = distanceToClosestEnemyTiles[tile]
        
        // Shortcut, we don't need to search for anything
        if (tileData != null && maxDist <= tileData.distanceSearched)
            return ArrayList<Tile>()
        
        val minDistanceToSearch = (tileData?.distanceSearched?.coerceAtLeast(0) ?: 0) + 1
        var distanceWithNoEnemies = tileData?.distanceSearched ?: 0
        var closestEnemyDistance = tileData?.distanceToClosestEnemy
        var tileWithEnemy = tileData?.tileWithEnemy
        val tilesWithEnemies = ArrayList<Tile>()

        for (i in minDistanceToSearch..maxDist) {
            for (searchTile in tile.getTilesAtDistance(i)) {
                if (doesTileHaveMilitaryEnemy(searchTile)) {
                    tilesWithEnemies.add(searchTile)
                }
            }
            if (tilesWithEnemies.isEmpty() && distanceWithNoEnemies < i) {
                distanceWithNoEnemies = i
            }
            if (tilesWithEnemies.isNotEmpty() && (closestEnemyDistance == null || closestEnemyDistance < i)) {
                closestEnemyDistance = i
                tileWithEnemy = tilesWithEnemies.first()
            }
        }
        // Cache our results for later
        // tilesWithEnemies must return the enemy at a distance of closestEnemyDistance
        distanceToClosestEnemyTiles[tile] = ClosestEnemyTileData(distanceWithNoEnemies, closestEnemyDistance, tileWithEnemy)
        return tilesWithEnemies
    }

    /**
     * Returns all enemy military units within maxDistance of the tile.
     */
    fun getEnemyMilitaryUnitsInDistance(tile: Tile, maxDist: Int): List<MapUnit> = 
        getEnemyUnitsOnTiles(getTilesWithEnemyUnitsInDistance(tile, maxDist))
    
    fun getEnemyUnitsOnTiles(tilesWithEnemyUnitsInDistance:List<Tile>): List<MapUnit> =
        tilesWithEnemyUnitsInDistance.flatMap { enemyTile -> enemyTile.getUnits()
            .filter { it.isMilitary() && civInfo.isAtWarWith(it.civ) } }

    
    fun getDangerousTiles(unit: MapUnit, distance: Int = 3): HashSet<Tile> {
        val tilesWithEnemyUnits = getTilesWithEnemyUnitsInDistance(unit.getTile(), distance)
        val nearbyRangedEnemyUnits = getEnemyUnitsOnTiles(tilesWithEnemyUnits)

        val tilesInRangeOfAttack = nearbyRangedEnemyUnits
            .flatMap { it.getTile().getTilesInDistance(it.getRange()) }

        val tilesWithinBombardmentRange = tilesWithEnemyUnits
            .filter { it.isCityCenter() && it.getCity()!!.civ.isAtWarWith(unit.civ) }
            .flatMap { it.getTilesInDistance(it.getCity()!!.range) }

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
        if (tile.militaryUnit != null
            && tile.militaryUnit!!.civ.isAtWarWith(civInfo)
            && !tile.militaryUnit!!.isInvisible(civInfo))
            return true
        return false
    }

    fun clear() {
        distanceToClosestEnemyTiles.clear()
    }
}

