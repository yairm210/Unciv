package com.unciv.ui.screens.devconsole

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.stats.Stat

internal fun String.toCliInput() = this.lowercase().replace(" ","-")

/** Returns the string to *add* to the existing command */
fun getAutocompleteString(lastWord: String, allOptions: Collection<String>):String? {
    val matchingOptions = allOptions.filter { it.toCliInput().startsWith(lastWord.toCliInput()) }
    if (matchingOptions.isEmpty()) return null
    if (matchingOptions.size == 1) return matchingOptions.first().drop(lastWord.length)

    val firstOption = matchingOptions.first()
    for ((index, char) in firstOption.withIndex()) {
        if (matchingOptions.any { it.lastIndex < index } ||
            matchingOptions.any { it[index] != char })
            return firstOption.substring(0, index).drop(lastWord.length)
    }
    return firstOption.drop(lastWord.length)
}

interface ConsoleCommand {
    fun handle(console: DevConsolePopup, params: List<String>): DevConsoleResponse
    /** Returns the string to *add* to the existing command */
    fun autocomplete(console: DevConsolePopup, params: List<String>): String? = ""
}

class ConsoleHintException(val hint:String):Exception()
class ConsoleErrorException(val error:String):Exception()

class ConsoleAction(val format: String, val action: (console: DevConsolePopup, params: List<String>) -> DevConsoleResponse) : ConsoleCommand {
    override fun handle(console: DevConsolePopup, params: List<String>): DevConsoleResponse {
        return try {
            validateFormat(format, params)
            action(console, params)
        } catch (hintException: ConsoleHintException) {
            DevConsoleResponse.hint(hintException.hint)
        } catch (errorException: ConsoleErrorException) {
            DevConsoleResponse.error(errorException.error)
        }
    }

    override fun autocomplete(console: DevConsolePopup, params: List<String>): String? {
        if (params.isEmpty()) return null

        val formatParams = format.split(" ").drop(2).map {
            it.removeSurrounding("<",">").removeSurrounding("[","]").removeSurrounding("\"")
        }
        if (formatParams.size < params.size) return null
        val formatParam = formatParams[params.lastIndex]

        val lastParam = params.last()
        val options = when (formatParam) {
            "civName" -> console.gameInfo.civilizations.map { it.civName }
            "unitName" -> console.gameInfo.ruleset.units.keys
            "promotionName" -> console.gameInfo.ruleset.unitPromotions.keys
            "improvementName" -> console.gameInfo.ruleset.tileImprovements.keys
            "featureName" -> console.gameInfo.ruleset.terrains.values.filter { it.type == TerrainType.TerrainFeature }.map { it.name }
            "stat" -> Stat.names()
            else -> listOf()
        }
        return getAutocompleteString(lastParam, options)
    }

    fun validateFormat(format: String, params:List<String>){
        val allParams = format.split(" ")
        val requiredParamsAmount = allParams.count { it.startsWith('<') }
        val optionalParamsAmount = allParams.count { it.startsWith('[') }
        if (params.size < requiredParamsAmount || params.size > requiredParamsAmount + optionalParamsAmount)
            throw ConsoleHintException("Format: $format")
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

    override fun autocomplete(console: DevConsolePopup, params: List<String>): String? {
        if (params.isEmpty()) return null
        val firstParam = params[0]
        if (firstParam in subcommands) return subcommands[firstParam]!!.autocomplete(console, params.drop(1))
        return getAutocompleteString(firstParam, subcommands.keys)
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

        "add" to ConsoleAction("unit add <civName> <unitName>") { console, params ->
            val selectedTile = console.getSelectedTile()
            val civ = console.getCivByName(params[0])
            val baseUnit = console.gameInfo.ruleset.units.values.firstOrNull { it.name.toCliInput() == params[1] }
                ?: throw ConsoleErrorException("Unknown unit")
            civ.units.placeUnitNearTile(selectedTile.position, baseUnit)
            DevConsoleResponse.OK
        },

        "remove" to ConsoleAction("unit remove") { console, params ->
            val unit = console.getSelectedUnit()
            unit.destroy()
            DevConsoleResponse.OK
        },

        "addpromotion" to ConsoleAction("unit addpromotion <promotionName>") { console, params ->
            val unit = console.getSelectedUnit()
            val promotion = console.gameInfo.ruleset.unitPromotions.values.firstOrNull { it.name.toCliInput() == params[0] }
                ?: throw ConsoleErrorException("Unknown promotion")
            unit.promotions.addPromotion(promotion.name, true)
            DevConsoleResponse.OK
        },

        "removepromotion" to ConsoleAction("unit removepromotion <promotionName>") { console, params ->
            val unit = console.getSelectedUnit()
            val promotion = unit.promotions.getPromotions().firstOrNull { it.name.toCliInput() == params[0] }
                ?: throw ConsoleErrorException("Promotion not found on unit")
            // No such action in-game so we need to manually update
            unit.promotions.promotions.remove(promotion.name)
            unit.updateUniques()
            unit.updateVisibleTiles()
            DevConsoleResponse.OK
        },

        "setmovement" to ConsoleAction("unit setmovement <amount>") { console, params ->
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

        "add" to ConsoleAction("city add <civName>") { console, params ->
            val civ = console.getCivByName(params[0])
            val selectedTile = console.getSelectedTile()
            if (selectedTile.isCityCenter())
                throw ConsoleErrorException("Tile already contains a city center")
            civ.addCity(selectedTile.position)
            DevConsoleResponse.OK
        },

        "remove" to ConsoleAction("city remove") { console, params ->
            val city = console.getSelectedCity()
            city.destroyCity(overrideSafeties = true)
            DevConsoleResponse.OK
        },

        "setpop" to ConsoleAction("city setpop <amount>") { console, params ->
            val city = console.getSelectedCity()
            val newPop = console.getInt(params[0])
            if (newPop < 1) throw ConsoleErrorException("Population must be at least 1")
            city.population.setPopulation(newPop)
            DevConsoleResponse.OK
        },

        "addtile" to ConsoleAction("city addtile <cityName>") { console, params ->
            val selectedTile = console.getSelectedTile()
            val city = console.getCity(params[0])
            if (selectedTile.neighbors.none { it.getCity() == city })
                throw ConsoleErrorException("Tile is not adjacent to any tile already owned by the city")
            if (selectedTile.isCityCenter()) throw ConsoleErrorException("Cannot tranfer city center")
            city.expansion.takeOwnership(selectedTile)
            DevConsoleResponse.OK
        },

        "removetile" to ConsoleAction("city removetile") { console, params ->
            val selectedTile = console.getSelectedTile()
            val city = console.getSelectedCity()
            city.expansion.relinquishOwnership(selectedTile)
            DevConsoleResponse.OK
        },

        "religion" to ConsoleAction("city religion <name> <±pressure>") { console, params ->
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

        "setimprovement" to ConsoleAction("tile setimprovement <improvementName> [civName]") { console, params ->
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

        "removeimprovement" to ConsoleAction("tile removeimprovement") { console, params ->
            val selectedTile = console.getSelectedTile()
            selectedTile.improvementFunctions.changeImprovement(null)
            DevConsoleResponse.OK
        },

        "addfeature" to ConsoleAction("tile addfeature <featureName>") { console, params ->
            val selectedTile = console.getSelectedTile()
            val feature = console.gameInfo.ruleset.terrains.values
                .firstOrNull { it.type == TerrainType.TerrainFeature && it.name.toCliInput() == params[0] }
                ?: throw ConsoleErrorException("Unknown feature")
            selectedTile.addTerrainFeature(feature.name)
            DevConsoleResponse.OK
        },

        "removefeature" to ConsoleAction("tile addfeature <featureName>") { console, params ->
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
        "addstat" to ConsoleAction("civ addstat <stat> <amount> [civ]") { console, params ->
            val stat = Stat.safeValueOf(params[0].replaceFirstChar(Char::titlecase))
                ?: throw ConsoleErrorException("\"${params[0]}\" is not an acceptable Stat")
            if (stat !in Stat.statsWithCivWideField)
                throw ConsoleErrorException("$stat is not civ-wide")

            val amount = console.getInt(params[1])

            val civ = if (params.size == 2) console.screen.selectedCiv
            else console.getCivByName(params[2])

            civ.addStat(stat, amount)
            DevConsoleResponse.OK
        },

        "setplayertype" to ConsoleAction("civ setplayertype <civName> <ai/human>") { console, params ->
            val civ = console.getCivByName(params[0])
            val playerType = PlayerType.values().firstOrNull { it.name.lowercase() == params[1].lowercase() }
                ?: throw ConsoleErrorException("Invalid player type, valid options are 'ai' or 'human'")
            civ.playerType = playerType
            DevConsoleResponse.OK
        },

        "revealmap" to ConsoleAction("civ revealmap <civName>") { console, params ->
            val civ = console.getCivByName(params[0])
            civ.gameInfo.tileMap.values.asSequence()
                .forEach { it.setExplored(civ, true) }
            DevConsoleResponse.OK
        },

        "activatetrigger" to ConsoleAction("civ activatetrigger <civName> <\"trigger\">") { console, params ->
            val civ = console.getCivByName(params[0])
            val unique = Unique(params[1])
            if (unique.type == null) throw ConsoleErrorException("Unrecognized trigger")
            val tile = console.screen.mapHolder.selectedTile
            val city = tile?.getCity()
            UniqueTriggerActivation.triggerCivwideUnique(unique, civ, city, tile)
            DevConsoleResponse.OK
        }
    )
}
