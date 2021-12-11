package com.unciv.models.modscripting

import com.badlogic.gdx.Gdx
import com.unciv.scripting.ExecResult
import com.unciv.scripting.ScriptingBackend
import com.unciv.scripting.ScriptingState
import com.unciv.scripting.utils.ScriptingErrorHandling
import com.unciv.scripting.sync.ScriptingRunThreader
import com.unciv.scripting.sync.makeScriptingRunName


data class RegisteredHandler(val backend: ScriptingBackend, val code: String, val mod: ScriptedMod?)

// For organizing and running script handlers during gameplay.
// Uses ModScriptingHandlerTypes.
object ModScriptingRunManager {

    private val registeredHandlers: Map<Handler, Collection<RegisteredHandler>> =
        ModScriptingHandlerTypes.all.asSequence()
            .map { it.handlers }
            .flatten()
            .associateWith { HashSet<RegisteredHandler>() }

    fun getRegisteredForHandler(handler: Handler) = registeredHandlers[handler]!!

    fun lambdaRegisteredHandlerRunner(registeredHandler: RegisteredHandler, withParams: Params): () -> Unit {
        // Looking at the source code, some .to<Collection>() extensions actually return mutable instances, and just type the return.
        // That means that scripts, and the heavy casting in Reflection.kt, might actually be able to modify them. So make a copy before every script run.
        val params = HashMap(withParams)
        val name = makeScriptingRunName(registeredHandler.mod?.name, registeredHandler.backend)
        return fun() {
            val execResult: ExecResult?
            try{
                execResult = ScriptingState.exec(
                    command = registeredHandler.code,
                    asName = name,
                    withParams = params,
                    withBackend = registeredHandler.backend
                )
            } catch(e: Throwable) {
                ScriptingErrorHandling.notifyPlayerScriptFailure(exception = e, asName = name)
                return
            }
            if (execResult.isException) {
                ScriptingErrorHandling.notifyPlayerScriptFailure(text = execResult.resultPrint, asName = name)
            }
        }
    }

    fun runHandler(handler: Handler, baseParams: Params?) { // Not sure how to keep caller from advancing… (I wonder if qualified return syntax
        val registeredHandlers = getRegisteredForHandler(handler)
        if (registeredHandlers.isNotEmpty()) {
            val params = handler.paramGetter(baseParams)
            ScriptingRunThreader.queueRuns(
                registeredHandlers.asSequence().map { lambdaRegisteredHandlerRunner(it, params) }
            )
            lockGame()
            ScriptingRunThreader.queueRun { Gdx.app.postRunnable(::unlockGame) }
            ScriptingRunThreader.doRuns()
        }
    }

    private fun lockGame() {}
    private fun unlockGame() {}
}


