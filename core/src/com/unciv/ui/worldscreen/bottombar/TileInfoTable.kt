package com.unciv.ui.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.civilopedia.MarkupRenderer
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.toLabel

class TileInfoTable(private val viewingCiv :CivilizationInfo) : Table(CameraStageBaseScreen.skin) {
    init {
        background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))
    }

    internal fun updateTileTable(tile: TileInfo?) {
        clearChildren()

        if (tile != null && (UncivGame.Current.viewEntireMapForDebug || viewingCiv.exploredTiles.contains(tile.position)) ) {
            add(getStatsTable(tile))
            add( MarkupRenderer.render(tile.toMarkup(viewingCiv), padding = 0f) {
                UncivGame.Current.setScreen(CivilopediaScreen(viewingCiv.gameInfo.ruleSet, link = it))
            } ).pad(5f)
            // For debug only!
//            add(tile.position.toString().toLabel()).colspan(2).pad(10f)
        }

        pack()
    }

    fun getStatsTable(tile: TileInfo): Table {
        val table = Table()
        table.defaults().pad(2f)

        // padLeft = padRight + 5: for symmetry. An extra 5 for the distance yield number to
        // tile text comes from the pad up there in updateTileTable
        for (entry in tile.getTileStats(viewingCiv).toHashMap()
                .filterNot { it.value == 0f || it.key.toString() == "" }) {
            table.add(ImageGetter.getStatIcon(entry.key.toString()))
                .size(20f).align(Align.right).padLeft(10f)
            table.add(entry.value.toInt().toLabel())
                .align(Align.left).padRight(5f)
            table.row()
        }
        return table
    }
}
