package com.unciv.ui.screens.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.map.tile.TileDescription
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addBorderAllowOpacity
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toPrettyString
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine.IconDisplay
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.DebugUtils

class TileInfoTable(private val worldScreen: WorldScreen) : Table(BaseScreen.skin) {
    var selectedCiv = worldScreen.selectedCiv

    init {
        background = BaseScreen.skinStrings.getUiBackground(
            "WorldScreen/TileInfoTable",
            tintColor = BaseScreen.skinStrings.skinConfig.baseColor.darken(0.5f)
        )
    }

    internal fun updateTileTable(tile: Tile?) {
        clearChildren()

        if (tile != null && (DebugUtils.VISIBLE_MAP || selectedCiv.hasExplored(tile)) ) {
            add(getStatsTable(tile))
            add(MarkupRenderer.render(TileDescription.toMarkup(tile, selectedCiv), padding = 0f, iconDisplay = IconDisplay.None) {
                worldScreen.openCivilopedia(it)
            } ).pad(5f).row()
            if (DebugUtils.VISIBLE_MAP)
                add(tile.position.toPrettyString().toLabel()).colspan(2).pad(5f)
        }

        pack()
        addBorderAllowOpacity(1f, Color.WHITE)
    }

    private fun getStatsTable(tile: Tile): Table {
        val table = Table()
        table.defaults().pad(2f)

        // padLeft = padRight + 5: for symmetry. An extra 5 for the distance yield number to
        // tile text comes from the pad up there in updateTileTable
        for ((key, value) in tile.stats.getTileStats(selectedCiv)) {
            table.add(ImageGetter.getStatIcon(key.name))
                .size(20f).align(Align.right).padLeft(10f)
            table.add(value.toInt().toLabel())
                .align(Align.left).padRight(5f)
            table.row()
        }
        table.touchable = Touchable.enabled
        table.onClick {
            Popup(worldScreen).apply {
                for ((name, stats) in tile.stats.getTileStatsBreakdown(tile.getCity(), selectedCiv))
                    add("${name.tr()}: {${stats.clone()}}".toLabel()).row()
                addCloseButton()
            }.open()
        }
        return table
    }
}
