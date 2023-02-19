package com.unciv.ui.screens.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.map.tile.TileDescription
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine.IconDisplay
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.BaseScreen
import com.unciv.ui.components.extensions.addBorderAllowOpacity
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.toLabel

class TileInfoTable(private val viewingCiv :Civilization) : Table(BaseScreen.skin) {
    init {
        background = BaseScreen.skinStrings.getUiBackground(
            "WorldScreen/TileInfoTable",
            tintColor = BaseScreen.skinStrings.skinConfig.baseColor.darken(0.5f)
        )
    }

    internal fun updateTileTable(tile: Tile?) {
        clearChildren()

        if (tile != null && (UncivGame.Current.viewEntireMapForDebug || viewingCiv.hasExplored(tile)) ) {
            add(getStatsTable(tile))
            add(MarkupRenderer.render(TileDescription.toMarkup(tile, viewingCiv), padding = 0f, iconDisplay = IconDisplay.None) {
                UncivGame.Current.pushScreen(CivilopediaScreen(viewingCiv.gameInfo.ruleset, link = it))
            } ).pad(5f).row()
            if (UncivGame.Current.viewEntireMapForDebug)
                add(tile.position.run { "(${x.toInt()},${y.toInt()})" }.toLabel()).colspan(2).pad(5f)
        }

        pack()
        addBorderAllowOpacity(1f, Color.WHITE)
    }

    fun getStatsTable(tile: Tile): Table {
        val table = Table()
        table.defaults().pad(2f)

        // padLeft = padRight + 5: for symmetry. An extra 5 for the distance yield number to
        // tile text comes from the pad up there in updateTileTable
        for ((key, value) in tile.stats.getTileStats(viewingCiv)) {
            table.add(ImageGetter.getStatIcon(key.name))
                .size(20f).align(Align.right).padLeft(10f)
            table.add(value.toInt().toLabel())
                .align(Align.left).padRight(5f)
            table.row()
        }
        return table
    }
}
