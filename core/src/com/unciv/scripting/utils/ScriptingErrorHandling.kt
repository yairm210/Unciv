package com.unciv.scripting.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.scripting.sync.ScriptingRunLock
import com.unciv.ui.utils.*
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

object ScriptingErrorHandling {
    fun notifyPlayerScriptFailure(text: String, asName: String? = null, toConsole: Boolean = true) {
        Gdx.app.postRunnable { // This can potentially be run for scripts in worker threads, so in that case needs to go to the main thread to have OpenGL context and not crash.
            val popup = Popup(UncivGame.Current.screen as BaseScreen)
            val widthTarget = popup.screen.stage.width / 2
            val msg1 = "{An error has occurred with the mod/script} \"${asName ?: ScriptingRunLock.runningName}\".\n\n{See system terminal output for details.}\n{Consider disabling mods if this keeps happening.}" // TODO: Translation.
            popup.add(msg1.toLabel().apply
            {
                setAlignment(Align.center)
                wrap = true
            } ).width(widthTarget).row()
            val contentTable = Table()
            val msg2 = "\n\n${text.prependIndent("\t")}"
            contentTable.add(Label(msg2, BaseScreen.skin).apply {
                setFontSize(15)
                wrap = true
            }).width(widthTarget).row()
            val scrollPane = ScrollPane(contentTable)
            popup.add(scrollPane).row()
            popup.addOKButton{}
            popup.open(true)
            if (toConsole)
                printConsolePlayerScriptFailure(text, asName)
        }
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
