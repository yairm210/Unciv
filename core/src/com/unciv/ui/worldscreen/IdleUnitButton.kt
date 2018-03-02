package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.map.TileInfo
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen

class IdleUnitButton internal constructor(internal val worldScreen: WorldScreen) : TextButton("Select next idle unit", CameraStageBaseScreen.skin) {
    init {
        setPosition(worldScreen.stage.width / 2 - width / 2, 5f)
        addClickListener {
            val tilesWithIdleUnits = worldScreen.civInfo.gameInfo.tileMap.values.where { arg0 -> arg0.hasIdleUnit() }

            val tileToSelect: TileInfo
            if (!tilesWithIdleUnits.contains(worldScreen.tileMapHolder.selectedTile))
                tileToSelect = tilesWithIdleUnits[0]
            else {
                var index = tilesWithIdleUnits.indexOf(worldScreen.tileMapHolder.selectedTile) + 1
                if (tilesWithIdleUnits.size == index) index = 0
                tileToSelect = tilesWithIdleUnits[index]
            }
            worldScreen.tileMapHolder.setCenterPosition(tileToSelect.position)
            worldScreen.tileMapHolder.selectedTile = tileToSelect
            worldScreen.update()
        }
    }

    internal fun update() {
        if (worldScreen.civInfo.gameInfo.tileMap.values.any { arg0 -> arg0.hasIdleUnit() }) {
            worldScreen.idleUnitButton.color = Color.WHITE
            worldScreen.idleUnitButton.touchable = Touchable.enabled
        } else {
            worldScreen.idleUnitButton.color = Color.GRAY
            worldScreen.idleUnitButton.touchable = Touchable.disabled
        }
    }
}
