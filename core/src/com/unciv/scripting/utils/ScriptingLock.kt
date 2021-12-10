package com.unciv.scripting.utils

import com.unciv.UncivGame
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.Popup
import com.unciv.ui.utils.stringifyException
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

object ScriptingLock {
    private val isRunning = AtomicBoolean(false) // Maybe make thread-safe just in case, but also officially disallow use by multiple threads at once?
    var runningName: String? = null
        private set
    private var runningKey: String? = null // Unique key set each run to make sure
    private var runQueue = ArrayDeque<() -> Unit>()
    // @return A randomly generated string to pass to the release function.
    fun acquire(name: String? = null): String? {
        val success = isRunning.compareAndSet(false, true)
        if (!success) throw IllegalStateException("Cannot acquire ScriptingLock for $name because already in use by $runningName.")
        runningKey = UUID.randomUUID().toString()
        return runningKey
//        ScriptingScope.worldScreen?.isPlayersTurn = false
        //Hm. Should return to original value, not necessarily true. That means keeping a property, which means I'd rather put this in its own class.
        //TODO: Move to ScriptingLock.
        //Not perfect. I think ScriptingScope also exposes mutating the GUI itself, and many things that aren't protected by this? Then again, a script that *wants* to cause a crash/ANR will always be able to do so by just assigning an invalid value or deleting a required node somewhere. Could make mod handlers outside of worldScreen blocking, with written stipulations on (dis)recommended size, and then
        //https://github.com/yairm210/Unciv/pull/5592/commits/a1f51e08ab782ab46bda220e0c4aaae2e8ba21a4
    }
    fun release(releaseKey: String) {
        if (releaseKey != runningKey) throw IllegalArgumentException("Invalid key given to release ScriptingLock.")
//        val success = isRunning.compareAndSet(true, false)
        if (isRunning.get()) {
            runningName = null
            runningKey = null
            isRunning.set(false)
            runQueue.removeFirstOrNull()?.invoke()
        } else {
            throw IllegalStateException("Cannot release ScriptingLock because it has not been acquired.")
        }
    }
    // TODO: Prevent UI collision, and also prevent recursive script executions.
    // Allow registering name text of running script, for error messages.
    fun notifyPlayerScriptFailure(exception: Throwable, asName: String? = null) {
        // Should this be in ScriptingState after that's been singleton'd?
        val popup = Popup(UncivGame.Current.screen as BaseScreen)
        val msg = "An error has occurred with the mod/script \"${asName ?: runningName}\":\n\n${exception.toString().prependIndent("\t")}\n\nSee system terminal output for details.\nConsider disabling mods if this keeps happening.\n"
        popup.addGoodSizedLabel(msg).row()
        popup.addOKButton{}
        popup.open(true)
        printConsolePlayerScriptFailure(exception, asName)
    }
    fun printConsolePlayerScriptFailure(exception: Throwable, asName: String? = null) {
        println("\nException with \"${asName ?: runningName}\" script:\n${exception.stringifyException().prependIndent("\t")}\n")
        // Really these should all go to STDERR.
    }
}
