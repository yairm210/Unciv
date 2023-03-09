package com.unciv.ui.screens.worldscreen.minimap

import com.badlogic.gdx.scenes.scene2d.Group
import kotlin.math.max
import kotlin.math.min

object MinimapTileUtil {

    fun spreadOutMinimapTiles(tileLayer: Group, tiles: List<MinimapTile>, tileSize: Float) {
        var topX = 0f
        var topY = 0f
        var bottomX = 0f
        var bottomY = 0f

        for (image in tiles.map { it.image }) {
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
    }
}
