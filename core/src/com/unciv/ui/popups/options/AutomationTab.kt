package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.Constants
import com.unciv.logic.civilization.PlayerType
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel

internal class AutomationTab(
    optionsPopup: OptionsPopup
): OptionsPopupTab(optionsPopup) {
    lateinit var autoPlayMaxTurnsSliderTable: Table
    lateinit var autoPlayMaxTurnsSliderCell: Cell<Table>

    override fun lateInitialize() {
        add("Automation".toLabel(fontSize = Constants.headingFontSize)).colspan(2).row()

        addCheckbox("Auto-assign city production", settings::autoAssignCityProduction, updateWorld = true) {
            allCitiesChooseNextConstruction(it)
        }
        addCheckbox("Auto-build roads", settings::autoBuildingRoads)
        addCheckbox("Automated workers replace improvements", settings::automatedWorkersReplaceImprovements)
        addCheckbox("Automated units move on turn start", settings::automatedUnitsMoveOnTurnStart, updateWorld = true)
        addCheckbox("Automated units can upgrade", settings::automatedUnitsCanUpgrade)
        addCheckbox("Automated units choose promotions", settings::automatedUnitsChoosePromotions)
        addCheckbox("Cities auto-bombard at end of turn", settings::citiesAutoBombardAtEndOfTurn)

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

        addCheckbox("Show AutoPlay button", settings.autoPlay::showAutoPlayButton, updateWorld = true) {
            GUI.getWorldScreenIfActive()?.autoPlay?.stopAutoPlay()
        }

        addCheckbox("AutoPlay until victory", settings.autoPlay::autoPlayUntilEnd) {
            autoPlayMaxTurnsSliderCell.setActor(if (it) autoPlayMaxTurnsSliderTable else null)
            pack()
        }

        autoPlayMaxTurnsSliderCell = addWrapped {
            addSlider("Multi-turn AutoPlay amount", settings.autoPlay.autoPlayMaxTurns, 1, 200, 1) { value, _ ->
                settings.autoPlay.autoPlayMaxTurns = value.toInt()
            }
        }
        autoPlayMaxTurnsSliderTable = autoPlayMaxTurnsSliderCell.actor

        if (!settings.autoPlay.autoPlayUntilEnd)
            autoPlayMaxTurnsSliderCell.setActor(null)

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

    private fun allCitiesChooseNextConstruction(shouldAutoAssignCityProduction: Boolean) {
        if (!shouldAutoAssignCityProduction) return
        val worldScreen = GUI.getWorldScreenIfActive() ?: return
        if (!worldScreen.viewingCiv.isCurrentPlayer() || worldScreen.viewingCiv.playerType != PlayerType.Human) return
        for (city in worldScreen.gameInfo.getCurrentPlayerCivilization().cities) {
            city.cityConstructions.chooseNextConstruction()
        }
    }
}
