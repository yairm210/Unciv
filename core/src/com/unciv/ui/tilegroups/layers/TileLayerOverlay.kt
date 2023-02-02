package com.unciv.ui.tilegroups.layers

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.tilegroups.TileGroup

class TileLayerOverlay(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    override fun act(delta: Float) {}
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null

    private val highlight = ImageGetter.getImage(strings().highlight).setHexagonSize() // for blue and red circles/emphasis on the tile
    private val crosshair = ImageGetter.getImage(strings().crosshair).setHexagonSize() // for when a unit is targeted
    private val fog = ImageGetter.getImage(strings().crosshatchHexagon ).setHexagonSize()

    init {

        highlight.isVisible = false
        crosshair.isVisible = false
        fog.isVisible = false
        fog.color = Color.WHITE.cpy().apply { a = 0.2f }

        addActor(highlight)
        addActor(fog)
        addActor(crosshair)
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

    fun setFog(isVisible: Boolean) {
        fog.isVisible = isVisible && !tileGroup.isForceVisible
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

    override fun doUpdate(viewingCiv: Civilization?) {

        val isViewable = viewingCiv == null || isViewable(viewingCiv)
        setFog(!isViewable)

        if (viewingCiv == null)
            return

        if (tile().getShownImprovement(viewingCiv) == Constants.barbarianEncampment
                && tile().isExplored(viewingCiv))
            showHighlight(Color.RED)
    }

    override fun determineVisibility() {
        isVisible = fog.isVisible || highlight.isVisible || crosshair.isVisible
    }

}
