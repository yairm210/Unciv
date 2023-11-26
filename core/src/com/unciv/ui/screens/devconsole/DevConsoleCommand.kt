package com.unciv.ui.screens.devconsole

import com.unciv.logic.civilization.Civilization

fun String.toCliInput() = this.lowercase().replace(" ","-")

interface ConsoleCommand {
    fun handle(console: DevConsolePopup, params: List<String>): String?
    fun autocomplete(params: List<String>): String? = ""
}

class ConsoleAction(val action: (console: DevConsolePopup, params: List<String>)->String?):ConsoleCommand{
    override fun handle(console: DevConsolePopup, params: List<String>): String? {
        return action(console, params)
    }
}

interface ConsoleCommandNode:ConsoleCommand{
    val subcommands: HashMap<String, ConsoleCommand>

    override fun handle(console: DevConsolePopup, params: List<String>): String? {
        if (params.isEmpty()) return "Available commands: " + subcommands.keys.joinToString()
        val handler = subcommands[params[0]] ?: return "Invalid command. Available commands: " + subcommands.keys.joinToString()
        return handler.handle(console, params.drop(1))
    }

    override fun autocomplete(params: List<String>): String? {
        if (params.isEmpty()) return null
        val firstParam = params[0]
        if (firstParam in subcommands) return subcommands[firstParam]!!.autocomplete(params.drop(1))
        val possibleSubcommands = subcommands.keys.filter { it.startsWith(firstParam) }
        if (possibleSubcommands.isEmpty()) return null
        if (possibleSubcommands.size == 1) return possibleSubcommands.first().removePrefix(firstParam)

        val firstSubcommand = possibleSubcommands.first()
        for ((index, char) in firstSubcommand.withIndex()){
            if (possibleSubcommands.any { it.lastIndex < index } ||
                possibleSubcommands.any { it[index] != char })
                return firstSubcommand.substring(0,index).removePrefix(firstParam)
        }
        return firstSubcommand.removePrefix(firstParam)
    }
}

class ConsoleCommandRoot:ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(
        "unit" to ConsoleUnitCommands(),
        "city" to ConsoleCityCommands(),
        "tile" to ConsoleTileCommands()
    )
}

class ConsoleUnitCommands:ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(

        "add" to ConsoleAction { console, params ->
            if (params.size != 2)
                return@ConsoleAction "Format: unit add <civName> <unitName>"
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction "No tile selected"
            val civ = console.getCivByName(params[0])
                ?: return@ConsoleAction "Unknown civ"
            val baseUnit = console.gameInfo.ruleset.units.values.firstOrNull { it.name.toCliInput() == params[3] }
                ?: return@ConsoleAction "Unknown unit"
            civ.units.placeUnitNearTile(selectedTile.position, baseUnit)
            return@ConsoleAction null
        },

        "remove" to ConsoleAction { console, params ->
            val unit = console.getSelectedUnit()
                ?: return@ConsoleAction "Select tile with unit"
            unit.destroy()
            return@ConsoleAction null
        },

        "addpromotion" to ConsoleAction { console, params ->
            if (params.size != 1)
                return@ConsoleAction "Format: unit addpromotion <promotionName>"
            val unit = console.getSelectedUnit()
                ?: return@ConsoleAction "Select tile with unit"
            val promotion = console.gameInfo.ruleset.unitPromotions.values.firstOrNull { it.name.toCliInput() == params[2] }
                ?: return@ConsoleAction "Unknown promotion"
            unit.promotions.addPromotion(promotion.name, true)
            return@ConsoleAction null
        },

        "removepromotion" to ConsoleAction { console, params ->
            if (params.size != 1)
                return@ConsoleAction "Format: unit removepromotion <promotionName>"
            val unit = console.getSelectedUnit()
                ?: return@ConsoleAction "Select tile with unit"
            val promotion = unit.promotions.getPromotions().firstOrNull { it.name.toCliInput() == params[2] }
                ?: return@ConsoleAction "Promotion not found on unit"
            // No such action in-game so we need to manually update
            unit.promotions.promotions.remove(promotion.name)
            unit.updateUniques()
            unit.updateVisibleTiles()
            return@ConsoleAction null
        }
    )
}

class ConsoleCityCommands:ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(

        "add" to ConsoleAction { console, params ->
            if (params.size != 1) return@ConsoleAction  "Format: city add <civName>"
            val civ = console.getCivByName(params[0]) ?: return@ConsoleAction "Unknown civ"
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction "No tile selected"
            if (selectedTile.isCityCenter()) return@ConsoleAction "Tile already contains a city center"
            civ.addCity(selectedTile.position)
            return@ConsoleAction null
        },

        "remove" to ConsoleAction { console, params ->
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction "No tile selected"
            val city = selectedTile.getCity() ?: return@ConsoleAction "No city in selected tile"
            city.destroyCity(overrideSafeties = true)
            return@ConsoleAction null
        },

        "setpop" to ConsoleAction { console, params ->
            if (params.size != 2) return@ConsoleAction "Format: city setpop <cityName> <amount>"
            val newPop = params[1].toIntOrNull() ?: return@ConsoleAction "Invalid amount " + params[1]
            if (newPop < 1) return@ConsoleAction "Invalid amount $newPop"
            val city = console.gameInfo.getCities().firstOrNull { it.name.toCliInput() == params[0] }
                ?: return@ConsoleAction "Unknown city"
            city.population.setPopulation(newPop)
            return@ConsoleAction null
        },

        "addtile" to ConsoleAction { console, params ->
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction "No tile selected"
            val city = console.gameInfo.getCities().firstOrNull { it.name.toCliInput() == params[0] }
                ?: return@ConsoleAction "Unknown city"
            if (selectedTile.neighbors.none { it.getCity() == city })
                return@ConsoleAction "Tile is not adjacent to city"
            city.expansion.takeOwnership(selectedTile)
            return@ConsoleAction null
        },

        "removetile" to ConsoleAction { console, params ->
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction "No tile selected"
            val city = selectedTile.getCity() ?: return@ConsoleAction "No city for selected tile"
            city.expansion.relinquishOwnership(selectedTile)
            return@ConsoleAction null
        })
}

class ConsoleTileCommands: ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(

        "setimprovement" to ConsoleAction { console, params ->
            if (params.size != 1 && params.size != 2) return@ConsoleAction "Format: tile setimprovement <improvementName> [<civName>]"
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction "No tile selected"
            val improvement = console.gameInfo.ruleset.tileImprovements.values.firstOrNull {
                it.name.toCliInput() == params[0]
            } ?: return@ConsoleAction "Unknown improvement"
            var civ:Civilization? = null
            if (params.size == 2){
                civ = console.getCivByName(params[1]) ?: return@ConsoleAction "Unknown civ"
            }
            selectedTile.improvementFunctions.changeImprovement(improvement.name, civ)
            return@ConsoleAction null
        },

        "removeimprovement" to ConsoleAction { console, params ->
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction "No tile selected"
            selectedTile.improvementFunctions.changeImprovement(null)
            return@ConsoleAction null
        }
    )
}
