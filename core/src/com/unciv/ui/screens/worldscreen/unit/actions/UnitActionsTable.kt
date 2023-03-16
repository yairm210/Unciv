package com.unciv.ui.screens.worldscreen.unit.actions

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.KeyboardBindings
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.keyShortcuts
import com.unciv.ui.components.extensions.onActivation
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.screens.worldscreen.WorldScreen

class UnitActionsTable(val worldScreen: WorldScreen) : Table() {

    fun update(unit: MapUnit?) {
        clear()
        if (unit == null) return
        if (!worldScreen.canChangeState) return // No actions when it's not your turn or spectator!
        for (button in UnitActions.getUnitActions(unit)
            .map { getUnitActionButton(unit, it) })
            add(button).left().padBottom(2f).row()
        pack()
    }


    private fun getUnitActionButton(unit: MapUnit, unitAction: UnitAction): Button {
        val icon = unitAction.getIcon()
        // If peripheral keyboard not detected, hotkeys will not be displayed
        val binding = unitAction.type.binding
        val key = if (GUI.keyboardAvailable) KeyboardBindings[binding] else KeyCharAndCode.UNKNOWN

        val fontColor = if (unitAction.isCurrentAction) Color.YELLOW else Color.WHITE
        val actionButton = IconTextButton(unitAction.title, icon, fontColor = fontColor)

        if (unitAction.type == UnitActionType.Promote && unitAction.action != null)
            actionButton.color = Color.GREEN.cpy().lerp(Color.WHITE, 0.5f)

        actionButton.addTooltip(key)
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
