package com.unciv.ui.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.civilopedia.FormattedLine.IconDisplay
import com.unciv.ui.civilopedia.MarkupRenderer
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.addBorderAllowOpacity
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.toLabel

class TileInfoTable(private val viewingCiv :CivilizationInfo) : Table(BaseScreen.skin) {
    init {
        background = ImageGetter.getBackground(ImageGetter.getBlue().darken(0.5f))
    }

    internal fun updateTileTable(tile: TileInfo?) {
        clearChildren()

        if (tile != null && (UncivGame.Current.viewEntireMapForDebug || viewingCiv.exploredTiles.contains(tile.position)) ) {
            add(getStatsTable(tile))
            add( MarkupRenderer.render(tile.toMarkup(viewingCiv), padding = 0f, iconDisplay = IconDisplay.None) {
                UncivGame.Current.pushScreen(CivilopediaScreen(viewingCiv.gameInfo.ruleSet, link = it))
            } ).pad(5f).row()
            if (UncivGame.Current.viewEntireMapForDebug)
                add(tile.position.run { "(${x.toInt()},${y.toInt()})" }.toLabel()).colspan(2).pad(5f)
        }

        pack()
        addBorderAllowOpacity(1f, Color.WHITE)
    }

    fun getStatsTable(tile: TileInfo): Table {
        val table = Table()
        table.defaults().pad(2f)

        // padLeft = padRight + 5: for symmetry. An extra 5 for the distance yield number to
        // tile text comes from the pad up there in updateTileTable
        for ((key, value) in tile.getTileStats(viewingCiv)) {
            table.add(ImageGetter.getStatIcon(key.name))
                .size(20f).align(Align.right).padLeft(10f)
            table.add(value.toInt().toLabel())
                .align(Align.left).padRight(5f)
            table.row()
        }
        return table
    }
}
