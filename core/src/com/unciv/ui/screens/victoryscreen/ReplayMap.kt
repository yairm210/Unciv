package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.UncivGame
import com.unciv.logic.map.TileMap
import com.unciv.ui.screens.worldscreen.minimap.MinimapTile
import kotlin.math.max
import kotlin.math.min

// Mostly copied from MiniMap
class ReplayMap(val tileMap: TileMap) : Group() {
    private val tileLayer = Group()
    private val minimapTiles: List<MinimapTile>

    init {
        // don't try to resize rotate etc - this table has a LOT of children so that's valuable
        // render time!
        isTransform = false

        var topX = 0f
        var topY = 0f
        var bottomX = 0f
        var bottomY = 0f

        val tileSize = calcTileSize(22)
        minimapTiles = createReplayMap(tileSize)
        for (image in minimapTiles.map { it.image }) {
            tileLayer.addActor(image)

            // keeps track of the current top/bottom/left/rightmost tiles to size and position the
            // minimap correctly
            topX = max(topX, image.x + tileSize)
            topY = max(topY, image.y + tileSize)
            bottomX = min(bottomX, image.x)
            bottomY = min(bottomY, image.y)
        }

        for (group in tileLayer.children) {
            group.moveBy(-bottomX, -bottomY)
        }

        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        tileLayer.setSize(topX - bottomX, topY - bottomY)

        setSize(tileLayer.width, tileLayer.height)
        addActor(tileLayer)
    }

    private fun calcTileSize(minimapSize: Int): Float {
        // Support rectangular maps with extreme aspect ratios by scaling to the larger coordinate
        // with a slight weighting to make the bounding box 4:3
        val effectiveRadius = with(tileMap.mapParameters) {
            if (shape != com.unciv.logic.map.MapShape.rectangular) mapSize.radius
            else max(
                mapSize.height,
                mapSize.width * 3 / 4
            ) * com.unciv.logic.map.MapSize.Huge.radius / com.unciv.logic.map.MapSize.Huge.height
        }
        val mapSizePercent = if (minimapSize < 22) minimapSize + 9 else minimapSize * 5 - 75
        val smallerWorldDimension =
                UncivGame.Current.worldScreen!!.stage.let { min(it.width, it.height) }
        return smallerWorldDimension * mapSizePercent / 100 / effectiveRadius
    }

    private fun createReplayMap(tileSize: Float): List<MinimapTile> {
        val tiles = ArrayList<MinimapTile>()
        for (tile in tileMap.values) {
            val minimapTile = MinimapTile(tile, tileSize) {}
            tiles.add(minimapTile)
        }
        return tiles
    }


    fun update(turn: Int) {
        for (minimapTile in minimapTiles) {
            minimapTile.updateColor(false, turn)
            minimapTile.updateBorders(turn).updateActorsIn(this)
            minimapTile.updateCityCircle(turn).updateActorsIn(this)
        }
    }

    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
}
