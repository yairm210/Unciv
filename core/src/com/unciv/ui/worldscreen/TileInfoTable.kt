package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter

class TileInfoTable(private val worldScreen: WorldScreen) : Table() {
    init{
        skin = CameraStageBaseScreen.skin
    }

    internal fun updateTileTable(tile: TileInfo) {
        clearChildren()
        val civInfo = worldScreen.civInfo
        columnDefaults(0).padRight(10f)

        if (civInfo.exploredTiles.contains(tile.position)) {
            add(getStatsTable(tile)).pad(10f)
            add(Label(tile.toString(), skin)).colspan(2)
        }

        pack()

        setPosition(worldScreen.stage.width - 10f - width, 10f)
    }

    fun getStatsTable(tile: TileInfo):Table{
        val table=Table()


        for (entry in tile.getTileStats(worldScreen.civInfo).toHashMap().filterNot { it.value == 0f }) {
            table.add(ImageGetter.getStatIcon(entry.key.toString())).align(Align.right)
            table.add(Label(entry.value.toInt().toString(), skin)).align(Align.left)
            table.row()
        }
        return table
    }
}

