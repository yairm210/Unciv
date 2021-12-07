package com.unciv.scripting

//import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.scripting.api.ScriptingScope
import com.unciv.scripting.reflection.Reflection
import com.unciv.scripting.protocol.Blackbox
import com.unciv.scripting.protocol.ScriptingReplManager
import com.unciv.scripting.protocol.ScriptingProtocolReplManager
import com.unciv.scripting.protocol.ScriptingRawReplManager
import com.unciv.scripting.protocol.SubprocessBlackbox
import com.unciv.scripting.utils.ApiSpecGenerator
import com.unciv.scripting.utils.SourceManager
import com.unciv.scripting.utils.SyntaxHighlighter
import kotlin.reflect.full.companionObjectInstance
//import java.util.*


/**
 * Data class representing an autocompletion result.
 *
 * @property matches List of valid matches. Each match should be a full input string after applying autocompletion (I.E. Don't truncate to the cursor position).
 * @property helpText String to print out instead of showing autocomplete matches.
 */
//TODO: Probably replace isHelpText with a nullable helpText.
data class AutocompleteResults(val matches: List<String> = listOf(), val isHelpText: Boolean = false, val helpText: String = "")


data class ExecResult(val resultPrint: String, val isException: Boolean = false)
//TODO: Use this.


/**
 * Base class for required companion objects of ScriptingBackend implementations.
 *
 * Subtypes (or specifically, companions of subtypes) of ScriptingBackend are organized in an Enum.
 * Companion objects allow new instances of the correct subtype to be created directly from the Enum constants.
 */
abstract class ScriptingBackend_metadata {
    /**
     * @return A new instance of the parent class of which this object is a companion.
     */
    abstract fun new(scriptingScope: ScriptingScope): ScriptingBackendBase
    abstract val displayName: String
    val syntaxHighlighting: SyntaxHighlighter? = null
}

abstract class EnvironmentedScriptBackend_metadata: ScriptingBackend_metadata() {
    abstract val engine: String
    // Why did I put this here? There was probably a reason, because it was a lot of trouble.
}


/**
 * Interface for a single object that parses, interprets, and executes scripts.
 */
interface ScriptingBackend {

    /**
     * @return Message to print on launch. Should be called exactly once per instance, and prior to calling any of the other methods defined here.
     */
    fun motd(): String {
        return "\n\nWelcome to the Unciv CLI!\nYou are currently running the dummy backend, which will echo all commands but never do anything.\n"
    }

    /**
     * @param command Current input to run autocomplete on.
     * @param cursorPos Active cursor position in the current command input.
     * @return AutocompleteResults object that represents either a List of full autocompletion matches or a help string to print for the current input.
     */
    fun autocomplete(command: String, cursorPos: Int? = null): AutocompleteResults {
        return AutocompleteResults(listOf(command+"_autocomplete"))
    }

    /**
     * @param command Code to execute
     * @return REPL printout.
     */
    fun exec(command: String): String {
        //TODO: To support modding (and more specifically, error catching in mod development and use), this (and everything that parallels/implements it) should eventually support returning a Boolean to flag the REPL printout as an error message, in addition to the fake STDOUT printout.
        return command
    }

    /**
     * @return null on successful termination, an Exception() otherwise.
     */
    fun terminate(): Exception? {
        // The same reasoning for returning instead of throwing an Exception() as for Blackbox.stop() also applies here.
        // Namely: Some errors may be expected, and thus not exceptional enough to warrant exceptional flow control via try/catch. Let those be propagated and handled or ignored more easily as return values.
        // This protects the distinction between errors that are an expected and predictable part of normal operation (Network down, zombie process, etc), which don't cause executed code blocks to be broken out of at arbitrary points, and legitimately exceptional runtime errors where the program is doing something it shouldn't be, which break normal control flow by breaking out of code blocks mid-execution.
        // So if an exception is raised, it can be caught and turned into a return value at the point where it happens, without having to wrap a try{} block around the entire call stack between there and the point where it's eventually handled.
        return null
    }

}

// TODO: Rename ScriptingBackend to ScriptingImplementation.
// TODO: Add .userTerminable flag and per-instance display string to ScriptingBackendBase. Let mod command histories be seen on ConsoleScreen?

open class ScriptingBackendBase(val scriptingScope: ScriptingScope): ScriptingBackend {
//A little bit confusing.
//Hm. Couldn't the metadata getter go on the interface?
//That would mean this exists only as a code example for defining the companion object.
//TODO: Move getter, delete class or rename to DummyScriptingBackend?

    /**
     * For the UI, a way is needed to list all available scripting backend types with 1. A readable display name and 2. A way to create new instances.
     *
     * So every ScriptngBackend has a Metadata:ScriptingBackend_metadata companion object, which is stored in the ScriptingBackendType enums.
     */
    companion object Metadata: ScriptingBackend_metadata() {
        override fun new(scriptingScope: ScriptingScope) = ScriptingBackendBase(scriptingScope)
        // Trying to instantiate from a KClass seems messy when the constructors are expected to be called normally. This is easier.
        override val displayName: String = "Dummy"
    }

    /**
     * Let the companion object of the correct subclass be accessed in subclass instances.
     */
    open val metadata
        get() = this::class.companionObjectInstance as ScriptingBackend_metadata

}

//Test, reference, example, and backup

//Has
// Non-essential. Nothing should depend on this. Should always be removable from the game's code.
class HardcodedScriptingBackend(scriptingScope: ScriptingScope): ScriptingBackendBase(scriptingScope) {
    companion object Metadata: ScriptingBackend_metadata() {
        override fun new(scriptingScope: ScriptingScope) = HardcodedScriptingBackend(scriptingScope)
        override val displayName: String = "Hardcoded"
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

    var cheats: Boolean = false

    override fun motd(): String {
        return "\n\nWelcome to the hardcoded demo CLI backend.\n\nPlease run \"help\" or press [TAB] to see a list of available commands.\nPress [TAB] at any time to see help for currently typed command.\n\nPlease note that the available commands are meant as a DEMO for the CLI.\n"
    }

    fun getCommandHelpText(command: String): String {
        if (command in commandshelp) {
            return "\n${commandshelp[command]}"
        } else {
            return "\nNo help entry found for command '${command}'"
        }
    }

    override fun autocomplete(command: String, cursorPos: Int?): AutocompleteResults{
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
                if (!(scriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return out }
                appendOut(scriptingScope.civInfo!!.cities.size.toString())
            }
            "locatebuildings" -> {
                var buildingcities: List<String> = listOf()
                if (!(scriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return out }
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
                var buildingcities: List<String> = listOf()
                if (!(scriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return out }
                if (args.size > 1) {
                    var searchfor = args.slice(1..args.size-1).joinToString(" ").trim(' ')
                    buildingcities = scriptingScope.civInfo!!.cities
                        .filter { !(searchfor in it.cityConstructions.builtBuildings) }
                        .map { it.name }
                }
                appendOut(buildingcities.joinToString(", "))
            }
            "listcities" -> {
                if (!(scriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return out }
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
                if (!(scriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return out }
                if (cheats) {
                    var godmode = if (args.size > 1) args[1].toBoolean() else !(scriptingScope.gameInfo!!.gameParameters.godMode)
                    scriptingScope.gameInfo!!.gameParameters.godMode = godmode
                    appendOut("${if (godmode) "Enabled" else "Disabled"} godmode.")
                } else {
                    appendOut("Cheats must be enabled to use this command!")
                }
            }
            "godview" -> {
                if (!(scriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return out }
                if (cheats) {
                    var godview = if (args.size > 1) args[1].toBoolean() else !(scriptingScope.uncivGame!!.viewEntireMapForDebug)
                    scriptingScope.uncivGame!!.viewEntireMapForDebug = godview
                    appendOut("${if (godview) "Enabled" else "Disabled"} whole map visibility.")
                } else {
                    appendOut("Cheats must be enabled to use this command!")
                }
            }
            "inspectpath" -> {
                if (!(scriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return out }
                if (cheats) {
                    val detailed = args.size > 1 && args[1] == "detailed"
                    val startindex = if (detailed) 2 else 1
                    val path = (if (args.size > startindex) args.slice(startindex..args.size-1) else listOf()).joinToString(" ")
                    try {
                        var obj = Reflection.evalKotlinString(scriptingScope, path)
                        val isnull = obj == null
                        appendOut(
                            if (detailed)
                                "Type: ${if (isnull) null else obj!!::class.qualifiedName}\n\nValue: ${obj}\n\nMembers: ${if (isnull) null else obj!!::class.members.map {it.name}}\n"
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
                        val value = Reflection.evalKotlinString(scriptingScope, args[1])
                        Reflection.setInstancePath(
                            scriptingScope,
                            Reflection.parseKotlinPath(path),
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
                if (!(scriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return out }
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
                if (!(scriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return out }
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


//Nothing should depend on this.
class ReflectiveScriptingBackend(scriptingScope: ScriptingScope): ScriptingBackendBase(scriptingScope) {

    companion object Metadata: ScriptingBackend_metadata() {
        override fun new(scriptingScope: ScriptingScope) = ReflectiveScriptingBackend(scriptingScope)
        override val displayName: String = "Reflective"
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
        "set \"Krakatoa\" worldScreen.mapHolder.selectedTile.naturalWonder",
        "get civInfo.addGold(civInfo.tech.techsResearched.size)",
        "get uncivGame.setScreen(apiHelpers.Jvm.constructorByQualname[\"com.unciv.ui.mapeditor.MapEditorScreen\"](gameInfo.tileMap))",
        "get apiHelpers.Jvm.constructorByQualname[\"com.unciv.ui.utils.ToastPopup\"](\"This is a popup!\", uncivGame.consoleScreen, 2000)"
        //apiHelpers.Jvm.constructorByQualname["com.unciv.ui.worldscreen.AlertPopup"](worldScreen, apiHelpers.Jvm.constructorByQualname["com.unciv.logic.civilization.PopupAlert"](apiHelpers.Enums.enumMapsByQualname["com.unciv.logic.civilization.AlertType"]["FirstContact"], "Carthage"))
    )

    override fun motd(): String {
        return "\n\nWelcome to the reflective Unciv CLI backend.\n\nCommands you enter will be parsed as a path consisting of property reads, key and index accesses, function calls, and string, numeric, boolean, and null literals.\nKeys, indices, and function arguments are parsed recursively.\nProperties can be both read from and written to.\n\nExamples:\n${examples.map({"> ${it}"}).joinToString("\n")}\n\nPress [TAB] at any time to trigger autocompletion for all known leaf names at the currently entered path.\n"
    }

    override fun autocomplete(command: String, cursorPos: Int?): AutocompleteResults {
        try {
            var comm = commandparams.keys.find { command.startsWith(it+" ") }
            if (comm != null) {
                val params = command.drop(comm.length+1).split(' ', limit=commandparams[comm]!!)
                //val prefix
                //val workingcode
                //val suffix
                val workingcode = params[params.size-1]
                val workingpath = Reflection.parseKotlinPath(workingcode)
                if (workingpath.any { it.type == Reflection.PathElementType.Call }) {
                    return AutocompleteResults(listOf(), true, "No autocomplete available for function calls.")
                }
                val leafname = if (workingpath.size > 0) workingpath[workingpath.size - 1].name else ""
                val prefix = command.dropLast(leafname.length)
                val branchobj = Reflection.resolveInstancePath(scriptingScope, workingpath.slice(0..workingpath.size-2))
                return AutocompleteResults(
                    branchobj!!::class.members
                        .map { it.name }
                        .filter { it.startsWith(leafname) }
                        .map { prefix + it }
                )
            } else {
                return AutocompleteResults(commandparams.keys.filter { it.startsWith(command) }.map { it+" " })
            }
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
                    appendOut("${Reflection.evalKotlinString(scriptingScope, parts[1])}")
                }
                "set" -> {
                    var setparts = parts[1].split(' ', limit=2)
                    var value = Reflection.evalKotlinString(scriptingScope, setparts[0])
                    Reflection.setInstancePath(
                        scriptingScope,
                        Reflection.parseKotlinPath(setparts[1]),
                        value
                    )
                    appendOut("Set ${setparts[1]} to ${value}")
                }
                "typeof" -> {
                    var obj = Reflection.evalKotlinString(scriptingScope, parts[1])
                    appendOut("${if (obj == null) null else obj!!::class.qualifiedName}")
                }
                "examples" -> {
                    throw RuntimeException("Not implemented.")
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


abstract class EnvironmentedScriptingBackend(scriptingScope: ScriptingScope): ScriptingBackendBase(scriptingScope) {

    companion object Metadata: EnvironmentedScriptBackend_metadata() {
        // Need full metadata companion here, or else won't compile.
        // Ideally would be able to just declare that subclasses must define a companion of the correct type, but ah well.
        override val displayName = ""
        override fun new(scriptingScope: ScriptingScope) = throw UnsupportedOperationException("Base scripting backend class not meant to be instantiated.")
        override val engine = ""
    }

    override val metadata
        // Since the companion object type is different, we have to define a new getter for the subclass instance companion getter to get its new members.
        get() = this::class.companionObjectInstance as EnvironmentedScriptBackend_metadata

    val folderHandle: FileHandle by lazy { SourceManager.setupInterpreterEnvironment(metadata.engine) }
    // This requires the overridden values for engine, so setting it in the constructor causes a null error... May be fixed since moving engine to the companions.
    // Also, BlackboxScriptingBackend inherits from this, but not all subclasses of BlackboxScriptingBackend might need it. So as long as it's not accessed, it won't be initialized.

    // TODO: Probably implement a the Disposable interface method here to clean up the folder.

}


abstract class BlackboxScriptingBackend(scriptingScope: ScriptingScope): EnvironmentedScriptingBackend(scriptingScope) {

    companion object Metadata: EnvironmentedScriptBackend_metadata() {
        // Need full metadata companion here, or else won't compile.
        // Ideally would be able to just declare that subclasses must define a companion of the correct type, but ah well.
        override val displayName = ""
        override fun new(scriptingScope: ScriptingScope) = throw UnsupportedOperationException("Base scripting backend class not meant to be instantiated.")
        override val engine = ""
    }

    abstract val blackbox: Blackbox

    abstract val replManager: ScriptingReplManager
    // Should be lazy in implementations. Was originally a method that could be called by subclasses' constructors. This seems cleaner. Subclasses don't even have to define any functions this way. And the liberal use of lazy should naturally make sure the properties will always be initialized in the right order.
    // Downside: Potential latency on first command, or possibly depending on motd() for immediate initialization.

    override fun motd(): String {
        try {
            return replManager.motd()
        } catch (e: Exception) {
            return "No MOTD for ${metadata.engine} backend: ${e}\n"
            //TODO: Can't you access companion properties directly from instances?
        }
    }

    override fun autocomplete(command: String, cursorPos: Int?): AutocompleteResults {
        try {
            return replManager.autocomplete(command, cursorPos)
        } catch (e: Exception) {
            return AutocompleteResults(isHelpText = true, helpText = "Autocomplete error: ${e}")
        }
    }

    override fun exec(command: String): String {
        try {
            return replManager.exec("${command}\n")
        } catch (e: RuntimeException) {
            return "${e}"
        }
    }

    override fun terminate(): Exception? {
        try {
            return replManager.terminate()
        } catch (e: Exception) {
            return e
        }
    }
}


abstract class SubprocessScriptingBackend(scriptingScope: ScriptingScope): BlackboxScriptingBackend(scriptingScope) {

    abstract val processCmd: Array<String>

    override val blackbox by lazy { SubprocessBlackbox(processCmd) }

    override val replManager: ScriptingReplManager by lazy { ScriptingRawReplManager(scriptingScope, blackbox) }

    override fun motd(): String {
        return """

            Welcome to the Unciv '${metadata.displayName}' API. This backend relies on running the system ${processCmd.firstOrNull()} command as a subprocess.

            If you do not have an interactive REPL below, then please make sure the following command is valid on your system:

            ${processCmd.joinToString(" ")}

        """.trimIndent() + super.motd()
    }
}


abstract class ProtocolSubprocessScriptingBackend(scriptingScope: ScriptingScope): SubprocessScriptingBackend(scriptingScope) {

    override val replManager by lazy { ScriptingProtocolReplManager(scriptingScope, blackbox) }

}


class SpyScriptingBackend(scriptingScope: ScriptingScope): ProtocolSubprocessScriptingBackend(scriptingScope) {

    companion object Metadata: EnvironmentedScriptBackend_metadata() {
        override fun new(scriptingScope: ScriptingScope) = SpyScriptingBackend(scriptingScope)
        override val displayName: String = "System Python"
        override val engine = "python"
    }

    override val processCmd = arrayOf("python3", "-u", "-X", "utf8", folderHandle.child("main.py").toString())

}


class SqjsScriptingBackend(scriptingScope: ScriptingScope): SubprocessScriptingBackend(scriptingScope) {

    companion object Metadata: EnvironmentedScriptBackend_metadata() {
        override fun new(scriptingScope: ScriptingScope) = SqjsScriptingBackend(scriptingScope)
        override val displayName: String = "System QuickJS"
        override val engine = "qjs"
    }

    override val processCmd = arrayOf("qjs", "--std", "--script", folderHandle.child("main.js").toString())

}


class SluaScriptingBackend(scriptingScope: ScriptingScope): SubprocessScriptingBackend(scriptingScope) {

    companion object Metadata: EnvironmentedScriptBackend_metadata() {
        override fun new(scriptingScope: ScriptingScope) = SluaScriptingBackend(scriptingScope)
        override val displayName: String = "System Lua"
        override val engine = "lua"
    }

    override val processCmd = arrayOf("lua", folderHandle.child("main.lua").toString())

}


class DevToolsScriptingBackend(scriptingScope: ScriptingScope): ScriptingBackendBase(scriptingScope) {

    //Probably redundant, and can probably be removed in favour of whatever mechanism is currently used to run the translation file generator.

    companion object Metadata: ScriptingBackend_metadata() {
        override fun new(scriptingScope: ScriptingScope) = DevToolsScriptingBackend(scriptingScope)
        override val displayName: String = "DevTools"
    }

    val commands = listOf(
        "PrintFlatApiDefs",
        "PrintClassApiDefs",
        "WriteOutApiFile <outfile>",
        "WriteOutApiFile android/assets/scripting/sharedfiles/ScriptAPI.json"
    )

    override fun motd() = """

        You have launched the DevTools CLI backend."
        This tool is meant to help update code files.

        Available commands:
    """.trimIndent()+"\n"+commands.map { "> ${it}" }.joinToString("\n")+"\n\n"

    override fun autocomplete(command: String, cursorPos: Int?) = AutocompleteResults(commands.filter { it.startsWith(command) })

    override fun exec(command: String): String {
        val commv = command.split(' ', limit=2)
        var out = "> ${command}\n"
        try {
            when (commv[0]) {
                "PrintFlatApiDefs" -> {
                    out += ApiSpecGenerator(scriptingScope).generateFlatApi().toString() + "\n"
                }
                "PrintClassApiDefs" -> {
                    out += ApiSpecGenerator(scriptingScope).generateClassApi().toString() + "\n"
                }
                else -> {
                    out += "Unknown command: ${commv[0]}\n"
                }
            }

        } catch (e: Exception) {
            out += e.toString()
        }
        return out
    }
}


enum class ScriptingBackendType(val metadata: ScriptingBackend_metadata) {
    Dummy(ScriptingBackendBase),
    Hardcoded(HardcodedScriptingBackend),
    Reflective(ReflectiveScriptingBackend),
    //MicroPython(UpyScriptingBackend),
    SystemPython(SpyScriptingBackend),
    SystemQuickJS(SqjsScriptingBackend),
    SystemLua(SluaScriptingBackend),
//    DevTools(DevToolsScriptingBackend),
    //For running ApiSpecGenerator. Comment in releases. Uncomment if needed.
    // TODO: Have .new function?
}

// TODO: Lowercase name. Actually, no. Just get rid of this.
fun SpawnNamedScriptingBackend(backendtype: ScriptingBackendType, scriptingScope: ScriptingScope): ScriptingBackendBase {
    // Seems unnecessary?
    return backendtype.metadata.new(scriptingScope)
}
