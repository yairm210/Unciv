package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.map.MapUnit
import com.unciv.models.UnitAction
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.worldscreen.WorldScreen
import com.unciv.utils.concurrency.Concurrency

class UnitActionsTable(val worldScreen: WorldScreen) : Table() {

    fun update(unit: MapUnit?) {
        clear()
        if (unit == null) return
        if (!worldScreen.canChangeState) return // No actions when it's not your turn or spectator!
        for (button in UnitActions.getUnitActions(unit, worldScreen).map { getUnitActionButton(it) })
            add(button).left().padBottom(2f).row()
        pack()
    }


    private fun getUnitActionButton(unitAction: UnitAction): Button {
        val icon = unitAction.getIcon()
        // If peripheral keyboard not detected, hotkeys will not be displayed
        val key = if (KeyCharAndCode.keyboardAvailable) unitAction.type.key else KeyCharAndCode.UNKNOWN

        val fontColor = if (unitAction.isCurrentAction) Color.YELLOW else Color.WHITE
        val actionButton = IconTextButton(unitAction.title, icon, fontColor = fontColor)
        actionButton.addTooltip(key)
        actionButton.pack()
        if (unitAction.action == null) {
            actionButton.disable()
        } else {
            actionButton.onActivation(unitAction.uncivSound) {
                unitAction.action.invoke()
                UncivGame.Current.worldScreen!!.shouldUpdate = true
                // We keep the unit action/selection overlay from the previous unit open even when already selecting another unit
                // so you need less clicks/touches to do things, but once we do an action with the new unit, we want to close this
                // overlay, since the user definitely wants to interact with the new unit.
                worldScreen.mapHolder.removeUnitActionOverlay()
            }
            actionButton.keyShortcuts.add(key)
        }

        return actionButton
    }
}
