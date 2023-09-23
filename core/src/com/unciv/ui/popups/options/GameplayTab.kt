package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.UncivSlider
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen

fun gameplayTab(
    optionsPopup: OptionsPopup
): Table = Table(BaseScreen.skin).apply {
    pad(10f)
    defaults().pad(5f)

    val settings = optionsPopup.settings

    optionsPopup.addCheckbox(this, "Check for idle units", settings.checkForDueUnits, true) { settings.checkForDueUnits = it }
    optionsPopup.addCheckbox(this, "Auto Unit Cycle", settings.autoUnitCycle, true) { settings.autoUnitCycle = it }
    optionsPopup.addCheckbox(this, "Move units with a single tap", settings.singleTapMove) { settings.singleTapMove = it }
    optionsPopup.addCheckbox(this, "Auto-assign city production", settings.autoAssignCityProduction, true) { shouldAutoAssignCityProduction ->
        settings.autoAssignCityProduction = shouldAutoAssignCityProduction
        val worldScreen = GUI.getWorldScreenIfActive()
        if (shouldAutoAssignCityProduction && worldScreen != null &&
                worldScreen.viewingCiv.isCurrentPlayer() && worldScreen.viewingCiv.playerType == PlayerType.Human
        ) {
            worldScreen.gameInfo.getCurrentPlayerCivilization().cities.forEach { city ->
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
    optionsPopup.addCheckbox(
        this,
        "Automated units move on turn start",
        settings.automatedUnitsMoveOnTurnStart, true
    ) { settings.automatedUnitsMoveOnTurnStart = it }
    optionsPopup.addCheckbox(
        this,
        "Automated units can upgrade",
        settings.automatedUnitsCanUpgrade, false
    ) { settings.automatedUnitsCanUpgrade = it }
    optionsPopup.addCheckbox(
        this,
        "Automated units choose promotions",
        settings.automatedUnitsChoosePromotions, false
    ) { settings.automatedUnitsChoosePromotions = it }
    optionsPopup.addCheckbox(this, "Order trade offers by amount", settings.orderTradeOffersByAmount) { settings.orderTradeOffersByAmount = it }
    optionsPopup.addCheckbox(this, "Ask for confirmation when pressing next turn", settings.confirmNextTurn) { settings.confirmNextTurn = it }

    addNotificationLogMaxTurnsSlider(this, settings, optionsPopup.selectBoxMinWidth)

    fun addAutoPlaySections() {
        optionsPopup.addCheckbox(
            this,
            "AutoPlay Military",
            settings.autoPlayMilitary, false
        ) { settings.autoPlayMilitary = it }
        optionsPopup.addCheckbox(
            this,
            "AutoPlay Civilian",
            settings.autoPlayCivilian, false
        ) { settings.autoPlayCivilian = it }
        optionsPopup.addCheckbox(
            this,
            "AutoPlay Economy",
            settings.autoPlayEconomy, false
        ) { settings.autoPlayEconomy = it }
        optionsPopup.addCheckbox(
            this,
            "AutoPlay Diplomacy",
            settings.autoPlayDiplomacy, false
        ) { settings.autoPlayDiplomacy = it }
        optionsPopup.addCheckbox(
            this,
            "AutoPlay Technology",
            settings.autoPlayTechnology, false
        ) { settings.autoPlayTechnology = it }
        optionsPopup.addCheckbox(
            this,
            "AutoPlay Policies",
            settings.autoPlayPolicies, false
        ) { settings.autoPlayPolicies = it }
        optionsPopup.addCheckbox(
            this,
            "AutoPlay Religion",
            settings.autoPlayReligion, false
        ) { settings.autoPlayReligion = it }
    }
    
    addAutoPlayMaxTurnsSlider(this, settings, optionsPopup.selectBoxMinWidth)
    optionsPopup.addCheckbox(
        this,
        "Full AutoPlay AI",
        settings.fullAutoPlayAI, false
    ) { settings.fullAutoPlayAI = it
        if (!it) addAutoPlaySections() 
        else optionsPopup.tabs.replacePage(optionsPopup.tabs.activePage, gameplayTab(optionsPopup))
    }
    if (!settings.fullAutoPlayAI)
        addAutoPlaySections()
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
private fun addAutoPlayMaxTurnsSlider(
    table: Table,
    settings: GameSettings,
    selectBoxMinWidth: Float
) {
    table.add("Max turns to AutoPlay".toLabel()).left().fillX()

    val minimapSlider = UncivSlider(
        1f, 1000f, 5f,
        initial = settings.autoPlayMaxTurns.toFloat()
    ) {
        val turns = it.toInt()
        settings.autoPlayMaxTurns = turns
    }
    table.add(minimapSlider).minWidth(selectBoxMinWidth).pad(10f).row()
}

