package com.unciv.console

import com.unciv.console.ConsoleScope
import kotlin.collections.ArrayList


data class AutoCompleteResults(val isHelpText:Boolean, val matches:List<String>, val helpText:String)

open class ConsoleBackend(consoleScope:ConsoleScope) {

    open val displayname:String = "Dummy"

    open fun motd(): String {
        // Message to print on launch.
        return "Welcome to the Unciv CLI!\nYou are currently running the dummy backend, which will echo all commands but never do anything."
    }
    
    open fun getAutocomplete(command: String): AutoCompleteResults {
        // Return either a `List` of autocomplete matches, or a
        return AutoCompleteResults(false, listOf(command+"_autocomplete"), "")
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


class HardcodedConsoleBackend(consoleScope:ConsoleScope): ConsoleBackend(consoleScope) {

    override val displayname:String = "Hardcoded"
}


enum class ConsoleBackendType(val displayname:String) {
    Dummy("Dummy"),
    Hardcoded("Hardcoded")
}


fun GetNamedConsoleBackend(backendtype:ConsoleBackendType, consoleScope:ConsoleScope): ConsoleBackend {
    if (backendtype == ConsoleBackendType.Dummy) {
        return ConsoleBackend(consoleScope)
    } else {
        throw IllegalArgumentException("Unexpected backend requsted: ${backendtype.displayname}")
    }
}
