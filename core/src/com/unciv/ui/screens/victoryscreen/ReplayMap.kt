package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.TileMap
import com.unciv.ui.screens.worldscreen.minimap.MinimapTile
import com.unciv.ui.screens.worldscreen.minimap.MinimapTileUtil
import kotlin.math.min
import kotlin.math.sqrt

// Mostly copied from MiniMap

/**
 *  A minimap with no WorldScreen dependencies.
 *  @param tileMap Map to display minimap-style
 *  @param viewingCiv used to determine tile visibility and explored area. If `null`, the entire map is shown.
 *  @param replayMapWidth Resulting Group will not exceed this width
 *  @param replayMapHeight Resulting Group will not exceed this height
 */
class ReplayMap(
    val tileMap: TileMap,
    val viewingCiv: Civilization?,
    replayMapWidth: Float,
    replayMapHeight: Float
) : Group() {
    private val tileLayer = Group()
    private val minimapTiles: List<MinimapTile>

    init {
        val tileSize = calcTileSize(replayMapWidth, replayMapHeight)
        minimapTiles = createReplayMap(tileSize)
        val tileExtension = MinimapTileUtil.spreadOutMinimapTiles(tileLayer, minimapTiles, tileSize)

        for (group in tileLayer.children) {
            group.moveBy(-tileExtension.x, -tileExtension.y)
        }
        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well

        tileLayer.setSize(tileExtension.width, tileExtension.height)
        setSize(tileLayer.width, tileLayer.height)
        addActor(tileLayer)
    }

    private fun calcTileSize(replayMapWidth: Float, replayMapHeight: Float): Float {
        val height: Float
        val width: Float
        val mapSize = tileMap.mapParameters.mapSize

        if (viewingCiv != null) {
            //TODO (ST): 0.5 isn't strictly correct. getHeight will give e.g. just under 4*radius on a mostly explored hexagonal map, because it speaks in latitude not hexes.
            height = viewingCiv.exploredRegion.getHeight() * 0.5f
            width = viewingCiv.exploredRegion.getWidth().toFloat()
        } else {
            if (tileMap.mapParameters.shape != MapShape.rectangular) {
                height = mapSize.radius * 2 + 1f
                width = height
            } else {
                height = mapSize.height.toFloat()
                width = mapSize.width.toFloat()
            }
        }

        // See HexMath.worldFromLatLong, the 0.6 is empiric to avoid rounding to cause the map to spill over
        return min(
            replayMapHeight / (height + 0.6f) / sqrt(3f) * 2f,
            replayMapWidth / (width + 0.6f) / 1.5f * 2f
        )
    }

    private fun createReplayMap(tileSize: Float): List<MinimapTile> {
        val tiles = ArrayList<MinimapTile>()
        for (tile in tileMap.values.filter { viewingCiv == null || it.isExplored(viewingCiv) }) {
            val minimapTile = MinimapTile(tile, tileSize) {}
            tiles.add(minimapTile)
        }
        return tiles
    }

    fun update(turn: Int) {
        val viewingCivIsDefeated = viewingCiv == null || viewingCiv.gameInfo.victoryData != null || !viewingCiv.isAlive()
        for (minimapTile in minimapTiles) {
            val isVisible = viewingCivIsDefeated || viewingCiv!!.hasExplored(minimapTile.tile)
            minimapTile.updateColor(!isVisible, turn)
            if (isVisible) {
                minimapTile.updateBorders(turn).updateActorsIn(this)
                minimapTile.updateCityCircle(turn).updateActorsIn(this)
            }
        }
    }
}
