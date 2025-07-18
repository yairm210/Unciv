package com.unciv.ui.screens.devconsole

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
    )
}
