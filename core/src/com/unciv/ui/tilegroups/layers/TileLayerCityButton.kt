package com.unciv.ui.tilegroups.layers

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.City
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.tilegroups.CityButton
import com.unciv.ui.tilegroups.TileGroup

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

    fun update(city: City?, viewable: Boolean) {

        // There used to be a city here but it was razed
        if (city == null && cityButton != null) {
            cityButton!!.remove()
            cityButton = null
        }

        // Create (if not yet) and update city button
        if (city != null && tileGroup.tile.isCityCenter()) {
            if (cityButton == null) {
                cityButton = CityButton(city, tileGroup)
                addActor(cityButton)
            }

            cityButton!!.update(viewable)
        }
    }

}
