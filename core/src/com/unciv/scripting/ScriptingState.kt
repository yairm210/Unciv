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
UncivGame():
    ScriptingState():
        ScriptingScope():
            civInfo
            gameInfo
            uncivGame
            worldScreen
        *ScriptingBackend():
            scriptingScope
            ?ScriptingReplManager():
                Blackbox()
    ConsoleScreen():
        scriptingState
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

    fun getAutocomplete(command: String, cursorPos: Int? = null): AutocompleteResults {
        if (!(hasBackend())) {
            return AutocompleteResults(listOf(), false, "")
        }
        return getActiveBackend().getAutocomplete(command, cursorPos)
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
