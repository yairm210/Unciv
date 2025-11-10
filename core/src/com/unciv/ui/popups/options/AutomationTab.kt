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
//     val fullAutoPlayTable: Table
//     val fullAutoPlayCell: Cell<Table>

    init {
        addHeader("Automation")

        addCheckbox("Auto-assign city production", settings::autoAssignCityProduction, updateWorld = true) {
            allCitiesChooseNextConstruction(it)
        }
        addCheckbox("Auto-build roads", settings::autoBuildingRoads)
        addCheckbox("Automated workers replace improvements", settings::automatedWorkersReplaceImprovements)
        addCheckbox("Automated units move on turn start", settings::automatedUnitsMoveOnTurnStart, updateWorld = true)
        addCheckbox("Automated units can upgrade", settings::automatedUnitsCanUpgrade)
        addCheckbox("Automated units choose promotions", settings::automatedUnitsChoosePromotions)
        addCheckbox("Cities auto-bombard at end of turn", settings::citiesAutoBombardAtEndOfTurn)

        addHeader("AutoPlay")

        addCheckbox("Show AutoPlay button", settings.autoPlay::showAutoPlayButton, updateWorld = true) {
            GUI.getWorldScreenIfActive()?.autoPlay?.stopAutoPlay()
        }

        addCheckbox("AutoPlay until victory", settings.autoPlay::autoPlayUntilEnd) {
            if (!it) addAutoPlayMaxTurnsSlider(this, settings, selectBoxMinWidth)
            else replacePage { parent -> AutomationTab(parent) }
        }

        if (!settings.autoPlay.autoPlayUntilEnd)
            addAutoPlayMaxTurnsSlider(this, settings, selectBoxMinWidth)

//         addCheckbox("Full AutoPlay AI", settings.autoPlay::fullAutoPlayAI) {
//             fullAutoPlayCell.setActor(if (it) fullAutoPlayTable else null)
//             pack()
//         }
//         fullAutoPlayCell = addWrapped {
//             addAutoPlaySections()
//         }
//         fullAutoPlayTable = fullAutoPlayCell.actor
//         if (!settings.autoPlay.fullAutoPlayAI)
//            fullAutoPlayCell.setActor(null)
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

//     private fun Table.addAutoPlaySections() {
//         defaults().space(5f)
//         addCheckbox("AutoPlay Military", settings.autoPlay::autoPlayMilitary)
//         addCheckbox("AutoPlay Civilian", settings.autoPlay::autoPlayCivilian)
//         addCheckbox("AutoPlay Economy", settings.autoPlay::autoPlayEconomy)
//         addCheckbox("AutoPlay Diplomacy", settings.autoPlay::autoPlayDiplomacy)
//         addCheckbox("AutoPlay Technology", settings.autoPlay::autoPlayTechnology)
//         addCheckbox("AutoPlay Policies", settings.autoPlay::autoPlayPolicies)
//         addCheckbox("AutoPlay Religion", settings.autoPlay::autoPlayReligion)
//     }

    private fun allCitiesChooseNextConstruction(shouldAutoAssignCityProduction: Boolean) {
        if (!shouldAutoAssignCityProduction) return
        val worldScreen = GUI.getWorldScreenIfActive() ?: return
        if (!worldScreen.viewingCiv.isCurrentPlayer() || worldScreen.viewingCiv.playerType != PlayerType.Human) return
        for (city in worldScreen.gameInfo.getCurrentPlayerCivilization().cities) {
            city.cityConstructions.chooseNextConstruction()
        }
    }
}
