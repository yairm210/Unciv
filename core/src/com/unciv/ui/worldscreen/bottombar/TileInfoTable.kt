package com.unciv.ui.worldscreen.bottombar

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.toLabel
import com.unciv.ui.worldscreen.WorldScreen

class TileInfoTable(private val worldScreen: WorldScreen) : Table(CameraStageBaseScreen.skin) {
    init {
        background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))
    }

    internal fun updateTileTable(tile: TileInfo) {
        clearChildren()
        val civInfo = worldScreen.viewingCiv

        if (UncivGame.Current.viewEntireMapForDebug || civInfo.exploredTiles.contains(tile.position)) {
            add(getStatsTable(tile))
            add(tile.toString().toLabel()).colspan(2).pad(10f)
        }

        pack()
    }

    fun getStatsTable(tile: TileInfo): Table {
        val table = Table()
        table.defaults().pad(2f)

        for (entry in tile.getTileStats(worldScreen.viewingCiv).toHashMap()
                .filterNot { it.value == 0f || it.key.toString() == "" }) {
            table.add(ImageGetter.getStatIcon(entry.key.toString())).size(20f).align(Align.right)
            table.add(entry.value.toInt().toString().toLabel()).align(Align.left).padRight(10f)
            table.row()
        }
        return table
    }
}