package com.unciv.ui.components.tilegroups

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Group
import com.unciv.view.CivView
import com.unciv.view.TileView
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.components.tilegroups.layers.*
import com.unciv.utils.DebugUtils
import kotlin.math.pow
import kotlin.math.sqrt

open class TileGroup(
    tileView: TileView,
    val tileSetStrings: TileSetStrings,
    groupSize: Float = TileGroupMap.groupSize + 4
) : Group() {

    /** A var because if we're spectator, the viewing civ can change as we select different civs to view as */
    var tileView: TileView = tileView
        private set

    val tile: Tile get() = tileView.getTile()
    /*
        Layers (reordered in TileGroupMap):
        1) Terrain
        2) Features: roads
        3) Borders
        4) Misc: improvements, resources, yields, citizens, arrows, starting locations (editor)
        5) Unit Arts
        6) Overlay:
        7) Unit Flags
        8) City Button
    */

    /** Cache simple but frequent calculations.
     * Honestly, I got these numbers empirically by printing `.x` and `.y` after `.center()`, and I'm not totally
     * clear on the stack of transformations that makes them work. But they are still exact ratios, AFAICT. */
    val hexagonImageWidth = groupSize * 1.5f
    val hexagonImageOriginX = hexagonImageWidth / 2f
    val hexagonImageOriginY = sqrt((hexagonImageWidth / 2f).pow(2) - (hexagonImageWidth / 4f).pow(2))
    val hexagonImagePosition = Pair(-hexagonImageOriginX / 3f, -hexagonImageOriginY / 4f)

    var isForceVisible = DebugUtils.VISIBLE_MAP
    var isForMapEditorIcon = false

    @Suppress("LeakingThis") val layerTerrain = TileLayerTerrain(this, groupSize)
    @Suppress("LeakingThis") val layerFeatures = TileLayerFeatures(this, groupSize)
    @Suppress("LeakingThis") val layerBorders = TileLayerBorders(this, groupSize)
    @Suppress("LeakingThis") val layerMisc = TileLayerMisc(this, groupSize)
    @Suppress("LeakingThis") val layerResource = TileLayerResource(this, groupSize)
    @Suppress("LeakingThis") val layerImprovement = TileLayerImprovement(this, groupSize)
    @Suppress("LeakingThis") val layerYield = TileLayerYield(this, groupSize)
    @Suppress("LeakingThis") val layerOverlay = TileLayerOverlay(this, groupSize)
    @Suppress("LeakingThis") val layerUnitArt = TileLayerUnitSprite(this, groupSize)
    @Suppress("LeakingThis") val layerUnitFlag = TileLayerUnitFlag(this, groupSize)
    @Suppress("LeakingThis") val layerCityButton = TileLayerCityButton(this, groupSize)

    private val allLayers = listOf(
        layerTerrain,
        layerFeatures, // includes roads
        layerBorders,
        layerResource,
        layerImprovement,
        layerMisc, // yields, citizens, arrows, starting locations (editor)
        layerYield,
        layerOverlay, // highlight, fog, crosshair
        layerUnitArt,
        layerUnitFlag,
        layerCityButton
    )

    init {
        this.setSize(groupSize, groupSize)
        this.isTransform = false // Cannot be a NonTransformGroup as this causes font-rendered terrain to be upside-down
        // Layers are registered into TileMapLayer containers in TileGroupMap; they are not
        // scene-graph children of this Group (only this Group itself lives in tileGroupLayer).
        layerTerrain.update(null)
    }

    open fun clone() = TileGroup(tileView, tileSetStrings)

    fun isViewable(viewingCiv: CivView) = isForceVisible
            || viewingCiv.canSeeTile(tileView)
            || viewingCiv.isSpectator()

    private fun reset() {
        layerTerrain.reset()
        layerBorders.reset()
        layerMisc.reset()
        layerResource.reset()
        layerImprovement.reset()
        layerYield.reset()
        layerOverlay.reset()
        layerUnitArt.reset()
        layerUnitFlag.reset()
    }

    private fun setAllLayersVisible(isVisible: Boolean) {
        for (layer in allLayers) layer.isVisible = isVisible
    }

    open fun update(viewingCiv: CivView? = null) {
        if (viewingCiv == null) {
            tileView = TileView(tile, null)
        } else {
            val civ = viewingCiv.getCiv()
            if (tileView.getViewer() !== civ)
                tileView = TileView(tile, civ)
        }
        layerMisc.removeHexOutline()
        layerMisc.hideTerrainOverlay()
        layerOverlay.hideHighlight()
        layerOverlay.hideCrosshair()
        layerOverlay.hideGoodCityLocationIndicator()

        // Do not update layers if tile is not explored by viewing player
        if (viewingCiv != null && !(isForceVisible || viewingCiv.hasExplored(tileView))) {
            if (tileView.neighbors.none { viewingCiv.hasExplored(it) }) {
                // No explored neighbors - hide all layers
                setAllLayersVisible(false)
            } else {
                // Has explored neighbors - reveal layers partially
                setAllLayersVisible(true) // visible, but...
                reset() // ...may not contain much
                layerOverlay.setUnexplored(viewingCiv)
            }
            return
        }

        setAllLayersVisible(true)

        removeMissingModReferences()

        for (layer in allLayers) layer.update(viewingCiv)
    }

    private fun removeMissingModReferences() {
        for (unit in tileView.getTile().getUnits())
            if (!tileView.getRuleset().nations.containsKey(unit.owner)) unit.removeFromTile()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) { super.draw(batch, parentAlpha) }
    override fun act(delta: Float) {}
}
