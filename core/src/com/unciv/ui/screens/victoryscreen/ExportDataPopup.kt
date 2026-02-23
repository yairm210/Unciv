package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.Civilization
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.AskNumberPopup
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * Popup for exporting game data to CSV format.
 * Allows users to select which turns to export and the export destination.
 */
class ExportDataPopup(
    screen: BaseScreen,
    private val gameInfo: com.unciv.logic.GameInfo,
    private val worldScreen: com.unciv.ui.screens.worldscreen.WorldScreen,
    private val onComplete: (message: String) -> Unit
) : Popup(screen, scrollable = Popup.Scrollability.WithoutButtons) {

    init {
        val majorCivs = gameInfo.civilizations.filter { it.isMajorCiv() }.sortedBy { it.civName }
        val availableTurns = majorCivs.firstOrNull()?.statsHistory?.keys?.sorted() ?: emptyList()

        addGoodSizedLabel("Export game statistics".tr())

        // Add export options
        addSeparator()
        add("Export options:".tr()).padTop(10f).row()

        val optionsTable = Table()
        optionsTable.defaults().fillX().pad(5f)

        // Option 1: Export current turn
        val currentTurnButton = "Export current turn".tr().toTextButton()
        currentTurnButton.onClick {
            VictoryScreenCSVExporter.exportTurns(
                worldScreen, listOf(gameInfo.turns), onComplete
            )
            close()
        }
        optionsTable.add(currentTurnButton).row()

        // Option 2: Export last N turns
        val lastTurnsButton = "Export last N turns".tr().toTextButton()
        lastTurnsButton.onClick {
            AskNumberPopup(
                screen,
                label = "How many turns to export?".tr(),
                defaultValue = 5,
                bounds = 1..availableTurns.size,
                actionOnOk = { turnsCount ->
                    val turnsToExport = availableTurns.takeLast(turnsCount)
                    VictoryScreenCSVExporter.exportTurns(worldScreen, turnsToExport, onComplete)
                }
            ).open()
            close()
        }
        optionsTable.add(lastTurnsButton).row()

        // Option 3: Export all turns
        val allTurnsButton = "Export all turns".tr().toTextButton()
        allTurnsButton.onClick {
            ConfirmPopup(
                screen,
                "Export all [count] turns?".fillPlaceholders(availableTurns.size.toString()).tr(),
                "OK"
            ) {
                VictoryScreenCSVExporter.exportTurns(worldScreen, availableTurns, onComplete)
            }.open()
            close()
        }
        optionsTable.add(allTurnsButton).row()

        // Option 4: Export specific range
        val rangeButton = "Export turn range".tr().toTextButton()
        rangeButton.onClick {
            ExportRangePopup(screen, worldScreen, availableTurns, onComplete).open()
            close()
        }
        optionsTable.add(rangeButton).row()

        add(optionsTable).growX().row()

        addCloseButton()
    }
}

/**
 * Popup for selecting a specific turn range to export.
 */
private class ExportRangePopup(
    screen: BaseScreen,
    private val worldScreen: com.unciv.ui.screens.worldscreen.WorldScreen,
    private val availableTurns: List<Int>,
    private val onComplete: (message: String) -> Unit
) : Popup(screen, scrollable = Popup.Scrollability.WithoutButtons) {

    init {
        addGoodSizedLabel("Export turn range".tr())

        // Start turn input
        addSeparator()
        add("Start turn (1-[max])".fillPlaceholders(availableTurns.size.toString()).tr()).row()
        val startField = com.unciv.ui.components.widgets.UncivTextField.Integer("Start turn", 1)
        add(startField).growX().row()

        // End turn input
        add("End turn (start-[max])".fillPlaceholders(availableTurns.size.toString()).tr()).row()
        val endField = com.unciv.ui.components.widgets.UncivTextField.Integer("End turn", availableTurns.size)
        add(endField).growX().row()

        addCloseButton()
        addOKButton {
            val startTurnIndex = startField.intValue ?: return@addOKButton
            val endTurnIndex = endField.intValue ?: return@addOKButton

            if (startTurnIndex < 1 || startTurnIndex > availableTurns.size) {
                return@addOKButton
            }
            if (endTurnIndex < startTurnIndex || endTurnIndex > availableTurns.size) {
                return@addOKButton
            }

            val turnsToExport = availableTurns.subList(startTurnIndex - 1, endTurnIndex)
            VictoryScreenCSVExporter.exportTurns(worldScreen, turnsToExport, onComplete)
        }
        equalizeLastTwoButtonWidths()
    }
}