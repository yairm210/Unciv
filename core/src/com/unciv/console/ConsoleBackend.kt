package com.unciv.console

import com.unciv.console.ConsoleScope
import kotlin.collections.ArrayList

class ConsoleBackend(val consoleScope:ConsoleScope) {

    //val history: ArrayList<String> = ArrayList<String>()

    init {
    }
    
    fun motd (): String {
        return "Welcome to the Unciv CLI!\nYou are currently running the dummy backend, which will echo all commands but never do anything."
    }
    
    fun exec (command: String): String {
        return command
    }
}
