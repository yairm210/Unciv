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
import com.unciv.ui.utils.setFontColor
import com.unciv.ui.worldscreen.WorldScreen

class UnitActionsTable(val worldScreen: WorldScreen) : Table(){

    fun getIconForUnitAction(unitAction:String): Image {
        if(unitAction.startsWith("Upgrade to")){
            val unitToUpgradeTo = Regex("""Upgrade to (\S*)""").find(unitAction)!!.groups[1]!!.value
            return ImageGetter.getUnitIcon(unitToUpgradeTo)
        }
        when(unitAction){
            "Move unit" -> return ImageGetter.getStatIcon("Movement")
            "Stop movement"-> return ImageGetter.getStatIcon("Movement").apply { color= Color.RED }
            "Fortify" -> return ImageGetter.getImage("OtherIcons/Shield.png").apply { color= Color.BLACK }
            "Construct improvement" -> return ImageGetter.getUnitIcon("Worker")
            "Automate" -> return ImageGetter.getUnitIcon("Great Engineer")
            "Stop automation" -> return ImageGetter.getImage("OtherIcons/Stop.png")
            "Found city" -> return ImageGetter.getUnitIcon("Settler")
            "Discover Technology" -> return ImageGetter.getUnitIcon("Great Scientist")
            "Construct Academy" -> return ImageGetter.getImprovementIcon("Academy")
            "Start Golden Age" -> return ImageGetter.getUnitIcon("Great Artist")
            "Construct Landmark" -> return ImageGetter.getImprovementIcon("Landmark")
            "Hurry Wonder" -> return ImageGetter.getUnitIcon("Great Engineer")
            "Construct Manufactory" -> return ImageGetter.getImprovementIcon("Manufactory")
            "Conduct Trade Mission" -> return ImageGetter.getUnitIcon("Great Merchant")
            "Construct Customs House" -> return ImageGetter.getImprovementIcon("Customs house")
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
                .setFontColor(Color.WHITE)).pad(5f)
        actionButton.pack()
        actionButton.addClickListener({ unitAction.action(); UnCivGame.Current.worldScreen!!.update() })
        if (!unitAction.canAct) actionButton.disable()
        return actionButton
    }
}