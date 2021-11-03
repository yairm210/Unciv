package com.unciv.scripting

import kotlin.reflect.full.*
//import kotlin.text.*
import java.io.*
import java.util.*


data class AutocompleteResults(val matches:List<String>, val isHelpText:Boolean = false, val helpText:String = "")


interface ScriptingBackend_metadata {
   fun new(scriptingScope: ScriptingScope): ScriptingBackend
   val displayname:String
}


open class ScriptingBackend(val scriptingScope: ScriptingScope) {

    companion object Metadata: ScriptingBackend_metadata {
        override fun new(scriptingScope: ScriptingScope) = ScriptingBackend(scriptingScope)
        override val displayname:String = "Dummy"
    }
    val metadata: ScriptingBackend_metadata
        get(): ScriptingBackend_metadata = this::class.companionObjectInstance as ScriptingBackend_metadata


    open fun motd(): String {
        // Message to print on launch.
        return "\n\nWelcome to the Unciv CLI!\nYou are currently running the dummy backend, which will echo all commands but never do anything.\n"
    }

    open fun getAutocomplete(command: String, cursorPos: Int? = null): AutocompleteResults {
        // Return either a `List` of autocomplete matches, or a
        return AutocompleteResults(listOf(command+"_autocomplete"))
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


class HardcodedScriptingBackend(scriptingScope: ScriptingScope): ScriptingBackend(scriptingScope) {

    companion object Metadata: ScriptingBackend_metadata {
        override fun new(scriptingScope: ScriptingScope) = HardcodedScriptingBackend(scriptingScope)
        override val displayname:String = "Hardcoded"
    }

    val commandshelp = mapOf<String, String>(
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
        return "\n\nWelcome to the hardcoded demo backend.\n\nPlease run \"help\" or press [TAB] to see a list of available commands.\nPress [TAB] at any time to see help for currently typed command.\n\nPlease note that the available commands are meant as a DEMO for the CLI.\n"
    }

    fun getCommandHelpText(command: String): String {
        if (command in commandshelp) {
            return "\n${commandshelp[command]}"
        } else {
            return "\nNo help entry found for command '${command}'"
        }
    }

    override fun getAutocomplete(command: String, cursorPos: Int?): AutocompleteResults{
        if (' ' in command) {
            return AutocompleteResults(listOf(), true, getCommandHelpText(command.split(' ')[0]))
        } else {
            return AutocompleteResults(commandshelp.keys.filter({ c -> c.startsWith(command) }).map({ c -> c + " " }))
        }
    }

    override fun exec(command: String): String {
        var args = command.split(' ')
        var out = "\n> ${command}\n"
        fun appendOut(text: String) {
            out += text + "\n"
        }
        when (args[0]) {
            "help" -> {
                if (args.size > 1) {
                    appendOut(getCommandHelpText(args[1]))
                } else {
                    appendOut(commandshelp.keys.joinToString(", "))
                }
            }
            "countcities" -> {
                if (!(scriptingScope.isInGame)) { appendOut("Must be in-game for this command!"); return out }
                appendOut(scriptingScope.civInfo!!.cities.size.toString())
            }
            "locatebuildings" -> {
                var buildingcities:List<String> = listOf()
                if (!(scriptingScope.isInGame)) { appendOut("Must be in-game for this command!"); return out }
                if (args.size > 1) {
                    var searchfor = args.slice(1..args.size-1).joinToString(" ").trim(' ')
                    buildingcities = scriptingScope.civInfo!!.cities
                        .filter {
                            searchfor in it.cityConstructions.builtBuildings ||
                            it.cityConstructions.builtBuildings.any({ building ->
                                scriptingScope.gameInfo!!.ruleSet.buildings[building]!!.requiresResource(searchfor)
                            })
                        }
                        .map { it.name }
                }
                appendOut(buildingcities.joinToString(", "))
            }
            "missingbuildings" -> {
                var buildingcities:List<String> = listOf()
                if (!(scriptingScope.isInGame)) { appendOut("Must be in-game for this command!"); return out }
                if (args.size > 1) {
                    var searchfor = args.slice(1..args.size-1).joinToString(" ").trim(' ')
                    buildingcities = scriptingScope.civInfo!!.cities
                        .filter { !(searchfor in it.cityConstructions.builtBuildings) }
                        .map { it.name }
                }
                appendOut(buildingcities.joinToString(", "))
            }
            "listcities" -> {
                if (!(scriptingScope.isInGame)) { appendOut("Must be in-game for this command!"); return out }
                appendOut(scriptingScope.civInfo!!.cities
                    .map { city -> city.name }
                    .joinToString(", ")
                )
            }
            "cheatson" -> {
                cheats = true
                appendOut("Cheats enabled.")
            }
            "cheatsoff" -> {
                cheats = false
                appendOut("Cheats disabled.")
            }
            "godmode" -> {
                if (!(scriptingScope.isInGame)) { appendOut("Must be in-game for this command!"); return out }
                if (cheats) {
                    var godmode = if (args.size > 1) args[1].toBoolean() else !(scriptingScope.gameInfo!!.gameParameters.godMode)
                    scriptingScope.gameInfo!!.gameParameters.godMode = godmode
                    appendOut("${if (godmode) "Enabled" else "Disabled"} godmode.")
                } else {
                    appendOut("Cheats must be enabled to use this command!")
                }
            }
            "godview" -> {
                if (!(scriptingScope.isInGame)) { appendOut("Must be in-game for this command!"); return out }
                if (cheats) {
                    var godview = if (args.size > 1) args[1].toBoolean() else !(scriptingScope.uncivGame!!.viewEntireMapForDebug)
                    scriptingScope.uncivGame!!.viewEntireMapForDebug = godview
                    appendOut("${if (godview) "Enabled" else "Disabled"} whole map visibility.")
                } else {
                    appendOut("Cheats must be enabled to use this command!")
                }
            }
            "inspectpath" -> {
                if (!(scriptingScope.isInGame)) { appendOut("Must be in-game for this command!"); return out }
                if (cheats) {
                    val detailed = args.size > 1 && args[1] == "detailed"
                    val startindex = if (detailed) 2 else 1
                    val path = (if (args.size > startindex) args.slice(startindex..args.size-1) else listOf()).joinToString(" ")
                    try {
                        var obj = evalKotlinString(scriptingScope, path)
                        val isnull = obj == null
                        appendOut(
                            if (detailed)
                                "Type: ${if (isnull) null else obj!!::class.qualifiedName}\n\nValue: ${obj}\n\nMembers: ${if (isnull) null else obj!!::class.members.map{it.name}}\n"
                            else
                                "${obj}"
                        )
                    } catch (e: Exception) {
                        appendOut("Error accessing: ${e}")
                    }
                } else {
                    appendOut("Cheats must be enabled to use this command!")
                }
            }
            "setpath" -> {
                if (cheats) {
                    try {
                        val path = (if (args.size > 2) args.slice(2..args.size-1) else listOf()).joinToString(" ")
                        val value = evalKotlinString(scriptingScope, args[1])
                        setInstancePath(
                            scriptingScope,
                            parseKotlinPath(path),
                            value
                        )
                        appendOut("Set ${path} to ${value}.")
                    } catch (e: Exception) {
                        appendOut("Error setting: ${e}")
                    }
                } else {
                    appendOut("Cheats must be enabled to use this command!")
                }
            }
            "simulatetoturn" -> {
                if (!(scriptingScope.isInGame)) { appendOut("Must be in-game for this command!"); return out }
                if (cheats) {
                    var numturn = 0
                    if (args.size > 1) {
                        try {
                            numturn = args[1].toInt()
                        } catch (e: NumberFormatException) {
                            appendOut("Invalid number: ${args[1]}\n")
                        }
                    }
                    scriptingScope.uncivGame!!.simulateUntilTurnForDebug = numturn
                    appendOut("Will automatically simulate game until turn ${numturn} after this turn.\nThe map will not update until completed.")
                } else {
                    appendOut("Cheats must be enabled to use this command!")
                }
            }
            "supercharge" -> {
                if (!(scriptingScope.isInGame)) { appendOut("Must be in-game for this command!"); return out }
                if (cheats) {
                    var supercharge = if (args.size > 1) args[1].toBoolean() else !(scriptingScope.uncivGame!!.superchargedForDebug)
                    scriptingScope.uncivGame!!.superchargedForDebug = supercharge
                    appendOut("${if (supercharge) "Enabled" else "Disabled"} stats supercharge.")
                } else {
                    appendOut("Cheats must be enabled to use this command!")
                }
            } else -> {
                appendOut("The command ${args[0]} is either not known or not implemented.")
            }
        }
        return out
        //return "\n> ${command}\n${out}"
    }
}


class ReflectiveScriptingBackend(scriptingScope: ScriptingScope): ScriptingBackend(scriptingScope) {

    companion object Metadata: ScriptingBackend_metadata {
        override fun new(scriptingScope: ScriptingScope) = ReflectiveScriptingBackend(scriptingScope)
        override val displayname:String = "Reflective"
    }

    private val commandparams = mapOf("get" to 1, "set" to 2, "typeof" to 1) //showprivates?
    private val examples = listOf(
        "get gameInfo.civilizations[1].policies.adoptedPolicies",
        "set 5 civInfo.tech.freeTechs",
        "set 1 civInfo.cities[0].health",
        "set 5 gameInfo.turns",
        "get civInfo.addGold(1337)",
        "set 2000 worldScreen.bottomUnitTable.selectedUnit.promotions.XP",
        "get worldScreen.bottomUnitTable.selectedCity.population.setPopulation(25)",
        "set \"Cattle\" worldScreen.mapHolder.selectedTile.resource",
        "set \"Krakatoa\" worldScreen.mapHolder.selectedTile.naturalWonder"
    )

    override fun motd(): String {
        return "\n\nWelcome to the reflective Unciv CLI backend.\n\nCommands you enter will be parsed as a path consisting of property reads, key and index accesses, function calls, and string, numeric, boolean, and null literals.\nKeys, indices, and function arguments are parsed recursively.\nProperties can be both read from and written to.\n\nExamples:\n${examples.map({"> ${it}"}).joinToString("\n")}\n\nPress [TAB] at any time to trigger autocompletion for all known leaf names at the currently entered path.\n"
    }
    
    override fun getAutocomplete(command: String, cursorPos: Int?): AutocompleteResults {
        try {
            var comm = commandparams.keys.find{ command.startsWith(it+" ") }
            if (comm != null) {
                val params = command.drop(comm.length+1).split(' ', limit=commandparams[comm]!!)
                val workingcode = params[params.size-1]
                val workingpath = parseKotlinPath(workingcode)
                if (workingpath.any{ it.type == PathElementType.Call }) {
                    return AutocompleteResults(listOf(), true, "No autocomplete available for function calls.")
                }
                val leafname = if (workingpath.size > 0) workingpath[workingpath.size - 1].name else ""
                val prefix = command.dropLast(leafname.length)
                val branchobj = resolveInstancePath(scriptingScope, workingpath.slice(0..workingpath.size-2))
                return AutocompleteResults(
                    branchobj!!::class.members
                        .map{ it.name }
                        .filter{ it.startsWith(leafname) }
                        .map{ prefix + it }
                )
            }
            return AutocompleteResults(commandparams.keys.filter{ it.startsWith(command) }.map{ it+" " })
        } catch (e: Exception) {
            return AutocompleteResults(listOf(), true, "Could not get autocompletion: ${e}")
        }
    }
    
    override fun exec(command: String): String{
        var parts = command.split(' ', limit=2)
        var out = "\n> ${command}\n"
        fun appendOut(text: String) {
            out += text + "\n"
        }
        try {
            when (parts[0]) {
                "get" -> {
                    appendOut("${evalKotlinString(scriptingScope, parts[1])}")
                }
                "set" -> {
                    var setparts = parts[1].split(' ', limit=2)
                    var value = evalKotlinString(scriptingScope, setparts[0])
                    setInstancePath(
                        scriptingScope,
                        parseKotlinPath(setparts[1]),
                        value
                    )
                    appendOut("Set ${setparts[1]} to ${value}")
                }
                "typeof" -> {
                    var obj = evalKotlinString(scriptingScope, parts[1])
                    appendOut("${if (obj == null) null else obj!!::class.qualifiedName}")
                }
                else -> {
                    appendOut("Unknown command: ${parts[0]}")
                }
            }
        } catch (e: Exception) {
            appendOut("Error evaluating command: ${e}")
        }
        return out
    }
}


class QjsScriptingBackend(scriptingScope: ScriptingScope): ScriptingBackend(scriptingScope) {

    companion object Metadata: ScriptingBackend_metadata {
        override fun new(scriptingScope: ScriptingScope) = QjsScriptingBackend(scriptingScope)
        override val displayname:String = "QuickJS"
    }

    override fun motd(): String {
        return "\n\nWelcome to the QuickJS Unciv CLI, which doesn't currently run QuickJS but might one day!\n"
    }
}


class LuaScriptingBackend(scriptingScope: ScriptingScope): ScriptingBackend(scriptingScope) {

    companion object Metadata: ScriptingBackend_metadata {
        override fun new(scriptingScope: ScriptingScope) = LuaScriptingBackend(scriptingScope)
        override val displayname:String = "Lua"
    }

    override fun motd(): String {
        return "\n\nWelcome to the Lua Unciv CLI, which doesn't currently run Lua but might one day!\n"
    }
}


class UpyScriptingBackend(scriptingScope: ScriptingScope): ScriptingBackend(scriptingScope) {

    companion object Metadata: ScriptingBackend_metadata {
        override fun new(scriptingScope: ScriptingScope) = UpyScriptingBackend(scriptingScope)
        override val displayname:String = "MicroPython"
    }

    override fun motd(): String {
        return "\n\nWelcome to the MicroPython Unciv CLI, which doesn't currently run MicroPython but might one day!\n"
    }
}


class SpyScripingBackend(scriptingScope: ScriptingScope): ScriptingBackend(scriptingScope) {

    companion object Metadata: ScriptingBackend_metadata {
        override fun new(scriptingScope: ScriptingScope) = SpyScripingBackend(scriptingScope)
        override val displayname:String = "System Python"
    }

    var pyProcess: java.lang.Process? = null
    
    var pyInStream: BufferedReader? = null
    var pyOutStream: BufferedWriter? = null
    
    var pyProcessLaunchFail = ""

    init {
        try {
            // With pipes, Python automatically execs STDIN: `python3 -c 'print("print(5+5)")' | python3`.
            // When run in interactive mode, it doesn't do this though: `python3` `echo 'print(5)\n' > /proc/$(pgrep python)/fd/0`
            // For now, I'm going to have it manually loop through output to get around this: `echo -e "import sys\nwhile True: exec(sys.stdin.readline())" > pyloop.py; python3 pyloop.py` `echo 'print(5)\n' > /proc/$(pgrep python)/fd/0`
            pyProcess = Runtime.getRuntime().exec(arrayOf("python3", "-u", "-X", "utf8", "-c", "import sys\nprint('sys.implementation == '+str(sys.implementation))\nwhile True:\n\tline=sys.stdin.readline()\n\ttry:\n\t\ttry:\n\t\t\tcode=compile(line, 'STDIN', 'eval')\n\t\texcept SyntaxError:\n\t\t\texec(line)\n\t\telse:\n\t\t\tprint(eval(code))\n\texcept Exception as e:\n\t\tprint(e)"))
            pyInStream = BufferedReader(InputStreamReader(pyProcess!!.getInputStream()))
            pyOutStream = BufferedWriter(OutputStreamWriter(pyProcess!!.getOutputStream()))
        } catch (e: Exception) {
            pyProcess = null
            pyProcessLaunchFail = e.toString()
        }
    }
    
    fun getPyOutput(block:Boolean = true): List<String> {
        val lines = ArrayList<String>()
        if (pyOutStream != null) {
            val input = pyInStream!!
            if (block) {
                lines.add(input.readLine())
            }
            while (input.ready()) {
                lines.add(input.readLine())
            }
        }
        return lines
    }
    
    override fun motd(): String {
        return "\n\nWelcome to the CPython Unciv CLI. Currently, this backend relies on launching the system Python 3 installation.\n\n${getPyOutput().joinToString("\n")}\n\n"
    }
    
    override fun exec(command: String): String {
        var out = "\n>>> ${command}\n"
        if (pyProcess == null) {
            out += "No Python process. Error on launch: ${pyProcessLaunchFail}\n"
        } else {
            //var comm = command.toByteArray(Charsets.UTF_8)
            //print("${comm}\n${comm.size}\n${comm.decodeToString()}\n")
            //var outstream = pyProcess!!.getOutputStream()
            //outstream.write("${command}\n".toByteArray(Charsets.UTF_8))
            //outstream.flush()
            //var instream = pyProcess!!.getInputStream()
            //var l = instream.available()
            //var ba = ByteArray(l)
            //instream.read(ba, 0, l)
            //out += "${ba}\n${ba.size}\n${ba.decodeToString()}\n"
            
            pyOutStream!!.write("${command}\n")
            pyOutStream!!.flush()
            out += getPyOutput().joinToString("\n")
            
            
            //var inputreader = BufferedReader(InputStreamReader(instream))
            //out += inputreader.readLine()
        }
        return out
    }
    
    override fun terminate(): Boolean {
        try {
            if (pyProcess != null) {
                pyProcess!!.destroy()
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

}


enum class ScriptingBackendType(val metadata:ScriptingBackend_metadata) {
    Dummy(ScriptingBackend),
    Hardcoded(HardcodedScriptingBackend),
    Reflective(ReflectiveScriptingBackend),
    QuickJS(QjsScriptingBackend),
    Lua(LuaScriptingBackend),
    MicroPython(UpyScriptingBackend),
    SystemPython(SpyScripingBackend)
}


fun SpawnNamedScriptingBackend(backendtype:ScriptingBackendType, scriptingScope: ScriptingScope): ScriptingBackend {
    return backendtype.metadata.new(scriptingScope)
}
