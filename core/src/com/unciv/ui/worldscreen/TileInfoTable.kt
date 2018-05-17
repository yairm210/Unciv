package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter

class TileInfoTable(private val worldScreen: WorldScreen) : Table() {

    internal fun updateTileTable(tile: TileInfo) {
        clearChildren()
        val civInfo = worldScreen.civInfo
        val stats = tile.getTileStats(civInfo)
        pad(20f)
        columnDefaults(0).padRight(10f)

        val skin = CameraStageBaseScreen.skin

        if (civInfo.exploredTiles.contains(tile.position)) {
            add(Label(tile.toString(), skin)).colspan(2)
            row()


            for (entry in stats.toHashMap().filterNot { it.value == 0f }) {
                add(ImageGetter.getStatIcon(entry.key.toString())).align(Align.right)
                add(Label(entry.value.toInt().toString(), skin)).align(Align.left)
                row()
            }
        }

        pack()

        setPosition(worldScreen.stage.width - 10f - width, 10f)
    }
}

