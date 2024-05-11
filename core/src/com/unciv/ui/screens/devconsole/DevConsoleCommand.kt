package com.unciv.ui.screens.devconsole

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.map.mapgenerator.RiverGenerator
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

internal inline fun <reified T: Enum<T>> findCliInput(param: String): T? {
    val paramCli = param.toCliInput()
    return enumValues<T>().firstOrNull {
        it.name.toCliInput() == paramCli
    }
}

@Suppress("USELESS_CAST")  // not useless, filterIsInstance annotates `T` with `@NoInfer`
internal inline fun <reified T: IRulesetObject> DevConsolePopup.findCliInput(param: String) =
    (gameInfo.ruleset.allRulesetObjects().filterIsInstance<T>() as Sequence<T>).findCliInput(param)

/** Returns the string to *add* to the existing command */
internal fun getAutocompleteString(lastWord: String, allOptions: Iterable<String>, console: DevConsolePopup): String {
    console.showResponse(null, Color.WHITE)

    val matchingOptions = allOptions.map { it.toCliInput() }.filter { it.startsWith(lastWord.toCliInput()) }
    if (matchingOptions.isEmpty()) return ""
    if (matchingOptions.size == 1) return matchingOptions.first().drop(lastWord.length) + " "

    console.showResponse("Matching completions: " + matchingOptions.joinToString(), Color.LIME.lerp(Color.OLIVE.cpy(), 0.5f))

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
     */
    fun autocomplete(console: DevConsolePopup, params: List<String>): String = ""
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

    override fun autocomplete(console: DevConsolePopup, params: List<String>): String {
        val formatParams = format.split(" ").drop(2).map {
            it.removeSurrounding("<",">").removeSurrounding("[","]").removeSurrounding("\"")
        }
        if (formatParams.size < params.size) return ""
        // It is possible we're here *with* another format parameter but an *empty* params (e.g. `tile addriver ` and hit tab) -> see else branch
        val (formatParam, lastParam) = if (params.lastIndex in formatParams.indices)
                formatParams[params.lastIndex] to params.last()
            else formatParams.first() to ""

        val options = ConsoleParameterType.getOptions(formatParam, console)
        return getAutocompleteString(lastParam, options, console)
    }

    private fun validateFormat(format: String, params: List<String>) {
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

    override fun autocomplete(console: DevConsolePopup, params: List<String>): String {
        val firstParam = params.firstOrNull().orEmpty()
        if (firstParam in subcommands) return subcommands[firstParam]!!.autocomplete(console, params.drop(1))
        return getAutocompleteString(firstParam, subcommands.keys, console)
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
