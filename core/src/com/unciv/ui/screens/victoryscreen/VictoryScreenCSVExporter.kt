package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.Gdx
import com.unciv.logic.civilization.Civilization
import com.unciv.models.translations.fillPlaceholders
import com.unciv.ui.screens.worldscreen.WorldScreen

/**
 * Handles CSV export functionality for victory screen statistics.
 */
object VictoryScreenCSVExporter {

    /**
     * Exports the current turn statistics to CSV format and copies to clipboard.
     */
    fun exportCurrentTurn(worldScreen: WorldScreen, showToast: (String, Int?) -> Unit) {
        val gameInfo = worldScreen.gameInfo
        val majorCivs = gameInfo.civilizations.filter { it.isMajorCiv() }.sortedBy { it.civName }
        val currentTurn = gameInfo.turns

        val csv = buildCSV(majorCivs, listOf(currentTurn), gameInfo)
        copyToClipboard(csv, showToast, "Current turn statistics exported to clipboard!", null)
    }

    /**
     * Exports the last 5 turns statistics to CSV format and copies to clipboard.
     * If fewer than 5 turns are available, exports all available turns.
     */
    fun exportLast5Turns(worldScreen: WorldScreen, showToast: (String, Int?) -> Unit) {
        val gameInfo = worldScreen.gameInfo
        val majorCivs = gameInfo.civilizations.filter { it.isMajorCiv() }.sortedBy { it.civName }

        // Get all available turns from the first civ's history
        val allTurns = majorCivs.firstOrNull()?.statsHistory?.keys?.sorted() ?: emptyList()

        // Take the last 5 turns (or all if fewer than 5)
        val turnsToExport = allTurns.takeLast(5)

        val csv = buildCSV(majorCivs, turnsToExport, gameInfo)
        val messageKey = if (turnsToExport.size < 5)
            "Last [] turns exported to clipboard!"
        else
            "Last 5 turns exported to clipboard!"

        val count = if (turnsToExport.size < 5) turnsToExport.size else null
        copyToClipboard(csv, showToast, messageKey, count)
    }

    /**
     * Builds CSV data for the specified turns.
     */
    private fun buildCSV(majorCivs: List<Civilization>, turns: List<Int>, gameInfo: com.unciv.logic.GameInfo): String {
        val rows = mutableListOf<List<String>>()

        // Build CSV header
        val header = mutableListOf("Turn", "Civ", "Defeated")
        header.addAll(RankingType.entries.map { it.label })
        rows.add(header)

        // Build data rows
        for (turn in turns) {
            for (civ in majorCivs) {
                val row = mutableListOf<String>()
                row.add(turn.toString())
                row.add(civ.civName)
                row.add(if (civ.isDefeated()) "Yes" else "No")

                val stats = civ.statsHistory[turn] ?: emptyMap()
                for (rankingType in RankingType.entries) {
                    row.add((stats[rankingType] ?: 0).toString())
                }

                rows.add(row)
            }
        }

        // Convert to CSV format
        return buildString {
            for (row in rows) {
                // Escape values that contain commas or quotes
                val escapedRow = row.map { value ->
                    if (value.contains(",") || value.contains("\"")) {
                        "\"${value.replace("\"", "\"\"")}\""
                    } else {
                        value
                    }
                }
                appendLine(escapedRow.joinToString(","))
            }
        }
    }

    /**
     * Copies text to clipboard and shows a toast message.
     */
    private fun copyToClipboard(text: String, showToast: (String, Int?) -> Unit, successMessage: String, count: Int?) {
        try {
            Gdx.app.clipboard.contents = text
            val messageToShow = if (count != null)
                successMessage.fillPlaceholders(count.toString())
            else
                successMessage
            showToast(messageToShow, count)
        } catch (ex: Exception) {
            showToast("Could not export statistics to clipboard!", null)
        }
    }
}
