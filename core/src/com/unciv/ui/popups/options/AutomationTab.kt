package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.logic.civilization.PlayerType

internal class AutomationTab(
    optionsPopup: OptionsPopup
): OptionsPopupTab(optionsPopup) {
    val autoPlayMaxTurnsSliderTable: Table
    val autoPlayMaxTurnsSliderCell: Cell<Table>
//     val fullAutoPlayTable: Table
//     val fullAutoPlayCell: Cell<Table>

    init {
        top() // So the dynamically displayed parts won't make the page jump up and down
        add().row() // Empty Cell for the "ensure layout won't jump" line below

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
            autoPlayMaxTurnsSliderCell.setActor(if (it) null else autoPlayMaxTurnsSliderTable)
            pack()
        }

        autoPlayMaxTurnsSliderCell = addWrapped {
            addSlider("Multi-turn AutoPlay amount", settings.autoPlay.autoPlayMaxTurns, 1, 200, 1) { value, _ ->
                settings.autoPlay.autoPlayMaxTurns = value.toInt()
            }
        }
        autoPlayMaxTurnsSliderTable = autoPlayMaxTurnsSliderCell.actor
        this.cells[0].minWidth(autoPlayMaxTurnsSliderTable.minWidth) // ensure layout won't jump when slider is hidden/shown

        if (settings.autoPlay.autoPlayUntilEnd)
            autoPlayMaxTurnsSliderCell.setActor(null)

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
