package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen

class UnitTable(val worldScreen: WorldScreen) : Table(){
    private val prevIdleUnitButton = IdleUnitButton(this,worldScreen.tileMapHolder,true)
    private val nextIdleUnitButton = IdleUnitButton(this,worldScreen.tileMapHolder,false)
    private val unitNameLabel = Label("",CameraStageBaseScreen.skin)
    private val promotionsTable = Table()
    private val unitDescriptionLabel = Label("",CameraStageBaseScreen.skin)
    var selectedUnit : MapUnit? = null
    var currentlyExecutingAction : String? = null

    // This is so that not on every update(), we will update the unit table.
    // Most of the time it's the same unit with the same stats so why waste precious time?
    var selectedUnitHasChanged = false

    init {
        pad(5f)

        add(Table().apply {
            add(prevIdleUnitButton)
            add(unitNameLabel).pad(5f)
            add(nextIdleUnitButton)
        }).colspan(2).row()
        add(promotionsTable).row()
        add(unitDescriptionLabel)
    }

    fun update() {
        if(selectedUnit!=null) {
            if (selectedUnit!!.civInfo != worldScreen.civInfo) { // The unit that was selected, was captured. It exists but is no longer ours.
                selectedUnit = null
                currentlyExecutingAction = null
                selectedUnitHasChanged = true
            } else if (selectedUnit!! !in selectedUnit!!.getTile().getUnits()) { // The unit that was there no longer exists}
                selectedUnit = null
                currentlyExecutingAction = null
                selectedUnitHasChanged = true
            }
        }

        if(prevIdleUnitButton.getTilesWithIdleUnits().isNotEmpty()) { // more efficient to do this check once for both
            prevIdleUnitButton.enable()
            nextIdleUnitButton.enable()
        }
        else{
            prevIdleUnitButton.disable()
            nextIdleUnitButton.disable()
        }

        if(selectedUnit!=null) { // set texts - this is valid even when it's the same unit, because movement points and health change
            val unit = selectedUnit!!
            var nameLabelText = unit.name.tr()
            if(unit.health<100) nameLabelText+=" ("+unit.health+")"
            unitNameLabel.setText(nameLabelText)

            var unitLabelText = "Movement".tr()+": " + unit.getMovementString()
            if (unit.baseUnit().unitType != UnitType.Civilian)
                unitLabelText += "\n"+"Strength".tr()+": " + unit.baseUnit().strength

            if (unit.baseUnit().rangedStrength!=0)
                unitLabelText += "\n"+"Ranged strength".tr()+": "+unit.baseUnit().rangedStrength

            if (unit.baseUnit().unitType != UnitType.Civilian)
                unitLabelText += "\n"+"XP".tr()+": "+unit.promotions.XP+"/"+unit.promotions.xpForNextPromotion()

            if(unit.isFortified() && unit.getFortificationTurns()>0)
                unitLabelText+="\n+"+unit.getFortificationTurns()*20+"% fortification"

            unitDescriptionLabel.setText(unitLabelText)

            if(unit.promotions.promotions.size != promotionsTable.children.size) // The unit has been promoted! Reload promotions!
                selectedUnitHasChanged = true
        }
        else {
            unitNameLabel.setText("")
            unitDescriptionLabel.setText("")
        }

        if(!selectedUnitHasChanged) return

        promotionsTable.clear()
        unitDescriptionLabel.clearListeners()

        if(selectedUnit!=null) {
            for(promotion in selectedUnit!!.promotions.promotions)
                promotionsTable.add(ImageGetter.getPromotionIcon(promotion)).size(20f)

            unitDescriptionLabel.onClick { worldScreen.tileMapHolder.setCenterPosition(selectedUnit!!.getTile().position) }
        }

        pack()
        selectedUnitHasChanged=false
    }

    fun tileSelected(selectedTile: TileInfo) {
        val previouslySelectedUnit = selectedUnit
        if(currentlyExecutingAction=="moveTo"){
            if(selectedUnit!!.movementAlgs()
                    .getShortestPath(selectedTile).isEmpty())
                return // can't reach there with the selected unit, watcha want me to do?

            val reachedTile = selectedUnit!!.movementAlgs().headTowards(selectedTile)

            selectedUnit!!.action=null // Disable any prior action (automation, fortification...)
            if(reachedTile!=selectedTile) // Didn't get all the way there
                selectedUnit!!.action = "moveTo " + selectedTile.position.x.toInt() + "," + selectedTile.position.y.toInt()
            currentlyExecutingAction = null
        }

        else if(selectedTile.militaryUnit!=null && selectedTile.militaryUnit!!.civInfo == worldScreen.civInfo
                && selectedUnit!=selectedTile.militaryUnit)
            selectedUnit = selectedTile.militaryUnit

        else if (selectedTile.civilianUnit!=null && selectedTile.civilianUnit!!.civInfo == worldScreen.civInfo
                        && selectedUnit!=selectedTile.civilianUnit)
                selectedUnit = selectedTile.civilianUnit

        if(selectedUnit != previouslySelectedUnit)
            selectedUnitHasChanged = true
    }

}

