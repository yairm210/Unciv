package com.unciv.scripting

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.UncivGame
import com.unciv.ui.worldscreen.WorldScreen
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min

/*
    ```
    The major classes added and changed by this PR are structured as follows. UpperCamelCase() and parentheses means a new instantiation of a class. lowerCamelCase means a reference to an already-existing instance. An asterisk at the start of an item means zero or multiple instances of that class may be held. A question mark at the start of an item means that it may not exist in all implementations of the parent base class/interface. A question mark at the end of an item means that it is nullable, or otherwise may not be set in all states.

    UncivGame():
        ScriptingState(): // Persistent per UncivGame().
            ScriptingScope():
                civInfo? // These are set by WorldScreen init, and unset by MainMenuScreen.
                gameInfo?
                uncivGame
                worldScreen?
            *ScriptingBackend():
                scriptingScope
                ?ScriptingReplManager():
                    Blackbox() // Common interface to wrap foreign interpreter with pipes, STDIN/STDOUT, queues, sockets, embedding, JNI, etc.
                ?folderHandler: setupInterpreterEnvironment() // If used, a temporary directory with file structure copied from engine and shared folders in `assets/scripting`.
        ConsoleScreen(): // Persistent as long as window isn't resized. Recreates itself and restores most of its state from scriptingState if resized.
            scriptingState
    WorldScreen():
        consoleScreen
        scriptingState // ScriptingState has getters and setters that wrap scriptingScope, which WorldScreen uses to update game info.
    MainMenuScreen():
        consoleScreen
        scriptingState // Same as for worldScreen.
    ```
*/

class ScriptingState(val scriptingScope: ScriptingScope, initialBackendType: ScriptingBackendType? = null){

    val scriptingBackends:ArrayList<ScriptingBackendBase> = ArrayList<ScriptingBackendBase>()

    val outputHistory:ArrayList<String> = ArrayList<String>()
    val commandHistory:ArrayList<String> = ArrayList<String>()

    var activeBackend:Int = 0

    var maxOutputHistory:Int = 50 // Not implemented
    var maxCommandHistory:Int = 50 // Not implemented

    var activeCommandHistory:Int = 0


    var civInfo: CivilizationInfo?
        get() = scriptingScope.civInfo
        set(value) { scriptingScope.civInfo = value }

    var gameInfo: GameInfo?
        get() = scriptingScope.gameInfo
        set(value) { scriptingScope.gameInfo = value }

    var uncivGame: UncivGame?
        get() = scriptingScope.uncivGame
        set(value) { scriptingScope.uncivGame = value }

    var worldScreen: WorldScreen?
        get() = scriptingScope.worldScreen
        set(value) { scriptingScope.worldScreen = value }


    init {
        if (initialBackendType != null) {
            echo(spawnBackend(initialBackendType))
        }
    }

    fun spawnBackend(backendtype: ScriptingBackendType): String {
        var backend:ScriptingBackendBase = SpawnNamedScriptingBackend(backendtype, scriptingScope)
        scriptingBackends.add(backend)
        activeBackend = scriptingBackends.size - 1
        var motd = backend.motd()
        echo(motd)
        return motd
    }

    fun switchToBackend(index: Int) {
        activeBackend = max(0, min(scriptingBackends.size - 1, index))
    }

    fun termBackend(index: Int): Exception? {
        if (!(0 <= index && index < scriptingBackends.size)) {
            throw IndexOutOfBoundsException()// Maybe checking should be better done and unified. Also, I don't love the idea of an exposed method being able to trigger a crash, but I had this fail silently before, which would probably be worse.
        }
        val result = scriptingBackends[index].terminate()
        if (result == null) {
            scriptingBackends.removeAt(index)
            activeBackend = min(activeBackend, scriptingBackends.size - 1)
        }
        return result
    }

    fun hasBackend(): Boolean {
        return scriptingBackends.size > 0
    }

    fun getActiveBackend(): ScriptingBackendBase {
        return scriptingBackends[activeBackend]
    }

    fun echo(text: String) {
        outputHistory.add(text)
    }

    fun autocomplete(command: String, cursorPos: Int? = null): AutocompleteResults {
        if (!(hasBackend())) {
            return AutocompleteResults(listOf(), false, "")
        }
        return getActiveBackend().autocomplete(command, cursorPos)
    }

    fun navigateHistory(increment: Int): String {
        activeCommandHistory = max(0, min(commandHistory.size, activeCommandHistory + increment))
        if (activeCommandHistory <= 0) {
            return ""
        } else {
            return commandHistory[commandHistory.size - activeCommandHistory]
        }
    }

    fun exec(command: String): String {
        if (command.length > 0) {
            commandHistory.add(command)
        }
        var out:String
        if (hasBackend()) {
            out = getActiveBackend().exec(command)
        } else {
            out = ""
        }
        echo(out)
        activeCommandHistory = 0
        return out
    }
}
