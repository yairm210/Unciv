package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.TileMap
import com.unciv.ui.screens.worldscreen.minimap.MinimapTile
import com.unciv.ui.screens.worldscreen.minimap.MinimapTileUtil
import kotlin.math.min

// Mostly copied from MiniMap
class ReplayMap(val tileMap: TileMap) : Group() {
    private val tileLayer = Group()
    private val minimapTiles: List<MinimapTile>

    init {
        // don't try to resize rotate etc - this table has a LOT of children so that's valuable
        // render time!
        isTransform = false

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
        val mapIsNotRectangular =
                tileMap.mapParameters.shape != com.unciv.logic.map.MapShape.rectangular
        val tileRows = with(tileMap.mapParameters.mapSize) {
            if (mapIsNotRectangular) radius * 2 + 1 else height
        }
        val tileColumns = with(tileMap.mapParameters.mapSize) {
            if (mapIsNotRectangular) radius * 2 + 1 else width
        }
        // 200 is about how much space we need for the top navigation and close button at the
        // bottom.
        val tileSizeToFitHeight = (UncivGame.Current.worldScreen!!.stage.height - 200) / tileRows
        val tileSizeToFitWidth = UncivGame.Current.worldScreen!!.stage.width / tileColumns
        return min(tileSizeToFitHeight, tileSizeToFitWidth)
    }

    private fun createReplayMap(tileSize: Float): List<MinimapTile> {
        val tiles = ArrayList<MinimapTile>()
        for (tile in tileMap.values) {
            val minimapTile = MinimapTile(tile, tileSize) {}
            tiles.add(minimapTile)
        }
        return tiles
    }


    fun update(turn: Int, viewingCiv: Civilization) {
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

    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
}
