package com.unciv.console

import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min

class ConsoleState(val consoleScope: ConsoleScope){

    val consoleBackends:ArrayList<ConsoleBackend> = ArrayList<ConsoleBackend>()
    
    val outputHistory:ArrayList<String> = ArrayList<String>() // Not implemented
    val commandHistory:ArrayList<String> = ArrayList<String>() // Not implemented
    
    var activeBackend:Int = 0
    
    var maxOutputHistory:Int = 50
    var maxCommandHistory:Int = 50
    
    var activeCommandHistory:Int = 0
    
    init {
        echo(spawnBackend(ConsoleBackendType.Dummy))
    }
    
    fun spawnBackend(backendtype: ConsoleBackendType): String {
        var backend:ConsoleBackend = GetNamedConsoleBackend(backendtype, consoleScope)
        consoleBackends.add(backend)
        activeBackend = consoleBackends.size - 1
        return backend.motd()
    }
    
    fun switchToBackend(index: Int) {
        activeBackend = max(0, min(consoleBackends.size - 1, index))
    }
    
    fun termBackend(index: Int) {
        if (!(0 <= index && index < consoleBackends.size)) {
            return // Maybe checking should be better done and unified, and raise a warning when out of bounds.
        }
        val result = consoleBackends[index].terminate()
        if (result) {
            consoleBackends.removeAt(index)
            activeBackend = min(activeBackend, consoleBackends.size - 1)
        }
    }
    
    fun hasBackend(): Boolean {
        return consoleBackends.size > 0
    }
    
    fun getActiveBackend(): ConsoleBackend {
        return consoleBackends[activeBackend]
    }
    
    fun echo(text: String) {
        outputHistory.add(text)
    }
    
    fun getAutocomplete(command: String): AutocompleteResults {
        if (!(hasBackend())) {
            return AutocompleteResults(false, listOf(), "")
        }
        return getActiveBackend().getAutocomplete(command)
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
        commandHistory.add(command)
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
