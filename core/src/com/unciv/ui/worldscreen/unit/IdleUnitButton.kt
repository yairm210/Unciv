package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.map.TileInfo
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.enable
import com.unciv.ui.worldscreen.WorldScreen

class IdleUnitButton internal constructor(internal val worldScreen: WorldScreen) : TextButton("Select next idle unit", CameraStageBaseScreen.skin) {
    init {
        addClickListener {
            val tilesWithIdleUnits = worldScreen.civInfo.gameInfo.tileMap.values.filter { it.hasIdleUnit() }

            val tileToSelect: TileInfo
            if (!tilesWithIdleUnits.contains(worldScreen.tileMapHolder.selectedTile))
                tileToSelect = tilesWithIdleUnits[0]
            else {
                var index = tilesWithIdleUnits.indexOf(worldScreen.tileMapHolder.selectedTile) + 1
                if (tilesWithIdleUnits.size == index) index = 0
                tileToSelect = tilesWithIdleUnits[index]
            }
            worldScreen.tileMapHolder.setCenterPosition(tileToSelect.position)
            worldScreen.update()
        }
    }

    internal fun update() {
        if (worldScreen.civInfo.gameInfo.tileMap.values.any { it.hasIdleUnit() }) enable()
        else disable()
    }
}

