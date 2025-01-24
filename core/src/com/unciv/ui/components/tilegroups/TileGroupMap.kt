package com.unciv.ui.components.tilegroups

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.TileMap
import com.unciv.ui.components.tilegroups.layers.*
import com.unciv.ui.components.widgets.ZoomableScrollPane
import kotlin.math.max
import kotlin.math.min


/**
 * A (potentially partial) map view
 * @param T [TileGroup] or a subclass ([WorldTileGroup], [CityTileGroup])
 * @param tileGroups Source of [TileGroup]s to include, will be **iterated several times**.
 * @param tileGroupsToUnwrap For these, coordinates will be unwrapped using [TileMap.getUnWrappedPosition]
 */
class TileGroupMap<T: TileGroup>(
    val mapHolder: ZoomableScrollPane,
    tileGroups: Iterable<T>,
    val worldWrap: Boolean = false,
    tileGroupsToUnwrap: Set<T>? = null
): Group() {
    
    companion object {
        /** Vertical size of a hex in world coordinates, or the distance between the centers of any two opposing edges
         *  (the hex is oriented so it has corners to the left and right of the center and its upper and lower bounds are horizontal edges) */
        const val groupSize = 50f

        /** Length of the diagonal of a hex, or distance between two opposing corners */
        const val groupSizeDiagonal = groupSize * 1.1547005f  // groupSize * sqrt(4/3)

        /** Horizontal displacement per hex, meaning the increase in overall map size (in world coordinates) when adding a column.
         *  On the hex, this can be visualized as the horizontal distance between the leftmost corner and the
         *  line connecting the two corners at 2 and 4 o'clock. */
        const val groupHorizontalAdvance = groupSizeDiagonal * 3 / 4
        //TODO magic numbers that **seem** like they might depend on these values can be found in
        //   TileGroupMap.getPositionalVector, TileGroup.updateArrows, TileGroup.updateRoadImages
        //   and other places. I can't understand them so I'm leaving cleanup of hardcoding to someone else.
    }

    /** If the [act] method should be performed. If this is false, every child within this [TileGroupMap] will not get their [act] method called
     * and thus not perform any [com.badlogic.gdx.scenes.scene2d.Action]s.
     * Most children here already do not do anything in their [act] methods. However, even iterating through all of them */
    var shouldAct = true
    var shouldHit = true

    private var topX = -Float.MAX_VALUE
    private var topY = -Float.MAX_VALUE
    private var bottomX = Float.MAX_VALUE
    private var bottomY = Float.MAX_VALUE

    private var drawTopX = 0f
    private var drawBottomX = 0f

    private var maxVisibleMapWidth = 0f

    init {

        for (tileGroup in tileGroups) {
            val positionalVector = if (tileGroupsToUnwrap?.contains(tileGroup) == true) {
                HexMath.hex2WorldCoords(
                    tileGroup.tile.tileMap.getUnWrappedPosition(tileGroup.tile.position)
                )
            } else {
                HexMath.hex2WorldCoords(tileGroup.tile.position)
            }

            tileGroup.setPosition(
                positionalVector.x * 0.8f * groupSize,
                positionalVector.y * 0.8f * groupSize
            )

            topX =
                    if (worldWrap)
                    // Well it's not pretty but it works
                    // The resulting topX was always missing 1.2 * groupSize in every possible
                    // combination of map size and shape
                        max(topX, tileGroup.x + groupSize * 1.2f)
                    else
                        max(topX, tileGroup.x + groupSize + 4f)

            topY = max(topY, tileGroup.y + groupSize)
            bottomX = min(bottomX, tileGroup.x)
            bottomY = min(bottomY, tileGroup.y)
        }

        for (group in tileGroups) {
            group.moveBy(-bottomX, -bottomY)
        }

        drawTopX = topX - bottomX
        drawBottomX = bottomX - bottomX

        val numberOfTilegroups = tileGroups.count()
        
        val baseLayers = ArrayList<TileLayerTerrain>(numberOfTilegroups)
        val featureLayers = ArrayList<TileLayerFeatures>(numberOfTilegroups)
        val borderLayers = ArrayList<TileLayerBorders>(numberOfTilegroups)
        val miscLayers = ArrayList<TileLayerMisc>(numberOfTilegroups)
        val yieldLayers = ArrayList<TileLayerYield>(numberOfTilegroups)
        val pixelUnitLayers = ArrayList<TileLayerUnitSprite>(numberOfTilegroups)
        val circleFogCrosshairLayers = ArrayList<TileLayerOverlay>(numberOfTilegroups)
        val unitLayers = ArrayList<TileLayerUnitFlag>(numberOfTilegroups)
        val cityButtonLayers = ArrayList<TileLayerCityButton>(numberOfTilegroups)

        // Apparently the sortedByDescending is kinda memory-intensive because it needs to sort ALL the tiles
        //  So instead we group by and then sort on the groups
        // Profiling is a bit iffy if this is actually better but...probably?
        for (group in tileGroups.groupBy { it.tile.position.x + it.tile.position.y }
            .entries.sortedByDescending { it.key }.flatMap { it.value }) {
            // now, we steal the subgroups from all the tilegroups, that's how we form layers!
            baseLayers.add(group.layerTerrain.apply { setPosition(group.x, group.y) })
            featureLayers.add(group.layerFeatures.apply { setPosition(group.x, group.y) })
            borderLayers.add(group.layerBorders.apply { setPosition(group.x, group.y) })
            miscLayers.add(group.layerMisc.apply { setPosition(group.x, group.y) })
            yieldLayers.add(group.layerYield.apply { setPosition(group.x, group.y) })
            pixelUnitLayers.add(group.layerUnitArt.apply { setPosition(group.x, group.y) })
            circleFogCrosshairLayers.add(group.layerOverlay.apply { setPosition(group.x, group.y) })
            unitLayers.add(group.layerUnitFlag.apply { setPosition(group.x, group.y) })
            cityButtonLayers.add(group.layerCityButton.apply { setPosition(group.x, group.y) })
        }

        val layerLists = listOf(
            baseLayers,
            featureLayers,
            borderLayers,
            miscLayers,
            yieldLayers,
            pixelUnitLayers,
            circleFogCrosshairLayers,
            tileGroups, // The above layers are for the visual layers, this is for the clickability of the tile
            unitLayers,  // Aaand units above everything else.
            cityButtonLayers
        )
        
        // Resize the children list ONCE instead of multiple times with item copying between them
        children.ensureCapacity(layerLists.sumOf { it.count() })
        for (layer in layerLists) 
            for (group in layer) 
                addActor(group)
        

        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        setSize(topX - bottomX, topY - bottomY)

        cullingArea = Rectangle(0f, 0f, width, height)

        maxVisibleMapWidth = width - groupSize * 1.5f
    }

    /**
     * Returns the positional coordinates of the TileGroupMap center.
     */
    fun getPositionalVector(stageCoords: Vector2): Vector2 {
        val trueGroupSize = 0.8f * groupSize
        return Vector2(bottomX, bottomY)
            .add(stageCoords)
            .sub(groupSize / 2f, groupSize / 2f)
            .scl(1f / trueGroupSize)
    }

    override fun act(delta: Float) {
        if (shouldAct)
            super.act(delta)
    }

    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? {
        if (shouldHit)
            return super.hit(x, y, touchable)
        return null
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {

        if (worldWrap) {
            // Prevent flickering when zoomed out so you can see entire map
            val visibleMapWidth =
                    if (mapHolder.width > maxVisibleMapWidth) maxVisibleMapWidth
                    else mapHolder.width

            // Where is viewport's boundaries
            val rightSide = mapHolder.scrollX + visibleMapWidth / 2f
            val leftSide = mapHolder.scrollX - visibleMapWidth / 2f

            // Have we looked beyond map?
            val diffRight = rightSide - drawTopX
            val diffLeft = leftSide - drawBottomX

            val beyondRight = diffRight >= 0f
            val beyondLeft = diffLeft <= 0f

            if (beyondRight || beyondLeft) {

                // If we looked beyond - reposition needed tiles from the other side
                // and update topX and bottomX accordingly.

                var newBottomX = Float.MAX_VALUE
                var newTopX = -Float.MAX_VALUE

                children.forEach {
                    if (beyondRight) {
                        // Move from left to right
                        if (it.x - drawBottomX <= diffRight)
                            it.x += width
                    } else if (beyondLeft) {
                        // Move from right to left
                        if (it.x + groupSize + 4f >= drawTopX + diffLeft)
                            it.x -= width
                    }
                    newBottomX = min(newBottomX, it.x)
                    newTopX = max(newTopX, it.x + groupSize + 4f)
                }

                drawBottomX = newBottomX
                drawTopX = newTopX
            }
        }
        super.draw(batch, parentAlpha)
    }
}
