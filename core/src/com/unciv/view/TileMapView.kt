package com.unciv.view

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.Readonly

/** Lazy cache of [TileView]s for a [TileMap] from the perspective of [viewer]. */
class TileMapView(private val tileMap: TileMap,
                  /** Null in map editor */ viewer: Civilization?,
                  spectatorMode: Boolean = false,
                  val gameView: GameView? = null) : View<TileMap>(tileMap, viewer, spectatorMode) {
    @Cache private val tileViews: Array<TileView?> by lazy { arrayOfNulls(tileMap.tileList.size) }

    /** Note that since this requires the original Tile, this CAN return tiles that are NOT visible to the viewing civ
     * This is currently required for the map since to even create the empty tiles we need to know it exists
     * I don't have a good solution for this data leak at the moment 
     * since calculating paths to tiles that don't exist will currently break pathfinding */
    @Readonly fun getTile(tile: Tile): TileView {
        val idx = tile.zeroBasedIndex
        return tileViews[idx] ?: TileView(tile, this, viewer, spectatorMode).also { tileViews[idx] = it }
    }

    @Readonly private fun Tile.toViewIfExplored(): TileView? {
        if (viewer != null && !isExplored(viewer)) return null
        return TileView(this, this@TileMapView, viewer, spectatorMode)
    }

    /** Returns the [TileView] at [position], or `null` if it isn't explored by [viewer]. */
    @Readonly fun getTile(position: HexCoord): TileView? = tileMap[position].toViewIfExplored()

    // Not sure if I want these as part of the API -
    // we can separate the "get coord" part and put it in HexMath,
    // And add a new function of "get tile by coord" in here :thunk:
    // These are really only used for borders so IDK
    @Readonly fun getLeftSharedNeighbor(tile: TileView, neighbor: TileView): TileView? {
        val clockPos = tileMap.getNeighborTileClockPosition(tile.unwrap(), neighbor.unwrap())
        val n = tileMap.getClockPositionNeighborTile(tile.unwrap(), (clockPos - 2) % 12) ?: return null
        return n.toViewIfExplored()
    }

    @Readonly fun getRightSharedNeighbor(tile: TileView, neighbor: TileView): TileView? {
        val clockPos = tileMap.getNeighborTileClockPosition(tile.unwrap(), neighbor.unwrap())
        val n = tileMap.getClockPositionNeighborTile(tile.unwrap(), (clockPos + 2) % 12) ?: return null
        return n.toViewIfExplored()
    }

    @Readonly fun getNeighborTilePositionAsWorldCoords(tile: TileView, neighbor: TileView): Vector2 =
        tileMap.getNeighborTilePositionAsWorldCoords(tile.unwrap(), neighbor.unwrap())

}
