package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.view.CivView
import com.unciv.view.ForeignCivView
import com.unciv.view.TileView
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.tilegroups.mix
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

        // If owner changed - clear previous borders
        if (previousTileOwner != tileOwner)
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

            val neighborOwner = neighbor.getOwner()
            if (neighborOwner == tileOwner && borderSegments.containsKey(neighbor)) { // the neighbor used to not belong to us, but now it's ours
                shouldRemoveBorderSegment = true
            }
            else if (neighborOwner != tileOwner) {
                val leftSharedNeighbor = tileMapView.getLeftSharedNeighbor(tileView, neighbor)
                val rightSharedNeighbor = tileMapView.getRightSharedNeighbor(tileView, neighbor)

                // If a shared neighbor doesn't exist (because it's past a map edge), we act as if it's our tile for border concave/convex-ity purposes.
                // This is because we do not draw borders against non-existing tiles either.
                borderSegmentShouldBeLeftConcave = leftSharedNeighbor == null || leftSharedNeighbor.getOwner() == tileOwner
                borderSegmentShouldBeRightConcave = rightSharedNeighbor == null || rightSharedNeighbor.getOwner() == tileOwner

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

    // Inputs of updateBorders: our owner, and the owners of each visible neighbor and of the two tiles it shares with us
    // (which decide concave/convex, and may themselves be unexplored, so they're not necessarily among the visible neighbors).
    // Owner colors are constant per civ, and a change of owner is reflected in the owner name.
    override fun getUpdateKey(viewingCiv: CivView?): Long {
        val tileView = tileGroup.tileView
        val tileOwner = tileView.getOwner() ?: return 0L // no owner - no borders; nothing else is read
        val tileMapView = tileView.getTileMap()
        var key = 1L.mix(tileOwner.civName)
        for (neighbor in tileView.getVisibleNeighbors()) {
            key = key.mix(neighbor.position()).mix(neighbor.getOwner()?.civName)
                .mix(tileMapView.getLeftSharedNeighbor(tileView, neighbor)?.getOwner()?.civName)
                .mix(tileMapView.getRightSharedNeighbor(tileView, neighbor)?.getOwner()?.civName)
        }
        return key
    }

    override fun doUpdate(viewingCiv: CivView?) {
        updateBorders()
    }
}
