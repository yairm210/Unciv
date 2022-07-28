package com.unciv.ui.map

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.logic.HexMath
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.ui.cityscreen.CityTileGroup
import com.unciv.ui.tilegroups.ActionlessGroup
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.WorldTileGroup
import kotlin.math.max
import kotlin.math.min


/**
 * A (potentially partial) map view
 * @param T [TileGroup] or a subclass ([WorldTileGroup], [CityTileGroup])
 * @param tileGroups Source of [TileGroup]s to include, will be **iterated several times**.
 * @param tileGroupsToUnwrap For these, coordinates will be unwrapped using [TileMap.getUnWrappedPosition]
 */
class TileGroupMap<T: TileGroup>(
    tileGroups: Iterable<T>,
    worldWrap: Boolean = false,
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

    private var topX = -Float.MAX_VALUE
    private var topY = -Float.MAX_VALUE
    private var bottomX = Float.MAX_VALUE
    private var bottomY = Float.MAX_VALUE
    private val mirrorTileGroups = HashMap<TileInfo, Pair<T, T>>()

    init {
        if (worldWrap) {
            for (tileGroup in tileGroups) {
                @Suppress("UNCHECKED_CAST")  // T is constrained such that casting these TileGroup clones to T should be OK
                mirrorTileGroups[tileGroup.tileInfo] = Pair(tileGroup.clone() as T, tileGroup.clone() as T)
            }
        }

        for (tileGroup in tileGroups) {
            val positionalVector = if (tileGroupsToUnwrap?.contains(tileGroup) == true) {
                HexMath.hex2WorldCoords(
                    tileGroup.tileInfo.tileMap.getUnWrappedPosition(tileGroup.tileInfo.position)
                )
            } else {
                HexMath.hex2WorldCoords(tileGroup.tileInfo.position)
            }

            tileGroup.setPosition(positionalVector.x * 0.8f * groupSize,
                    positionalVector.y * 0.8f * groupSize)

            topX =
                if (worldWrap)
                    // Well it's not pretty but it works
                    // This is so topX is the same no matter what worldWrap is
                    // wrapped worlds are missing one tile width on the right side
                    // which would result in a smaller topX
                    // The resulting topX was always missing 1.2 * groupSize in every possible
                    // combination of map size and shape
                    max(topX, tileGroup.x + groupSize * 2.2f)
                else
                    max(topX, tileGroup.x + groupSize)

            topY = max(topY, tileGroup.y + groupSize)
            bottomX = min(bottomX, tileGroup.x)
            bottomY = min(bottomY, tileGroup.y)
        }

        for (group in tileGroups) {
            group.moveBy(-bottomX, -bottomY)
        }

        if (worldWrap) {
            for (mirrorTiles in mirrorTileGroups.values){
                val positionalVector = HexMath.hex2WorldCoords(mirrorTiles.first.tileInfo.position)

                mirrorTiles.first.setPosition(positionalVector.x * 0.8f * groupSize,
                    positionalVector.y * 0.8f * groupSize)
                mirrorTiles.first.moveBy(-bottomX - bottomX * 2, -bottomY )

                mirrorTiles.second.setPosition(positionalVector.x * 0.8f * groupSize,
                    positionalVector.y * 0.8f * groupSize)
                mirrorTiles.second.moveBy(-bottomX + bottomX * 2, -bottomY)
            }
        }

        val baseLayers = ArrayList<ActionlessGroup>()
        val featureLayers = ArrayList<ActionlessGroup>()
        val borderLayers = ArrayList<ActionlessGroup>()
        val miscLayers = ArrayList<ActionlessGroup>()
        val pixelUnitLayers = ArrayList<ActionlessGroup>()
        val circleFogCrosshairLayers = ArrayList<ActionlessGroup>()
        val unitLayers = ArrayList<Group>()
        val unitImageLayers = ArrayList<ActionlessGroup>()
        val cityButtonLayers = ArrayList<Group>()

        // Apparently the sortedByDescending is kinda memory-intensive because it needs to sort ALL the tiles
        for (group in tileGroups.sortedByDescending { it.tileInfo.position.x + it.tileInfo.position.y }) {
            // now, we steal the subgroups from all the tilegroups, that's how we form layers!
            baseLayers.add(group.baseLayerGroup.apply { setPosition(group.x,group.y) })
            featureLayers.add(group.terrainFeatureLayerGroup.apply { setPosition(group.x,group.y) })
            borderLayers.add(group.borderLayerGroup.apply { setPosition(group.x,group.y) })
            miscLayers.add(group.miscLayerGroup.apply { setPosition(group.x,group.y) })
            pixelUnitLayers.add(group.pixelMilitaryUnitGroup.apply { setPosition(group.x,group.y) })
            pixelUnitLayers.add(group.pixelCivilianUnitGroup.apply { setPosition(group.x,group.y) })
            circleFogCrosshairLayers.add(group.highlightFogCrosshairLayerGroup.apply { setPosition(group.x,group.y) })
            unitLayers.add(group.unitLayerGroup.apply { setPosition(group.x,group.y) })
            unitImageLayers.add(group.unitImageLayerGroup.apply { setPosition(group.x,group.y) })
            cityButtonLayers.add(group.cityButtonLayerGroup.apply { setPosition(group.x,group.y) })

            if (worldWrap) {
                for (mirrorTile in mirrorTileGroups[group.tileInfo]!!.toList()) {
                    baseLayers.add(mirrorTile.baseLayerGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                    featureLayers.add(mirrorTile.terrainFeatureLayerGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                    borderLayers.add(mirrorTile.borderLayerGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                    miscLayers.add(mirrorTile.miscLayerGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                    pixelUnitLayers.add(mirrorTile.pixelMilitaryUnitGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                    pixelUnitLayers.add(mirrorTile.pixelCivilianUnitGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                    circleFogCrosshairLayers.add(mirrorTile.highlightFogCrosshairLayerGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                    unitLayers.add(mirrorTile.unitLayerGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                    unitImageLayers.add(mirrorTile.unitImageLayerGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                    cityButtonLayers.add(mirrorTile.cityButtonLayerGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                }
            }
        }
        for (group in baseLayers) addActor(group)
        for (group in featureLayers) addActor(group)
        for (group in borderLayers) addActor(group)
        for (group in miscLayers) addActor(group)
        for (group in pixelUnitLayers) addActor(group)
        for (group in circleFogCrosshairLayers) addActor(group)
        for (group in tileGroups) addActor(group) // The above layers are for the visual layers, this is for the clickability of the tile
        if (worldWrap) {
            for (mirrorTiles in mirrorTileGroups.values) {
                addActor(mirrorTiles.first)
                addActor(mirrorTiles.second)
            }
        }
        for (group in unitLayers) addActor(group) // Aaand units above everything else.
        for (group in unitImageLayers) addActor(group) // This is so the individual textures for the units are rendered together
        for (group in cityButtonLayers) addActor(group) // city buttons + clickability

        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        // Map's width is reduced by groupSize if it is wrapped, because wrapped map will miss a tile on the right.
        // This ensures that wrapped maps have a smooth transition.
        // If map is not wrapped, Map's width doesn't need to be reduce by groupSize
        if (worldWrap) setSize(topX - bottomX - groupSize, topY - bottomY)
        else setSize(topX - bottomX, topY - bottomY)
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

    fun getMirrorTiles(): HashMap<TileInfo, Pair<T, T>> = mirrorTileGroups

    override fun act(delta: Float) {
        if(shouldAct) {
            super.act(delta)
        }
    }

    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) { super.draw(batch, parentAlpha) }

}
