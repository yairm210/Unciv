package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.screens.worldscreen.minimap.MinimapTile
import com.unciv.ui.screens.worldscreen.minimap.MinimapTileUtil
import kotlin.math.min
import kotlin.math.sqrt

// Mostly copied from MiniMap

/**
 *  Base for a MiniMap not intertwined with a WorldScreen.
 *  For a _minimal_ implementation see [LoadMapPreview]
 *
 *  TODO: Analyze why MiniMap needs the tight WorldScreen integration and clean up / merge
 */
abstract class IndependentMiniMap(
    val tileMap: TileMap
) : Group() {
    protected lateinit var minimapTiles: List<MinimapTile>

    /** Call this in the init of derived classes.
     *
     * Needs to be deferred only to allow [calcTileSize] or [includeTileFilter] to use class parameters added in the derived class. */
    protected open fun deferredInit(maxWidth: Float, maxHeight: Float) {
        val tileSize = calcTileSize(maxWidth, maxHeight)
        minimapTiles = createReplayMap(tileSize)
        val tileExtension = MinimapTileUtil.spreadOutMinimapTiles(this, minimapTiles, tileSize)

        for (group in children) {
            group.moveBy(-tileExtension.x, -tileExtension.y)
        }

        setSize(tileExtension.width, tileExtension.height)
    }

    /** Calculate a tile radius in screen coordinates so that the resulting map, after distributimg
     *  the tiles using spreadOutMinimapTiles, will not exceed the bounds ([maxWidth],[maxHeight]) */
    protected abstract fun calcTileSize(maxWidth: Float, maxHeight: Float): Float

    /** Controls which tiles are included */
    protected open fun includeTileFilter(tile: Tile): Boolean = true

    private fun createReplayMap(tileSize: Float): List<MinimapTile> {
        @Suppress("EmptyFunctionBlock")
        val doNothing = fun() {}
        val tiles = ArrayList<MinimapTile>(tileMap.values.size)
        for (tile in tileMap.values.filter(::includeTileFilter) ) {
            val minimapTile = MinimapTile(tile, tileSize, doNothing)
            minimapTile.updateColor(false, null)
            tiles.add(minimapTile)
        }
        tiles.trimToSize()
        return tiles
    }
}

/**
 *  A minimap with no WorldScreen dependencies, always shows the entire map.
 *
 *  @param tileMap Map to display minimap-style
 *  @param maxWidth Resulting Group will not exceed this width
 *  @param maxHeight Resulting Group will not exceed this height
 */
class LoadMapPreview(
    tileMap: TileMap,
    maxWidth: Float,
    maxHeight: Float
) : IndependentMiniMap(tileMap) {
    init {
        deferredInit(maxWidth, maxHeight)
    }

    override fun calcTileSize(maxWidth: Float, maxHeight: Float): Float {
        val height: Float
        val width: Float
        val mapSize = tileMap.mapParameters.mapSize
        if (tileMap.mapParameters.shape != MapShape.rectangular) {
            height = mapSize.radius * 2 + 1f
            width = height
        } else {
            height = mapSize.height.toFloat()
            width = mapSize.width.toFloat()
        }
        // See HexMath.worldFromLatLong, the 0.6 is empiric to avoid rounding to cause the map to spill over
        return min(
            maxWidth / (width + 0.6f) / 1.5f * 2f,
            maxHeight / (height + 0.6f) / sqrt(3f) * 2f,
        )
    }
}

/**
 *  A minimap with no WorldScreen dependencies, with the ability to show historical states.
 *
 *  @param tileMap Map to display minimap-style
 *  @param viewingCiv used to determine tile visibility and explored area
 *  @param maxWidth Resulting Group should not exceed this width
 *  @param maxHeight Resulting Group should not exceed this height
 */
class ReplayMap(
    tileMap: TileMap,
    val viewingCiv: Civilization,
    maxWidth: Float,
    maxHeight: Float
) : IndependentMiniMap(tileMap) {
    init {
        deferredInit(maxWidth, maxHeight)
    }

    override fun calcTileSize(maxWidth: Float, maxHeight: Float): Float {
        val height = viewingCiv.exploredRegion.getHeight().toFloat()
        val width = viewingCiv.exploredRegion.getWidth().toFloat()
        return min (
            maxHeight / (height + 1.5f) / sqrt(3f) * 4f, // 1.5 - padding, hex height = sqrt(3) / 2 * d / 2 -> d = height / sqrt(3) * 2 * 2
            maxWidth / (width + 0.5f) / 0.75f // 0.5 - padding, hex width = 0.75 * d -> d = width / 0.75
        )
    }

    override fun includeTileFilter(tile: Tile) = tile.isExplored(viewingCiv)

    fun update(turn: Int) {
        val viewingCivIsDefeated = viewingCiv.gameInfo.victoryData != null || !viewingCiv.isAlive()
        for (minimapTile in minimapTiles) {
            val isVisible = viewingCivIsDefeated || viewingCiv.hasExplored(minimapTile.tile)
            minimapTile.updateColor(!isVisible, turn)
            if (isVisible) {
                minimapTile.updateBorders(turn).updateActorsIn(this)
                minimapTile.updateCityCircle(turn).updateActorsIn(this)
            }
        }
    }
}
