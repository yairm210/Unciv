package com.unciv.models.modscripting

import com.badlogic.gdx.Gdx
import com.unciv.scripting.ExecResult
import com.unciv.scripting.ScriptingBackend
import com.unciv.scripting.ScriptingState
import com.unciv.scripting.utils.ScriptingErrorHandling
import com.unciv.scripting.sync.ScriptingRunThreader
import com.unciv.scripting.sync.makeScriptingRunName
import java.lang.IllegalStateException


data class RegisteredHandler(val backend: ScriptingBackend, val code: String, val mod: ScriptedMod?)

// For organizing and running script handlerTypes during gameplay.
// Uses ModScriptingHandlerTypes.
object ModScriptingRunManager {

    private val registeredHandlers: Map<HandlerType, Collection<RegisteredHandler>> =
        ModScriptingHandlerTypes.all.asSequence()
            .map { it.handlerTypes }
            .flatten()
            .associateWith { HashSet<RegisteredHandler>() }

    fun getRegisteredForHandler(handlerType: HandlerType) = registeredHandlers[handlerType]!!

    fun lambdaRegisteredHandlerRunner(registeredHandler: RegisteredHandler, withParams: Params): () -> Unit {
        val params = HashMap(withParams)
        val name = makeScriptingRunName(registeredHandler.mod?.name, registeredHandler.backend)
        return fun() {
            val execResult: ExecResult?
            try{
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
    }

    fun runHandler(handlerType: HandlerType, baseParams: Params?, after: () -> Unit = {}) {
        val registeredHandlers = getRegisteredForHandler(handlerType)
        if (registeredHandlers.isNotEmpty()) {
            val params = handlerType.paramGetter(baseParams)
            if (!handlerType.checkParamsValid(params)) {
                throw IllegalStateException(
                    """
                    Incorrect parameter signature for running mod script handlerTypes:
                        handlerType = ${handlerType.name}
                        handlerType.paramTypes = ${handlerType.paramTypes}
                        baseParams = $baseParams
                        params = $params
                    """.trimIndent()
                )
            }
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


