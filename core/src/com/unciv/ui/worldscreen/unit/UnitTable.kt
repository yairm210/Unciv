package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.UnitType
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.worldscreen.WorldScreen

class UnitTable(val worldScreen: WorldScreen) : Table(){
    private val idleUnitButton = IdleUnitButton(worldScreen)
    private val unitLabel = Label("",CameraStageBaseScreen.skin)
    var selectedUnit : MapUnit? = null
    var currentlyExecutingAction : String? = null

    private val unitActionsTable = Table()

    init {
        val tileTableBackground = ImageGetter.getDrawable("skin/tileTableBackground.png")
                .tint(Color(0x004085bf))
        pad(20f)
        background = tileTableBackground
        add(unitLabel)
        add(unitActionsTable)
        row()
        add(idleUnitButton).colspan(2)
    }

    fun update() {
        idleUnitButton.update()
        unitActionsTable.clear()
        if(selectedUnit!=null)
        {
            try{ selectedUnit!!.getTile()}
            catch(ex:Exception) {selectedUnit=null} // The unit that was there no longer exists}
        }

        if(selectedUnit!=null) {
            val unit = selectedUnit!!
            if (unit.getBaseUnit().unitType == UnitType.Civilian) {
                unitLabel.setText(unit.name
                        + "\r\nMovement: " + unit.getMovementString()
                )
            } else {
                unitLabel.setText(unit.name
                        + "\r\nMovement: " + unit.getMovementString()
                        + "\r\nHealth: " + unit.health
                        + "\r\nStrength: " + unit.getBaseUnit().strength
                )
            }
            for (button in UnitActions().getUnitActions(selectedUnit!!, worldScreen))
                unitActionsTable.add(button).colspan(2).pad(5f)
                        .size(button.width * worldScreen.buttonScale, button.height * worldScreen.buttonScale).row()
        }
        else unitLabel.setText("")

        unitActionsTable.pack()
        pack()

    }

    fun tileSelected(selectedTile: TileInfo) {
        if(currentlyExecutingAction=="moveTo"){
            val reachedTile = selectedUnit!!.headTowards(selectedTile.position)

            if(reachedTile!=selectedTile) // Didn't get all the way there
                selectedUnit!!.action = "moveTo " + selectedTile.position.x.toInt() + "," + selectedTile.position.y.toInt()
            currentlyExecutingAction = null
        }

        if(selectedTile.unit!=null && selectedTile.unit!!.civInfo == worldScreen.civInfo)
            selectedUnit= selectedTile.unit
    }

    fun getViewablePositionsForExecutingAction(): List<Vector2>
    {
        if(currentlyExecutingAction == "moveTo")
            return selectedUnit!!.getDistanceToTiles().keys.map { it.position }
        return emptyList()
    }
}
