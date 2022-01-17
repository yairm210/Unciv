package com.unciv.scripting

import com.unciv.UncivGame // Only for  blocking execution in multiplayer.
import com.unciv.scripting.api.ScriptingScope
import com.unciv.scripting.sync.ScriptingRunLock
import com.unciv.scripting.sync.makeScriptingRunName
import com.unciv.scripting.utils.ScriptingDebugParameters
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.ToastPopup
import com.unciv.ui.utils.clipIndexToBounds
import com.unciv.ui.utils.enforceValidIndex
import com.unciv.ui.worldscreen.WorldScreen // Only for  blocking execution in multiplayer.
import java.lang.IllegalStateException
import java.util.concurrent.Semaphore
import kotlin.collections.ArrayList

// TODO: Add .github/CODEOWNERS file for automatic PR notifications.

// TODO: Check for places to use Sequences.
// Hm. It seems that Sequence performance isn't even a simple question of number of loops, and is also affected by boxed types and who know what else.
// Premature optimization and such. Clearly long chains of loops can be rewritten as sequences.

// TODO: There's probably some public vars that can/should be private set.

// TODO: Mods blacklist, for security threats.

// See https://github.com/yairm210/Unciv/pull/5592/commits/a1f51e08ab782ab46bda220e0c4aaae2e8ba21a4 for example of running locking operation in separate thread.


// TODO:

/**
 * Self-contained instance of scripting API use.
 *
 * Abstracts available scope, running backends, command history
 * Should be unique per isolated use of scripting. E.G. One for the [~]-key console screen, one for each mod/all mods per save file (or whatever works best), etc.
 *
 * @property ScriptingScope ScriptingScope instance at the root of all scripting API.
 */
//This will be responsible for: Using the lock, threading, passing the entrypoint name to the lock, exposing context/running backend in ScriptingScope, and setting handler context arguments.
object ScriptingState {
    // Singletons. Biggest benefit to having multiple ScriptingStates/ScriptingScopes would be concurrent execution of different engines with different states, which sounds more like a nightmare than a benefit.

    val scriptingBackends = ArrayList<ScriptingBackend>()

    private val outputHistory = ArrayList<String>()
    private val commandHistory = ArrayList<String>()

    var activeBackendIndex: Int = 0
        private set
    val activeBackend get() = scriptingBackends[activeBackendIndex]

    val maxOutputHistory: Int = 511
    val maxCommandHistory: Int = 511

    var activeCommandHistory: Int = 0
    // Actually inverted, because history items are added to end of list and not start. 0 means nothing, 1 means most recent command at end of list.

    var consoleScreenListener: ((String) -> Unit)? = null // TODO: Switch to push instead of pull for ConsoleScreen.

    fun getOutputHistory() = outputHistory.toList()

    data class BackendSpawnResult(val backend: ScriptingBackend, val motd: String)

    fun spawnBackend(backendtype: ScriptingBackendType): BackendSpawnResult {
        val backend: ScriptingBackend = backendtype.metadata.new()
        scriptingBackends.add(backend)
        activeBackendIndex = scriptingBackends.lastIndex
        val motd = backend.motd()
        echo(motd)
        return BackendSpawnResult(backend, motd)
    }

    fun getIndexOfBackend(backend: ScriptingBackend): Int? {
        val index = scriptingBackends.indexOf(backend)
        return if (index >= 0)
            index
        else
            null
    }

    fun switchToBackend(index: Int) {
        scriptingBackends.enforceValidIndex(index)
        activeBackendIndex = index
    }

    fun switchToBackend(backend: ScriptingBackend) = switchToBackend(getIndexOfBackend(backend)!!)

    fun termBackend(index: Int): Exception? {
        scriptingBackends.enforceValidIndex(index)
        val result = scriptingBackends[index].terminate()
        if (result == null) {
            scriptingBackends.removeAt(index)
            if (index < activeBackendIndex) {
                activeBackendIndex -= 1
            }
            activeBackendIndex = scriptingBackends.clipIndexToBounds(activeBackendIndex)
        }
        return result
    }

    fun termBackend(backend: ScriptingBackend) = termBackend(getIndexOfBackend(backend)!!)

    fun hasBackend(): Boolean {
        return scriptingBackends.isNotEmpty()
    }

    fun echo(text: String) {
        outputHistory.add(text)
        consoleScreenListener?.invoke(text)
        while (outputHistory.size > maxOutputHistory) {
            outputHistory.removeAt(0)
            // If these are ArrayLists, performance will probably be O(n) relative to maxOutputHistory.
            // But premature optimization would be bad.
        }
    }

    fun autocomplete(command: String, cursorPos: Int? = null): AutocompleteResults {
        // Deliberately not calling echo() to add into history because I consider autocompletion a protocol/API/UI level feature
        if (!(hasBackend())) {
            return AutocompleteResults()
        }
        return activeBackend.autocomplete(command, cursorPos)
    }

    fun navigateHistory(increment: Int): String {
        activeCommandHistory = commandHistory.clipIndexToBounds(activeCommandHistory + increment, extendEnd = 1)
        if (activeCommandHistory <= 0) {
            return ""
        } else {
            return commandHistory[commandHistory.size - activeCommandHistory]
        }
    }

    private val runLock = Semaphore(1, true)
    var runningName: String? = null
        private set

    // @throws IllegalStateException On failure to acquire scripting lock.
    fun exec(
        command: String,
        asName: String? = null,
        withParams: Map<String, Any?>? = null,
        allowWait: Boolean = false
    ): ExecResult {
        // TODO: Synchronize here instead of in ScriptingRunLock and ScriptingRunThreader.
        if (ScriptingDebugParameters.printCommandsForDebug) {
            println("Running: $command")
        }
        if (UncivGame.Current.screen is WorldScreen
            && UncivGame.Current.isGameInfoInitialized()
            && UncivGame.Current.gameInfo.gameParameters.isOnlineMultiplayer
        ) { // TODO: After leaving game?
            ToastPopup("Scripting not allowed in online multiplayer.", UncivGame.Current.screen as BaseScreen) // TODO: Translation.
            return ExecResult("", true)
        }
        val backend = activeBackend
        val name = asName ?: makeScriptingRunName(this::class.simpleName, backend)
//        val releaseKey = ScriptingRunLock.acquire(name)
        // Lock acquisition failure gets propagated as thrown Exception, rather than as return. E.G.: Lets lambdas (from modApiHelpers) fail and trigger their own error handling (exposing misbehaving mods to the user via popup).
        // isException in ExecResult return value means exception in completely opaque scripting backend. Kotlin exception should still be thrown and propagated like normal.
        try {
            if (allowWait) {
                runLock.acquire()
            } else {
                if (!runLock.tryAcquire()) {
                    throw IllegalStateException()
                }
            }
            runningName = name
            ScriptingScope.apiExecutionContext.apply {
                handlerParameters = withParams?.toMap()
                // Looking at the source code, some .to<Collection>() extensions actually return mutable instances, and just type the return.
                // That means that scripts, and the heavy casting in Reflection.kt, might actually be able to modify them. So make a copy before every script run.
                scriptingBackend = backend
            }
            if (command.isNotEmpty()) {
                if (command != commandHistory.lastOrNull())
                    commandHistory.add(command)
                while (commandHistory.size > maxCommandHistory) {
                    commandHistory.removeAt(0)
                    // No need to restrict activeCommandHistory to valid indices here because it gets set to zero anyway.
                    // Also probably O(n) to remove from start..
                }
            }
            activeCommandHistory = 0
            var out = if (hasBackend())
                    backend.exec(command)
                else
                    ExecResult("${this::class.simpleName} has no backends.", true)
            echo(out.resultPrint)
            return out
        } finally {
            ScriptingScope.apiExecutionContext.apply {
                handlerParameters = null
                scriptingBackend = null
            }
            runningName = null
            runLock.release()
//            ScriptingRunLock.release(releaseKey)
        }
    }

    fun exec(
        command: String,
        asName: String? = null,
        withParams: Map<String, Any?>? = null,
        allowWait: Boolean = false,
        withBackend: ScriptingBackend
    ): ExecResult {
        switchToBackend(withBackend)
        return exec(
            command = command,
            asName = asName,
            withParams = withParams,
            allowWait = allowWait
        )
    }
}

// UI locking can honestly probably go into the mod script dispatcher thingy.
//        ScriptingScope.worldScreen?.isPlayersTurn = false
//Hm. Should return to original value, not necessarily true. That means keeping a property, which means I'd rather put this in its own class.
//Not perfect. I think ScriptingScope also exposes mutating the GUI itself, and many things that aren't protected by this? Then again, a script that *wants* to cause a crash/ANR will always be able to do so by just assigning an invalid value or deleting a required node somewhere. Could make mod handlers outside of worldScreen blocking, with written stipulations on (dis)recommended size, and then
//https://github.com/yairm210/Unciv/pull/5592/commits/a1f51e08ab782ab46bda220e0c4aaae2e8ba21a4
