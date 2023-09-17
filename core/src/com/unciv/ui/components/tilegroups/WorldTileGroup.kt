package com.unciv.ui.components.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.darken


class WorldTileGroup(tile: Tile, tileSetStrings: TileSetStrings)
    : TileGroup(tile,tileSetStrings) {

    init {
        layerMisc.touchable = Touchable.disabled
    }

    override fun update(viewingCiv: Civilization?, localUniqueCache: LocalUniqueCache) {
        super.update(viewingCiv, localUniqueCache)

        updateWorkedIcon(viewingCiv!!)
    }

    private fun updateWorkedIcon(viewingCiv: Civilization) {

        layerMisc.removeWorkedIcon()

        val shouldShowWorkedIcon = UncivGame.Current.settings.showWorkedTiles   // Overlay enabled;
                && isViewable(viewingCiv)                                       // We see tile;
                && tile.getCity()?.civ == viewingCiv                            // Tile belongs to us;
                && tile.isWorked()                                              // Tile is worked;

        if (!shouldShowWorkedIcon)
            return

        val icon = when {
            tile.isLocked() -> ImageGetter.getImage("TileIcons/Locked").apply { color = Color.WHITE.darken(0.5f) }
            tile.isWorked() && tile.providesYield() -> ImageGetter.getImage("TileIcons/Worked").apply { color = Color.WHITE.darken(0.5f) }
            else -> null
        }

        if (icon != null) {
            icon.setSize(20f, 20f)
            icon.center(this)
            icon.x += 20f
            layerMisc.addWorkedIcon(icon)
        }
    }

    override fun clone(): WorldTileGroup = WorldTileGroup(tile , tileSetStrings)
}
