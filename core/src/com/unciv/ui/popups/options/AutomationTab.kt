package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.Constants
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.components.extensions.toLabel

internal class AutomationTab(
    optionsPopup: OptionsPopup
): OptionsPopupTab(optionsPopup) {
    override fun lateInitialize() {
        add("Automation".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()

        addCheckbox("Auto-assign city production", settings.autoAssignCityProduction, true) { shouldAutoAssignCityProduction ->
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
        addCheckbox("Auto-build roads", settings.autoBuildingRoads) { settings.autoBuildingRoads = it }
        addCheckbox(
            "Automated workers replace improvements",
            settings.automatedWorkersReplaceImprovements
        ) { settings.automatedWorkersReplaceImprovements = it }
        addCheckbox(
            "Automated units move on turn start",
            settings.automatedUnitsMoveOnTurnStart, true
        ) { settings.automatedUnitsMoveOnTurnStart = it }
        addCheckbox(
            "Automated units can upgrade",
            settings.automatedUnitsCanUpgrade, false
        ) { settings.automatedUnitsCanUpgrade = it }
        addCheckbox(
            "Automated units choose promotions",
            settings.automatedUnitsChoosePromotions, false
        ) { settings.automatedUnitsChoosePromotions = it }
        addCheckbox(
            "Cities auto-bombard at end of turn",
            settings.citiesAutoBombardAtEndOfTurn, false
        ) { settings.citiesAutoBombardAtEndOfTurn = it }

        addSeparator()
        add("AutoPlay".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()
//    fun addAutoPlaySections() {
//        addCheckbox(
//            "AutoPlay Military",
//            settings.autoPlay.autoPlayMilitary, false
//        ) { settings.autoPlay.autoPlayMilitary = it }
//        addCheckbox(
//            "AutoPlay Civilian",
//            settings.autoPlay.autoPlayCivilian, false
//        ) { settings.autoPlay.autoPlayCivilian = it }
//        addCheckbox(
//            "AutoPlay Economy",
//            settings.autoPlay.autoPlayEconomy, false
//        ) { settings.autoPlay.autoPlayEconomy = it }
//        addCheckbox(
//            "AutoPlay Diplomacy",
//            settings.autoPlay.autoPlayDiplomacy, false
//        ) { settings.autoPlay.autoPlayDiplomacy = it }
//        addCheckbox(
//            "AutoPlay Technology",
//            settings.autoPlay.autoPlayTechnology, false
//        ) { settings.autoPlay.autoPlayTechnology = it }
//        addCheckbox(
//            "AutoPlay Policies",
//            settings.autoPlay.autoPlayPolicies, false
//        ) { settings.autoPlay.autoPlayPolicies = it }
//        addCheckbox(
//            "AutoPlay Religion",
//            settings.autoPlay.autoPlayReligion, false
//        ) { settings.autoPlay.autoPlayReligion = it }
//    }

        addCheckbox(
            "Show AutoPlay button",
            settings.autoPlay.showAutoPlayButton, true
        ) {
            settings.autoPlay.showAutoPlayButton = it
            GUI.getWorldScreenIfActive()?.autoPlay?.stopAutoPlay()
        }


        addCheckbox(
            "AutoPlay until victory",
            settings.autoPlay.autoPlayUntilEnd, false
        ) {
            settings.autoPlay.autoPlayUntilEnd = it
            if (!it) addAutoPlayMaxTurnsSlider(this, settings, selectBoxMinWidth)
            else replacePage(::AutomationTab)
        }


        if (!settings.autoPlay.autoPlayUntilEnd)
            addAutoPlayMaxTurnsSlider(this, settings, selectBoxMinWidth)

//    addCheckbox(
//        "Full AutoPlay AI",
//        settings.autoPlay.fullAutoPlayAI, false
//    ) { settings.autoPlay.fullAutoPlayAI = it
//        if (!it) addAutoPlaySections()
//        else optionsPopup.tabs.replacePage(optionsPopup.tabs.activePage, autoPlayTab(optionsPopup))
//    }
//    if (!settings.autoPlay.fullAutoPlayAI)
//        addAutoPlaySections()

        super.lateInitialize()
    }

    private fun addAutoPlayMaxTurnsSlider(
        table: Table,
        settings: GameSettings,
        selectBoxMinWidth: Float
    ) {
        table.add("Multi-turn AutoPlay amount".toLabel()).left().fillX()

        val minimapSlider = UncivSlider(
            1f, 200f, 1f,
            initial = settings.autoPlay.autoPlayMaxTurns.toFloat()
        ) {
            val turns = it.toInt()
            settings.autoPlay.autoPlayMaxTurns = turns
        }
        table.add(minimapSlider).minWidth(selectBoxMinWidth).pad(10f).row()
    }
}
