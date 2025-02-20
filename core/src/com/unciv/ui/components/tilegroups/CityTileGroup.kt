package com.unciv.ui.components.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.LocalUniqueCache
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

class CityTileGroup(val city: City, tile: Tile, tileSetStrings: TileSetStrings, private val nightMode: Boolean) : TileGroup(tile,tileSetStrings) {

    var tileState = CityTileState.NONE

    init {
        layerMisc.touchable = Touchable.childrenOnly
    }

    override fun update(viewingCiv: Civilization?, localUniqueCache: LocalUniqueCache) {
        super.update(city.civ, localUniqueCache)

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
            tile.getOwner() != city.civ -> {
                setDimmed(0.6f)
                layerYield.setYieldVisible(UncivGame.Current.settings.showTileYields)
                layerYield.dimYields(true)

                // Can be purchased in principle? Add icon.
                if (city.expansion.canBuyTile(tile)) {

                    val price = city.expansion.getGoldCostOfTile(tile)
                    val label = price.tr().toLabel(fontSize = 9, alignment = Align.center)
                    val image = ImageGetter.getImage("TileIcons/Buy")
                    icon = image.toGroup(26f).apply { isTransform = false }
                    icon.addToCenter(label)
                    label.y -= 15f

                    // Can be purchased now?
                    if (!city.civ.hasStatToBuy(Stat.Gold, price)) {
                        image.color = Color.WHITE.darken(0.5f)
                        label.setFontColor(Color.RED)
                    } else {
                        tileState = CityTileState.PURCHASABLE
                    }
                }
            }

            // Out of city range
            tile !in city.tilesInRange -> {
                setDimmed(1f)
                layerYield.dimYields(true)
            }

            // Worked by another city
            tile.isWorked() && tile.getWorkingCity() != city -> {
                setDimmed(1f)
                layerYield.dimYields(true)
            }

            // City Center
            tile.isCityCenter() -> {
                icon = ImageGetter.getImage("TileIcons/CityCenter")
                // Night mode does not apply to the city tile itself
                layerYield.dimYields(false)
            }

            // Does not provide yields
            tile.stats.getTileStats(city, city.civ).isEmpty() -> {
                // Do nothing except night-mode dimming
                setUndimmed()
            }

            // Blockaded
            tile.isBlockaded() -> {
                icon = ImageGetter.getImage("TileIcons/Blockaded")
                tileState = CityTileState.BLOCKADED
                setUndimmed()
                layerYield.dimYields(true)
            }

            // Locked
            tile.isLocked() -> {
                icon = ImageGetter.getImage("TileIcons/Locked")
                tileState = CityTileState.WORKABLE
                setUndimmed()
                layerYield.dimYields(false)
            }

            // Worked
            tile.isWorked() -> {
                icon = ImageGetter.getImage("TileIcons/Worked")
                tileState = CityTileState.WORKABLE
                setUndimmed()
                layerYield.dimYields(false)
            }

            // Provides yield without worker assigned (isWorked already tested above)
            tile.providesYield() -> {
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
            icon.setPosition(width/2 - icon.width/2,
                height*0.85f - icon.height/2)
            layerMisc.addWorkedIcon(icon)
        }

        // No unit flags and city-buttons inside CityScreen
        layerUnitFlag.isVisible = false
        layerCityButton.isVisible = false

        // Pixel art, roads, improvements are dimmed inside CityScreen
        layerUnitArt.dim()
        layerFeatures.dim()
        layerImprovement.dimImprovement(true)

        // Put whole layer (yield, pop, improvement, res) to front
        layerMisc.toFront()
    }
}
