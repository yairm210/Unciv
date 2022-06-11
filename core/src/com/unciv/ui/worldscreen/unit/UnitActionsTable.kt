package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.map.MapUnit
import com.unciv.models.UnitAction
import com.unciv.ui.audio.Sounds
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.utils.*
import com.unciv.ui.utils.KeyPressDispatcher.Companion.keyboardAvailable
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.worldscreen.WorldScreen

class UnitActionsTable(val worldScreen: WorldScreen) : Table() {

    fun update(unit: MapUnit?) {
        clear()
        if (unit == null) return
        if (!worldScreen.canChangeState) return // No actions when it's not your turn or spectator!
        for (button in UnitActions.getUnitActions(unit, worldScreen).map { getUnitActionButton(it) })
            if (UncivGame.Current.settings.androidCutout)
                add(button).left().padLeft(60f).padBottom(2f).row()
            else
                add(button).left().padBottom(2f).row()
        pack()
    }


    private fun getUnitActionButton(unitAction: UnitAction): Button {
        val icon = unitAction.getIcon()
        // If peripheral keyboard not detected, hotkeys will not be displayed
        val key = if (keyboardAvailable) unitAction.type.key else KeyCharAndCode.UNKNOWN

        val fontColor = if (unitAction.isCurrentAction) Color.YELLOW else Color.WHITE
        val actionButton = IconTextButton(unitAction.title, icon, fontColor = fontColor)
        actionButton.addTooltip(key)
        actionButton.pack()
        val action = {
            unitAction.action?.invoke()
            UncivGame.Current.worldScreen.shouldUpdate = true
        }
        if (unitAction.action == null) actionButton.disable()
        else {
            actionButton.onClick(unitAction.uncivSound, action)
            if (key != KeyCharAndCode.UNKNOWN)
                worldScreen.keyPressDispatcher[key] = {
                    launchCrashHandling("UnitSound") { Sounds.play(unitAction.uncivSound) }
                    action()
                    worldScreen.mapHolder.removeUnitActionOverlay()
                }
        }

        return actionButton
    }
}
