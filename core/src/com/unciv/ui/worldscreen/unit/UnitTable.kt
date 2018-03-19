package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.worldscreen.WorldScreen

class UnitTable(val worldScreen: WorldScreen) : Table(){
    private val idleUnitButton = IdleUnitButton(worldScreen)
    private val unitLabel = Label("",CameraStageBaseScreen.skin)
    var selectedUnitTile : TileInfo? = null
    var currentlyExecutingAction : String? = null

    private val unitActionsTable = Table()

    fun getSelectedUnit(): MapUnit {
        if(selectedUnitTile==null) throw Exception("getSelectedUnit was called when no unit was selected!")
        else return selectedUnitTile!!.unit!!
    }

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
        if(selectedUnitTile!=null && selectedUnitTile!!.unit==null) selectedUnitTile=null // The unit that was there no longer exists

        if(selectedUnitTile!=null) {
            unitLabel.setText(getSelectedUnit().name+"  "+getSelectedUnit().movementString)
            for (button in UnitActions().getUnitActions(selectedUnitTile!!,worldScreen))
                unitActionsTable.add(button).colspan(2).pad(5f)
                        .size(button.width * worldScreen.buttonScale, button.height * worldScreen.buttonScale).row()
        }
        else unitLabel.setText("")

        unitActionsTable.pack()
        pack()

        setPosition(worldScreen.stage.width / 2 - width / 2, 5f)
    }

    fun tileSelected(selectedTile: TileInfo) {
        if(currentlyExecutingAction=="moveTo"){
            val reachedTile = getSelectedUnit().headTowards(selectedUnitTile!!.position, selectedTile.position)
            selectedUnitTile = reachedTile
            if(reachedTile!=selectedTile) // Didn't get all the way there
                getSelectedUnit().action = "moveTo " + selectedTile.position.x.toInt() + "," + selectedTile.position.y.toInt()
            currentlyExecutingAction = null
        }

        if(selectedTile.unit!=null) selectedUnitTile = selectedTile
    }

    private fun getDistanceToTiles(): HashMap<TileInfo, Float> {
        return worldScreen.tileMapHolder.tileMap.getDistanceToTilesWithinTurn(selectedUnitTile!!.position,
                getSelectedUnit().currentMovement,
                getSelectedUnit().civInfo.tech.isResearched("Machinery"))
    }

    fun getViewablePositionsForExecutingAction(): List<Vector2>
    {
        if(currentlyExecutingAction == "moveTo")
            return getDistanceToTiles().keys.map { it.position }
        return emptyList()
    }
}
