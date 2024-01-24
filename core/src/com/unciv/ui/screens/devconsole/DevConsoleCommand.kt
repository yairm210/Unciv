package com.unciv.ui.screens.devconsole

import com.unciv.models.ruleset.IRulesetObject
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.models.stats.Stat

internal fun String.toCliInput() = this.lowercase().replace(" ","-")

internal fun Iterable<String>.findCliInput(param: String): String? {
    val paramCli = param.toCliInput()
    return firstOrNull { it.toCliInput() == paramCli }
}
internal fun <T: IRulesetObject> Iterable<T>.findCliInput(param: String): T? {
    val paramCli = param.toCliInput()
    return firstOrNull { it.name.toCliInput() == paramCli }
}
internal fun <T: IRulesetObject> Sequence<T>.findCliInput(param: String) = asIterable().findCliInput(param)

/** Returns the string to *add* to the existing command */
internal fun getAutocompleteString(lastWord: String, allOptions: Iterable<String>):String? {
    val matchingOptions = allOptions.map { it.toCliInput() }.filter { it.startsWith(lastWord.toCliInput()) }
    if (matchingOptions.isEmpty()) return null
    if (matchingOptions.size == 1) return matchingOptions.first().drop(lastWord.length) + " "

    val firstOption = matchingOptions.first()
    for ((index, char) in firstOption.withIndex()) {
        if (matchingOptions.any { it.lastIndex < index } ||
            matchingOptions.any { it[index] != char })
            return firstOption.substring(0, index).drop(lastWord.length)
    }
    return firstOption.drop(lastWord.length)  // don't add space, e.g. found drill-i and user might want drill-ii
}

interface ConsoleCommand {
    fun handle(console: DevConsolePopup, params: List<String>): DevConsoleResponse
    /** Returns the string to *add* to the existing command.
     *  The function should add a space at the end if and only if the "match" is an unambiguous choice!
     *  Returning `null` means there was no match at all, while returning an empty string means the last word is a match as-is.
     */
    fun autocomplete(console: DevConsolePopup, params: List<String>): String? = ""
}

class ConsoleHintException(val hint: String) : Exception()
class ConsoleErrorException(val error: String) : Exception()

open class ConsoleAction(val format: String, val action: (console: DevConsolePopup, params: List<String>) -> DevConsoleResponse) : ConsoleCommand {
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
            "terrainName" -> console.gameInfo.ruleset.terrains.values.filter { it.type.isBaseTerrain }.map { it.name }
            "resourceName" -> console.gameInfo.ruleset.tileResources.keys
            "stat" -> Stat.names()
            "religionName" -> console.gameInfo.religions.keys
            else -> listOf()
        }
        return getAutocompleteString(lastParam, options)
    }

    private fun validateFormat(format: String, params:List<String>){
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
