package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.screens.basescreen.BaseScreen

internal class GameplayTab(
    optionsPopup: OptionsPopup
) : Table(BaseScreen.skin), OptionsPopupHelpers {
    override val selectBoxMinWidth by optionsPopup::selectBoxMinWidth

    init {
        pad(10f)
        defaults().pad(5f)

        val settings = optionsPopup.settings

        addCheckbox("Check for idle units", settings.checkForDueUnits, true) { settings.checkForDueUnits = it }
        addCheckbox("'Next unit' button cycles idle units", settings.checkForDueUnitsCycles, true) { settings.checkForDueUnitsCycles = it }
        addCheckbox("Show Small Skip/Cycle Unit Button", settings.smallUnitButton, true) { settings.smallUnitButton = it }
        addCheckbox("Auto Unit Cycle", settings.autoUnitCycle, true) { settings.autoUnitCycle = it }
        addCheckbox("Move units with a single tap", settings.singleTapMove) { settings.singleTapMove = it }
        addCheckbox("Move units with a long tap", settings.longTapMove) { settings.longTapMove = it }
        addCheckbox("Order trade offers by amount", settings.orderTradeOffersByAmount) { settings.orderTradeOffersByAmount = it }
        addCheckbox("Ask for confirmation when pressing next turn", settings.confirmNextTurn) { settings.confirmNextTurn = it }

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
