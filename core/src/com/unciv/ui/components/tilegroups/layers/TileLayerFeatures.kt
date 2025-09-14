package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.tilegroups.TileGroup
import kotlin.math.atan2


private class RoadImage {
    var roadStatus: RoadStatus = RoadStatus.None
    var image: Image? = null
}

class TileLayerFeatures(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    private val roadImages = HashMap<Tile, RoadImage>()

    override fun act(delta: Float) {}
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)

    private fun updateRoadImages(viewingCiv: Civilization?) {

        if (tileGroup.isForMapEditorIcon)
            return

        val tile = tileGroup.tile
        val isTileVisible = viewingCiv == null || tile.isVisible(viewingCiv)

        for (neighbor in tile.neighbors) {
            val roadImage = roadImages[neighbor] ?: RoadImage()
                .also { roadImages[neighbor] = it }

            val roadStatus = when {
                !isTileVisible && viewingCiv != null && !neighbor.isVisible(viewingCiv) -> RoadStatus.None // don't show roads on non-visible tiles
                tile.roadStatus == RoadStatus.None || neighbor.roadStatus === RoadStatus.None -> RoadStatus.None
                tile.roadStatus == RoadStatus.Road || neighbor.roadStatus === RoadStatus.Road -> RoadStatus.Road
                else -> RoadStatus.Railroad
            }
            if (roadImage.roadStatus == roadStatus) continue // the image is correct

            roadImage.roadStatus = roadStatus

            if (roadImage.image != null) {
                roadImage.image!!.remove()
                roadImage.image = null
            }
            if (roadStatus == RoadStatus.None) continue // no road image

            val image = ImageGetter.getImage(strings.orFallback { roadsMap[roadStatus]!! })
            roadImage.image = image

            val relativeWorldPosition = tile.tileMap.getNeighborTilePositionAsWorldCoords(tile, neighbor)

            // This is some crazy voodoo magic so I'll explain.
            image.moveBy(25f, 25f) // Move road to center of tile
            // in addTiles, we set   the position of groups by relative world position *0.8*groupSize, filter groupSize = 50
            // Here, we want to have the roads start HALFWAY THERE and extend towards the tiles, so we give them a position of 0.8*25.
            image.moveBy(-relativeWorldPosition.x * 0.8f * 25f, -relativeWorldPosition.y * 0.8f * 25f)

            image.setSize(10f, 6f)
            image.setOrigin(0f, 3f) // This is so that the rotation is calculated from the middle of the road and not the edge

            image.rotation = (180 / Math.PI * atan2(relativeWorldPosition.y.toDouble(),relativeWorldPosition.x.toDouble())).toFloat()

            addActor(image)
        }

    }

    override fun doUpdate(viewingCiv: Civilization?, localUniqueCache: LocalUniqueCache) {
        updateRoadImages(viewingCiv)
    }

    fun dim() {
        color.a = 0.5f
    }

}
