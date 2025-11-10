package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.widgets.UncivSlider

internal class GameplayTab(
    optionsPopup: OptionsPopup
) : OptionsPopupTab(optionsPopup) {
    init {
        addCheckbox("Check for idle units", settings::checkForDueUnits, updateWorld = true)
        addCheckbox("'Next unit' button cycles idle units", settings::checkForDueUnitsCycles, updateWorld = true)
        addCheckbox("Show Small Skip/Cycle Unit Button", settings::smallUnitButton, updateWorld = true)
        addCheckbox("Auto Unit Cycle", settings::autoUnitCycle, updateWorld = true)
        addCheckbox("Move units with a single tap", settings::singleTapMove)
        addCheckbox("Move units with a long tap", settings::longTapMove)
        addCheckbox("Order trade offers by amount", settings::orderTradeOffersByAmount)
        addCheckbox("Ask for confirmation when pressing next turn", settings::confirmNextTurn)

        addNotificationLogMaxTurnsSlider(this, settings, selectBoxMinWidth)
    }

    private fun addNotificationLogMaxTurnsSlider(
        table: Table,
        settings: GameSettings,
        selectBoxMinWidth: Float
    ) {
        table.add("Notifications log max turns".toLabel()).left().fillX()

        val minimapSlider = UncivSlider(
            3f, 15f, 1f,
            initial = settings.notificationsLogMaxTurns.toFloat()
        ) {
            val turns = it.toInt()
            settings.notificationsLogMaxTurns = turns
        }
        table.add(minimapSlider).minWidth(selectBoxMinWidth).pad(10f).row()
    }
}
