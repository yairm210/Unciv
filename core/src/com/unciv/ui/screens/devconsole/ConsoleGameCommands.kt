package com.unciv.ui.screens.devconsole

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency

internal class ConsoleGameCommands : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(
        "setdifficulty" to ConsoleAction("game setdifficulty <difficulty>") { console, params ->
            val difficulty = params[0].findOrNull(console.gameInfo.ruleset.difficulties.values)
                ?: throw ConsoleErrorException("Unrecognized difficulty")
            console.gameInfo.difficulty = difficulty.name
            console.gameInfo.setTransients()
            DevConsoleResponse.OK
        },

        "setturn" to ConsoleAction("game setturn <nonNegativeAmount>") { console, params ->
            val turn = params[0].toInt()
            console.gameInfo.turns = turn
            console.gameInfo.setTransients()
            DevConsoleResponse.OK
        },

        "add-spectator" to ConsoleAction("game add-spectator") { console, _ ->
            val existingSpectator = console.gameInfo.getSpectatorOrNull()
            if (existingSpectator != null) throw ConsoleErrorException("Spectator already exists")
            console.gameInfo.getSpectator("")
            DevConsoleResponse.OK
        },

        "remove-spectator" to ConsoleAction("game remove-spectator") { console, _ ->
            val existingSpectator = console.gameInfo.getSpectatorOrNull()
                ?: throw ConsoleErrorException("No Spectator in this game")
            ConfirmPopup(console.screen, "Warning: This needs to save and reload an autosave", "Do it anyway") {
                doRemoveSpectator(console, existingSpectator)
            }.open(true)
            DevConsoleResponse.OK
        },
    )

    /** Similar to [GameInfo.getSpectator], but for single player only (no player id check), and won't automatically add one */
    private fun GameInfo.getSpectatorOrNull() = civilizations.firstOrNull { it.nation.isSpectator }

    private fun doRemoveSpectator(console: DevConsolePopup, existingSpectator: Civilization) {
        val game = console.gameInfo
        if (game.currentPlayerCiv == existingSpectator)
            game.currentPlayer = ""
        game.civilizations.remove(existingSpectator)
        // dunno why a spectator makes it into everybody's diplomacy maps
        for (civ in game.civilizations)
            civ.diplomacy.remove(Constants.spectator)
        // Saving and reloading clears up the rest
        UncivGame.Current.files.autosaves.autoSave(console.gameInfo)
        console.screen.shouldUpdate = false
        UncivGame.Current.removeScreensOfType(WorldScreen::class)
        val newGame = UncivGame.Current.files.autosaves.loadLatestAutosave()
        Concurrency.runOnGLThread {
            UncivGame.Current.loadGame(newGame, callFromLoadScreen = true)
        }
    }
}
