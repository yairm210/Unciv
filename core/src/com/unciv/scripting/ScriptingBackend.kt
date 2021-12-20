package com.unciv.scripting

//import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.scripting.api.ScriptingScope
import com.unciv.scripting.reflection.Reflection
import com.unciv.scripting.protocol.Blackbox
import com.unciv.scripting.protocol.ScriptingReplManager
import com.unciv.scripting.protocol.ScriptingProtocolReplManager
import com.unciv.scripting.protocol.ScriptingRawReplManager
import com.unciv.scripting.protocol.SubprocessBlackbox
import com.unciv.scripting.utils.ApiSpecGenerator
import com.unciv.scripting.utils.ScriptingDebugParameters
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
data class AutocompleteResults(val matches: List<String> = listOf(), val helpText: String? = null)


// Data class representing the result of executing a command or script in an opaque ScriptingImplementation/ScriptingBackend.

// @property resultPrint Unstructured output text of command. Analogous to STDOUT, or STDERR if isException is set.
// @property isException Whether the resultPrint represents an uncaught exception. Should only be used for errors that occur inside of a ScriptingImplementation/ScriptingBackend; For errors that occur in Kotlin code outside of a running ScriptingImplementation/ScriptingBackend, an Exception() should be thrown as usual.
data class ExecResult(val resultPrint: String, val isException: Boolean = false)


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
    abstract fun new(): ScriptingBackend // TODO: Um, class references are totally a thing, and probably distinct from KClass, right?
    abstract val displayName: String // TODO: Translations on all these?
    val syntaxHighlighting: SyntaxHighlighter? = null
}

abstract class EnvironmentedScriptBackend_metadata: ScriptingBackend_metadata() {
    abstract val engine: String
    // Why did I put this here? There was probably a reason, because it was a lot of trouble.
}


/**
 * Interface for a single object that parses, interprets, and executes scripts.
 */
interface ScriptingImplementation {

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
    fun exec(command: String): ExecResult {
        return ExecResult(command)
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

// TODO: Add .userTerminable flag and per-instance display string to ScriptingBackendBase. Let mod command histories be seen on ConsoleScreen?

// TODO: Add note that methods should be called through ScriptingState, rather than directly.

open class ScriptingBackend: ScriptingImplementation {
    /**
     * For the UI, a way is needed to list all available scripting backend types with 1. A readable display name and 2. A way to create new instances.
     *
     * So every ScriptngBackend has a Metadata:ScriptingBackend_metadata companion object, which is stored in the ScriptingBackendType enums.
     */
    companion object Metadata: ScriptingBackend_metadata() {
        override fun new() = ScriptingBackend()
        // Trying to instantiate from a KClass seems messy when the constructors are expected to be called normally. This is easier.
        override val displayName = "Dummy"
    }

    /**
     * Let the companion object of the correct subclass be accessed in subclass instances.
     *
     * Any access to the companion from instance methods should be done using this property, or else the companion object accessed will be the one from where instances was declared.
     */
    open val metadata
        get() = this::class.companionObjectInstance as ScriptingBackend_metadata

    // Flag marking whether or not the user should be allowed to manually terminate this backend.
    // Meant to be set externally.
    var userTerminable = true
    // Optional short text conveying further information to show the user alongside the displayName.
    // Meant to be set externally.
    var displayNote: String? = null

}

//Test, reference, example, and backup

//Has
// Non-essential. Nothing should depend on this. Should always be removable from the game's code.
class HardcodedScriptingBackend(): ScriptingBackend() {
    companion object Metadata: ScriptingBackend_metadata() {
        override fun new() = HardcodedScriptingBackend()
        override val displayName = "Hardcoded"
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
        return if (' ' in command) {
            AutocompleteResults(helpText = getCommandHelpText(command.split(' ')[0]))
        } else {
            AutocompleteResults(commandshelp.keys.filter({ c -> c.startsWith(command) }).map({ c -> c + " " }))
        }
    }

    override fun exec(command: String): ExecResult {
        var args = command.split(' ')
        var out = "\n> ${command}\n"
        fun appendOut(text: String) {
            out += (text + "\n").prependIndent("  ")
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
                if (!(ScriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return ExecResult(out) }
                appendOut(ScriptingScope.civInfo!!.cities.size.toString())
            }
            "locatebuildings" -> {
                var buildingcities: List<String> = listOf()
                if (!(ScriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return ExecResult(out) }
                if (args.size > 1) {
                    var searchfor = args.slice(1..args.size-1).joinToString(" ").trim(' ')
                    buildingcities = ScriptingScope.civInfo!!.cities
                        .filter {
                            searchfor in it.cityConstructions.builtBuildings ||
                            it.cityConstructions.builtBuildings.any({ building ->
                                ScriptingScope.gameInfo!!.ruleSet.buildings[building]!!.requiresResource(searchfor)
                            })
                        }
                        .map { it.name }
                }
                appendOut(buildingcities.joinToString(", "))
            }
            "missingbuildings" -> {
                var buildingcities: List<String> = listOf()
                if (!(ScriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return ExecResult(out) }
                if (args.size > 1) {
                    var searchfor = args.slice(1..args.size-1).joinToString(" ").trim(' ')
                    buildingcities = ScriptingScope.civInfo!!.cities
                        .filter { !(searchfor in it.cityConstructions.builtBuildings) }
                        .map { it.name }
                }
                appendOut(buildingcities.joinToString(", "))
            }
            "listcities" -> {
                if (!(ScriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return ExecResult(out) }
                appendOut(ScriptingScope.civInfo!!.cities
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
                if (!(ScriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return ExecResult(out) }
                if (cheats) {
                    var godmode = if (args.size > 1) args[1].toBoolean() else !(ScriptingScope.gameInfo!!.gameParameters.godMode)
                    ScriptingScope.gameInfo!!.gameParameters.godMode = godmode
                    appendOut("${if (godmode) "Enabled" else "Disabled"} godmode.")
                } else {
                    appendOut("Cheats must be enabled to use this command!")
                }
            }
            "godview" -> {
                if (!(ScriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return ExecResult(out) }
                if (cheats) {
                    var godview = if (args.size > 1) args[1].toBoolean() else !(ScriptingScope.uncivGame!!.viewEntireMapForDebug)
                    ScriptingScope.uncivGame!!.viewEntireMapForDebug = godview
                    appendOut("${if (godview) "Enabled" else "Disabled"} whole map visibility.")
                } else {
                    appendOut("Cheats must be enabled to use this command!")
                }
            }
            "inspectpath" -> {
                if (!(ScriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return ExecResult(out) }
                if (cheats) {
                    val detailed = args.size > 1 && args[1] == "detailed"
                    val startindex = if (detailed) 2 else 1
                    val path = (if (args.size > startindex) args.slice(startindex..args.size-1) else listOf()).joinToString(" ")
                    try {
                        var obj = Reflection.evalKotlinString(ScriptingScope, path)
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
                        val value = Reflection.evalKotlinString(ScriptingScope, args[1])
                        Reflection.setInstancePath(
                            ScriptingScope,
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
                if (!(ScriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return ExecResult(out) }
                if (cheats) {
                    var numturn = 0
                    if (args.size > 1) {
                        try {
                            numturn = args[1].toInt()
                        } catch (e: NumberFormatException) {
                            appendOut("Invalid number: ${args[1]}\n")
                        }
                    }
                    ScriptingScope.uncivGame!!.simulateUntilTurnForDebug = numturn
                    appendOut("Will automatically simulate game until turn ${numturn} the next time you press Next Turn.\nThe map will not update until completed.")
                } else {
                    appendOut("Cheats must be enabled to use this command!")
                }
            }
            "supercharge" -> {
                if (!(ScriptingScope.apiHelpers.isInGame)) { appendOut("Must be in-game for this command!"); return ExecResult(out) }
                if (cheats) {
                    var supercharge = if (args.size > 1) args[1].toBoolean() else !(ScriptingScope.uncivGame!!.superchargedForDebug)
                    ScriptingScope.uncivGame!!.superchargedForDebug = supercharge
                    appendOut("${if (supercharge) "Enabled" else "Disabled"} stats supercharge.")
                } else {
                    appendOut("Cheats must be enabled to use this command!")
                }
            } else -> {
                appendOut("The command ${args[0]} is either not known or not implemented.")
            }
        }
        return ExecResult(out)
        //return "\n> ${command}\n${out}"
    }
}


//Nothing should depend on this.
class ReflectiveScriptingBackend(): ScriptingBackend() {

    companion object Metadata: ScriptingBackend_metadata() {
        override fun new() = ReflectiveScriptingBackend()
        override val displayName = "Reflective"
    }

    private val commandparams = mapOf("get" to 1, "set" to 2, "typeof" to 1, "examples" to 0, "runtests" to 0) //showprivates?
    private val examples = listOf( // The new splitToplevelExprs means set can safely use equals sign for assignment.
        "get uncivGame.loadGame(Unciv.GameStarter.startNewGame(apiHelpers.Jvm.companionByQualClass[\"com.unciv.models.metadata.GameSetupInfo\"].fromSettings(\"Chieftain\")))",
        "get gameInfo.civilizations[1].policies.adoptedPolicies",
        "set civInfo.tech.freeTechs = 5",
//        "set civInfo.cities[0].health = 1", // Doesn't work as test due to new game, no city.
        "set gameInfo.turns = 5",
        "get civInfo.addGold(1337)",
        "get civInfo.addNotification(\"Here's a notification!\", apiHelpers.Jvm.arrayOfTyped1(\"StatIcons/Resistance\"))",
        "set worldScreen.bottomUnitTable.selectedUnit.promotions.XP = 2000",
//        "get worldScreen.bottomUnitTable.selectedCity.population.setPopulation(25)", // Doesn't work as test due to new game, no city.
        "set worldScreen.mapHolder.selectedTile.resource = \"Cattle\"",
        "set worldScreen.mapHolder.selectedTile.naturalWonder = \"Krakatoa\"",
        "get apiHelpers.Jvm.constructorByQualname[\"com.unciv.ui.worldscreen.AlertPopup\"](worldScreen, apiHelpers.Jvm.constructorByQualname[\"com.unciv.logic.civilization.PopupAlert\"](apiHelpers.Jvm.enumMapsByQualname[\"com.unciv.logic.civilization.AlertType\"][\"StartIntro\"], \"Text text.\")).open(false)",
        "get civInfo.addGold(civInfo.tech.techsResearched.size)",
        //"get uncivGame.setScreen(apiHelpers.Jvm.constructorByQualname[\"com.unciv.ui.mapeditor.MapEditorScreen\"](gameInfo.tileMap))",
        // FIXME: This was working, but now hits an uinitialized .ruleset in the screen constructor.
        // Still works in the .JAR.
        "get apiHelpers.Jvm.constructorByQualname[\"com.unciv.ui.utils.ToastPopup\"](\"This is a popup!\", apiHelpers.Jvm.companionByQualClass[\"com.unciv.UncivGame\"].Current.getScreen(), 2000)",
        "get apiHelpers.Jvm.singletonByQualname[\"com.unciv.ui.utils.Fonts\"].turn",
        "get apiHelpers.App.assetImageB64(\"StatIcons/Resistance\")",
        "get apiHelpers.App.assetFileString(\"jsons/Civ V - Gods & Kings/Terrains.json\")",
        "get apiHelpers.App.assetFileB64(\"jsons/Tutorials.json\")",
        "get apiHelpers.Jvm.staticPropertyByQualClassAndName[\"com.badlogic.gdx.graphics.Color\"][\"WHITE\"]",
        "get apiHelpers.Jvm.constructorByQualname[\"com.unciv.ui.utils.ToastPopup\"](\"This is a popup!\", apiHelpers.Jvm.companionByQualClass[\"com.unciv.UncivGame\"].Current.getScreen(), 2000).add(apiHelpers.Jvm.functionByQualClassAndName[\"com.unciv.ui.utils.ExtensionFunctionsKt\"][\"toLabel\"](\"With Scarlet text! \", apiHelpers.Jvm.staticPropertyByQualClassAndName[\"com.badlogic.gdx.graphics.Color\"][\"SCARLET\"], 24))",
        "set Unciv.ScriptingDebugParameters.printCommandsForDebug = true",
        "set Unciv.ScriptingDebugParameters.printCommandsForDebug = false"
    )
    private val tests = listOf(
        "get modApiHelpers.lambdifyExecScript(\"get uncivGame\")",
        "get apiHelpers.Jvm.functionByQualClassAndName[\"com.unciv.ui.utils.ExtensionFunctionsKt\"][\"onClick\"](apiHelpers.Jvm.constructorByQualname[\"com.unciv.ui.utils.ToastPopup\"](\"Click to add gold!\", apiHelpers.Jvm.companionByQualClass[\"com.unciv.UncivGame\"].Current.getScreen(), 5000), modApiHelpers.lambdifyReadPathcode(null, \"civInfo.addGold(1000)\"))", // The click action doesn't work, but this still tests extension/static function access.
//        "get fFeiltali.stastIRFI" // Force a failure.
    )

    private fun runTests(): ExecResult { // TODO: Could add suppress flag to disable printing in unit tests.
        val failResult = exec("get This.Command[Should](Fail)!")
        if (!failResult.isException) {
            throw AssertionError("ERROR in reflective scripting tests: Unable to detect failures.".also { println(it) })
        }
        val failures = mutableMapOf<String, String>()
        val tests = sequenceOf(examples.filterNot { it.startsWith("runtests") }, tests).flatten().toList()
        for (command in tests) {
            val execResult = exec(command)
            if (execResult.isException) {
                failures[command] = execResult.resultPrint
            }
        }
        return if (failures.isEmpty()) {ExecResult(
                "${tests.size} reflective scripting tests PASSED!".also { println(it) }
            )} else {ExecResult(
                listOf(
                    "${failures.size}/${tests.size} reflective scripting tests FAILED:",
                    *failures.map { "\t${it.key}\n\t\t${it.value    }" }.toTypedArray()
                ).joinToString("\n").also { println(it) },
                true
            )}
    }

    private fun examplesPrintable() = "\nExamples:\n${examples.map({"> ${it}"}).joinToString("\n")}\n"

    override fun motd(): String {
        return """
            
            
            Welcome to the reflective Unciv CLI backend.
            
            Commands you enter will be parsed as a path consisting of property reads, key and index accesses, function calls, and string, numeric, boolean, and null literals.
            Keys, indices, and function arguments are parsed recursively.
            Properties can be both read from and written to.
            
            Press [TAB] at any time to trigger autocompletion for all known leaf names at the currently entered path.
            
            """.trimIndent()
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
                    return AutocompleteResults(helpText = "No autocomplete available for function calls.")
                }
                val leafname = if (workingpath.isNotEmpty()) workingpath[workingpath.size - 1].name else ""
                val prefix = command.dropLast(leafname.length)
                val branchobj = Reflection.resolveInstancePath(ScriptingScope, workingpath.slice(0..workingpath.size-2))
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
            return AutocompleteResults(helpText = "Could not get autocompletion: ${e}")
        }
    }

    override fun exec(command: String): ExecResult { // TODO: Treat multiple lines as consecutive commands, for modding.
        var parts = command.split(' ', limit=2)
        var out = "\n> ${command}\n"
        var isException = false
        fun appendOut(text: String) {
            out += text + "\n" // Slow? Meh. The user will always be the bottleneck in this code.
        }
        try {
            when (parts[0]) {
                "get" -> {
                    appendOut("${Reflection.evalKotlinString(ScriptingScope, parts[1])}")
                }
                "set" -> { // TODO: Use the new pathcode splitter to accept equals sign format.
//                    var setparts = parts[1].split(' ', limit=2)
                    val setparts = Reflection.splitToplevelExprs(
                        parts[1],
                        delimiters = " "
                    ).filter { it.isNotBlank() }
                    if (setparts.size != 3 || setparts.elementAtOrNull(1) != "=") {
                        throw IllegalArgumentException("Expected two expressions separated by an equals sign with spaces. Got:\n" + setparts.joinToString("\n"))
                    }
                    var value = Reflection.evalKotlinString(ScriptingScope, setparts[2])
                    Reflection.setInstancePath(
                        ScriptingScope,
                        path = Reflection.parseKotlinPath(setparts[0]),
                        value = value
                    )
                    appendOut("Set ${setparts[0]} to ${value}")
                }
                "typeof" -> {
                    var obj = Reflection.evalKotlinString(ScriptingScope, parts[1])
                    appendOut("${if (obj == null) null else obj!!::class.qualifiedName}")
                }
                "examples" -> {
                    appendOut(examplesPrintable())
                }
                "runtests" -> {
                    val testResults = runTests()
                    appendOut(testResults.resultPrint)
                    isException = testResults.isException
                }
                else -> {
                    throw Exception("Unknown command:\n${parts[0].prependIndent("\t")}")
                }
            }
        } catch (e: Throwable) { // The runtest command is meant to catch breakage from isMinifyEnabled=true removing scripting API functions, which would be NoSuchElementError, I think, so not an Exception subclass.
            appendOut("Error evaluating command:\n${e.toString().prependIndent("\t")}")
            isException = true
        }
        return ExecResult(out, isException)
    }
}

// Uses SourceManager to copy library files for engine type into a temporary directory per instance.

// Tries to deletes temporary directory on backend termination, and registers a handler with UncivGame to delete temporary directory on application end as well.
abstract class EnvironmentedScriptingBackend(): ScriptingBackend() {

    companion object Metadata: EnvironmentedScriptBackend_metadata() {
        // Need full metadata companion here, or else won't compile.
        // Ideally would be able to just declare that subclasses must define a companion of the correct type, but ah well.
        override val displayName = ""
        override fun new() = throw UnsupportedOperationException("Base scripting backend class not meant to be instantiated.")
        override val engine = ""
    }

    val folderHandle = SourceManager.setupInterpreterEnvironment(metadata.engine) // Temporary directories are often RAM-backed, so, meh. Alternative to copying entire interpreter library/bindings would be to either implement a virtual filesystem (complicated and sounds brittle) or make scripts share files and thus let them interfere with each other if they have filesystem access (as is deliberately the case in the Python backend)… A couple hundred lines of text and a small, compressed example JPEG or three won't kill anything.
    fun deleteFolder(): Unit {
        if (ScriptingDebugParameters.printEnvironmentFolderCreation) {
            println("Deleting interpreter environment for ${metadata.engine} scripting engine: ${folderHandle.path()}")
        }
        folderHandle.deleteDirectory()
    }

    init {
        UncivGame.Current.disposeCallbacks.add(::deleteFolder)
    }

    override val metadata
        // Since the companion object type is different, we have to define a new getter for the subclass instance companion getter to get its new members.
        get() = this::class.companionObjectInstance as EnvironmentedScriptBackend_metadata

    // This requires the overridden values for engine, so setting it in the constructor causes a null error... May be fixed since moving engine to the companions.
    // Also, BlackboxScriptingBackend inherits from this, but not all subclasses of BlackboxScriptingBackend might need it. So as long as it's not accessed, it won't be initialized.

    // TODO: Probably implement a the Disposable interface method here to clean up the folder.

    override fun terminate(): Exception? {
        return try {
            deleteFolder()
            null
        } catch (e: Exception) {
            e
        } finally {
            UncivGame.Current.disposeCallbacks.remove(::deleteFolder) // Looks like value equality, but not referential equality, is preserved between different references to the same function… Good enough.
        }
    }

}


abstract class BlackboxScriptingBackend(): EnvironmentedScriptingBackend() {

    companion object Metadata: EnvironmentedScriptBackend_metadata() {
        // Need full metadata companion here, or else won't compile.
        // Ideally would be able to just declare that subclasses must define a companion of the correct type, but ah well.
        override val displayName = ""
        override fun new() = throw UnsupportedOperationException("Base scripting backend class not meant to be instantiated.")
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
            return "No MOTD for ${metadata.engine} backend: ${e}\n" // TODO: Hm..
        }
    }

    override fun autocomplete(command: String, cursorPos: Int?): AutocompleteResults {
        return try {
            replManager.autocomplete(command, cursorPos)
        } catch (e: Exception) {
            AutocompleteResults(helpText = "Autocomplete error: ${e}")
        }
    }

    override fun exec(command: String): ExecResult {
        return try {
            replManager.exec("${command}\n")
        } catch (e: RuntimeException) {
            ExecResult("${e}", true)
        }
    }

    override fun terminate(): Exception? {
        val deleteResult = super.terminate()
        return (try {
            replManager.terminate()
        } catch (e: Exception) {
            e
        }) ?: deleteResult
    }
}


abstract class SubprocessScriptingBackend(): BlackboxScriptingBackend() {

    abstract val processCmd: Array<String>

    override val blackbox by lazy { SubprocessBlackbox(processCmd) }

    override val replManager: ScriptingReplManager by lazy { ScriptingRawReplManager(ScriptingScope, blackbox) }

    override fun motd(): String {
        return """


            Welcome to the Unciv '${metadata.displayName}' API. This backend relies on running the system ${processCmd.firstOrNull()} command as a subprocess.

            If you do not have an interactive REPL below, then please make sure the following command is valid on your system:

            ${processCmd.joinToString(" ")}


            """.trimIndent() + super.motd() // I don't think trying to translate this (or its subcomponents— Although I guess translations are going to be available for displayName anyway) would be the best idea.
    }
}


abstract class ProtocolSubprocessScriptingBackend(): SubprocessScriptingBackend() {

    override val replManager by lazy { ScriptingProtocolReplManager(ScriptingScope, blackbox) }

}


class SpyScriptingBackend(): ProtocolSubprocessScriptingBackend() {

    companion object Metadata: EnvironmentedScriptBackend_metadata() {
        override fun new() = SpyScriptingBackend()
        override val displayName = "System Python"
        override val engine = "python"
    }

    override val processCmd = arrayOf("python3", "-u", "-X", "utf8", folderHandle.child("main.py").toString())

}


class SqjsScriptingBackend(): SubprocessScriptingBackend() {

    companion object Metadata: EnvironmentedScriptBackend_metadata() {
        override fun new() = SqjsScriptingBackend()
        override val displayName = "System QuickJS"
        override val engine = "qjs"
    }

    override val processCmd = arrayOf("qjs", "--std", "--script", folderHandle.child("main.js").toString())

}


class SluaScriptingBackend(): SubprocessScriptingBackend() {

    companion object Metadata: EnvironmentedScriptBackend_metadata() {
        override fun new() = SluaScriptingBackend()
        override val displayName = "System Lua"
        override val engine = "lua"
    }

    override val processCmd = arrayOf("lua", folderHandle.child("main.lua").toString())

}


class DevToolsScriptingBackend(): ScriptingBackend() {

    //Probably redundant, and can probably be removed in favour of whatever mechanism is currently used to run the translation file generator.

    companion object Metadata: ScriptingBackend_metadata() {
        override fun new() = DevToolsScriptingBackend()
        override val displayName = "DevTools"
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

    override fun exec(command: String): ExecResult {
        val commv = command.split(' ', limit=2)
        var out = "> ${command}\n"
        try {
            when (commv[0]) {
                "PrintFlatApiDefs" -> {
                    out += ApiSpecGenerator().generateFlatApi().toString() + "\n"
                }
                "PrintClassApiDefs" -> {
                    out += ApiSpecGenerator().generateClassApi().toString() + "\n"
                }
                else -> {
                    out += "Unknown command: ${commv[0]}\n"
                }
            }

        } catch (e: Exception) {
            out += e.toString()
        }
        return ExecResult(out)
    }
}


// @property suggestedStartup Default startup code to run when started by ConsoleScreen *only*.
enum class ScriptingBackendType(val metadata: ScriptingBackend_metadata, val suggestedStartup: String = "") { // Not sure how I feel about having suggestedStartup here— Kinda breaks separation of functionality and UI— But keeping a separate Map in the UI files would be too messy, and it's not as bad as putting it in the companion objects— Really, this entire Enum is a mash of stuff needed to launch and use all the backend types by anything else, so that fits.
    Dummy(ScriptingBackend),
    Hardcoded(HardcodedScriptingBackend, "help"),
    Reflective(ReflectiveScriptingBackend, "examples"),
    //MicroPython(UpyScriptingBackend),
    SystemPython(SpyScriptingBackend, "from unciv import *; from unciv_pyhelpers import *"),
    SystemQuickJS(SqjsScriptingBackend),
    SystemLua(SluaScriptingBackend),
//    DevTools(DevToolsScriptingBackend),
    //For running ApiSpecGenerator. Comment in releases. Uncomment if needed.
    // TODO: Have .new function?
}
