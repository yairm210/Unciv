package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UnCivGame
import com.unciv.logic.map.MapUnit
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.disable
import com.unciv.ui.worldscreen.WorldScreen

class UnitActionsTable(val worldScreen: WorldScreen) : Table(){

    fun getIconForUnitAction(unitAction:String): Image {
        when(unitAction){
            "Move unit" -> return ImageGetter.getStatIcon("Movement")
            "Stop movement"-> return ImageGetter.getStatIcon("Movement").apply { color= Color.RED }
            "Fortify" -> return ImageGetter.getImage("OtherIcons/Shield.png").apply { color= Color.BLACK }
            "Construct improvement" -> return ImageGetter.getImage("UnitIcons/Worker.png")
            "Automate" -> return ImageGetter.getImage("UnitIcons/Great Engineer.png")
            "Stop automation" -> return ImageGetter.getImage("OtherIcons/Stop.png")
            "Found city" -> return ImageGetter.getImage("UnitIcons/Settler.png")
            "Discover Technology" -> return ImageGetter.getImage("UnitIcons/Great Scientist.png")
            "Construct Academy" -> return ImageGetter.getImage("ImprovementIcons/Academy_(Civ5).png")
            "Start Golden Age" -> return ImageGetter.getImage("UnitIcons/Great Artist.png")
            "Construct Landmark" -> return ImageGetter.getImage("ImprovementIcons/Landmark_(Civ5).png")
            "Hurry Wonder" -> return ImageGetter.getImage("UnitIcons/Great Engineer.png")
            "Construct Manufactory" -> return ImageGetter.getImage("ImprovementIcons/Manufactory_(Civ5).png")
            "Conduct Trade Mission" -> return ImageGetter.getImage("UnitIcons/Great Merchant.png")
            "Construct Customs House" -> return ImageGetter.getImage("ImprovementIcons/Customs_house_(Civ5).png")
            else -> return ImageGetter.getImage("OtherIcons/Star.png")
        }
    }

    fun update(unit: MapUnit?){
        clear()
        if (unit == null) return
        for (button in UnitActions().getUnitActions(unit, worldScreen).map { getUnitActionButton(it) })
            add(button).colspan(2).pad(5f)
                    .size(button.width * worldScreen.buttonScale, button.height * worldScreen.buttonScale).row()
        pack()
    }


    private fun getUnitActionButton(unitAction: UnitAction): Button {
        val actionButton = Button(CameraStageBaseScreen.skin)
        actionButton.add(getIconForUnitAction(unitAction.name)).size(20f).pad(5f)
        actionButton.add(Label(unitAction.name,CameraStageBaseScreen.skin)
                .apply { style= Label.LabelStyle(style); style.fontColor = Color.WHITE })
        actionButton.pack()
        actionButton.addClickListener({ unitAction.action(); UnCivGame.Current.worldScreen!!.update() })
        if (!unitAction.canAct) actionButton.disable()
        return actionButton
    }
}