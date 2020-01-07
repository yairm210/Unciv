package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.map.MapUnit
import com.unciv.models.UnitAction
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen

class UnitActionsTable(val worldScreen: WorldScreen) : Table(){

    init {
        touchable = Touchable.enabled
    }
    
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
            "Construct improvement" -> return ImageGetter.getUnitIcon(Constants.worker)
            "Automate" -> return ImageGetter.getUnitIcon("Great Engineer")
            "Stop automation" -> return ImageGetter.getImage("OtherIcons/Stop")
            "Found city" -> return ImageGetter.getUnitIcon(Constants.settler)
            "Hurry Research" -> return ImageGetter.getUnitIcon("Great Scientist")
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
            "Stop exploration" -> return ImageGetter.getImage("OtherIcons/Stop")
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
        if(!worldScreen.isPlayersTurn) return // No actions when it's not your turn!
        for (button in UnitActions().getUnitActions(unit, worldScreen).map { getUnitActionButton(it) })
            add(button).colspan(2).padBottom(2f).row()
        pack()
    }


    private fun getUnitActionButton(unitAction: UnitAction): Button {
        val actionButton = Button(CameraStageBaseScreen.skin)
        actionButton.add(getIconForUnitAction(unitAction.title)).size(20f).pad(5f)
        val fontColor = if(unitAction.isCurrentAction) Color.YELLOW else Color.WHITE
        actionButton.add(unitAction.title.toLabel(fontColor)).pad(5f)
        actionButton.pack()
        actionButton.onClick(unitAction.uncivSound) { unitAction.action?.invoke(); UncivGame.Current.worldScreen.shouldUpdate=true }
        if (!unitAction.canAct) actionButton.disable()
        return actionButton
    }
}