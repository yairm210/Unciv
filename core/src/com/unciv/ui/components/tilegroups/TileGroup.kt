package com.unciv.ui.components.tilegroups

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.components.tilegroups.layers.TileLayerBorders
import com.unciv.ui.components.tilegroups.layers.TileLayerCityButton
import com.unciv.ui.components.tilegroups.layers.TileLayerFeatures
import com.unciv.ui.components.tilegroups.layers.TileLayerMisc
import com.unciv.ui.components.tilegroups.layers.TileLayerOverlay
import com.unciv.ui.components.tilegroups.layers.TileLayerTerrain
import com.unciv.ui.components.tilegroups.layers.TileLayerUnitArt
import com.unciv.ui.components.tilegroups.layers.TileLayerUnitFlag
import kotlin.math.pow
import kotlin.math.sqrt

open class TileGroup(
    var tile: Tile,
    val tileSetStrings: TileSetStrings,
    groupSize: Float = TileGroupMap.groupSize + 4
) : Group() {
    /*
        Layers (reordered in TileGroupMap):
        1) Terrain
        2) Features: roads
        3) Borders
        4) Misc: improvements, resources, yields, citizens, arrows, starting locations (editor)
        5) Unit Arts
        6) Overlay: highlight, fog, crosshair
        7) Unit Flags
        8) City Button
    */

    /** Cache simple but frequent calculations.
     * Honestly, I got these numbers empirically by printing `.x` and `.y` after `.center()`, and I'm not totally
     * clear on the stack of transformations that makes them work. But they are still exact ratios, AFAICT. */
    val hexagonImageWidth = groupSize * 1.5f
    val hexagonImageOrigin = Pair(hexagonImageWidth / 2f, sqrt((hexagonImageWidth / 2f).pow(2) - (hexagonImageWidth / 4f).pow(2)))
    val hexagonImagePosition = Pair(-hexagonImageOrigin.first / 3f, -hexagonImageOrigin.second / 4f)

    var isForceVisible = UncivGame.Current.viewEntireMapForDebug
    var isForMapEditorIcon = false

    @Suppress("LeakingThis") val layerTerrain = TileLayerTerrain(this, groupSize)
    @Suppress("LeakingThis") val layerFeatures = TileLayerFeatures(this, groupSize)
    @Suppress("LeakingThis") val layerBorders = TileLayerBorders(this, groupSize)
    @Suppress("LeakingThis") val layerOverlay = TileLayerOverlay(this, groupSize)
    @Suppress("LeakingThis") val layerUnitArt = TileLayerUnitArt(this, groupSize)
    @Suppress("LeakingThis") val layerUnitFlag = TileLayerUnitFlag(this, groupSize)
    @Suppress("LeakingThis") val layerCityButton = TileLayerCityButton(this, groupSize)
    @Suppress("LeakingThis") val layerMisc = TileLayerMisc(this, groupSize)

    init {
        this.setSize(groupSize, groupSize)
        this.addActor(layerTerrain)
        this.addActor(layerFeatures)
        this.addActor(layerBorders)
        this.addActor(layerMisc)
        this.addActor(layerOverlay)
        this.addActor(layerUnitArt)
        this.addActor(layerUnitFlag)
        this.addActor(layerCityButton)

        layerTerrain.update(null)

        isTransform = false // performance helper - nothing here is rotated or scaled
    }

    open fun clone() = TileGroup(tile, tileSetStrings)

    fun isViewable(viewingCiv: Civilization) = isForceVisible
            || viewingCiv.viewableTiles.contains(tile)
            || viewingCiv.isSpectator()

    private fun reset() {
        layerTerrain.reset()
        layerBorders.reset()
        layerMisc.reset()
        layerOverlay.reset()
        layerUnitArt.reset()
        layerUnitFlag.reset()
    }

    open fun update(viewingCiv: Civilization? = null) {

        layerMisc.removeHexOutline()
        layerOverlay.hideHighlight()
        layerOverlay.hideCrosshair()

        val layers = listOf(
            layerTerrain, layerFeatures, layerBorders, layerMisc,
            layerOverlay, layerUnitArt, layerUnitFlag, layerCityButton)

        // Show all layers by default
        layers.forEach { it.isVisible = true }

        // Do not update layers if tile is not explored by viewing player
        if (viewingCiv != null && !(isForceVisible || viewingCiv.hasExplored(tile))) {
            // If tile has explored neighbors - reveal layers partially
            if (tile.neighbors.any { viewingCiv.hasExplored(it) })
                reset()
            // Else - hide all layers
            else
                layers.forEach { it.isVisible = false }
            return
        }

        removeMissingModReferences()

        layerTerrain.update(viewingCiv)
        layerFeatures.update(viewingCiv)
        layerBorders.update(viewingCiv)
        layerOverlay.update(viewingCiv)
        layerMisc.update(viewingCiv)
        layerUnitArt.update(viewingCiv)
        layerUnitFlag.update(viewingCiv)
        layerCityButton.update(viewingCiv)
    }

    private fun removeMissingModReferences() {
        for (unit in tile.getUnits())
            if (!tile.ruleset.nations.containsKey(unit.owner)) unit.removeFromTile()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) { super.draw(batch, parentAlpha) }
    override fun act(delta: Float) {}
}
