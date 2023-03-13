package com.unciv.ui.screens.worldscreen.minimap

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Group
import kotlin.math.max
import kotlin.math.min

object MinimapTileUtil {

    fun spreadOutMinimapTiles(tileLayer: Group, tiles: List<MinimapTile>, tileSize: Float) : Rectangle {
        var topX = -Float.MAX_VALUE
        var topY = -Float.MAX_VALUE
        var bottomX = Float.MAX_VALUE
        var bottomY = Float.MAX_VALUE

        for (image in tiles.map { it.image }) {
            tileLayer.addActor(image)

            // keeps track of the current top/bottom/left/rightmost tiles to size and position the minimap correctly
            topX = max(topX, image.x + tileSize)
            topY = max(topY, image.y + tileSize)
            bottomX = min(bottomX, image.x)
            bottomY = min(bottomY, image.y)
        }

        return Rectangle(bottomX, bottomY, topX-bottomX, topY-bottomY)
    }
}
