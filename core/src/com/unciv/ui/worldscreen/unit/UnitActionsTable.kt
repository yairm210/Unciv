package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.MapUnit
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.worldscreen.WorldScreen

class UnitActionsTable(val worldScreen: WorldScreen) : Table(){

    fun getIconForUnitAction(unitAction:String): Image {
        when(unitAction){
            "Move unit" -> return ImageGetter.getStatIcon("Movement")
            "Stop movement"-> return ImageGetter.getStatIcon("Movement").apply { color= Color.RED }

            else -> return ImageGetter.getImage("StatIcons/Star.png")
        }
    }

    fun update(unit: MapUnit?){
        clear()
        if (unit == null) return
        for (button in UnitActions().getUnitActionButtons(unit, worldScreen))
            add(button).colspan(2).pad(5f)
                    .size(button.width * worldScreen.buttonScale, button.height * worldScreen.buttonScale).row()
        pack()
    }
}