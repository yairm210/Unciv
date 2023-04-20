package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.TileMap
import com.unciv.ui.screens.worldscreen.minimap.MinimapTile
import com.unciv.ui.screens.worldscreen.minimap.MinimapTileUtil
import kotlin.math.min
import kotlin.math.sqrt

// Mostly copied from MiniMap
class ReplayMap(
    val tileMap: TileMap,
    val viewingCiv: Civilization?,
    private val replayMapWidth: Float,
    private val replayMapHeight: Float
) : Group() {
    private val tileLayer = Group()
    private val minimapTiles: List<MinimapTile>

    init {
        val tileSize = calcTileSize()
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

    private fun calcTileSize(): Float {
        val height = viewingCiv?.exploredRegion?.getHeight() ?: tileMap.mapParameters.mapSize.height
        val width = viewingCiv?.exploredRegion?.getWidth() ?: tileMap.mapParameters.mapSize.width
        return min(
            replayMapHeight / (height + 1.5f) / sqrt(3f) * 4f, // 1.5 - padding, hex height = sqrt(3) / 2 * d / 2 -> d = height / sqrt(3) * 2 * 2
            replayMapWidth / (width + 0.5f) / 0.75f // 0.5 - padding, hex width = 0.75 * d -> d = width / 0.75
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
