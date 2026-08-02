package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile

/** Lazy cache of [TileView]s for a [TileMap] from the perspective of [viewer]. */
class TileMapView(private val tileMap: TileMap, private val viewer: Civilization) {
    private val tileViews = arrayOfNulls<TileView>(tileMap.tileList.size)

    fun getTile(tile: Tile): TileView {
        val idx = tile.zeroBasedIndex
        return tileViews[idx] ?: TileView(tile, viewer).also { tileViews[idx] = it }
    }
}
