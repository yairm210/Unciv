package com.unciv.ui.components.tilegroups

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
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
 * @param tileGroupsToUnwrap For these, coordinates will be unwrapped using [TileMap.getUnwrappedPosition]
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

    /** All top-level layer container groups in render order (bottom to top).
     *  Used by the world-wrap draw path to reposition tile-level actors. */
    private val allMapLayers: List<Group>

    init {

        for (tileGroup in tileGroups) {
            val positionalVector = if (tileGroupsToUnwrap?.contains(tileGroup) == true) {
                HexMath.hex2WorldCoords(
                    tileGroup.tile.tileMap.getUnwrappedPosition(tileGroup.tile.position)
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

        val terrainMapLayer     = TileMapLayer<TileLayerTerrain>(numberOfTilegroups)
        val featureMapLayer     = TileMapLayer<TileLayerFeatures>(numberOfTilegroups)
        val borderMapLayer      = TileMapLayer<TileLayerBorders>(numberOfTilegroups)
        val resourceMapLayer    = TileMapLayer<TileLayerResource>(numberOfTilegroups, actable = true)
        val improvementMapLayer = TileMapLayer<TileLayerImprovement>(numberOfTilegroups, actable = true)
        // TileLayerMisc.hit() may delegate to workedIcon, so the container must forward touches
        val miscMapLayer        = TileMapLayer<TileLayerMisc>(numberOfTilegroups, touchable = true)
        val yieldMapLayer       = TileMapLayer<TileLayerYield>(numberOfTilegroups)
        val unitSpriteMapLayer  = TileMapLayer<TileLayerUnitSprite>(numberOfTilegroups)
        val overlayMapLayer     = TileMapLayer<TileLayerOverlay>(numberOfTilegroups)
        // TileGroups themselves provide click detection; not TileLayer subclasses so plain Group
        val tileGroupLayer      = Group().also {
            it.isTransform = false
            it.touchable = Touchable.childrenOnly
            it.children.ensureCapacity(numberOfTilegroups)
        }
        val unitFlagMapLayer    = TileMapLayer<TileLayerUnitFlag>(numberOfTilegroups, actable = true)
        // TileLayerCityButton has Touchable.childrenOnly internally, so the container must forward touches
        val cityButtonMapLayer  = TileMapLayer<TileLayerCityButton>(numberOfTilegroups, actable = true, touchable = true)

        // Apparently the sortedByDescending is kinda memory-intensive because it needs to sort ALL the tiles
        //  So instead we group by and then sort on the groups
        // Profiling is a bit iffy if this is actually better but...probably?
        for (group in tileGroups.groupBy { it.tile.position.x + it.tile.position.y }
            .entries.sortedByDescending { it.key }.flatMap { it.value }) {
            // now, we steal the subgroups from all the tilegroups, that's how we form layers!
            terrainMapLayer.add(group.layerTerrain.apply { setPosition(group.x, group.y) })
            featureMapLayer.add(group.layerFeatures.apply { setPosition(group.x, group.y) })
            borderMapLayer.add(group.layerBorders.apply { setPosition(group.x, group.y) })
            resourceMapLayer.add(group.layerResource.apply { setPosition(group.x, group.y) })
            improvementMapLayer.add(group.layerImprovement.apply { setPosition(group.x, group.y) })
            miscMapLayer.add(group.layerMisc.apply { setPosition(group.x, group.y) })
            yieldMapLayer.add(group.layerYield.apply { setPosition(group.x, group.y) })
            unitSpriteMapLayer.add(group.layerUnitArt.apply { setPosition(group.x, group.y) })
            overlayMapLayer.add(group.layerOverlay.apply { setPosition(group.x, group.y) })
            unitFlagMapLayer.add(group.layerUnitFlag.apply { setPosition(group.x, group.y) })
            cityButtonMapLayer.add(group.layerCityButton.apply { setPosition(group.x, group.y) })
        }

        for (group in tileGroups) tileGroupLayer.addActor(group)

        allMapLayers = listOf(
            terrainMapLayer,
            featureMapLayer,
            borderMapLayer,
            resourceMapLayer,
            improvementMapLayer,
            tileGroupLayer,       // TileGroups for click detection; kept below miscMapLayer so
                                  // miscMapLayer is hit-tested first (city-screen workedIcon clicks)
            miscMapLayer,
            yieldMapLayer,
            unitSpriteMapLayer,
            overlayMapLayer,
            unitFlagMapLayer,
            cityButtonMapLayer
        )

        children.ensureCapacity(allMapLayers.size)
        for (mapLayer in allMapLayers) addActor(mapLayer)

        val mapWidth = topX - bottomX
        val mapHeight = topY - bottomY

        // Each container group must cover the full map so TileGroupMap's culling check always
        // passes for it (actual per-tile culling is done inside each container via its own
        // cullingArea, which is propagated from TileGroupMap's cullingArea in draw()).
        for (mapLayer in allMapLayers) mapLayer.setSize(mapWidth, mapHeight)

        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        setSize(mapWidth, mapHeight)

        cullingArea = Rectangle(0f, 0f, mapWidth, mapHeight)

        maxVisibleMapWidth = mapWidth - groupSize * 1.5f
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
        // Propagate the viewport culling area into each layer container so that individual tile
        // actors are culled correctly. Container groups span the full map so they are never
        // culled at the TileGroupMap level; the real per-tile work happens inside each container.
        val ca = cullingArea
        for (mapLayer in allMapLayers) mapLayer.setCullingArea(ca)

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

                // Reposition tile-level actors within each layer container.
                // Bounds are tracked only from the first layer (all layers share the same positions).
                val referenceLayer = allMapLayers[0]
                for (mapLayer in allMapLayers) {
                    val trackBounds = mapLayer === referenceLayer
                    for (actor in mapLayer.children) {
                        if (beyondRight) {
                            if (actor.x - drawBottomX <= diffRight)
                                actor.x += width
                        } else if (beyondLeft) {
                            if (actor.x + groupSize + 4f >= drawTopX + diffLeft)
                                actor.x -= width
                        }
                        if (trackBounds) {
                            newBottomX = min(newBottomX, actor.x)
                            newTopX = max(newTopX, actor.x + groupSize + 4f)
                        }
                    }
                }

                // Also reposition any actors added directly to this group (e.g. transient overlays).
                for (child in children) {
                    if (child !in allMapLayers) {
                        if (beyondRight) {
                            if (child.x - drawBottomX <= diffRight)
                                child.x += width
                        } else if (beyondLeft) {
                            if (child.x + groupSize + 4f >= drawTopX + diffLeft)
                                child.x -= width
                        }
                    }
                }

                drawBottomX = newBottomX
                drawTopX = newTopX
            }
        }
        super.draw(batch, parentAlpha)
    }
}
