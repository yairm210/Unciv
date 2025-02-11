package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.images.ImageGetter

class TileLayerOverlay(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    override fun act(delta: Float) {}
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha) // perf

    private var highlight: Image? = null // for blue and red circles/emphasis on the tile
    private var crosshair: Image? = null // for when a unit is targeted
    private var goodCityLocationIndicator: Image? = null
    private var fog: Image? = null
    private var unexplored: Image? = null

    private fun getHighlight() = ImageGetter.getImage(strings.highlight).setHexagonSize() // for blue and red circles/emphasis on the tile
    private fun getCrosshair() = ImageGetter.getImage(strings.crosshair).setHexagonSize() // for when a unit is targeted
    private fun getGoodCityLocationIndicator() = ImageGetter.getImage("OtherIcons/Cities").setHexagonSize(0.25f)
    private fun getFog() = ImageGetter.getImage(strings.crosshatchHexagon ).setHexagonSize().apply { 
        color = Color.WHITE.cpy().apply { a = 0.2f }
    }
    private fun getUnexplored() = ImageGetter.getImage(strings.unexploredTile ).setHexagonSize()
    
    fun orderToFront() {
        unexplored?.toFront()
        highlight?.toFront()
        fog?.toFront()
        crosshair?.toFront()
        goodCityLocationIndicator?.toFront()
    }

    fun showCrosshair(alpha: Float = 1f) {
        if (crosshair != null){
            crosshair = getCrosshair()
            addActor(crosshair)
            determineVisibility()
        }
        crosshair?.color?.a = alpha
    }

    fun hideCrosshair() {
        if (crosshair == null) return
        crosshair?.remove()
        crosshair = null
        determineVisibility()
    }

    fun showHighlight(color: Color = Color.WHITE, alpha: Float = 0.3f) {
        if (highlight == null) {
            highlight = getHighlight()
            addActor(highlight)
            determineVisibility()
        }
        highlight?.color = color.cpy().apply { a = alpha }
    }

    fun hideHighlight() {
        if (highlight == null) return
        highlight?.remove()
        highlight = null
        determineVisibility()
    }

    fun showGoodCityLocationIndicator() {
        if (goodCityLocationIndicator != null) return
        goodCityLocationIndicator = getGoodCityLocationIndicator()
        addActor(goodCityLocationIndicator)
        determineVisibility()
    }

    fun hideGoodCityLocationIndicator() {
        if (goodCityLocationIndicator == null) return
        goodCityLocationIndicator?.remove()
        goodCityLocationIndicator = null
        determineVisibility()
    }

    fun reset() {
        hideHighlight()
        hideCrosshair()
        hideGoodCityLocationIndicator()
        determineVisibility()
    }

    override fun doUpdate(viewingCiv: Civilization?, localUniqueCache: LocalUniqueCache) {
        val isViewable = viewingCiv == null || isViewable(viewingCiv)
        
        setFog(isViewable)
        
        if (viewingCiv == null) return

        setUnexplored(viewingCiv)

        if (tile.getShownImprovement(viewingCiv) == Constants.barbarianEncampment
                && tile.isExplored(viewingCiv))
            showHighlight(Color.RED)
    }

    fun setUnexplored(viewingCiv: Civilization) {
        val unexploredShouldBeVisible = !viewingCiv.hasExplored(tile)
        val unexploredIsVisible = unexplored != null
        if (unexploredIsVisible && !unexploredShouldBeVisible) {
            unexplored?.remove()
            determineVisibility()
        } else if (!unexploredIsVisible && unexploredShouldBeVisible
                && ImageGetter.imageExists(strings.unexploredTile)) {
            unexplored = getUnexplored()
            addActor(unexplored)
            determineVisibility()
        }
    }

    private fun setFog(isViewable: Boolean) {
        val fogShouldBeVisible = !isViewable && !tileGroup.isForceVisible
        val fogIsVisible = fog != null
        if (fogIsVisible && !fogShouldBeVisible) {
            fog?.remove()
            fog = null
            determineVisibility()
        } else if (!fogIsVisible && fogShouldBeVisible) {
            fog = getFog()
            addActor(fog)
            determineVisibility()
        }
    }

    override fun determineVisibility() {
        isVisible = fog != null || unexplored != null || highlight != null || crosshair != null || goodCityLocationIndicator != null
        orderToFront()
    }

}
