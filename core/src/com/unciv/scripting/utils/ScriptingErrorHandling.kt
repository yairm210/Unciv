package com.unciv.scripting.utils

import com.unciv.UncivGame
import com.unciv.scripting.sync.ScriptingRunLock
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.Popup
import com.unciv.ui.utils.stringifyException

object ScriptingErrorHandling {
    fun notifyPlayerScriptFailure(text: String, asName: String? = null, toConsole: Boolean = true) {
        val popup = Popup(UncivGame.Current.screen as BaseScreen)
        val msg = "{An error has occurred with the mod/script} ${asName ?: ScriptingRunLock.runningName}:\n\n${text.prependIndent("\t")}\n\n{See system terminal output for details.}\n{Consider disabling mods if this keeps happening.}\n" // TODO: Translation.
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
