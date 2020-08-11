package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.Constants
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.action.BuildLongRoadAction
import com.unciv.logic.map.action.MapUnitAction
import com.unciv.models.UncivSound
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.Sounds
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.WorldMapHolder
import kotlin.concurrent.thread

class UnitContextMenu(val tileMapHolder: WorldMapHolder, val selectedUnit: MapUnit, val targetTile: TileInfo) : VerticalGroup() {

    init {

        space(10f)

        addButton(ImageGetter.getStatIcon("Movement"), "Move unit") {
            onMoveButtonClick()
        }

        // Basic scenarios sommetimes don't have roads
        if (selectedUnit.civInfo.gameInfo.ruleSet.tileImprovements.containsKey("Road"))
            addButton(
                    ImageGetter.getImprovementIcon("Road"),
                    "Construct road",
                    BuildLongRoadAction(selectedUnit, targetTile)
            )

        pack()
    }

    fun addButton(icon: Actor, label: String, action: MapUnitAction) {
        if (action.isAvailable()) {
            addButton(icon, label) {
                selectedUnit.mapUnitAction = action
                selectedUnit.mapUnitAction?.doPreTurnAction()
                tileMapHolder.unitActionOverlay?.remove()
                tileMapHolder.worldScreen.shouldUpdate = true
            }
        }
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
        thread(name = "TileToMoveTo") {
            // these are the heavy parts, finding where we want to go
            // Since this runs in a different thread, even if we check movement.canReach()
            // then it might change until we get to the getTileToMoveTo, so we just try/catch it
            val tileToMoveTo: TileInfo
            try {
                tileToMoveTo = selectedUnit.movement.getTileToMoveToThisTurn(targetTile)
            } catch (ex: Exception) {
                return@thread
            } // can't move here

            Gdx.app.postRunnable {
                try {
                    // Because this is darned concurrent (as it MUST be to avoid ANRs),
                    // there are edge cases where the canReach is true,
                    // but until it reaches the headTowards the board has changed and so the headTowards fails.
                    // I can't think of any way to avoid this,
                    // but it's so rare and edge-case-y that ignoring its failure is actually acceptable, hence the empty catch
                    selectedUnit.movement.moveToTile(tileToMoveTo)
                    if (selectedUnit.action == Constants.unitActionExplore) selectedUnit.action = null // remove explore on manual move
                    Sounds.play(UncivSound.Whoosh)
                    if (selectedUnit.currentTile != targetTile)
                        selectedUnit.action = "moveTo " + targetTile.position.x.toInt() + "," + targetTile.position.y.toInt()
                    if (selectedUnit.currentMovement > 0) {
                        tileMapHolder.worldScreen.bottomUnitTable.selectedUnit = selectedUnit
                    }

                    tileMapHolder.worldScreen.shouldUpdate = true
                    tileMapHolder.unitActionOverlay?.remove()
                } catch (e: Exception) {}

            }
        }
    }

}