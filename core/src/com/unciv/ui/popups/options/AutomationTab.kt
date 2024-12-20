package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.Constants
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen

fun automationTab(optionsPopup: OptionsPopup
): Table = Table(BaseScreen.skin).apply {
    defaults().padBottom(5f)

    val settings = optionsPopup.settings

    optionsPopup.addCategoryHeading(this, "Automation", true)
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
    optionsPopup.addCheckbox(
        this,
        "Cities auto-bombard at end of turn",
        settings.citiesAutoBombardAtEndOfTurn, false
    ) { settings.citiesAutoBombardAtEndOfTurn = it }

    optionsPopup.addCategoryHeading(this, "AutoPlay")
//    fun addAutoPlaySections() {
//        optionsPopup.addCheckbox(
//            this,
//            "AutoPlay Military",
//            settings.autoPlay.autoPlayMilitary, false
//        ) { settings.autoPlay.autoPlayMilitary = it }
//        optionsPopup.addCheckbox(
//            this,
//            "AutoPlay Civilian",
//            settings.autoPlay.autoPlayCivilian, false
//        ) { settings.autoPlay.autoPlayCivilian = it }
//        optionsPopup.addCheckbox(
//            this,
//            "AutoPlay Economy",
//            settings.autoPlay.autoPlayEconomy, false
//        ) { settings.autoPlay.autoPlayEconomy = it }
//        optionsPopup.addCheckbox(
//            this,
//            "AutoPlay Diplomacy",
//            settings.autoPlay.autoPlayDiplomacy, false
//        ) { settings.autoPlay.autoPlayDiplomacy = it }
//        optionsPopup.addCheckbox(
//            this,
//            "AutoPlay Technology",
//            settings.autoPlay.autoPlayTechnology, false
//        ) { settings.autoPlay.autoPlayTechnology = it }
//        optionsPopup.addCheckbox(
//            this,
//            "AutoPlay Policies",
//            settings.autoPlay.autoPlayPolicies, false
//        ) { settings.autoPlay.autoPlayPolicies = it }
//        optionsPopup.addCheckbox(
//            this,
//            "AutoPlay Religion",
//            settings.autoPlay.autoPlayReligion, false
//        ) { settings.autoPlay.autoPlayReligion = it }
//    }

    optionsPopup.addCheckbox(
        this,
        "Show AutoPlay button",
        settings.autoPlay.showAutoPlayButton, true
    ) { settings.autoPlay.showAutoPlayButton = it
        GUI.getWorldScreenIfActive()?.autoPlay?.stopAutoPlay() 
    }


    optionsPopup.addCheckbox(
        this,
        "AutoPlay until victory",
        settings.autoPlay.autoPlayUntilEnd, false
    ) { settings.autoPlay.autoPlayUntilEnd = it
        if (!it) addAutoPlayMaxTurnsSlider(this, settings, optionsPopup.selectBoxMinWidth) 
        else optionsPopup.tabs.replacePage(optionsPopup.tabs.activePage, automationTab(optionsPopup))}


    if (!settings.autoPlay.autoPlayUntilEnd)
        addAutoPlayMaxTurnsSlider(this, settings, optionsPopup.selectBoxMinWidth)

//    optionsPopup.addCheckbox(
//        this,
//        "Full AutoPlay AI",
//        settings.autoPlay.fullAutoPlayAI, false
//    ) { settings.autoPlay.fullAutoPlayAI = it
//        if (!it) addAutoPlaySections() 
//        else optionsPopup.tabs.replacePage(optionsPopup.tabs.activePage, autoPlayTab(optionsPopup))
//    }
//    if (!settings.autoPlay.fullAutoPlayAI)
//        addAutoPlaySections()
}

private fun addAutoPlayMaxTurnsSlider(
    table: Table,
    settings: GameSettings,
    selectBoxMinWidth: Float
) {
    val minimapSlider = UncivSlider(
        "Multi-turn AutoPlay amount",
        1f, 200f, 1f,
        settings.autoPlay.autoPlayMaxTurns.toFloat()
    ) {
        val turns = it.toInt()
        settings.autoPlay.autoPlayMaxTurns = turns
    }
    table.add(minimapSlider).padTop(10f).colspan(2).growX().row()
}

