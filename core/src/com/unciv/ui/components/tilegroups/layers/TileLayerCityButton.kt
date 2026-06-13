package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.tilegroups.citybutton.CityButton
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.tilegroups.WorldTileGroup
import com.unciv.utils.DebugUtils

class TileLayerCityButton(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    private var cityButton: CityButton? = null

    // Per-city-tile wrapper Group so that CityButton.moveButtonDown/Up can still call
    // `parent.addAction(moveTo(...))` and have the action processed by the actable cityButtonMapLayer.
    private var cityButtonWrapper: Group? = null

    fun moveUp() {
        cityButton?.moveButtonUp()
    }

    fun moveDown() {
        cityButton?.moveButtonDown()
    }

    /** Returns true when a CityButton is currently displayed on this tile. */
    fun hasButton(): Boolean = cityButton != null

    /** Enables or disables scene-graph transform on the city button wrapper (for scaling). */
    fun setButtonTransform(isTransform: Boolean) {
        cityButtonWrapper?.isTransform = isTransform
    }

    /** Scales the city button wrapper (use together with [setButtonTransform]). */
    fun setButtonScale(scale: Float) {
        cityButtonWrapper?.setScale(scale)
    }

    override fun doUpdate(viewingCiv: Civilization?) {
        if (tileGroup !is WorldTileGroup) return

        val city = tile.getCity()

        // There used to be a city here but it was razed
        if (city == null && cityButtonWrapper != null) {
            removeOwnedActor(cityButtonWrapper!!)
            cityButtonWrapper = null
            cityButton = null
        }

        if (viewingCiv == null) return
        if (city == null || !tileGroup.tile.isCityCenter()) return

        // Create wrapper + city button if not yet present
        if (cityButton == null) {
            cityButtonWrapper = Group().apply {
                isTransform = false
                touchable = Touchable.childrenOnly
                setPosition(tileX, tileY)
            }
            cityButton = CityButton(city, tileGroup)
            cityButtonWrapper!!.addActor(cityButton!!)
            addOwnedActor(cityButtonWrapper!!)
        }

        cityButton!!.update(DebugUtils.VISIBLE_MAP || isViewable(viewingCiv))
    }

    override fun determineVisibility() {
        isVisible = cityButton != null
    }
}
