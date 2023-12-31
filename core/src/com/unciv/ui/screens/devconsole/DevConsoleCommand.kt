package com.unciv.ui.screens.devconsole

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.stats.Stat

internal fun String.toCliInput() = this.lowercase().replace(" ","-")

interface ConsoleCommand {
    fun handle(console: DevConsolePopup, params: List<String>): DevConsoleResponse
    fun autocomplete(params: List<String>): String? = ""
}

class ConsoleHintException(val hint:String):Exception()
class ConsoleErrorException(val error:String):Exception()

class ConsoleAction(val action: (console: DevConsolePopup, params: List<String>) -> DevConsoleResponse) : ConsoleCommand {
    override fun handle(console: DevConsolePopup, params: List<String>): DevConsoleResponse {
        return try {
            action(console, params)
        } catch (hintException: ConsoleHintException) {
            DevConsoleResponse.hint(hintException.hint)
        } catch (errorException: ConsoleErrorException) {
            DevConsoleResponse.error(errorException.error)
        }
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


fun validateFormat(format: String, params:List<String>){
    val allParams = format.split(" ")
    val requiredParamsAmount = allParams.count { it.startsWith('<') }
    val optionalParamsAmount = allParams.count { it.startsWith('[') }
    if (params.size < requiredParamsAmount || params.size > requiredParamsAmount + optionalParamsAmount)
        throw ConsoleHintException("Format: $format")
}

class ConsoleUnitCommands : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(

        "add" to ConsoleAction { console, params ->
            validateFormat("unit add <civName> <unitName>", params)
            val selectedTile = console.getSelectedTile()
            val civ = console.getCivByName(params[0])
            val baseUnit = console.gameInfo.ruleset.units.values.firstOrNull { it.name.toCliInput() == params[1] }
                ?: throw ConsoleErrorException("Unknown unit")
            civ.units.placeUnitNearTile(selectedTile.position, baseUnit)
            DevConsoleResponse.OK
        },

        "remove" to ConsoleAction { console, params ->
            validateFormat("unit remove", params)
            val unit = console.getSelectedUnit()
            unit.destroy()
            DevConsoleResponse.OK
        },

        "addpromotion" to ConsoleAction { console, params ->
            validateFormat("unit addpromotion <promotionName>", params)
            val unit = console.getSelectedUnit()
            val promotion = console.gameInfo.ruleset.unitPromotions.values.firstOrNull { it.name.toCliInput() == params[0] }
                ?: throw ConsoleErrorException("Unknown promotion")
            unit.promotions.addPromotion(promotion.name, true)
            DevConsoleResponse.OK
        },

        "removepromotion" to ConsoleAction { console, params ->
            validateFormat("unit removepromotion <promotionName>", params)
            val unit = console.getSelectedUnit()
            val promotion = unit.promotions.getPromotions().firstOrNull { it.name.toCliInput() == params[0] }
                ?: throw ConsoleErrorException("Promotion not found on unit")
            // No such action in-game so we need to manually update
            unit.promotions.promotions.remove(promotion.name)
            unit.updateUniques()
            unit.updateVisibleTiles()
            DevConsoleResponse.OK
        },

        "setmovement" to ConsoleAction { console, params ->
            validateFormat("unit setmovement <amount>", params)
            val movement = params[0].toFloatOrNull()
            if (movement == null || movement < 0) throw ConsoleErrorException("Invalid number")
            val unit = console.getSelectedUnit()
            unit.currentMovement = movement
            DevConsoleResponse.OK
        }
    )
}

class ConsoleCityCommands : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(

        "add" to ConsoleAction { console, params ->
            validateFormat("city add <civName>", params)
            val civ = console.getCivByName(params[0])
            val selectedTile = console.getSelectedTile()
            if (selectedTile.isCityCenter())
                throw ConsoleErrorException("Tile already contains a city center")
            civ.addCity(selectedTile.position)
            DevConsoleResponse.OK
        },

        "remove" to ConsoleAction { console, params ->
            validateFormat("city remove", params)
            val city = console.getSelectedCity()
            city.destroyCity(overrideSafeties = true)
            DevConsoleResponse.OK
        },

        "setpop" to ConsoleAction { console, params ->
            validateFormat("city setpop <amount>", params)
            val city = console.getSelectedCity()
            val newPop = console.getInt(params[0])
            if (newPop < 1) throw ConsoleErrorException("Population must be at least 1")
            city.population.setPopulation(newPop)
            DevConsoleResponse.OK
        },

        "addtile" to ConsoleAction { console, params ->
            validateFormat("city addtile <cityName>", params)
            val selectedTile = console.getSelectedTile()
            val city = console.getCity(params[0])
            if (selectedTile.neighbors.none { it.getCity() == city })
                throw ConsoleErrorException("Tile is not adjacent to any tile already owned by the city")
            if (selectedTile.isCityCenter()) throw ConsoleErrorException("Cannot tranfer city center")
            city.expansion.takeOwnership(selectedTile)
            DevConsoleResponse.OK
        },

        "removetile" to ConsoleAction { console, params ->
            validateFormat("city removetile", params)
            val selectedTile = console.getSelectedTile()
            val city = console.getSelectedCity()
            city.expansion.relinquishOwnership(selectedTile)
            DevConsoleResponse.OK
        },

        "religion" to ConsoleAction { console, params ->
            validateFormat("city religion <name> <±pressure>", params)
            val city = console.getSelectedCity()
            val religion = city.civ.gameInfo.religions.keys.firstOrNull { it.toCliInput() == params[0] }
                ?: throw ConsoleErrorException("'${params[0]}' is not a known religion")
            val pressure = console.getInt(params[1])
            city.religion.addPressure(religion, pressure.coerceAtLeast(-city.religion.getPressures()[religion]))
            city.religion.updatePressureOnPopulationChange(0)
            DevConsoleResponse.OK
        },
    )
}

class ConsoleTileCommands: ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(

        "setimprovement" to ConsoleAction { console, params ->
            validateFormat("tile setimprovement <improvementName> [civName]", params)
            val selectedTile = console.getSelectedTile()
            val improvement = console.gameInfo.ruleset.tileImprovements.values.firstOrNull {
                it.name.toCliInput() == params[0]
            } ?: throw ConsoleErrorException("Unknown improvement")
            var civ: Civilization? = null
            if (params.size == 2){
                civ = console.getCivByName(params[1])
            }
            selectedTile.improvementFunctions.changeImprovement(improvement.name, civ)
            DevConsoleResponse.OK
        },

        "removeimprovement" to ConsoleAction { console, params ->
            validateFormat("tile removeimprovement", params)
            val selectedTile = console.getSelectedTile()
            selectedTile.improvementFunctions.changeImprovement(null)
            DevConsoleResponse.OK
        },

        "addfeature" to ConsoleAction { console, params ->
            validateFormat("tile addfeature <featureName>", params)
            val selectedTile = console.getSelectedTile()
            val feature = console.gameInfo.ruleset.terrains.values
                .firstOrNull { it.type == TerrainType.TerrainFeature && it.name.toCliInput() == params[0] }
                ?: throw ConsoleErrorException("Unknown feature")
            selectedTile.addTerrainFeature(feature.name)
            DevConsoleResponse.OK
        },

        "removefeature" to ConsoleAction { console, params ->
            validateFormat("tile addfeature <featureName>", params)
            val selectedTile = console.getSelectedTile()
            val feature = console.gameInfo.ruleset.terrains.values
                .firstOrNull { it.type == TerrainType.TerrainFeature && it.name.toCliInput() == params[0] }
                ?: throw ConsoleErrorException("Unknown feature")
            selectedTile.removeTerrainFeature(feature.name)
            DevConsoleResponse.OK
        }
    )
}

class ConsoleCivCommands : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(
        "addstat" to ConsoleAction { console, params ->
            var statPos = 0
            validateFormat("civ addstat [civ] <stat> <amount>", params)
            val civ = if (params.size == 2) console.screen.selectedCiv
                else {
                    statPos++
                    console.getCivByName(params[0])
                }
            val amount = console.getInt(params[statPos+1])
            val stat = Stat.safeValueOf(params[statPos].replaceFirstChar(Char::titlecase))
                ?: throw ConsoleErrorException("Whut? \"${params[statPos]}\" is not a Stat!")
            if (stat !in Stat.statsWithCivWideField)
                throw ConsoleErrorException("$stat is not civ-wide")
            civ.addStat(stat, amount)
            DevConsoleResponse.OK
        },

        "setplayertype" to ConsoleAction { console, params ->
            validateFormat("civ setplayertype <civName> <ai/human>", params)
            val civ = console.getCivByName(params[0])
            val playerType = PlayerType.values().firstOrNull { it.name.lowercase() == params[1].lowercase() }
                ?: throw ConsoleErrorException("Invalid player type, valid options are 'ai' or 'human'")
            civ.playerType = playerType
            DevConsoleResponse.OK
        },

        "revealmap" to ConsoleAction { console, params ->
            validateFormat("civ revealmap <civName>", params)
            val civ = console.getCivByName(params[0])
            civ.gameInfo.tileMap.values.asSequence()
                .forEach { it.setExplored(civ, true) }
            DevConsoleResponse.OK
        },

        "activatetrigger" to ConsoleAction { console, params ->
            validateFormat("civ activatetrigger <civName> <\"trigger\">", params)
            val civ = console.getCivByName(params[0])
            val unique = Unique(params[1])
            if (unique.type == null) throw ConsoleErrorException("Unrecognized trigger")
            val tile = console.screen.mapHolder.selectedTile
            val city = tile?.getCity()
            UniqueTriggerActivation.triggerCivwideUnique(unique, civ, city, tile)
            DevConsoleResponse.OK
        }
    )

    override fun autocomplete(params: List<String>): String? {
        if (params.isNotEmpty())
            when (params[0]){
                "addstat" -> if (params.size == 2)
                    return Stat.names()
                        .firstOrNull { it.lowercase().startsWith(params[1]) }
                        ?.drop(params[1].length)
            }
        return super.autocomplete(params)
    }
}
