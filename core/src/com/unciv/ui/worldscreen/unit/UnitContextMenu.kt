package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.Sounds
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.TileMapHolder
import com.unciv.logic.map.BuildLongRoadAction
import kotlin.concurrent.thread

class UnitContextMenu(val tileMapHolder: TileMapHolder, val selectedUnit: MapUnit, val targetTile: TileInfo) : VerticalGroup() {

    init {

        space(10f)

        addButton(ImageGetter.getStatIcon("Movement"), "Move here") {
            onMoveButtonClick()
        }
        addButton(ImageGetter.getImprovementIcon("Road"), "Construct Road this way") {
            onConstructRoadButtonClick()
        }

        pack()

    }

    fun addButton(icon: Actor, label: String, action: () -> Unit) {
        val skin = CameraStageBaseScreen.skin
        val button = Button(skin)
        button.add(icon).size(20f).padRight(10f)
        button.add(label)
        addActor(button)
        button.onClick { action() }
    }

    fun onMoveButtonClick() {
        // this can take a long time, because of the unit-to-tile calculation needed, so we put it in a different thread
        thread {
            if (selectedUnit.movementAlgs().canReach(targetTile)) {
                try {
                    // Because this is darned concurrent (as it MUST be to avoid ANRs),
                    // there are edge cases where the canReach is true,
                    // but until it reaches the headTowards the board has changed and so the headTowards fails.
                    // I can't think of any way to avoid this,
                    // but it's so rare and edge-case-y that ignoring its failure is actually acceptable, hence the empty catch
                    selectedUnit.movementAlgs().headTowards(targetTile)
                    Sounds.play("whoosh")
                    if (selectedUnit.currentTile != targetTile)
                        selectedUnit.action = "moveTo " + targetTile.position.x.toInt() + "," + targetTile.position.y.toInt()
                    if(selectedUnit.currentMovement>0){
                        tileMapHolder.worldScreen.bottomBar.unitTable.selectedUnit=selectedUnit
                    }
                } catch (e: Exception) {
                }
            }

            // we don't update it directly because we're on a different thread; instead, we tell it to update itself
            tileMapHolder.worldScreen.shouldUpdate = true

            tileMapHolder.removeUnitActionOverlay=true
        }
    }

    private fun onConstructRoadButtonClick() {
        selectedUnit.mapUnitAction = BuildLongRoadAction()
    }
}