package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.addClickListener
import com.unciv.ui.utils.tr
import com.unciv.ui.worldscreen.WorldScreen

class UnitTable(val worldScreen: WorldScreen) : Table(){
    private val prevIdleUnitButton = IdleUnitButton(this,worldScreen.tileMapHolder,true)
    private val nextIdleUnitButton = IdleUnitButton(this,worldScreen.tileMapHolder,false)
    private val unitNameLabel = Label("",CameraStageBaseScreen.skin)
    private val promotionsTable = Table()
    private val unitDescriptionLabel = Label("",CameraStageBaseScreen.skin)
    var selectedUnit : MapUnit? = null
    var currentlyExecutingAction : String? = null

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
        prevIdleUnitButton.update()
        nextIdleUnitButton.update()
        promotionsTable.clear()
        unitDescriptionLabel.clearListeners()

        if(selectedUnit!=null)
        {
            if(selectedUnit!!.civInfo != worldScreen.civInfo) { // The unit that was selected, was captured. It exists but is no longer ours.
                selectedUnit = null
                currentlyExecutingAction = null
            }
            else {
                try {
                    selectedUnit!!.getTile()
                } catch (ex: Exception) { // The unit that was there no longer exists}
                    selectedUnit = null
                    currentlyExecutingAction = null
                }
            }
        }

        if(selectedUnit!=null) {
            val unit = selectedUnit!!
            var nameLabelText = unit.name
            if(unit.health<100) nameLabelText+=" ("+unit.health+")"
            unitNameLabel.setText(nameLabelText)

            for(promotion in unit.promotions.promotions)
                promotionsTable.add(ImageGetter.getPromotionIcon(promotion)).size(20f)

            var unitLabelText = "Movement".tr()+": " + unit.getMovementString()
            if (unit.getBaseUnit().unitType != UnitType.Civilian) {
                unitLabelText += "\n"+"Strength".tr()+": " + unit.getBaseUnit().strength
            }
            if (unit.getBaseUnit().rangedStrength!=0)
                unitLabelText += "\n"+"Ranged strength".tr()+": "+unit.getBaseUnit().rangedStrength

            unitLabelText += "\n"+"XP".tr()+": "+unit.promotions.XP

            if(unit.isFortified() && unit.getFortificationTurns()>0)
                unitLabelText+="\n+"+unit.getFortificationTurns()*20+"% fortification"

            unitDescriptionLabel.setText(unitLabelText)
            unitDescriptionLabel.addClickListener { worldScreen.tileMapHolder.setCenterPosition(unit.getTile().position) }
        }
        else {
            unitNameLabel.setText("")
            unitDescriptionLabel.setText("")
        }

        pack()
    }

    fun tileSelected(selectedTile: TileInfo) {
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

        if(selectedTile.militaryUnit!=null && selectedTile.militaryUnit!!.civInfo == worldScreen.civInfo
                && selectedUnit!=selectedTile.militaryUnit)
            selectedUnit = selectedTile.militaryUnit

        else if (selectedTile.civilianUnit!=null && selectedTile.civilianUnit!!.civInfo == worldScreen.civInfo
                        && selectedUnit!=selectedTile.civilianUnit)
                selectedUnit = selectedTile.civilianUnit
    }

}

