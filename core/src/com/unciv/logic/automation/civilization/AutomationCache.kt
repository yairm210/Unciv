package com.unciv.logic.automation.civilization

import com.unciv.view.TileView

/**
 * Holds data that automation caches while a civ's moves are being automated.
 * Created in [NextTurnAutomation.automateCivMoves] and passed down to wherever it's needed - it is never stored on game state,
 * so it cannot go stale between turns.
 */
class AutomationCache {

    class ClosestEnemyTileData(
        /** The farthest radius in which we have checked tiles for enemies.
         * A value of 2 means all enemies at a radius of 2 are in tilesWithEnemies. */
        var distanceSearched: Int,
        /** Stores the location of the enemy tiles that we saw with the distance at which we saw them.
         * Tiles are sorted by distance in increasing order.
         * This allows us to quickly check if they are still alive and if we should search farther.
         * It is not guaranteed that each tile in this list has an enemy (since they may have died).*/
        var tilesWithEnemies: MutableList<Pair<TileView,Int>>
    )

    val distanceToClosestEnemyTiles = HashMap<TileView, ClosestEnemyTileData>()
}
