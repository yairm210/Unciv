package com.unciv.logic.civilization.managers

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile

class ThreatManager(val civInfo: Civilization) {

    class ClosestEnemyTileData {
        var distanceToClosestEnemy: Int? = null
        var distanceToClosestEnemySearched: Int? = null
    }

    @Transient
    val distanceToClosestEnemyTiles = HashMap<Tile, ClosestEnemyTileData>()

    /**
     * Gets the distance to the closest visible enemy unit or city.
     * The result value is cached
     * Since it is called each turn each subsequent call is essentially free
     */
    fun getDistanceToEnemyUnit(tile: Tile, maxDist: Int, takeLargerValues: Boolean = true): Int {
        if (distanceToClosestEnemyTiles.containsKey(tile)) {
            return if ((takeLargerValues || distanceToClosestEnemyTiles[tile]!!.distanceToClosestEnemy!! < maxDist))
                distanceToClosestEnemyTiles[tile]!!.distanceToClosestEnemy!!
            // In some cases we might rely on every distance farther than maxDist being the same
            else 500000
        }

        fun tileHasEnemyCity(tile: Tile): Boolean = tile.isExplored(civInfo)
            && tile.isCityCenter()
            && tile.getCity()!!.civ.isAtWarWith(civInfo)

        fun tileHasEnemyMilitaryUnit(tile: Tile): Boolean = tile.isVisible(civInfo)
            && tile.militaryUnit != null
            && tile.militaryUnit!!.civ.isAtWarWith(civInfo)
            && !tile.militaryUnit!!.isInvisible(civInfo)

        // Needs to be a high value, but not the max value so we can still add to it
        // For example in nextTurnAutomation sorting 
        var distToClosesestEnemy = 500000

        for (i in 1..maxDist) {
            for (searchTile in tile.getTilesAtDistance(i)) {
                if (tileHasEnemyCity(searchTile) || tileHasEnemyMilitaryUnit(searchTile)) {
                    distToClosesestEnemy = i
                    break
                }
            }
        }
        return distToClosesestEnemy
    }
}