package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.UnitType
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.worldscreen.WorldScreen

class UnitTable(val worldScreen: WorldScreen) : Table(){
    private val prevIdleUnitButton = IdleUnitButton(this,worldScreen.tileMapHolder,true)
    private val nextIdleUnitButton = IdleUnitButton(this,worldScreen.tileMapHolder,false)
    private val unitNameLabel = Label("",CameraStageBaseScreen.skin)
    private val unitDescriptionLabel = Label("",CameraStageBaseScreen.skin)
    var selectedUnit : MapUnit? = null
    var currentlyExecutingAction : String? = null

    init {

        pad(20f)

        add(Table().apply {
            add(prevIdleUnitButton)
            add(unitNameLabel).pad(10f)
            add(nextIdleUnitButton)
        }).colspan(2)
        row()
        add(unitDescriptionLabel)
    }

    fun update() {
        prevIdleUnitButton.update()
        nextIdleUnitButton.update()

        if(selectedUnit!=null)
        {
            try{ selectedUnit!!.getTile()}
            catch(ex:Exception) {selectedUnit=null} // The unit that was there no longer exists}
        }

        if(selectedUnit!=null) {
            val unit = selectedUnit!!
            var nameLabelText = unit.name
            if(unit.health<100) nameLabelText+=" ("+unit.health+")"
            unitNameLabel.setText(nameLabelText)

            var unitLabelText = "Movement: " + unit.getMovementString()
            if (unit.getBaseUnit().unitType != UnitType.Civilian) {
                unitLabelText += "\nStrength: " + unit.getBaseUnit().strength
            }
            if (unit.getBaseUnit().rangedStrength!=0)
                unitLabelText += "\nRanged strength: "+unit.getBaseUnit().rangedStrength

            if(unit.isFortified() && unit.getFortificationTurns()>0)
                unitLabelText+="\n+"+unit.getFortificationTurns()*20+"% fortification"

            unitDescriptionLabel.setText(unitLabelText)


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

        if(selectedTile.unit!=null && selectedTile.unit!!.civInfo == worldScreen.civInfo)
            selectedUnit= selectedTile.unit
    }

}

