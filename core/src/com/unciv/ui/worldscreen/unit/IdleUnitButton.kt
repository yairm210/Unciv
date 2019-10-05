package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.MapUnit
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.TileMapHolder

class IdleUnitButton (
        internal val unitTable: UnitTable,
        val tileMapHolder: TileMapHolder,
        val previous:Boolean
) : Table() {

    val image = ImageGetter.getImage("OtherIcons/BackArrow")

    fun hasIdleUnits() = unitTable.worldScreen.viewingCiv.getIdleUnits().isNotEmpty()

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

            val idleUnits = unitTable.worldScreen.viewingCiv.getIdleUnits()
            if(idleUnits.isEmpty()) return@onClick

            val unitToSelect: MapUnit
            if (unitTable.selectedUnit==null || !idleUnits.contains(unitTable.selectedUnit!!))
                unitToSelect = idleUnits[0]
            else {
                var index = idleUnits.indexOf(unitTable.selectedUnit!!)
                if(previous) index-- else index++
                index += idleUnits.size
                index %= idleUnits.size // for looping
                unitToSelect = idleUnits[index]
            }

            unitToSelect.due = false
            tileMapHolder.setCenterPosition(unitToSelect.currentTile.position)
            unitTable.selectedUnit = unitToSelect
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

