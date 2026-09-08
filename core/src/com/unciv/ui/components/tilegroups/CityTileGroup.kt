package com.unciv.ui.components.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.view.CityView
import com.unciv.view.CivView
import com.unciv.view.TileView
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.extensions.addToCenter
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.setFontColor
import com.unciv.ui.components.extensions.toGroup
import com.unciv.ui.components.extensions.toLabel

enum class CityTileState {
    NONE,
    WORKABLE,
    PURCHASABLE,
    BLOCKADED
}

class CityTileGroup(val cityView: CityView, tileView: TileView, tileSetStrings: TileSetStrings, private val nightMode: Boolean, private val isSpying: Boolean = false) : TileGroup(tileView, tileSetStrings) {

    var tileState = CityTileState.NONE

    override fun update(viewingCiv: CivView?) {
        super.update(cityView.viewingCiv())

        tileState = CityTileState.NONE

        layerMisc.removeWorkedIcon()
        var icon: Actor? = null

        val setDimmed = if (nightMode) fun(factor: Float) {
                layerTerrain.dim(0.25f * factor)
            } else fun(factor: Float) {
                layerTerrain.dim(0.5f * factor)
            }
        val setUndimmed = if (nightMode) fun() {
                layerTerrain.dim(0.5f)
            } else fun() {}

        when {

            // Does not belong to us
            tileView.owningCity()?.isSameCivAs(cityView) != true -> {
                setDimmed(0.6f)
                layerYield.setYieldVisible(UncivGame.Current.settings.showTileYields)
                layerYield.dimYields(true)

                // Can be purchased in principle? Add icon.
                if (cityView.canBuyTile(tileView)) {

                    val price = cityView.getGoldCostOfTile(tileView)
                    val label = price.tr().toLabel(fontSize = 9, alignment = Align.center)
                    val image = ImageGetter.getImage("TileIcons/Buy")
                    icon = image.toGroup(26f).apply { isTransform = false }
                    icon.addToCenter(label)
                    label.y -= 15f

                    // Can be purchased now?
                    if (!cityView.owningCivView.hasStatToBuy(Stat.Gold, price)) {
                        image.color = Color.WHITE.darken(0.5f)
                        label.setFontColor(Color.RED)
                    } else {
                        tileState = CityTileState.PURCHASABLE
                    }
                }
            }

            // Out of city range
            !cityView.isInRange(tileView) -> {
                setDimmed(1f)
                layerYield.dimYields(true)
            }

            // Worked by another city
            tileView.isWorked() && tileView.getWorkingCity() != cityView -> {
                setDimmed(1f)
                layerYield.dimYields(true)
            }

            // City Center
            tileView.isCityCenter() -> {
                icon = ImageGetter.getImage("TileIcons/CityCenter")
                // Night mode does not apply to the city tile itself
                layerYield.dimYields(false)
            }

            // Does not provide yields
            tileView.getTileStats(cityView.viewingCiv(), cityView).isEmpty() -> {
                // Do nothing except night-mode dimming
                setUndimmed()
            }

            // Blockaded
            tileView.isBlockaded() -> {
                icon = ImageGetter.getImage("TileIcons/Blockaded")
                tileState = CityTileState.BLOCKADED
                setUndimmed()
                layerYield.dimYields(true)
            }

            // Locked
            tileView.isLocked() -> {
                icon = ImageGetter.getImage("TileIcons/Locked")
                tileState = CityTileState.WORKABLE
                setUndimmed()
                layerYield.dimYields(false)
            }

            // Worked
            tileView.isWorked() -> {
                icon = ImageGetter.getImage("TileIcons/Worked")
                tileState = CityTileState.WORKABLE
                setUndimmed()
                layerYield.dimYields(false)
            }

            // Provides yield without worker assigned (isWorked already tested above)
            tileView.providesYield() -> {
                // defaults are OK
                setUndimmed()
            }

            // Not-worked
            else -> {
                icon = ImageGetter.getImage("TileIcons/NotWorked")
                tileState = CityTileState.WORKABLE
                setUndimmed()
                layerYield.dimYields(true)
            }
        }

        if (icon != null) {
            icon.setSize(26f, 26f)
            // Position absolutely: tile origin (x,y) + tile-local offset
            icon.setPosition(x + width/2 - icon.width/2, y + height*0.85f - icon.height/2)
            layerMisc.addWorkedIcon(icon)
        }

        // No unit flags and city-buttons inside CityScreen
        layerUnitFlag.isVisible = false
        layerCityButton.isVisible = false

        // Pixel art, roads, improvements are dimmed inside CityScreen
        if (isSpying) layerUnitArt.isVisible = false
        else layerUnitArt.dim()
        layerFeatures.dim()
        layerImprovement.dimImprovement(true)
    }
}
