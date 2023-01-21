package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.tile.Tile
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.centerX

class CityTileGroup(private val city: CityInfo, tile: Tile, tileSetStrings: TileSetStrings) : TileGroup(tile,tileSetStrings) {

    var isWorkable = false
    private var yieldGroup = YieldGroup()

    init {
        isTransform = false // performance helper - nothing here is rotated or scaled
        addActor(yieldGroup)
        if (city.location == tile.position) {
            icons.addPopulationIcon(ImageGetter.getImage("OtherIcons/Star"))
        }
        unitLayerGroup.isVisible = false
    }

    fun update() {
        super.update(city.civInfo, showResourcesAndImprovements = true, showTileYields = false)

        fun dim(brightness: Float) {
            // Dimming with alpha looks weird with overlapping tiles— Can't just set group alpha.
            // Image.draw() doesn't inherit parent colour tints— Can't just set group colour.
            // Covering up with fogImage or adding another tileset image with variable alpha doesn't look good for all terrain shapes— Can't cover up group with image.
            // Directly setting child actor colors breaks Default tileset terrain colours— Need to interpolate between existing and background colour.
            //
            // Reapplying the colour with every update is fine, and doesn't cause the tiles to get darker with every click, because the colours are always reset in either TileGroup.updateTileColor() or the early exit by super.update()/TileGroup.update().
            // Mutating the Color() in-place seems dangerous, but GDX source already mutates even when Kotlin notation suggests it's replacing.
            for (image in tileBaseImages) {
                image.color.lerp(BaseScreen.clearColor, 1-brightness)
            }
        }

        // this needs to happen on update, because we can buy tiles, which changes the definition of the bought tiles...
        when {
            tile.getOwner() != city.civInfo -> { // outside of civ boundary
                dim(0.3f)
                yieldGroup.isVisible = UncivGame.Current.settings.showTileYields
            }

            tile !in city.tilesInRange -> { // within city but not close enough to be workable
                yieldGroup.isVisible = UncivGame.Current.settings.showTileYields
                dim(0.5f)
            }

            tile.isWorked() && tile.getWorkingCity() != city -> {
                // Don't fade out, but don't add a population icon either.
                dim(0.5f)
            }


            tile.isLocked() -> {
                icons.addPopulationIcon(ImageGetter.getImage("OtherIcons/Lock"))
                isWorkable = true
            }

            tile.isWorked() || !tile.providesYield() -> { // workable
                icons.addPopulationIcon()
                isWorkable = true
            }
        }

        unitLayerGroup.isVisible = false
        terrainFeatureLayerGroup.color.a = 0.5f
        icons.improvementIcon?.setColor(1f, 1f, 1f, 0.5f)
        updatePopulationIcon()
        updateYieldGroup()
    }

    private fun updateYieldGroup() {
        yieldGroup.setStats(tile.stats.getTileStats(city, city.civInfo))
        yieldGroup.setOrigin(Align.center)
        yieldGroup.setScale(0.7f)
        yieldGroup.toFront()
        yieldGroup.centerX(this)
        yieldGroup.y = height * 0.25f - yieldGroup.height / 2

        if (tile.providesYield()) yieldGroup.color = Color.WHITE
        else yieldGroup.color = Color.GRAY.cpy().apply { a = 0.5f }
    }

    private fun updatePopulationIcon() {
        val populationIcon = icons.populationIcon
        if (populationIcon != null) {
            populationIcon.setSize(25f, 25f)
            populationIcon.setPosition(width / 2 - populationIcon.width / 2,
                    height * 0.85f - populationIcon.height / 2)

            if (tile.isCityCenter()) populationIcon.color = Color.GOLD
            else if (tile.providesYield()) populationIcon.color = Color.WHITE
            else populationIcon.color = Color.GRAY.cpy() // City center gets a GOLD star

            populationIcon.toFront()
        }
    }

}
