package com.unciv.scripting.api

import com.unciv.scripting.reflection.Reflection
import com.unciv.scripting.utils.ScriptingLock
import com.unciv.ui.utils.stringifyException

// Wrapper for a function that takes no arguments.
// @param func The function to wrap.
class LambdaWrapper0<R>(func: () -> R): () -> R {
    // Kotlin reflection has difficulties with the anonymous classes used for Lambdas, and this seems easier than trying to figure out why and cleaner than trying to work through it.
    val lambda: () -> R = func.unwrapped()
    override fun invoke() = lambda()
}

// Extension function to strip a zero-argument function of a wrapping LambdaWrapper0 if present.
fun <R> (() -> R).unwrapped() = if (this is LambdaWrapper0) this.lambda else this


class ScriptingModApiHelpers(val scriptingScope: ScriptingScope) {
    //fun lambdifyExecScript(code: String ): () -> Unit = fun(){ scriptingScope.uncivGame!!.scriptingState.exec(code) } // FIXME: Requires awareness of which scriptingState and which backend to use.
    //Directly invoking the resulting lambda from a running script will almost certainly break the REPL loop/IPC protocol.
    //setTimeout? // Probably don't implement this. But see ToastPopup.startTimer() if you do.
    fun lambdifyReadPathcode(instance: Any?, pathcode: String): () -> Any? {
        val path = Reflection.parseKotlinPath(pathcode)
        return LambdaWrapper0 { Reflection.resolveInstancePath(instance ?: scriptingScope, path) }
    }
    fun lambdifyAssignPathcode(instance: Any?, pathcode: String, value: Any?): () -> Unit {
        val path = Reflection.parseKotlinPath(pathcode)
        return LambdaWrapper0 { Reflection.setInstancePath(instance ?: scriptingScope, path, value) }
    }
    fun lambdifyAssignPathcode(instance: Any?, pathcode: String, value: () -> Any?): () -> Unit {
        val path = Reflection.parseKotlinPath(pathcode)
        val getter = value.unwrapped()
        return LambdaWrapper0 { Reflection.setInstancePath(instance ?: scriptingScope, path, getter()) }
    }
//    fun lambdifyCallWithArgs() // Wouldn't it be easier to just use lambdifyExecScript? This is rapidly growing into its own, very verbose, functional programming language otherwise.
//    fun lambdifyCallWithDynamicArgs() // Okay, these aren't possible unless you type as Function, due to static arg counts, I think.
    fun <R> lambdifySuppressReturn(func: () -> R): () -> Unit {
        val lambda = func.unwrapped()
        return LambdaWrapper0 { lambda() }
    }
    fun <R> lambdifyIgnoreExceptions(func: () -> R): () -> R? { // TODO: Probably implicitly do this for all lambdas from here. The host program doesn't have to make it easy for scripts to crash it.
        val lambda = func.unwrapped()
        return LambdaWrapper0<R?> {
            try {
                lambda()
            } catch (e: Exception) {
                ScriptingLock.notifyPlayerScriptFailure(e)
//                println("Error in function from ${this::class.simpleName}.lambdifySilentFailure():\n${e.stringifyException().prependIndent("\t")}") //// TODO: Toast.
                // Really these should all go to STDERR.
                null
            }
        }
    }
    fun lambdifyCombine(funcs: List<() -> Any?>): () -> Unit {
        val lambdas = funcs.map { it.unwrapped() }
        return LambdaWrapper0 { for (f in lambdas) { f() } }
    }
    //fun lambdifyReduce // Not sure about this, since so far the model for script-created lambdas makes sense only for side effects, and not for return values.
}
