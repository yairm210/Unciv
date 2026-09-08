package com.unciv.ui.components.tilegroups

import com.badlogic.gdx.graphics.Color
import com.unciv.UncivGame
import com.unciv.view.CivView
import com.unciv.view.TileView
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.extensions.darken


class WorldTileGroup(tileView: TileView, tileSetStrings: TileSetStrings)
    : TileGroup(tileView, tileSetStrings) {

    override fun update(viewingCiv: CivView?) {
        super.update(viewingCiv)

        updateWorkedIcon(viewingCiv!!)
    }

    private fun updateWorkedIcon(viewingCiv: CivView) {

        layerMisc.removeWorkedIcon()

        val shouldShowWorkedIcon = UncivGame.Current.settings.showWorkedTiles   // Overlay enabled;
                && isViewable(viewingCiv)                                       // We see tile;
                && tileView.owningCity()?.let { viewingCiv.isOwnerOf(it) } == true // Tile belongs to us;
                && tileView.isWorked()                                          // Tile is worked;

        if (!shouldShowWorkedIcon)
            return

        val icon = when {
            tileView.isLocked() -> ImageGetter.getImage("TileIcons/Locked").apply { color = Color.WHITE.darken(0.5f) }
            tileView.isWorked() && tileView.providesYield() -> ImageGetter.getImage("TileIcons/Worked").apply { color = Color.WHITE.darken(0.5f) }
            else -> null
        }

        if (icon != null) {
            icon.setSize(20f, 20f)
            // Position absolutely: tile origin (x,y) + tile-local centre + rightward offset
            icon.x = x + (width - 20f) / 2 + 20f
            icon.y = y + (height - 20f) / 2
            layerMisc.addWorkedIcon(icon)
        }
    }
}
