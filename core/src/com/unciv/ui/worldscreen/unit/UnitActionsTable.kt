package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UnCivGame
import com.unciv.logic.map.MapUnit
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen

class UnitActionsTable(val worldScreen: WorldScreen) : Table(){

    fun getIconForUnitAction(unitAction:String): Actor {
        if(unitAction.startsWith("Upgrade to")){
            // Regexplaination: start with a [, take as many non-] chars as you can, until you reach a ].
            // What you find between the first [ and the first ] that comes after it, will be group no. 1
            val unitToUpgradeTo = Regex("""Upgrade to \[([^\]]*)\]""").find(unitAction)!!.groups[1]!!.value
            return ImageGetter.getUnitIcon(unitToUpgradeTo)
        }
        when(unitAction){
            "Move unit" -> return ImageGetter.getStatIcon("Movement")
            "Stop movement"-> return ImageGetter.getStatIcon("Movement").apply { color= Color.RED }
            "Fortify" -> return ImageGetter.getImage("OtherIcons/Shield").apply { color= Color.BLACK }
            "Promote" -> return ImageGetter.getImage("OtherIcons/Star").apply { color= Color.GOLD }
            "Construct improvement" -> return ImageGetter.getUnitIcon("Worker")
            "Automate" -> return ImageGetter.getUnitIcon("Great Engineer")
            "Stop automation" -> return ImageGetter.getImage("OtherIcons/Stop")
            "Found city" -> return ImageGetter.getUnitIcon("Settler")
            "Discover Technology" -> return ImageGetter.getUnitIcon("Great Scientist")
            "Construct Academy" -> return ImageGetter.getImprovementIcon("Academy")
            "Start Golden Age" -> return ImageGetter.getUnitIcon("Great Artist")
            "Construct Landmark" -> return ImageGetter.getImprovementIcon("Landmark")
            "Hurry Wonder" -> return ImageGetter.getUnitIcon("Great Engineer")
            "Construct Manufactory" -> return ImageGetter.getImprovementIcon("Manufactory")
            "Conduct Trade Mission" -> return ImageGetter.getUnitIcon("Great Merchant")
            "Construct Customs House" -> return ImageGetter.getImprovementIcon("Customs house")
            "Set up" -> return ImageGetter.getUnitIcon("Catapult")
            "Disband unit" -> return ImageGetter.getImage("OtherIcons/DisbandUnit")
            "Sleep" -> return ImageGetter.getImage("OtherIcons/Sleep")
            "Explore" -> return ImageGetter.getUnitIcon("Scout")
            "Stop exploration" -> return ImageGetter.getImage("OtherIcons/Stop.png")
            "Create Fishing Boats" -> return ImageGetter.getImprovementIcon("Fishing Boats")
            "Create Oil well" -> return ImageGetter.getImprovementIcon("Oil well")
            "Pillage" -> return ImageGetter.getImage("OtherIcons/Pillage")
            "Construct road" -> return ImageGetter.getImprovementIcon("Road")
            else -> return ImageGetter.getImage("OtherIcons/Star")
        }
    }

    fun update(unit: MapUnit?){
        clear()
        if (unit == null) return
        for (button in UnitActions().getUnitActions(unit, worldScreen).map { getUnitActionButton(it) })
            add(button).colspan(2).pad(5f).row()
        pack()
    }


    private fun getUnitActionButton(unitAction: UnitAction): Button {
        val actionButton = Button(CameraStageBaseScreen.skin)
        actionButton.add(getIconForUnitAction(unitAction.name)).size(20f).pad(5f)
        actionButton.add(Label(unitAction.name.tr(),CameraStageBaseScreen.skin).setFontColor(Color.WHITE))
                .pad(5f)
        actionButton.pack()
        actionButton.onClick(unitAction.sound) { unitAction.action(); UnCivGame.Current.worldScreen.shouldUpdate=true }
        if (!unitAction.canAct) actionButton.disable()
        return actionButton
    }
}