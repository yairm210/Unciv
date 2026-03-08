package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.Popup
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * Popup for exporting victory statistics to CSV format.
 */
class ExportDataPopup(
    screen: BaseScreen,
    gameInfo: com.unciv.logic.GameInfo,
    worldScreen: com.unciv.ui.screens.worldscreen.WorldScreen,
    onComplete: (message: String) -> Unit
) : Popup(screen, scrollable = Scrollability.WithoutButtons) {

    init {
        val majorCivs = gameInfo.civilizations.filter { it.isMajorCiv() }
        val historyTurns = majorCivs
            .flatMap { it.statsHistory.keys }
            .toSortedSet()

        val maxTurn = gameInfo.turns
        val minTurn = historyTurns.firstOrNull() ?: maxTurn

        addGoodSizedLabel("Options".tr())
        addSeparator()

        val optionsTable = Table()
        optionsTable.defaults().fillX().pad(5f)

        val startTurnField = UncivTextField.Integer("Start turn", minTurn)
        val endTurnField = UncivTextField.Integer("End turn", maxTurn)

        fun clampTurn(turn: Int?): Int = turn?.coerceIn(minTurn, maxTurn) ?: minTurn

        fun setRange(start: Int, end: Int) {
            startTurnField.intValue = start.coerceIn(minTurn, maxTurn)
            endTurnField.intValue = end.coerceIn(minTurn, maxTurn)
        }

        val presetsRow = Table()
        presetsRow.defaults().pad(5f)
        val allTurnsButton = "All".tr().toTextButton()
        allTurnsButton.onClick {
            setRange(minTurn, maxTurn)
        }
        val currentTurnButton = "Current turn".tr().toTextButton()
        currentTurnButton.onClick {
            setRange(maxTurn, maxTurn)
        }
        presetsRow.add(allTurnsButton)
        presetsRow.add(currentTurnButton)

        optionsTable.add(presetsRow).row()
        optionsTable.add("Start turn".toLabel()).left().row()
        optionsTable.add(startTurnField).growX().row()
        optionsTable.add("End turn".toLabel()).left().row()
        optionsTable.add(endTurnField).growX().row()
        add(optionsTable).growX().row()

        addCloseButton()
        addOKButton {
            val startTurn = clampTurn(startTurnField.intValue)
            val endTurn = clampTurn(endTurnField.intValue)
            val actualStart = minOf(startTurn, endTurn)
            val actualEnd = maxOf(startTurn, endTurn)
            val turnsToExport = (actualStart..actualEnd).toList()
            VictoryScreenCSVExporter.exportTurns(worldScreen, turnsToExport, onComplete)
        }
        equalizeLastTwoButtonWidths()
    }
}
