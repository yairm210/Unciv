package com.unciv.ui.screens.victoryscreen

import com.badlogic.gdx.Gdx
import com.unciv.logic.civilization.Civilization
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.screens.worldscreen.WorldScreen

/**
 * Handles CSV export functionality for victory screen statistics.
 * Provides flexible export options for any set of turns.
 */
object VictoryScreenCSVExporter {

    /**
     * Exports statistics for the specified turns to CSV format and copies to clipboard.
     *
     * @param worldScreen The world screen containing the game data
     * @param turns List of turn numbers to export (must be sorted)
     * @param onComplete Callback with success/error message
     */
    fun exportTurns(
        worldScreen: WorldScreen,
        turns: List<Int>,
        onComplete: (message: String) -> Unit
    ) {
        val gameInfo = worldScreen.gameInfo
        val majorCivs = gameInfo.civilizations.filter { it.isMajorCiv() }.sortedBy { it.civName }

        if (turns.isEmpty()) {
            onComplete("No turns to export".tr())
            return
        }

        val csv = buildCSV(majorCivs, turns, gameInfo)
        copyToClipboard(csv, onComplete, turns.size)
    }

    /**
     * Builds CSV data for the specified turns and civilizations.
     *
     * @param majorCivs List of major civilizations to include
     * @param turns List of turn numbers to export
     * @param gameInfo Game information
     * @return CSV formatted string
     */
    private fun buildCSV(
        majorCivs: List<Civilization>,
        turns: List<Int>,
        gameInfo: com.unciv.logic.GameInfo
    ): String {
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
     *
     * @param text Text to copy
     * @param onComplete Callback with success/error message
     * @param turnCount Number of turns exported (for message formatting)
     */
    private fun copyToClipboard(
        text: String,
        onComplete: (message: String) -> Unit,
        turnCount: Int
    ) {
        try {
            Gdx.app.clipboard.contents = text
            val message = if (turnCount == 1) {
                "Current turn statistics exported to clipboard!".tr()
            } else {
                "Last [count] turns exported to clipboard!".fillPlaceholders(turnCount.toString()).tr()
            }
            onComplete(message)
        } catch (ex: Exception) {
            onComplete("Could not export statistics to clipboard!".tr())
        }
    }
}
