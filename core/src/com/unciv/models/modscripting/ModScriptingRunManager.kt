package com.unciv.models.modscripting

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.scripting.ExecResult
import com.unciv.scripting.ScriptingBackend
import com.unciv.scripting.ScriptingState
import com.unciv.scripting.utils.ScriptingErrorHandling
import com.unciv.scripting.sync.ScriptingRunThreader
import com.unciv.scripting.sync.blockingConcurrentRun
import com.unciv.scripting.sync.makeScriptingRunName
import com.unciv.ui.utils.BaseScreen
import java.lang.IllegalArgumentException
import kotlin.concurrent.thread


data class RegisteredHandler(val backend: ScriptingBackend, val code: String, val modRules: ScriptedModRules?, val mainThread: Boolean = false)

// For organizing and running script handlerTypes during gameplay.
// Uses ModScriptingHandlerTypes.
object ModScriptingRunManager {

    private val registeredHandlers = ALL_HANDLER_TYPES.associateWith { mutableSetOf<RegisteredHandler>() } // Let's specify that the registration and functions must maintain the order, so mods can run multiple interacting commands predictably.

    fun registerHandler(handlerType: HandlerType, registeredHandler: RegisteredHandler) {
        if (ModScriptingDebugParameters.printHandlerRegister) {
            println("Registering $handlerType handler:\n${registeredHandler.toString().prependIndent("\t")}")
        }
        val registry = registeredHandlers[handlerType]!!
        if (registeredHandler in registry) {
            throw IllegalArgumentException("$registeredHandler is already registered for $handlerType!")
        }
        registry.add(registeredHandler)
    }

    fun unregisterHandler(handlerType: HandlerType, registeredHandler: RegisteredHandler) {
        if (ModScriptingDebugParameters.printHandlerRegister) {
            println("Unregistering $handlerType handler:\n${registeredHandler.toString().prependIndent("\t")}")
        }
        val registry = registeredHandlers[handlerType]!!
        if (registeredHandler !in registry) {
            throw IllegalArgumentException("$registeredHandler isn't registered for $handlerType!")
        }
        registry.remove(registeredHandler)
    }

    fun getRegisteredForHandler(handlerType: HandlerType) = registeredHandlers[handlerType]!!

    fun lambdaRegisteredHandlerRunner(registeredHandler: RegisteredHandler, withParams: Params): () -> Unit {
        val name = makeScriptingRunName(registeredHandler.modRules?.name, registeredHandler.backend)
        val nakedRunnable = fun() {
            val execResult: ExecResult?
            try {
                execResult = ScriptingState.exec(
                    command = registeredHandler.code,
                    asName = name,
                    withParams = withParams,
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
        return if (registeredHandler.mainThread) fun() {
            blockingConcurrentRun(Gdx.app::postRunnable, nakedRunnable)
            // Just running Gdx.app.postRunnable would bypass ScriptingRunThreader's sequential locking, I think. It still kinda works, because I put @Synchronized on a lot of things, but it seems too unpredictable/uncontrolled/unmaintainable to me, especially with lots of modded scripts.
        } else
            fun() { nakedRunnable() }
        // Both scripts and the error reporting mechanism for scripts can involve UI widgets, which means OpenGL calls, which means crashes unless they're on the main thread.
        // …I think the easiest way to avoid those while still letting scripts run in the background might be to let scripts specify a set of "background" and "foreground" commands per handler… Also the game will probably thread-lock if another run is attempted/waited for while the current one's been posted to and is waiting on the main thread, so I should move the lock acquisition to a worker thread too. —Except that postRunnable's done concurrently, so it might be fine?
    }

    fun runHandler(handlerType: HandlerType, baseParams: Any?, after: () -> Unit = {}) {
        val registeredHandlers = getRegisteredForHandler(handlerType)
        if (ModScriptingDebugParameters.printHandlerRun) {
            println("Running ${registeredHandlers.size} handlers for $handlerType with $baseParams.")
        }
        if (registeredHandlers.isNotEmpty()) {
            val params = handlerType.paramGetter(baseParams)
            if (ModScriptingDebugParameters.printHandlerRun) {
                println("\tFinal parameters:\n\t\t${params?.map { "${it.key} = ${it.value}" }?.joinToString("\n\t\t") }")
            }
//            if (!handlerType.checkParamsValid(params)) {
//                throw IllegalStateException(
//                    """
//                    Incorrect parameter signature for running mod script handlerTypes:
//                        handlerType = ${handlerType.name}
//                        handlerType.paramTypes = ${handlerType.paramTypes}
//                        baseParams = $baseParams
//                        params = $params
//                    """.trimIndent()
//                )
//            }
            ScriptingRunThreader.queueRuns(
                registeredHandlers.asSequence().map { lambdaRegisteredHandlerRunner(it, params) }
            )
            lockGame()
            ScriptingRunThreader.queueRun { Gdx.app.postRunnable(::unlockGame) }
            ScriptingRunThreader.queueRun { after() }
            thread { // A locking operation. I have the thought that calling from a short-lived thread will be more resilient to threadlocking in the long term. Consider: Main thread waits on the lock. But then releasing lock ends up getting posted to main thread, so lock won't be released ever, as main thread needs to finish waiting on it before it can execute the release runnable.
                ScriptingRunThreader.doRuns()
            }
        } else {
            after()
        }
    }

    private fun lockGame() {
        //isPlayersTurn.
        Gdx.input.inputProcessor = null
    }
    private fun unlockGame() {
        Gdx.input.inputProcessor = (UncivGame.Current.screen as BaseScreen).stage
    }
}


