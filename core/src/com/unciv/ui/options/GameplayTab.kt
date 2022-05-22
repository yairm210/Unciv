package com.unciv.ui.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.PlayerType
import com.unciv.ui.utils.BaseScreen
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
}