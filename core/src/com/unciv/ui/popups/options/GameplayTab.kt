package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.screens.basescreen.BaseScreen

fun gameplayTab(
    optionsPopup: OptionsPopup
): Table = Table(BaseScreen.skin).apply {
    pad(10f)
    defaults().pad(5f)

    val settings = optionsPopup.settings

    optionsPopup.addCheckbox(this, "Check for idle units", settings.checkForDueUnits, true) { settings.checkForDueUnits = it }
    optionsPopup.addCheckbox(this, "'Next unit' button cycles idle units", settings.checkForDueUnitsCycles, true) { settings.checkForDueUnitsCycles = it }
    optionsPopup.addCheckbox(this, "Auto Unit Cycle", settings.autoUnitCycle, true) { settings.autoUnitCycle = it }
    optionsPopup.addCheckbox(this, "'Next unit' button cycles idle units", settings.checkForDueUnitsCycles, true) { settings.checkForDueUnitsCycles = it }
    optionsPopup.addCheckbox(this, "Move units with a single tap", settings.singleTapMove) { settings.singleTapMove = it }
    optionsPopup.addCheckbox(this, "Move units with a long tap", settings.longTapMove) { settings.longTapMove = it }
    optionsPopup.addCheckbox(this, "Order trade offers by amount", settings.orderTradeOffersByAmount) { settings.orderTradeOffersByAmount = it }
    optionsPopup.addCheckbox(this, "Ask for confirmation when pressing next turn", settings.confirmNextTurn) { settings.confirmNextTurn = it }

    addNotificationLogMaxTurnsSlider(this, settings, optionsPopup.selectBoxMinWidth)
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
