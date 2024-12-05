package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.ui.components.tilegroups.CityButton
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.tilegroups.WorldTileGroup
import com.unciv.utils.DebugUtils

class TileLayerCityButton(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    private var cityButton: CityButton? = null

    init {
        touchable = Touchable.childrenOnly
        setOrigin(Align.center)
    }

    override fun act(delta: Float) {
        if (tileGroup.tile.isCityCenter())
            super.act(delta)
    }

    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? {
        if (tileGroup.tile.isCityCenter())
            return super.hit(x, y, touchable)
        return null
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (tileGroup.tile.isCityCenter())
            super.draw(batch, parentAlpha)
    }

    fun moveUp() {
        cityButton?.moveButtonUp()
    }

    fun moveDown() {
        cityButton?.moveButtonDown()
    }

    override fun doUpdate(viewingCiv: Civilization?, localUniqueCache: LocalUniqueCache) {
        if (tileGroup !is WorldTileGroup) return

        val city = tile().getCity()

        // There used to be a city here but it was razed
        if (city == null && cityButton != null) {
            cityButton!!.remove()
            cityButton = null
        }

        if (viewingCiv == null) return
        if (city == null || !tileGroup.tile.isCityCenter()) return
        
        // Create (if not yet) and update city button
        if (cityButton == null) {
            cityButton = CityButton(city, tileGroup)
            addActor(cityButton)
        }

        cityButton!!.update(DebugUtils.VISIBLE_MAP || isViewable(viewingCiv))
    }

}
