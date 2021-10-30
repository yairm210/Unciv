package com.unciv.console

import com.unciv.console.ConsoleBackend
import com.unciv.console.ConsoleScope
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min

class ConsoleState(val consoleScope: ConsoleScope){

    val consoleBackends:ArrayList<ConsoleBackend> = ArrayList<ConsoleBackend>()
    
    val outputHistory:ArrayList<String> = ArrayList<String>()
    val commandHistory:ArrayList<String> = ArrayList<String>()
    
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
    
    fun getActiveBackend(): ConsoleBackend {
        return consoleBackends[activeBackend]
    }
    
    //fun getOutputHistory(): ArrayList<String> {
    //    return outputHistory
    //}
    
    fun echo(text: String) {
        outputHistory.add(text)
    }
    
    fun getAutocomplete(command: String): AutoCompleteResults {
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
        var out = getActiveBackend().exec(command)
        echo(out)
        activeCommandHistory = 0
        return out
    }
}
