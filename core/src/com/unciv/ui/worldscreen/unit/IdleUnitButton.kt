package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.addClickListener
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.enable
import com.unciv.ui.worldscreen.TileMapHolder

class IdleUnitButton internal constructor(internal val unitTable: UnitTable,
                                          val tileMapHolder: TileMapHolder, val previous:Boolean)
    : TextButton(if(previous)"<" else ">", CameraStageBaseScreen.skin) {

    fun getTilesWithIdleUnits() = tileMapHolder.tileMap.values
                    .filter { it.hasIdleUnit() && it.getUnits().first().owner == unitTable.worldScreen.civInfo.civName }
    init {
        addClickListener {
            val tilesWithIdleUnits = getTilesWithIdleUnits()

            val tileToSelect: TileInfo
            if (unitTable.selectedUnit==null || !tilesWithIdleUnits.contains(unitTable.selectedUnit!!.getTile()))
                tileToSelect = tilesWithIdleUnits[0]
            else {
                var index = tilesWithIdleUnits.indexOf(unitTable.selectedUnit!!.getTile())
                if(previous) index-- else index++
                index += tilesWithIdleUnits.size
                index %= tilesWithIdleUnits.size // for looping
                tileToSelect = tilesWithIdleUnits[index]
            }
            tileMapHolder.setCenterPosition(tileToSelect.position)
            unitTable.worldScreen.update()
        }
    }

    internal fun update() {
        if (getTilesWithIdleUnits().isNotEmpty()) enable()
        else disable()
    }
}

