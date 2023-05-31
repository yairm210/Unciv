package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.images.ImageGetter

class TileLayerOverlay(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    override fun act(delta: Float) {}
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null

    private val highlight = ImageGetter.getImage(strings().highlight).setHexagonSize() // for blue and red circles/emphasis on the tile
    private val crosshair = ImageGetter.getImage(strings().crosshair).setHexagonSize() // for when a unit is targeted
    private val goodCityLocationIndicator = ImageGetter.getImage("OtherIcons/Cities").setHexagonSize(0.25f)
    private val fog = ImageGetter.getImage(strings().crosshatchHexagon ).setHexagonSize()
    private val unexplored = ImageGetter.getImage(strings().unexploredTile ).setHexagonSize()

    init {

        highlight.isVisible = false
        crosshair.isVisible = false
        goodCityLocationIndicator.isVisible = false
        fog.isVisible = false
        fog.color = Color.WHITE.cpy().apply { a = 0.2f }

        if (ImageGetter.imageExists(strings().unexploredTile))
            addActor(unexplored)
        addActor(highlight)
        addActor(fog)
        addActor(crosshair)
        addActor(goodCityLocationIndicator)
    }

    fun showCrosshair(alpha: Float = 1f) {
        crosshair.isVisible = true
        crosshair.color.a = alpha
        determineVisibility()
    }

    fun hideCrosshair() {
        crosshair.isVisible = false
        determineVisibility()
    }

    fun showHighlight(color: Color, alpha: Float = 0.3f) {
        highlight.isVisible = true
        highlight.color = color.cpy().apply { a = alpha }
        determineVisibility()
    }

    fun showHighlight() {
        highlight.isVisible = true
        determineVisibility()
    }

    fun hideHighlight() {
        highlight.isVisible = false
        determineVisibility()
    }

    fun showGoodCityLocationIndicator() {
        goodCityLocationIndicator.isVisible = true
        determineVisibility()
    }

    fun hideGoodCityLocationIndicator() {
        goodCityLocationIndicator.isVisible = false
        determineVisibility()
    }

    fun reset() {
        fog.isVisible = true
        highlight.isVisible = false
        crosshair.isVisible = false
        goodCityLocationIndicator.isVisible = false
        determineVisibility()
    }

    override fun doUpdate(viewingCiv: Civilization?, localUniqueCache: LocalUniqueCache) {
        val isViewable = viewingCiv == null || isViewable(viewingCiv)
        fog.isVisible = !isViewable && !tileGroup.isForceVisible

        if (viewingCiv == null)
            return

        unexplored.isVisible = !viewingCiv.hasExplored(tile())
        if (tile().getShownImprovement(viewingCiv) == Constants.barbarianEncampment
                && tile().isExplored(viewingCiv))
            showHighlight(Color.RED)
    }

    override fun determineVisibility() {
        isVisible = fog.isVisible || highlight.isVisible || crosshair.isVisible || goodCityLocationIndicator.isVisible
    }

}
