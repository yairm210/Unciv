package com.unciv.ui.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.UncivSlider
import com.unciv.ui.utils.toLabel
import com.unciv.ui.worldscreen.WorldScreen

fun gameplayTab(
    optionsPopup: OptionsPopup
) = Table(BaseScreen.skin).apply {
    pad(10f)
    defaults().pad(5f)

    val settings = optionsPopup.settings
    val screen = optionsPopup.screen

    optionsPopup.addCheckbox(this, "Check for idle units", settings.checkForDueUnits, true) { settings.checkForDueUnits = it }
    optionsPopup.addCheckbox(this, "Move units with a single tap", settings.singleTapMove) { settings.singleTapMove = it }
    optionsPopup.addCheckbox(this, "Auto-assign city production", settings.autoAssignCityProduction, true) {
        settings.autoAssignCityProduction = it
        if (it && screen is WorldScreen &&
            screen.viewingCiv.isCurrentPlayer() && screen.viewingCiv.playerType == PlayerType.Human
        ) {
            screen.gameInfo.currentPlayerCiv.cities.forEach { city ->
                city.cityConstructions.chooseNextConstruction()
            }
        }
    }
    optionsPopup.addCheckbox(this, "Auto-build roads", settings.autoBuildingRoads) { settings.autoBuildingRoads = it }
    optionsPopup.addCheckbox(
        this,
        "Automated workers replace improvements",
        settings.automatedWorkersReplaceImprovements
    ) { settings.automatedWorkersReplaceImprovements = it }
    optionsPopup.addCheckbox(this, "Order trade offers by amount", settings.orderTradeOffersByAmount) { settings.orderTradeOffersByAmount = it }
    optionsPopup.addCheckbox(this, "Ask for confirmation when pressing next turn", settings.confirmNextTurn) { settings.confirmNextTurn = it }

    addNotificationLogMaxTurnsSlider(this, settings, screen, optionsPopup.selectBoxMinWidth)
}

private fun addNotificationLogMaxTurnsSlider(table: Table, settings: GameSettings, screen: BaseScreen, selectBoxMinWidth: Float) {
    table.add("Notifications log max turns".toLabel()).left().fillX()

    val minimapSlider = UncivSlider(
        3f, 15f, 1f,
        initial = settings.notificationsLogMaxTurns.toFloat()
    ) {
        val turns = it.toInt()
        settings.notificationsLogMaxTurns = turns
        settings.save()
        if (screen is WorldScreen)
            screen.shouldUpdate = true
    }
    table.add(minimapSlider).minWidth(selectBoxMinWidth).pad(10f).row()
}
