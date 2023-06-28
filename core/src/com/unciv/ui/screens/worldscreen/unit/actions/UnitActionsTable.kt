package com.unciv.ui.screens.worldscreen.unit.actions

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.UpgradeUnitAction
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.popups.UnitUpgradeMenu
import com.unciv.ui.screens.worldscreen.WorldScreen

class UnitActionsTable(val worldScreen: WorldScreen) : Table() {

    fun update(unit: MapUnit?) {
        clear()
        if (unit == null) return
        if (!worldScreen.canChangeState) return // No actions when it's not your turn or spectator!
        for (unitAction in UnitActions.getUnitActions(unit)) {
            val button = getUnitActionButton(unit, unitAction)
            if (unitAction is UpgradeUnitAction) {
                button.onRightClick {
                    val pos = button.localToStageCoordinates(Vector2(button.width, button.height))
                    UnitUpgradeMenu(worldScreen.stage, pos, unit, unitAction, callbackAfterAnimation = true) {
                        worldScreen.shouldUpdate = true
                    }
                }
            }
            add(button).left().padBottom(2f).row()
        }
        pack()
    }


    private fun getUnitActionButton(unit: MapUnit, unitAction: UnitAction): Button {
        val icon = unitAction.getIcon()
        // If peripheral keyboard not detected, hotkeys will not be displayed
        val binding = unitAction.type.binding

        val fontColor = if (unitAction.isCurrentAction) Color.YELLOW else Color.WHITE
        val actionButton = IconTextButton(unitAction.title, icon, fontColor = fontColor)

        if (unitAction.type == UnitActionType.Promote && unitAction.action != null)
            actionButton.color = Color.GREEN.cpy().lerp(Color.WHITE, 0.5f)

        actionButton.addTooltip(binding)
        actionButton.pack()

        if (unitAction.action == null) {
            actionButton.disable()
        } else {
            actionButton.onActivation(unitAction.uncivSound) {
                unitAction.action.invoke()
                GUI.setUpdateWorldOnNextRender()
                // We keep the unit action/selection overlay from the previous unit open even when already selecting another unit
                // so you need less clicks/touches to do things, but once we do an action with the new unit, we want to close this
                // overlay, since the user definitely wants to interact with the new unit.
                worldScreen.mapHolder.removeUnitActionOverlay()
                if (UncivGame.Current.settings.autoUnitCycle
                        && (unit.isDestroyed || unitAction.type.isSkippingToNextUnit || unit.currentMovement == 0f)) {
                    worldScreen.switchToNextUnit()
                }
            }
            actionButton.keyShortcuts.add(binding)
        }

        return actionButton
    }
}
