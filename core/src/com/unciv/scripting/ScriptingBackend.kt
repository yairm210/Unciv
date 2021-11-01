package com.unciv.scripting

import java.util.*


data class AutocompleteResults(val isHelpText:Boolean, val matches:List<String>, val helpText:String)


open class ScriptingBackend(val scriptingScope:ScriptingScope) {

    open val displayname:String = "Dummy"

    /* val scriptingScope: ScriptingScope
            get() = scriptingState.scriptingScope */

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


class HardcodedScriptingBackend(scriptingScope:ScriptingScope): ScriptingBackend(scriptingScope) {

    override val displayname:String = "Hardcoded"

    val commandshelp:Map<String, String> = mapOf(
        "help" to "help - Display all commands\nhelp <command> - Display information on a specific command.",
        "countcities" to "countcities - Print out a numerical count of all cities in the current empire.",
        "listcities" to "listcities - Print the names of all cities in the current empire.",
        "locatebuildings" to "locatebuildings <buildingname> - Print out a list of all cities that have a given building.\nlocatebuildings <resourcename> - Print out a list of all cities that are using a given resource.",
        "missingbuildings" to "missingbuildings <buildingname> - Print out a list of all cities that do not have a given building.",
        "cheatson" to "cheatson - Enable commands that break game rules.",
        "cheatsoff" to "cheatsoff - Disable commands that break game rules.",
        "godmode" to "godmode [true|false] - Ignore many game rule restrictions. Allows instant purchase of tech, policies, buildings, tiles, and more.\n\tRun with no arguments to toggle. (Requires cheats.)",
        "godview" to "godview [true|false] - Make the entire map visible.\n\tRun with no arguments to toggle. (Requires cheats.)",
        "inspectpath" to "inspectpath <path> - Read out the value of the Kotlin object at a given <path>.\n\tThe path can be a string representing any combination of property accesses, map keys, array indexes, and method calls.\ninspectpath detailed <path> - Also print out the class name and members of the object at the given path.",
        "setpath" to "setpath <value> <path> - Set the Kotlin property at a given <path> to a given <value>.\n\tThe <path> can be a string representing any combination of property accesses, map keys, array indexes, and method calls.\n\tThe value will be resolved the same was as the path, but will be delineated by the first space character after its start.",
        "simulatetoturn" to "simulatetoturn <integer> - After this turn, automatically play until the turn specified by <integer>.\n\tMap view will be frozen while simulating. (Requires cheats.)",
        "spawnbuilding" to "",
        "spawnunit" to "",
        "supercharge" to "supercharge [true|false] - Massively boost all empire growth stats.\n\tRun with no arguments to toggle. (Requires cheats.)"
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
            return AutocompleteResults(false, commandshelp.keys.filter({ c -> c.startsWith(command) }).map({ c -> c + " " }), "")
        }
    }

    override fun exec(command: String): String {
        var args = command.split(' ')
        var out = ""
        when (args[0]) {
            "help" -> {
                if (args.size > 1) {
                    out = getCommandHelpText(args[1])
                } else {
                    out = commandshelp.keys.joinToString(", ")
                }
            }
            "countcities" -> {
                out = scriptingScope.civInfo.cities.size.toString()
            }
            "locatebuildings" -> {
                var buildingcities:List<String> = listOf()
                if (args.size > 1) {
                    var searchfor = args.slice(1..args.size-1).joinToString(" ").trim(' ')
                    buildingcities = scriptingScope.civInfo.cities
                        .filter {
                            searchfor in it.cityConstructions.builtBuildings ||
                            it.cityConstructions.builtBuildings.any({ building ->
                                scriptingScope.gameInfo.ruleSet.buildings[building]!!.requiresResource(searchfor)
                            })
                        }
                        .map { it.name }
                }
                out = buildingcities.joinToString(", ")
            }
            "missingbuildings" -> {
                var buildingcities:List<String> = listOf()
                if (args.size > 1) {
                    var searchfor = args.slice(1..args.size-1).joinToString(" ").trim(' ')
                    buildingcities = scriptingScope.civInfo.cities
                        .filter { !(searchfor in it.cityConstructions.builtBuildings) }
                        .map { it.name }
                }
                out = buildingcities.joinToString(", ")
            }
            "listcities" -> {
                out = scriptingScope.civInfo.cities
                    .map { city -> city.name }
                    .joinToString(", ")
            }
            "cheatson" -> {
                cheats = true
                out = "Cheats enabled."
            }
            "cheatsoff" -> {
                cheats = false
                out = "Cheats disabled."
            }
            "godmode" -> {
                if (cheats) {
                    var godmode = if (args.size > 1) args[1].toBoolean() else !(scriptingScope.gameInfo.gameParameters.godMode)
                    scriptingScope.gameInfo.gameParameters.godMode = godmode
                    out = "${if (godmode) "Enabled" else "Disabled"} godmode."
                } else {
                    out = "Cheats must be enabled to use this command!"
                }
            }
            "godview" -> {
                if (cheats) {
                    var godview = if (args.size > 1) args[1].toBoolean() else !(scriptingScope.uncivGame.viewEntireMapForDebug)
                    scriptingScope.uncivGame.viewEntireMapForDebug = godview
                    out = "${if (godview) "Enabled" else "Disabled"} whole map visibility."
                } else {
                    out = "Cheats must be enabled to use this command!"
                }
            }
            "inspectpath" -> {
                if (cheats) {
                    val detailed = args.size > 1 && args[1] == "detailed"
                    val startindex = if (detailed) 2 else 1
                    val path = (if (args.size > startindex) args.slice(startindex..args.size-1) else listOf()).joinToString(" ")
                    try {
                        var obj = evalKotlinString(scriptingScope, path)
                        out =
                            if (detailed)
                                "Type: ${obj::class.qualifiedName}\n\nValue: ${obj}\n\nMembers: ${obj::class.members.map{it.name}}\n"
                            else
                                "${obj}"
                    } catch (e: Exception) {
                        out = "Error accessing: ${e}"
                    }
                } else {
                    out = "Cheats must be enabled to use this command!"
                }
            }
            "setpath" -> {
                if (cheats) {
                    try {
                        val path = (if (args.size > 2) args.slice(2..args.size-1) else listOf()).joinToString(" ")
                        val value = evalKotlinString(scriptingScope, args[1])
                        var obj = setInstancePath(
                            scriptingScope,
                            parseKotlinPath(path),
                            value
                        )
                        out = "Set ${path} to ${value}."
                    } catch (e: Exception) {
                        out = "Error setting: ${e}"
                    }
                } else {
                    out = "Cheats must be enabled to use this command!"
                }
            }
            "simulatetoturn" -> {
                if (cheats) {
                    var numturn = 0
                    if (args.size > 1) {
                        try {
                            numturn = args[1].toInt()
                        } catch (e: NumberFormatException) {
                            out += "Invalid number: ${args[1]}\n"
                        }
                    }
                    scriptingScope.uncivGame.simulateUntilTurnForDebug = numturn
                    out += "Will automatically simulate game until turn ${numturn} after this turn.\nThe map will not update until completed."
                } else {
                    out = "Cheats must be enabled to use this command!"
                }
            }
            "supercharge" -> {
                if (cheats) {
                    var supercharge = if (args.size > 1) args[1].toBoolean() else !(scriptingScope.uncivGame.superchargedForDebug)
                    scriptingScope.uncivGame.superchargedForDebug = supercharge
                    out = "${if (supercharge) "Enabled" else "Disabled"} stats supercharge."
                } else {
                    out = "Cheats must be enabled to use this command!"
                }
            } else -> {
                out = "The command ${args[0]} is either not known or not implemented."
            }
        }
        return "\n> ${command}\n${out}"
    }
}


class QjsScriptingBackend(scriptingScope:ScriptingScope): ScriptingBackend(scriptingScope) {
    override val displayname:String = "QuickJS"
    override fun motd(): String {
        return "\n\nWelcome to the QuickJS Unciv CLI, which doesn't currently run QuickJS but might one day!"
    }
}


class LuaScriptingBackend(scriptingScope:ScriptingScope): ScriptingBackend(scriptingScope) {
    override val displayname:String = "Lua"
    override fun motd(): String {
        return "\n\nWelcome to the Lua Unciv CLI, which doesn't currently run Lua but might one day!"
    }
}


class UpyScriptingBackend(scriptingScope:ScriptingScope): ScriptingBackend(scriptingScope) {
    override val displayname:String = "MicroPython"
    override fun motd(): String {
        return "\n\nWelcome to the MicroPython Unciv CLI, which doesn't currently run MicroPython but might one day!"
    }
}


enum class ScriptingBackendType(val displayname:String) {
    Dummy("Dummy"),
    Hardcoded("Hardcoded"),
    QuickJS("QuickJS"),
    Lua("Lua"),
    MicroPython("MicroPython")
}


fun GetNamedScriptingBackend(backendtype:ScriptingBackendType, scriptingScope:ScriptingScope): ScriptingBackend {
    if (backendtype == ScriptingBackendType.Dummy) {
        return ScriptingBackend(scriptingScope)
    } else if (backendtype == ScriptingBackendType.Hardcoded) {
        return HardcodedScriptingBackend(scriptingScope)
    } else if (backendtype == ScriptingBackendType.QuickJS) {
        return QjsScriptingBackend(scriptingScope)
    } else if (backendtype == ScriptingBackendType.Lua) {
        return LuaScriptingBackend(scriptingScope)
    } else if (backendtype == ScriptingBackendType.MicroPython) {
        return UpyScriptingBackend(scriptingScope)
    } else {
        throw IllegalArgumentException("Unexpected backend requsted: ${backendtype.displayname}")
    }
}
