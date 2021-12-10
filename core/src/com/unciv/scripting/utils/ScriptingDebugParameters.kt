package com.unciv.scripting.utils

import com.unciv.UncivGame
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.Popup
import com.unciv.ui.utils.stringifyException

object ScriptingDebugParameters {
    // Whether to print out all/most IPC packets for debug.
    var printPacketsForDebug = false
    // Whether to print out all executed script code strings for debug (more readable than printing all packets).
    var printCommandsForDebug = false
    // Whether to print out all/most IPC actions for debug (more readable than printing all packets).
    var printAccessForDebug = false
    // Whether to print out major token count changes and cleaning events in InstanceTokenizer for debug.
    var printTokenizerMilestones = false
    // Whether to print out a warning when reflectively accessing definitions that have been deprecated.
    var printReflectiveDeprecationWarnings = false // TODO
}

object ScriptingErrorHandling {
    fun notifyPlayerScriptFailure(text: String, asName: String? = null, toConsole: Boolean = true) {
        val popup = Popup(UncivGame.Current.screen as BaseScreen)
        val msg = "{An error has occurred with the mod/script} ${asName ?: ScriptingRunLock.runningName}:\n\n${text.prependIndent("\t")}\n\n{See system terminal output for details.}\n{Consider disabling mods if this keeps happening.}\n"
        popup.addGoodSizedLabel(msg).row()
        popup.addOKButton{}
        popup.open(true)
        if (toConsole)
            printConsolePlayerScriptFailure(text, asName)
    }
    fun notifyPlayerScriptFailure(exception: Throwable, asName: String? = null) {
        notifyPlayerScriptFailure(exception.toString(), asName, false)
        printConsolePlayerScriptFailure(exception, asName)
    }
    fun printConsolePlayerScriptFailure(text: String, asName: String? = null) {
        println("\nException with <${asName ?: ScriptingRunLock.runningName}> script:\n${text.prependIndent("\t")}\n")
        // Really these should all go to STDERR.
    }
    fun printConsolePlayerScriptFailure(exception: Throwable, asName: String? = null) {
        printConsolePlayerScriptFailure(exception.stringifyException(), asName)
    }
}
