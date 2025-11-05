package com.unciv.ui.popups.options

internal class GameplayTab(
    optionsPopup: OptionsPopup
): OptionsPopupTab(optionsPopup) {
    override fun lateInitialize() {
        addCheckbox("Check for idle units", settings.checkForDueUnits, true) { settings.checkForDueUnits = it }
        addCheckbox("'Next unit' button cycles idle units", settings.checkForDueUnitsCycles, true) { settings.checkForDueUnitsCycles = it }
        addCheckbox("Show Small Skip/Cycle Unit Button", settings.smallUnitButton, true) { settings.smallUnitButton = it }
        addCheckbox("Auto Unit Cycle", settings.autoUnitCycle, true) { settings.autoUnitCycle = it }
        addCheckbox("Move units with a single tap", settings.singleTapMove) { settings.singleTapMove = it }
        addCheckbox("Move units with a long tap", settings.longTapMove) { settings.longTapMove = it }
        addCheckbox("Order trade offers by amount", settings.orderTradeOffersByAmount) { settings.orderTradeOffersByAmount = it }
        addCheckbox("Ask for confirmation when pressing next turn", settings.confirmNextTurn) { settings.confirmNextTurn = it }

        addSlider("Notifications log max turns", settings.notificationsLogMaxTurns, 3, 15, 1) { value, _ ->
            settings.notificationsLogMaxTurns = value.toInt()
        }

        super.lateInitialize()
    }
}
