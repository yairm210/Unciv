package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.stats.Stat
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.addToCenter
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.setFontColor
import com.unciv.ui.utils.extensions.toGroup
import com.unciv.ui.utils.extensions.toLabel

class CityTileGroup(private val city: City, tile: Tile, tileSetStrings: TileSetStrings) : TileGroup(tile,tileSetStrings) {

    var isWorkable = false
    var isPurchasable = false

    init {
        layerMisc.touchable = Touchable.childrenOnly
    }

    override fun update(viewingCiv: Civilization?) {
        super.update(city.civ)

        isWorkable = false
        isPurchasable = false

        layerMisc.removeWorkedIcon()
        var icon: Actor? = null

        when {

            // Does not belong to us
            tile.getOwner() != city.civ -> {
                layerTerrain.dim(0.3f)
                layerMisc.setYieldVisible(UncivGame.Current.settings.showTileYields)
                layerMisc.dimYields(true)

                // Can be purchased in principle? Add icon.
                if (city.expansion.canBuyTile(tile)) {

                    val price = city.expansion.getGoldCostOfTile(tile)
                    val label = price.toString().toLabel(fontSize = 9, alignment = Align.center)
                    val image = ImageGetter.getImage("TileIcons/Buy")
                    icon = image.toGroup(26f).apply { isTransform = false }
                    icon.addToCenter(label)
                    label.y -= 15f

                    // Can be purchased now?
                    if (!city.civ.hasStatToBuy(Stat.Gold, price)) {
                        image.color = Color.WHITE.darken(0.5f)
                        label.setFontColor(Color.RED)
                    } else {
                        isPurchasable = true
                    }
                }
            }

            // Out of city range
            tile !in city.tilesInRange -> {
                layerTerrain.dim(0.5f)
                layerMisc.dimYields(true)
            }

            // Worked by another city
            tile.isWorked() && tile.getWorkingCity() != city -> {
                layerTerrain.dim(0.5f)
                layerMisc.dimYields(true)
            }

            // City Center
            tile.isCityCenter() -> {
                icon = ImageGetter.getImage("TileIcons/CityCenter")
                layerMisc.dimYields(false)
            }

            // Does not provide yields
            tile.stats.getTileStats(city.civ).isEmpty() -> {
                // Do nothing
            }

            // Locked
            tile.isLocked() -> {
                icon = ImageGetter.getImage("TileIcons/Locked")
                isWorkable = true
                layerMisc.dimYields(false)
            }

            // Worked
            tile.isWorked() -> {
                icon = ImageGetter.getImage("TileIcons/Worked")
                isWorkable = true
                layerMisc.dimYields(false)
            }

            // Not-worked
            else -> {
                icon = ImageGetter.getImage("TileIcons/NotWorked")
                isWorkable = true
                layerMisc.dimYields(true)
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
        layerMisc.dimImprovement(true)

        // Put whole layer (yield, pop, improvement, res) to front
        layerMisc.toFront()
    }
}
