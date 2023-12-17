package com.unciv.ui.screens.devconsole

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.stats.Stat

internal fun String.toCliInput() = this.lowercase().replace(" ","-")

interface ConsoleCommand {
    fun handle(console: DevConsolePopup, params: List<String>): DevConsoleResponse
    fun autocomplete(params: List<String>): String? = ""
}

class ConsoleAction(val action: (console: DevConsolePopup, params: List<String>) -> DevConsoleResponse) : ConsoleCommand {
    override fun handle(console: DevConsolePopup, params: List<String>): DevConsoleResponse {
        return action(console, params)
    }
}

interface ConsoleCommandNode : ConsoleCommand {
    val subcommands: HashMap<String, ConsoleCommand>

    override fun handle(console: DevConsolePopup, params: List<String>): DevConsoleResponse {
        if (params.isEmpty())
            return DevConsoleResponse.hint("Available commands: " + subcommands.keys.joinToString())
        val handler = subcommands[params[0]]
            ?: return DevConsoleResponse.error("Invalid command.\nAvailable commands:" + subcommands.keys.joinToString("") { "\n- $it" })
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

class ConsoleCommandRoot : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(
        "unit" to ConsoleUnitCommands(),
        "city" to ConsoleCityCommands(),
        "tile" to ConsoleTileCommands(),
        "civ" to ConsoleCivCommands()
    )
}

class ConsoleUnitCommands : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(

        "add" to ConsoleAction { console, params ->
            if (params.size != 2)
                return@ConsoleAction DevConsoleResponse.hint("Format: unit add <civName> <unitName>")
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction DevConsoleResponse.error("No tile selected")
            val civ = console.getCivByName(params[0])
                ?: return@ConsoleAction DevConsoleResponse.error("Unknown civ")
            val baseUnit = console.gameInfo.ruleset.units.values.firstOrNull { it.name.toCliInput() == params[1] }
                ?: return@ConsoleAction DevConsoleResponse.error("Unknown unit")
            civ.units.placeUnitNearTile(selectedTile.position, baseUnit)
            return@ConsoleAction DevConsoleResponse.OK
        },

        "remove" to ConsoleAction { console, params ->
            if (params.isNotEmpty())
                return@ConsoleAction DevConsoleResponse.hint("Format: unit remove")
            val unit = console.getSelectedUnit()
                ?: return@ConsoleAction DevConsoleResponse.error("Select tile with unit")
            unit.destroy()
            return@ConsoleAction DevConsoleResponse.OK
        },

        "addpromotion" to ConsoleAction { console, params ->
            if (params.size != 1)
                return@ConsoleAction DevConsoleResponse.hint("Format: unit addpromotion <promotionName>")
            val unit = console.getSelectedUnit()
                ?: return@ConsoleAction DevConsoleResponse.error("Select tile with unit")
            val promotion = console.gameInfo.ruleset.unitPromotions.values.firstOrNull { it.name.toCliInput() == params[0] }
                ?: return@ConsoleAction DevConsoleResponse.error("Unknown promotion")
            unit.promotions.addPromotion(promotion.name, true)
            return@ConsoleAction DevConsoleResponse.OK
        },

        "removepromotion" to ConsoleAction { console, params ->
            if (params.size != 1)
                return@ConsoleAction DevConsoleResponse.hint("Format: unit removepromotion <promotionName>")
            val unit = console.getSelectedUnit()
                ?: return@ConsoleAction DevConsoleResponse.error("Select tile with unit")
            val promotion = unit.promotions.getPromotions().firstOrNull { it.name.toCliInput() == params[0] }
                ?: return@ConsoleAction DevConsoleResponse.error("Promotion not found on unit")
            // No such action in-game so we need to manually update
            unit.promotions.promotions.remove(promotion.name)
            unit.updateUniques()
            unit.updateVisibleTiles()
            return@ConsoleAction DevConsoleResponse.OK
        },

        "setmovement" to ConsoleAction { console, params ->
            if (params.size != 1)
                return@ConsoleAction DevConsoleResponse.hint("Format: unit setmovement <amount>")
            val movement = params[0].toFloatOrNull()
            if (movement == null || movement < 0) return@ConsoleAction DevConsoleResponse.error("Invalid number")
            val unit = console.getSelectedUnit()
                ?: return@ConsoleAction DevConsoleResponse.error("Select tile with unit")
            unit.currentMovement = movement
            return@ConsoleAction DevConsoleResponse.OK
        }
    )
}

class ConsoleCityCommands : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(

        "add" to ConsoleAction { console, params ->
            if (params.size != 1)
                return@ConsoleAction DevConsoleResponse.hint("Format: city add <civName>")
            val civ = console.getCivByName(params[0])
                ?: return@ConsoleAction DevConsoleResponse.error("Unknown civ")
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction DevConsoleResponse.error("No tile selected")
            if (selectedTile.isCityCenter())
                return@ConsoleAction DevConsoleResponse.error("Tile already contains a city center")
            civ.addCity(selectedTile.position)
            return@ConsoleAction DevConsoleResponse.OK
        },

        "remove" to ConsoleAction { console, params ->
            if (params.isNotEmpty())
                return@ConsoleAction DevConsoleResponse.hint("Format: city remove")
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction DevConsoleResponse.error("No tile selected")
            val city = selectedTile.getCity()
                ?: return@ConsoleAction DevConsoleResponse.error("No city in selected tile")
            city.destroyCity(overrideSafeties = true)
            return@ConsoleAction DevConsoleResponse.OK
        },

        "setpop" to ConsoleAction { console, params ->
            if (params.size != 2)
                return@ConsoleAction DevConsoleResponse.hint("Format: city setpop <cityName> <amount>")
            val newPop = params[1].toIntOrNull() ?: return@ConsoleAction DevConsoleResponse.error("Invalid amount " + params[1])
            if (newPop < 1) return@ConsoleAction DevConsoleResponse.error("Invalid amount $newPop")
            val city = console.gameInfo.getCities().firstOrNull { it.name.toCliInput() == params[0] }
                ?: return@ConsoleAction DevConsoleResponse.error("Unknown city")
            city.population.setPopulation(newPop)
            return@ConsoleAction DevConsoleResponse.OK
        },

        "addtile" to ConsoleAction { console, params ->
            if (params.size != 1)
                return@ConsoleAction DevConsoleResponse.hint("Format: city addtile <cityName>")
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction DevConsoleResponse.error("No tile selected")
            val city = console.gameInfo.getCities().firstOrNull { it.name.toCliInput() == params[0] }
                ?: return@ConsoleAction DevConsoleResponse.error("Unknown city")
            if (selectedTile.neighbors.none { it.getCity() == city })
                return@ConsoleAction DevConsoleResponse.error("Tile is not adjacent to any tile already owned by the city")
            city.expansion.takeOwnership(selectedTile)
            return@ConsoleAction DevConsoleResponse.OK
        },

        "removetile" to ConsoleAction { console, params ->
            if (params.isNotEmpty())
                return@ConsoleAction DevConsoleResponse.hint("Format: city removetile")
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction DevConsoleResponse.error("No tile selected")
            val city = selectedTile.getCity() ?: return@ConsoleAction DevConsoleResponse.error("No city for selected tile")
            city.expansion.relinquishOwnership(selectedTile)
            return@ConsoleAction DevConsoleResponse.OK
        },

        "religion" to ConsoleAction { console, params ->
            if (params.size != 2)
                return@ConsoleAction DevConsoleResponse.hint("Format: city religion <name> <±pressure>")
            val city = console.screen.bottomUnitTable.selectedCity
                ?: return@ConsoleAction DevConsoleResponse.hint("Select a city first")
            val religion = city.civ.gameInfo.religions.keys.firstOrNull { it.toCliInput() == params[0] }
                ?: return@ConsoleAction DevConsoleResponse.error("'${params[0]}' is not a known religion")
            val pressure = params[1].toIntOrNull()
                ?: return@ConsoleAction DevConsoleResponse.error("'${params[1]}' is not an integer")
            city.religion.addPressure(religion, pressure.coerceAtLeast(-city.religion.getPressures()[religion]))
            city.religion.updatePressureOnPopulationChange(0)
            return@ConsoleAction DevConsoleResponse.OK
        },
    )
}

class ConsoleTileCommands: ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(

        "setimprovement" to ConsoleAction { console, params ->
            if (params.size != 1 && params.size != 2)
                return@ConsoleAction DevConsoleResponse.hint("Format: tile setimprovement <improvementName> [<civName>]")
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction DevConsoleResponse.error("No tile selected")
            val improvement = console.gameInfo.ruleset.tileImprovements.values.firstOrNull {
                it.name.toCliInput() == params[0]
            } ?: return@ConsoleAction DevConsoleResponse.error("Unknown improvement")
            var civ:Civilization? = null
            if (params.size == 2){
                civ = console.getCivByName(params[1])
                    ?: return@ConsoleAction DevConsoleResponse.error("Unknown civ")
            }
            selectedTile.improvementFunctions.changeImprovement(improvement.name, civ)
            return@ConsoleAction DevConsoleResponse.OK
        },

        "removeimprovement" to ConsoleAction { console, params ->
            if (params.isNotEmpty())
                return@ConsoleAction DevConsoleResponse.hint("Format: tile removeimprovement")
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction DevConsoleResponse.error("No tile selected")
            selectedTile.improvementFunctions.changeImprovement(null)
            return@ConsoleAction DevConsoleResponse.OK
        },

        "addfeature" to ConsoleAction { console, params ->
            if (params.size != 1)
                return@ConsoleAction DevConsoleResponse.hint("Format: tile addfeature <featureName>")
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction DevConsoleResponse.error("No tile selected")
            val feature = console.gameInfo.ruleset.terrains.values
                .firstOrNull { it.type == TerrainType.TerrainFeature && it.name.toCliInput() == params[0] }
                ?: return@ConsoleAction DevConsoleResponse.error("Unknown feature")
            selectedTile.addTerrainFeature(feature.name)
            return@ConsoleAction DevConsoleResponse.OK
        },

        "removefeature" to ConsoleAction { console, params ->
            if (params.size != 1)
                return@ConsoleAction DevConsoleResponse.hint("Format: tile addfeature <featureName>")
            val selectedTile = console.screen.mapHolder.selectedTile
                ?: return@ConsoleAction DevConsoleResponse.error("No tile selected")
            val feature = console.gameInfo.ruleset.terrains.values
                .firstOrNull { it.type == TerrainType.TerrainFeature && it.name.toCliInput() == params[0] }
                ?: return@ConsoleAction DevConsoleResponse.error("Unknown feature")
            selectedTile.removeTerrainFeature(feature.name)
            return@ConsoleAction DevConsoleResponse.OK
        }
    )
}

class ConsoleCivCommands : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(
        "add" to ConsoleAction { console, params ->
            var statPos = 0
            if (params.size !in 2..3)
                return@ConsoleAction DevConsoleResponse.hint("Format: civ add [civ] <stat> <amount>")
            val civ = if (params.size == 2) console.screen.selectedCiv
                else {
                    statPos++
                    console.getCivByName(params[0])
                        ?: return@ConsoleAction DevConsoleResponse.error("Unknown civ")
                }
            val amount = params[statPos+1].toIntOrNull()
                ?: return@ConsoleAction DevConsoleResponse.error("Whut? \"${params[statPos+1]}\" is not a number!")
            val stat = Stat.safeValueOf(params[statPos].replaceFirstChar(Char::titlecase))
                ?: return@ConsoleAction DevConsoleResponse.error("Whut? \"${params[statPos]}\" is not a Stat!")
            if (stat !in Stat.statsWithCivWideField)
                return@ConsoleAction DevConsoleResponse.error("$stat is not civ-wide")
            civ.addStat(stat, amount)
            DevConsoleResponse.OK
        }
    )

    override fun autocomplete(params: List<String>): String? {
        if (params.size == 2 && params[0] == "add")
            return Stat.names()
                .firstOrNull { it.lowercase().startsWith(params[1]) }
                ?.drop(params[1].length)
        return super.autocomplete(params)
    }
}
