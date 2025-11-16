package com.unciv.ui.popups.options

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

        addSlider("Notifications log max turns", settings.notificationsLogMaxTurns, 3, 15, 1) { value, _ ->
            settings.notificationsLogMaxTurns = value.toInt()
        }
    }
}
