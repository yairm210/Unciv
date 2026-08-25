package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.view.CivView
import com.unciv.view.ForeignCivView
import com.unciv.view.TileView
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.images.ImageGetter
import kotlin.math.PI
import kotlin.math.atan

class TileLayerBorders(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    data class BorderSegment(
        var images: List<Image>,
        var isLeftConcave: Boolean = false,
        var isRightConcave: Boolean = false,
    )

    private var previousTileOwner: ForeignCivView? = null
    private val borderSegments = HashMap<TileView, BorderSegment>()

    fun reset() {
        if (borderSegments.isNotEmpty()) {
            for (borderSegment in borderSegments.values)
                for (image in borderSegment.images)
                    removeOwnedActor(image)
            borderSegments.clear()
        }
    }


    private fun updateBorders() {

        // This is longer than it could be, because of performance -
        // before fixing, about half (!) the time of update() was wasted on
        // removing all the border images and putting them back again!

        val tileView = tileGroup.tileView
        val tileOwner = tileView.getOwner()
        val tileOwnerCiv = tileOwner?.getCiv()

        // If owner changed - clear previous borders
        if (previousTileOwner?.getCiv() !== tileOwnerCiv)
            reset()

        previousTileOwner = tileOwner

        // No owner - no borders
        if (tileOwner == null)
            return

        val tileMapView = tileView.getTileMap()

        // Setup new borders
        val civOuterColor = tileOwner.getOuterColor()
        val civInnerColor = tileOwner.getInnerColor()
        for (neighbor in tileView.getVisibleNeighbors()) {
            var shouldRemoveBorderSegment = false
            var shouldAddBorderSegment = false

            var borderSegmentShouldBeLeftConcave = false
            var borderSegmentShouldBeRightConcave = false

            val neighborOwnerCiv = neighbor.getOwner()?.getCiv()
            if (neighborOwnerCiv === tileOwnerCiv && borderSegments.containsKey(neighbor)) { // the neighbor used to not belong to us, but now it's ours
                shouldRemoveBorderSegment = true
            }
            else if (neighborOwnerCiv !== tileOwnerCiv) {
                val leftSharedNeighbor = tileMapView.getLeftSharedNeighbor(tileView, neighbor)
                val rightSharedNeighbor = tileMapView.getRightSharedNeighbor(tileView, neighbor)

                // If a shared neighbor doesn't exist (because it's past a map edge), we act as if it's our tile for border concave/convex-ity purposes.
                // This is because we do not draw borders against non-existing tiles either.
                borderSegmentShouldBeLeftConcave = leftSharedNeighbor == null || leftSharedNeighbor.getOwner()?.getCiv() === tileOwnerCiv
                borderSegmentShouldBeRightConcave = rightSharedNeighbor == null || rightSharedNeighbor.getOwner()?.getCiv() === tileOwnerCiv

                if (!borderSegments.containsKey(neighbor)) { // there should be a border here but there isn't
                    shouldAddBorderSegment = true
                }
                else if (
                        borderSegmentShouldBeLeftConcave != borderSegments[neighbor]!!.isLeftConcave ||
                        borderSegmentShouldBeRightConcave != borderSegments[neighbor]!!.isRightConcave
                ) { // the concave/convex-ity of the border here is wrong
                    shouldRemoveBorderSegment = true
                    shouldAddBorderSegment = true
                }
            }

            if (shouldRemoveBorderSegment) {
                for (image in borderSegments[neighbor]!!.images)
                    removeOwnedActor(image)
                borderSegments.remove(neighbor)
            }
            if (shouldAddBorderSegment) {
                val images = mutableListOf<Image>()
                val borderSegment = BorderSegment(
                    images,
                    borderSegmentShouldBeLeftConcave,
                    borderSegmentShouldBeRightConcave
                )
                borderSegments[neighbor] = borderSegment

                val borderShapeString = when {
                    borderSegment.isLeftConcave && borderSegment.isRightConcave -> "Concave"
                    !borderSegment.isLeftConcave && !borderSegment.isRightConcave -> "Convex"
                    !borderSegment.isLeftConcave && borderSegment.isRightConcave -> "ConvexConcave"
                    borderSegment.isLeftConcave && !borderSegment.isRightConcave -> "ConcaveConvex"
                    else -> error("This shouldn't happen?")
                }

                val relativeWorldPosition = tileMapView.getNeighborTilePositionAsWorldCoords(tileView, neighbor)

                val sign = if (relativeWorldPosition.x < 0) -1 else 1
                val angle = sign * (atan(sign * relativeWorldPosition.y / relativeWorldPosition.x) * 180 / PI - 90.0).toFloat()

                val innerBorderImage = ImageGetter.getImage(
                    strings.orFallback { getBorder(borderShapeString,"Inner") }
                ).setHexagonSize()

                addOwnedActor(innerBorderImage)
                images.add(innerBorderImage)
                innerBorderImage.rotateBy(angle)
                innerBorderImage.color = civOuterColor

                val outerBorderImage = ImageGetter.getImage(
                    strings.orFallback { getBorder(borderShapeString, "Outer") }
                ).setHexagonSize()

                addOwnedActor(outerBorderImage)
                images.add(outerBorderImage)
                outerBorderImage.rotateBy(angle)
                outerBorderImage.color = civInnerColor
            }
        }

    }

    override fun doUpdate(viewingCiv: CivView?) {
        updateBorders()
    }
}
