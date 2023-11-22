package com.unciv.ui.screens.devconsole

import com.unciv.ui.screens.devconsole.DevConsoleCommand.Companion.toCliInput

@Suppress("EnumEntryName")
enum class DevConsoleCommandCity {
    setpop {
        override fun handle(console: DevConsolePopup, params: List<String>): String? {
            if (params.size != 2) return "Format: city setpop <cityName> <amount>"
            val newPop = params[1].toIntOrNull() ?: return "Invalid amount "+params[1]
            if (newPop < 1) return "Invalid amount $newPop"
            val city = console.gameInfo.getCities().firstOrNull { it.name.toCliInput() == params[0] }
                ?: return "Unknown city"
            city.population.setPopulation(newPop)
            return null
        }
    },
    addtile {
        override fun handle(console: DevConsolePopup, params: List<String>): String? {
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return "No tile selected"
            val city = console.gameInfo.getCities().firstOrNull { it.name.toCliInput() == params[2] }
                ?: return "Unknown city"
            if (selectedTile.neighbors.none { it.getCity() == city })
                return "Tile is not adjacent to city"
            city.expansion.takeOwnership(selectedTile)
            return null
        }
    },
    removetile {
        override fun handle(console: DevConsolePopup, params: List<String>): String? {
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return "No tile selected"
            val city = console.gameInfo.getCities().firstOrNull { it.name.toCliInput() == params[2] }
                ?: return "Unknown city"
            if (city.tiles.contains(selectedTile.position)) city.expansion.relinquishOwnership(selectedTile)
            return null
        }
    },
    ;

    abstract fun handle(console: DevConsolePopup, params: List<String>): String?

    companion object {
        fun handle(console: DevConsolePopup, params: List<String>): String? {
            if (params.isEmpty())
                return "Available subcommands: " + values().joinToString { it.name }
            val handler = values().firstOrNull { it.name == params[0] }
                ?: return "Invalid subcommand"
            return handler.handle(console, params.drop(1))
        }
    }
}
