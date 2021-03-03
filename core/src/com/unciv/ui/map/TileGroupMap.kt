package com.unciv.ui.map

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.logic.HexMath
import com.unciv.logic.map.TileInfo
import com.unciv.ui.tilegroups.TileGroup
import kotlin.math.max
import kotlin.math.min

class TileGroupMap<T: TileGroup>(val tileGroups: Collection<T>, val padding: Float, worldWrap: Boolean = false): Group(){
    var topX = -Float.MAX_VALUE
    var topY = -Float.MAX_VALUE
    var bottomX = Float.MAX_VALUE
    var bottomY = Float.MAX_VALUE
    val groupSize = 50
    private val mirrorTileGroups = HashMap<TileInfo, Pair<T, T>>()

    init{
        if (worldWrap) {
            for(tileGroup in tileGroups) {
                mirrorTileGroups[tileGroup.tileInfo] = Pair(tileGroup.clone() as T, tileGroup.clone() as T)
            }
        }

        for(tileGroup in tileGroups) {
            val positionalVector = HexMath.hex2WorldCoords(tileGroup.tileInfo.position)

            tileGroup.setPosition(positionalVector.x * 0.8f * groupSize.toFloat(),
                    positionalVector.y * 0.8f * groupSize.toFloat())

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
            group.moveBy(-bottomX + padding, -bottomY + padding * 0.5f)
        }

        if (worldWrap) {
            for (mirrorTiles in mirrorTileGroups.values){
                val positionalVector = HexMath.hex2WorldCoords(mirrorTiles.first.tileInfo.position)

                mirrorTiles.first.setPosition(positionalVector.x * 0.8f * groupSize.toFloat(),
                        positionalVector.y * 0.8f * groupSize.toFloat())
                mirrorTiles.first.moveBy(-bottomX + padding - bottomX * 2, -bottomY + padding * 0.5f)

                mirrorTiles.second.setPosition(positionalVector.x * 0.8f * groupSize.toFloat(),
                        positionalVector.y * 0.8f * groupSize.toFloat())
                mirrorTiles.second.moveBy(-bottomX + padding + bottomX * 2, -bottomY + padding * 0.5f)
            }
        }

        val baseLayers = ArrayList<Group>()
        val featureLayers = ArrayList<Group>()
        val miscLayers = ArrayList<Group>()
        val unitLayers = ArrayList<Group>()
        val unitImageLayers = ArrayList<Group>()
        val cityButtonLayers = ArrayList<Group>()
        val circleCrosshairFogLayers = ArrayList<Group>()

        // Apparently the sortedByDescending is kinda memory-intensive because it needs to sort ALL the tiles
        for(group in tileGroups.sortedByDescending { it.tileInfo.position.x + it.tileInfo.position.y }){
            // now, we steal the subgroups from all the tilegroups, that's how we form layers!
            baseLayers.add(group.baseLayerGroup.apply { setPosition(group.x,group.y) })
            featureLayers.add(group.terrainFeatureLayerGroup.apply { setPosition(group.x,group.y) })
            miscLayers.add(group.miscLayerGroup.apply { setPosition(group.x,group.y) })
            unitLayers.add(group.unitLayerGroup.apply { setPosition(group.x,group.y) })
            unitImageLayers.add(group.unitImageLayerGroup.apply { setPosition(group.x,group.y) })
            cityButtonLayers.add(group.cityButtonLayerGroup.apply { setPosition(group.x,group.y) })
            circleCrosshairFogLayers.add(group.circleCrosshairFogLayerGroup.apply { setPosition(group.x,group.y) })

            if (worldWrap){
                for (mirrorTile in mirrorTileGroups[group.tileInfo]!!.toList()){
                    baseLayers.add(mirrorTile.baseLayerGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                    featureLayers.add(mirrorTile.terrainFeatureLayerGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                    miscLayers.add(mirrorTile.miscLayerGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                    unitLayers.add(mirrorTile.unitLayerGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                    unitImageLayers.add(mirrorTile.unitImageLayerGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                    cityButtonLayers.add(mirrorTile.cityButtonLayerGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                    circleCrosshairFogLayers.add(mirrorTile.circleCrosshairFogLayerGroup.apply { setPosition(mirrorTile.x,mirrorTile.y) })
                }
            }
        }
        for(group in baseLayers) addActor(group)
        for(group in featureLayers) addActor(group)
        for(group in miscLayers) addActor(group)
        for(group in circleCrosshairFogLayers) addActor(group)
        for(group in tileGroups) addActor(group) // The above layers are for the visual layers, this is for the clickability of the tile
        if (worldWrap){
            for (mirrorTiles in mirrorTileGroups.values){
                addActor(mirrorTiles.first)
                addActor(mirrorTiles.second)
            }
        }
        for(group in unitLayers) addActor(group) // Aaand units above everything else.
        for(group in unitImageLayers) addActor(group) // This is so the individual textures for the units are rendered together
        for(group in cityButtonLayers) addActor(group) // city buttons + clickability


        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        setSize(topX - bottomX + padding * 2 - groupSize, topY - bottomY + padding * 2 * 0.5f)
    }

    /**
     * Returns the positional coordinates of the TileGroupMap center.
     */
    fun getPositionalVector(stageCoords: Vector2): Vector2 {
        val trueGroupSize = 0.8f * groupSize.toFloat()
        return Vector2(bottomX - padding, bottomY - padding * 0.5f)
                .add(stageCoords)
                .sub(groupSize.toFloat() / 2f, groupSize.toFloat() / 2f)
                .scl(1f / trueGroupSize)
    }

    fun getMirrorTiles(): HashMap<TileInfo, Pair<T, T>> = mirrorTileGroups

    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) { super.draw(batch, parentAlpha) }
    override fun act(delta: Float) { super.act(delta) }
}