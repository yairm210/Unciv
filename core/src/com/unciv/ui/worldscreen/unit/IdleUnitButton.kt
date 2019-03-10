package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.TileMapHolder

class IdleUnitButton (internal val unitTable: UnitTable,
                                          val tileMapHolder: TileMapHolder, val previous:Boolean)
    : Table() {

    val image = ImageGetter.getImage("OtherIcons/BackArrow")

    fun getTilesWithIdleUnits() = tileMapHolder.tileMap.values
                    .filter { it.hasIdleUnit() && it.getUnits().first().owner == unitTable.worldScreen.currentPlayerCiv.civName }

    init {
        val imageSize = 25f
        if(!previous){
            image.setSize(imageSize,imageSize)
            image.setOrigin(Align.center)
            image.rotateBy(180f)
        }
        add(image).size(imageSize).pad(10f,20f,10f,20f)
        enable()
        onClick {
            val tilesWithIdleUnits = getTilesWithIdleUnits()
            if(tilesWithIdleUnits.isEmpty()) return@onClick
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
            unitTable.worldScreen.shouldUpdate=true
        }
    }

    fun enable(){
        image.color= Color.WHITE
        touchable=Touchable.enabled
    }

    fun disable(){
        image.color= Color.GRAY
        touchable=Touchable.disabled
    }
}

