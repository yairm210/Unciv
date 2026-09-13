package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.UncivGame
import com.unciv.view.CivView
import com.unciv.view.TileView
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.tilegroups.TileGroup
import kotlin.math.atan2


private class RoadImage {
    var roadStatus: RoadStatus = RoadStatus.None
    var image: Image? = null
}

class TileLayerFeatures(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    private val roadImages = HashMap<TileView, RoadImage>()

    private fun applyRoadHighlightColor(image: Image, roadStatus: RoadStatus) {
        // Mutate the actor's own Color object in place (via .set) rather than assigning a shared
        // Color instance like Color.WHITE by reference - other code (e.g. dim()) mutates
        // image.color's fields directly, which would otherwise corrupt a shared static Color.
        if (!UncivGame.Current.settings.showRoadHighlight) {
            image.color.set(1f, 1f, 1f, 1f)
            return
        }
        val highlightColor = if (roadStatus == RoadStatus.Railroad) RAILROAD_HIGHLIGHT_COLOR else ROAD_HIGHLIGHT_COLOR
        image.color.set(highlightColor)
    }

    private fun updateRoadImages(viewingCiv: CivView?) {

        if (tileGroup.isForMapEditorIcon)
            return

        val tileView = tileGroup.tileView
        val tileMapView = tileView.getTileMap()
        val isTileVisible = viewingCiv == null || viewingCiv.canSeeTile(tileView)

        for (neighbor in tileView.getVisibleNeighbors()) {
            var roadImage = roadImages[neighbor]
            val currentStatus = roadImage?.roadStatus ?: RoadStatus.None

            val roadStatus = when {
                !isTileVisible && !viewingCiv.canSeeTile(neighbor) -> RoadStatus.None // don't show roads on non-visible tiles
                tileView.roadStatus == RoadStatus.None || neighbor.roadStatus === RoadStatus.None -> RoadStatus.None
                tileView.roadStatus == RoadStatus.Road || neighbor.roadStatus === RoadStatus.Road -> RoadStatus.Road
                else -> RoadStatus.Railroad
            }
            if (currentStatus == roadStatus) {
                // Geometry is already correct, but the highlight toggle may have changed since the last update.
                roadImage?.image?.let { applyRoadHighlightColor(it, roadStatus) }
                continue
            }

            if (roadImage == null) { // create when missing
                roadImage = RoadImage().also { roadImages[neighbor] = it }
                roadImages[neighbor] = roadImage
            }

            roadImage.roadStatus = roadStatus

            if (roadImage.image != null) {
                removeOwnedActor(roadImage.image!!)
                roadImage.image = null
            }
            if (roadStatus == RoadStatus.None) continue // no road image

            val image = ImageGetter.getImage(strings.orFallback { roadsMap[roadStatus]!! })
            roadImage.image = image
            applyRoadHighlightColor(image, roadStatus)

            val relativeWorldPosition = tileMapView.getNeighborTilePositionAsWorldCoords(tileView, neighbor)

            // This is some crazy voodoo magic so I'll explain.
            // Roads start at the tile origin; we offset them to the tile centre then toward the neighbor.
            image.setPosition(
                tileX + 25f - relativeWorldPosition.x * 0.8f * 25f,
                tileY + 25f - relativeWorldPosition.y * 0.8f * 25f
            )

            image.setSize(10f, 6f)
            image.setOrigin(0f, 3f) // This is so that the rotation is calculated from the middle of the road and not the edge

            image.rotation = (180 / Math.PI * atan2(relativeWorldPosition.y.toDouble(),relativeWorldPosition.x.toDouble())).toFloat()

            addOwnedActor(image)
        }

    }

    override fun doUpdate(viewingCiv: CivView?) {
        updateRoadImages(viewingCiv)
    }

    fun dim() {
        forEachOwnedActor { it.color.a = 0.5f }
    }

    companion object {
        /** Vivid colour used to tint roads when [com.unciv.models.metadata.GameSettings.showRoadHighlight] is on. */
        val ROAD_HIGHLIGHT_COLOR: Color = Color(0.05f, 0.85f, 0.75f, 1f) // teal
        /** Vivid colour used to tint railroads when [com.unciv.models.metadata.GameSettings.showRoadHighlight] is on. */
        val RAILROAD_HIGHLIGHT_COLOR: Color = Color(0.95f, 0.55f, 0.05f, 1f) // orange
    }

}
