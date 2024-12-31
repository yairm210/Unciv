package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.screens.basescreen.BaseScreen

fun gameplayTab(
    optionsPopup: OptionsPopup
): Table = Table(BaseScreen.skin).apply {
    defaults().padBottom(5f)

    val settings = optionsPopup.settings

    optionsPopup.addCategoryHeading(this, "Units", true)

    optionsPopup.addCheckbox(this, "Check for idle units", settings.checkForDueUnits, true) { settings.checkForDueUnits = it }
    optionsPopup.addCheckbox(this, "Auto Unit Cycle", settings.autoUnitCycle, true) { settings.autoUnitCycle = it }
    optionsPopup.addCheckbox(this, "Move units with a single tap", settings.singleTapMove) { settings.singleTapMove = it }
    optionsPopup.addCheckbox(this, "Move units with a long tap", settings.longTapMove) { settings.longTapMove = it }
    
    optionsPopup.addCategoryHeading(this, "Other")

    optionsPopup.addCheckbox(this, "Order trade offers by amount", settings.orderTradeOffersByAmount) { settings.orderTradeOffersByAmount = it }
    optionsPopup.addCheckbox(this, "Ask for confirmation when pressing next turn", settings.confirmNextTurn) { settings.confirmNextTurn = it }

    addNotificationLogMaxTurnsSlider(this, settings, optionsPopup.selectBoxMinWidth)
}

private fun addNotificationLogMaxTurnsSlider(
    table: Table,
    settings: GameSettings,
    selectBoxMinWidth: Float
) {
    val minimapSlider = UncivSlider(
        "Notifications log max turns",
        3f, 15f, 1f,
        settings.notificationsLogMaxTurns.toFloat()
    ) {
        val turns = it.toInt()
        settings.notificationsLogMaxTurns = turns
    }
    table.add(minimapSlider).padTop(10f).colspan(2).growX().row()
}
