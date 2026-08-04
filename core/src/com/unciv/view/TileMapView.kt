package com.unciv.view

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.Readonly

/** Lazy cache of [TileView]s for a [TileMap] from the perspective of [viewer]. */
class TileMapView(private val tileMap: TileMap,
                  /** Null in map editor */ private val viewer: Civilization?,
                  val spectatorMode: Boolean = false) {
    @Cache private val tileViews: Array<TileView?> by lazy { arrayOfNulls(tileMap.tileList.size) }

    @Readonly fun getTile(tile: Tile): TileView {
        val idx = tile.zeroBasedIndex
        return tileViews[idx] ?: TileView(tile, viewer, spectatorMode).also { tileViews[idx] = it }
    }

    @Readonly private fun Tile.toViewIfExplored(): TileView? {
        if (viewer != null && !isExplored(viewer)) return null
        return TileView(this, viewer, spectatorMode)
    }

    // Not sure if I want these as part of the API - 
    // we can separate the "get coord" part and put it in HexMath,
    // And add a new function of "get tile by coord" in here :thunk:
    // These are really only used for borders so IDK
    @Readonly fun getLeftSharedNeighbor(tile: TileView, neighbor: TileView): TileView? {
        val clockPos = tileMap.getNeighborTileClockPosition(tile.getTile(), neighbor.getTile())
        val n = tileMap.getClockPositionNeighborTile(tile.getTile(), (clockPos - 2) % 12) ?: return null
        return n.toViewIfExplored()
    }

    @Readonly fun getRightSharedNeighbor(tile: TileView, neighbor: TileView): TileView? {
        val clockPos = tileMap.getNeighborTileClockPosition(tile.getTile(), neighbor.getTile())
        val n = tileMap.getClockPositionNeighborTile(tile.getTile(), (clockPos + 2) % 12) ?: return null
        return n.toViewIfExplored()
    }

    @Readonly fun getNeighborTilePositionAsWorldCoords(tile: TileView, neighbor: TileView): Vector2 =
        tileMap.getNeighborTilePositionAsWorldCoords(tile.getTile(), neighbor.getTile())
}
