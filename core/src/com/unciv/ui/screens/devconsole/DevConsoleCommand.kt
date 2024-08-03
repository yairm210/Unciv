package com.unciv.ui.screens.devconsole

import com.unciv.ui.screens.devconsole.CliInput.Companion.getAutocompleteString
import com.unciv.ui.screens.devconsole.CliInput.Companion.orEmpty

internal interface ConsoleCommand {
    fun handle(console: DevConsolePopup, params: List<CliInput>): DevConsoleResponse

    /** Returns the string to replace the last parameter of the existing command with. `null` means no change due to no options.
     *  The function should add a space at the end if and only if the "match" is an unambiguous choice!
     */
    fun autocomplete(console: DevConsolePopup, params: List<CliInput>): String? = null
}

/** An Exception representing a minor user error in [DevConsolePopup] input. [hint] is user-readable but never translated and should help understanding how to fix the mistake. */
internal class ConsoleHintException(val hint: String) : Exception()

/** An Exception representing a user error in [DevConsolePopup] input. [error] is user-readable but never translated. */
internal class ConsoleErrorException(val error: String) : Exception()

internal open class ConsoleAction(
    val format: String,
    val action: (console: DevConsolePopup, params: List<CliInput>) -> DevConsoleResponse
) : ConsoleCommand {
    override fun handle(console: DevConsolePopup, params: List<CliInput>): DevConsoleResponse {
        return try {
            validateFormat(format, params)
            action(console, params)
        } catch (hintException: ConsoleHintException) {
            DevConsoleResponse.hint(hintException.hint)
        } catch (errorException: ConsoleErrorException) {
            DevConsoleResponse.error(errorException.error)
        }
    }

    override fun autocomplete(console: DevConsolePopup, params: List<CliInput>): String? {
        val formatParams = format.split(' ').drop(2).map {
            it.removeSurrounding("<",">").removeSurrounding("[","]").removeSurrounding("\"")
        }
        if (formatParams.none()) return null // nothing to autocomplete - for example "history " + tab
        if (formatParams.size < params.size) return null // format has no definition, so there are no options to choose from
        // It is possible we're here *with* another format parameter but an *empty* params (e.g. `tile addriver` and hit tab) -> see below
        val (formatParam, lastParam) = if (params.lastIndex in formatParams.indices)
                formatParams[params.lastIndex] to params.last()
            else formatParams.first() to null

        val options = ConsoleParameterType.multiOptions(formatParam, console)
        val result = getAutocompleteString(lastParam.orEmpty(), options, console)
        if (lastParam != null || result == null) return result
        // we got the situation described above and something to add: The caller will ultimately replace the second subcommand, so add it back
        // border case, only happens right after the second token, not after the third: Don't optimize the double split call
        return format.split(' ')[1] + " " + result
    }

    private fun validateFormat(format: String, params: List<CliInput>) {
        val allParams = format.split(' ')
        val requiredParamsAmount = allParams.count { it.startsWith('<') }
        val optionalParamsAmount = if (format.endsWith("]...")) 999999 else allParams.count { it.startsWith('[') }
        // For this check, ignore an empty token caused by a trailing blank
        val paramsSize = if (params.isEmpty()) 0 else if (params.last().isEmpty()) params.size - 1 else params.size
        if (paramsSize < requiredParamsAmount || paramsSize > requiredParamsAmount + optionalParamsAmount)
            throw ConsoleHintException("Format: $format")
    }
}

internal interface ConsoleCommandNode : ConsoleCommand {
    val subcommands: HashMap<String, ConsoleCommand>

    override fun handle(console: DevConsolePopup, params: List<CliInput>): DevConsoleResponse {
        if (params.isEmpty())
            return DevConsoleResponse.hint("Available commands: " + subcommands.keys.joinToString())
        val handler = subcommands[params[0].toString()]
            ?: return DevConsoleResponse.error("Invalid command.\nAvailable commands:" + subcommands.keys.joinToString("") { "\n- $it" })
        return handler.handle(console, params.drop(1))
    }

    override fun autocomplete(console: DevConsolePopup, params: List<CliInput>): String? {
        val firstParam = params.firstOrNull().orEmpty()
        val handler = subcommands[firstParam.toString()]
            ?: return getAutocompleteString(firstParam, subcommands.keys, console)
        return handler.autocomplete(console, params.drop(1))
    }
}

internal class ConsoleCommandRoot : ConsoleCommandNode {
    override val subcommands = hashMapOf<String, ConsoleCommand>(
        "unit" to ConsoleUnitCommands(),
        "city" to ConsoleCityCommands(),
        "tile" to ConsoleTileCommands(),
        "civ" to ConsoleCivCommands(),
        "history" to ConsoleAction("history") { console, _ ->
            console.showHistory()
            DevConsoleResponse.hint("") // Trick console into staying open
        },
        "game" to ConsoleGameCommands()
    )
}
