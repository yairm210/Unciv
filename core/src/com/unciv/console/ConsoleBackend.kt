package com.unciv.console

import kotlin.collections.ArrayList


data class AutocompleteResults(val isHelpText:Boolean, val matches:List<String>, val helpText:String)


open class ConsoleBackend(consoleScope:ConsoleScope) {

    open val displayname:String = "Dummy"

    open fun motd(): String {
        // Message to print on launch.
        return "\n\nWelcome to the Unciv CLI!\nYou are currently running the dummy backend, which will echo all commands but never do anything."
    }
    
    open fun getAutocomplete(command: String): AutocompleteResults {
        // Return either a `List` of autocomplete matches, or a
        return AutocompleteResults(false, listOf(command+"_autocomplete"), "")
    }
    
    open fun exec(command: String): String {
        // Execute code and return output.
        return command
    }
    
    open fun terminate(): Boolean {
        // Return `true` on successful termination, `false` otherwise.
        return true
    }
}


class HardcodedConsoleBackend(consoleScope:ConsoleScope): ConsoleBackend(consoleScope) {

    override val displayname:String = "Hardcoded"
    
    val commands:List<String> = listOf("help", "countcities", "locatebuildings", "listcities", "cheatson", "cheatsoff", "entergodmode", "exitgodmode")
    val commandshelp:Map<String, String> = mapOf(
        "help" to "help - Display all commands\nhelp <command> - Display information on a specific command.",
        "countcities" to "countcities - Print out a numerical count of all cities in the current empire.",
        "locatebuildings" to "locatebuildings <buildingname> - Print out a list of all cities that have a given building.\nlocatebuildings <resourcename> - Print out a list of all cities that are using a given resource.",
        "listcities" to "listcities - Print the names of all cities in the current empire.",
        "cheatson" to "cheatson - Enable commands that break game rules.",
        "cheatsoff" to "cheatsoff - Disable commands that break game rules.",
        "entergodmode" to "entergodmode - Massively boost all empire growth stats. (Requires cheats.)",
        "exitgodemode" to "exitgodmode - Clear any previous boosts on all empire growth stats. (Requires cheats.)"
    )
    
    var cheats:Boolean = false
    
    override fun motd(): String {
        return "\n\nWelcome to the hardcoded demo backend.\n\nPlease run \"help\" or press [TAB] to see a list of available commands.\nPress [TAB] at any time to see help for currently typed command.\n\nPlease note that the available commands are meant as a DEMO for the CLI."
    }
    
    fun getCommandHelpText(command: String): String {
        if (command in commandshelp) {
            return "\n${commandshelp[command]}"
        } else {
            return "\nNo help entry found for command '${command}'"
        }
    }
    
    override fun getAutocomplete(command: String): AutocompleteResults{
        if (' ' in command) {
            return AutocompleteResults(true, listOf(), getCommandHelpText(command.split(' ')[0]))
        } else {
            return AutocompleteResults(false, commands.filter({ c -> c.startsWith(command) }).map({ c -> c + " " }), "")
        }
    }
    
    override fun exec(command: String): String {
        var args = command.split(' ')
        var out:String
        if (args[0] == "help") {
            if (args.size > 1) {
                out = getCommandHelpText(args[1])
            } else {
                out = commands.joinToString(", ")
            }
        } else if (args[0] == "countcities") {
            out = "Not implemented."
        } else if (args[0] == "locatebuildings") {
            out = "Not implemented."
        } else if (args[0] == "listcities") {
            out = "Not implemented."
        } else if (args[0] == "cheatson") {
            cheats = true
            out = "Cheats enabled."
        } else if (args[0] == "cheatsoff") {
            cheats = false
            out = "Cheats disabled."
        } else if (args[0] == "entergodmode") {
            if (cheats) {
                out = "Not implemented."
            } else {
                out = "Cheats must be enabled to use this command!"
            }
        } else if (args[0] == "exitgodmode") {
            if (cheats) {
                out = "Not implemented."
            } else {
                out = "Cheats must be enabled to use this command!"
            }
        } else {
            out = "The command ${args[0]} is either not known or not implemented."
        }
        return "\n> ${command}\n${out}"
    }
}


class QjsConsoleBackend(consoleScope:ConsoleScope): ConsoleBackend(consoleScope) {
    override val displayname:String = "QuickJS"
    override fun motd(): String {
        return "\n\nWelcome to the QuickJS Unciv CLI, which doesn't currently run QuickJS but might one day!"
    }
}


class LuaConsoleBackend(consoleScope:ConsoleScope): ConsoleBackend(consoleScope) {
    override val displayname:String = "Lua"
    override fun motd(): String {
        return "\n\nWelcome to the Lua Unciv CLI, which doesn't currently run Lua but might one day!"
    }
}


class UpyConsoleBackend(consoleScope:ConsoleScope): ConsoleBackend(consoleScope) {
    override val displayname:String = "MicroPython"
    override fun motd(): String {
        return "\n\nWelcome to the MicroPython Unciv CLI, which doesn't currently run MicroPython but might one day!"
    }
}


enum class ConsoleBackendType(val displayname:String) {
    Dummy("Dummy"),
    Hardcoded("Hardcoded"),
    QuickJS("QuickJS"),
    Lua("Lua"),
    MicroPython("MicroPython")
}


fun GetNamedConsoleBackend(backendtype:ConsoleBackendType, consoleScope:ConsoleScope): ConsoleBackend {
    if (backendtype == ConsoleBackendType.Dummy) {
        return ConsoleBackend(consoleScope)
    } else if (backendtype == ConsoleBackendType.Hardcoded) {
        return HardcodedConsoleBackend(consoleScope)
    } else if (backendtype == ConsoleBackendType.QuickJS) {
        return QjsConsoleBackend(consoleScope)
    } else if (backendtype == ConsoleBackendType.Lua) {
        return LuaConsoleBackend(consoleScope)
    } else if (backendtype == ConsoleBackendType.MicroPython) {
        return UpyConsoleBackend(consoleScope)
    } else {
        throw IllegalArgumentException("Unexpected backend requsted: ${backendtype.displayname}")
    }
}
