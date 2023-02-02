package com.unciv.ui.tilegroups

import com.unciv.UncivGame
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.images.ImageGetter

class CityTileGroup(private val city: City, tile: Tile, tileSetStrings: TileSetStrings) : TileGroup(tile,tileSetStrings) {

    var isWorkable = false

    init {
        if (city.location == tile.position)
            layerMisc.setNewPopulationIcon(ImageGetter.getImage("OtherIcons/Star"))
    }

    override fun update(viewingCiv: Civilization?) {
        super.update(city.civInfo)

        when {

            // Does not belong to us
            tile.getOwner() != city.civInfo -> {
                layerTerrain.dim(0.3f)
                layerMisc.setYieldVisible(UncivGame.Current.settings.showTileYields)
                layerMisc.dimYields(true)
            }

            // Out of city range
            tile !in city.tilesInRange -> {
                layerTerrain.dim(0.5f)
                layerMisc.setYieldVisible(UncivGame.Current.settings.showTileYields)
                layerMisc.dimYields(true)
            }

            // Worked by another city
            tile.isWorked() && tile.getWorkingCity() != city -> {
                layerTerrain.dim(0.5f)
                layerMisc.dimYields(true)
            }

            // Locked
            tile.isLocked() -> {
                layerMisc.setNewPopulationIcon(ImageGetter.getImage("OtherIcons/Lock"))
                isWorkable = true
            }

            // Workable
            tile.isWorked() || !tile.providesYield() -> {
                layerMisc.setNewPopulationIcon()
                isWorkable = true
            }
        }

        // No unit flags inside CityScreen
        layerUnitFlag.isVisible = false

        // Pixel art, road, improvements are dimmed inside CityScreen
        layerUnitArt.dim()
        layerFeatures.dim()
        layerMisc.dimImprovement(true)

        // Dim yield icons if tile is not worked
        if (!tile.providesYield())
            layerMisc.dimYields(true)

        // Update citizen icon and put whole layer (yield, pop, improvement, res) to front
        layerMisc.updatePopulationIcon()
        layerMisc.toFront()
    }

}
