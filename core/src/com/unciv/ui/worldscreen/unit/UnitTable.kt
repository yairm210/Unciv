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

    private val unitActionsTable = Table()

    init {

        pad(20f)
        //background = tileTableBackground

        add(Table().apply {
            add(prevIdleUnitButton)
            add(unitNameLabel).pad(10f)
            add(nextIdleUnitButton)
        }).colspan(2)
        row()
        add(unitDescriptionLabel)
        add(unitActionsTable)
    }

    fun update() {
        prevIdleUnitButton.update()
        nextIdleUnitButton.update()
        unitActionsTable.clear()
        if(selectedUnit!=null)
        {
            try{ selectedUnit!!.getTile()}
            catch(ex:Exception) {selectedUnit=null} // The unit that was there no longer exists}
        }

        if(selectedUnit!=null) {
            val unit = selectedUnit!!
            unitNameLabel.setText(unit.name)

            var unitLabelText = "Movement: " + unit.getMovementString()
            if (unit.getBaseUnit().unitType != UnitType.Civilian) {
                unitLabelText += "\r\nHealth: " + unit.health +
                        "\r\nStrength: " + unit.getBaseUnit().strength
            }
            if (unit.getBaseUnit().rangedStrength!=0)
                unitLabelText += "\r\nRanged strength: "+unit.getBaseUnit().rangedStrength

            unitDescriptionLabel.setText(unitLabelText)

            for (button in UnitActions().getUnitActionButtons(selectedUnit!!, worldScreen))
                unitActionsTable.add(button).colspan(2).pad(5f)
                        .size(button.width * worldScreen.buttonScale, button.height * worldScreen.buttonScale).row()
        }
        else {
            unitNameLabel.setText("")
            unitDescriptionLabel.setText("")
        }

        unitActionsTable.pack()
        pack()

    }

    fun tileSelected(selectedTile: TileInfo) {
        if(currentlyExecutingAction=="moveTo"){

            if(selectedUnit!!.movementAlgs()
                    .getShortestPath(selectedTile).isEmpty())
                return // can't reach there with the selected unit, watcha want me to do?

            val reachedTile = selectedUnit!!.movementAlgs().headTowards(selectedTile)

            if(reachedTile!=selectedTile) // Didn't get all the way there
                selectedUnit!!.action = "moveTo " + selectedTile.position.x.toInt() + "," + selectedTile.position.y.toInt()
            currentlyExecutingAction = null
        }

        if(selectedTile.unit!=null && selectedTile.unit!!.civInfo == worldScreen.civInfo)
            selectedUnit= selectedTile.unit
    }

}
