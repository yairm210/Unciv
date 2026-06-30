package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.images.ImageGetter

class TileLayerOverlay(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    private var highlight: Image? = null // for blue and red circles/emphasis on the tile
    private var crosshair: Image? = null // for when a unit is targeted
    private var goodCityLocationIndicator: Image? = null
    private var fog: Image? = null
    private var unexplored: Image? = null

    private fun getHighlight() = ImageGetter.getImage(strings.highlight).setHexagonSize()
    private fun getCrosshair() = ImageGetter.getImage(strings.crosshair).setHexagonSize()
    private fun getGoodCityLocationIndicator() = ImageGetter.getImage("OtherIcons/Cities").setHexagonSize(0.25f)
    private fun getFog() = ImageGetter.getImage(strings.crosshatchHexagon).setHexagonSize().apply {
        color = Color.WHITE.cpy().apply { a = 0.2f }
    }
    private fun getUnexplored() = ImageGetter.getImage(strings.unexploredTile).setHexagonSize()

    fun showCrosshair(alpha: Float = 1f) {
        if (crosshair != null){
            crosshair = getCrosshair()
            addOwnedActor(crosshair!!)
            determineVisibility()
        }
        crosshair?.color?.a = alpha
    }

    fun hideCrosshair() {
        if (crosshair == null) return
        removeOwnedActor(crosshair!!)
        crosshair = null
        determineVisibility()
    }

    fun showHighlight(color: Color = Color.WHITE, alpha: Float = 0.3f) {
        if (highlight == null) {
            highlight = getHighlight()
            addOwnedActor(highlight!!)
            determineVisibility()
        }
        highlight?.color = color.cpy().apply { a = alpha }
    }

    fun hideHighlight() {
        if (highlight == null) return
        removeOwnedActor(highlight!!)
        highlight = null
        determineVisibility()
    }

    fun showGoodCityLocationIndicator() {
        if (goodCityLocationIndicator != null) return
        goodCityLocationIndicator = getGoodCityLocationIndicator()
        addOwnedActor(goodCityLocationIndicator!!)
        determineVisibility()
    }

    fun hideGoodCityLocationIndicator() {
        if (goodCityLocationIndicator == null) return
        removeOwnedActor(goodCityLocationIndicator!!)
        goodCityLocationIndicator = null
        determineVisibility()
    }

    fun reset() {
        hideHighlight()
        hideCrosshair()
        hideGoodCityLocationIndicator()
        determineVisibility()
    }

    override fun doUpdate(viewingCiv: Civilization?) {
        val isViewable = viewingCiv == null || isViewable(viewingCiv)

        setFog(isViewable)

        if (viewingCiv == null) return

        setUnexplored(viewingCiv)

        val improvement = tile.ruleset.tileImprovements[tile.getShownImprovement(viewingCiv)]
        if (improvement?.isBarbarianCampEquivalent(tile.stateThisTile) == true && tile.isExplored(viewingCiv))
            showHighlight(Color.RED)
    }

    fun setUnexplored(viewingCiv: Civilization) {
        val unexploredShouldBeVisible = !viewingCiv.hasExplored(tile)
        val unexploredIsVisible = unexplored != null
        if (unexploredIsVisible && !unexploredShouldBeVisible) {
            removeOwnedActor(unexplored!!)
            unexplored = null
            determineVisibility()
        } else if (!unexploredIsVisible && unexploredShouldBeVisible
                && ImageGetter.imageExists(strings.unexploredTile)) {
            unexplored = getUnexplored()
            addOwnedActor(unexplored!!)
            determineVisibility()
        }
    }

    private fun setFog(isViewable: Boolean) {
        val fogShouldBeVisible = !isViewable && !tileGroup.isForceVisible
        val fogIsVisible = fog != null
        if (fogIsVisible && !fogShouldBeVisible) {
            removeOwnedActor(fog!!)
            fog = null
            determineVisibility()
        } else if (!fogIsVisible && fogShouldBeVisible) {
            fog = getFog()
            addOwnedActor(fog!!)
            determineVisibility()
        }
    }

    override fun determineVisibility() {
        isVisible = fog != null || unexplored != null || highlight != null || crosshair != null || goodCityLocationIndicator != null
    }

}
