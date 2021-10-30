package com.unciv.console

import com.unciv.console.ConsoleBackend
import com.unciv.console.ConsoleScope
import kotlin.collections.ArrayList

class ConsoleState(val consoleScope: ConsoleScope){

    val consoleBackends:ArrayList<ConsoleBackend> = ArrayList<ConsoleBackend>()
    
    val outputHistory:ArrayList<String> = ArrayList<String>()
    val commandHistory:ArrayList<String> = ArrayList<String>()
    
    var activeBackend:Int = 0
    
    var maxOutputHistory:Int = 50
    var maxCommandHistory:Int = 50
    
    init {
        echo(spawnBackend())
    }
    
    fun spawnBackend(): String {
        var backend = ConsoleBackend(consoleScope)
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
    
    fun exec(command: String): String {
        commandHistory.add(command)
        var out = getActiveBackend().exec(command)
        echo(out)
        return out
    }
}
