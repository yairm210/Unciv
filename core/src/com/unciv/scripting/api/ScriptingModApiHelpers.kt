package com.unciv.scripting.api

import com.unciv.scripting.reflection.Reflection

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
    // var handlerContext: NamedTuple?
    // Why not just use a map? String keys will be clearer in scripts than integers anyway.
    // Collection that gets replaced with any contextual parameters when running script handlers. E.G. Unit moved, city founded, tech researched, construction finished.
    // TODO: Mods blacklist, for security threats.
    //fun lambdifyExecScript(code: String ): () -> Unit = fun(){ scriptingScope.uncivGame!!.scriptingState.exec(code) } // FIXME: Requires awareness of which scriptingState and which backend to use.
    //Directly invoking the resulting lambda from a running script will almost certainly break the REPL loop/IPC protocol.
    //setTimeout?
    fun lambdifyReadPathcode(instance: Any?, pathcode: String): () -> Any? {
        val path = Reflection.parseKotlinPath(pathcode)
        return LambdaWrapper0 { Reflection.resolveInstancePath(instance, path) }
    }
    fun lambdifyAssignPathcode(instance: Any?, pathcode: String, value: Any?): () -> Unit {
        val path = Reflection.parseKotlinPath(pathcode)
        return LambdaWrapper0 { Reflection.setInstancePath(instance, path, value) }
    }
    fun lambdifyAssignPathcode(instance: Any?, pathcode: String, value: () -> Any?): () -> Unit {
        val path = Reflection.parseKotlinPath(pathcode)
        val getter = value.unwrapped()
        return LambdaWrapper0 { Reflection.setInstancePath(instance, path, getter()) }
    }
//    fun lambdifyCallWithArgs() // Wouldn't it be easier to just use lambdifyExecScript? This is rapidly growing into its own, very verbose, functional programming language otherwise.
//    fun lambdifyCallWithDynamicArgs() // Okay, these aren't possible unless you type as Function, due to static arg counts, I think.
    fun <R> lambdifySuppressReturn(func: () -> R): () -> Unit {
        val lambda = func.unwrapped()
        return LambdaWrapper0 { lambda() }
    }
    fun <R> lambdifyIgnoreExceptions(func: () -> R): () -> R? {
        val lambda = func.unwrapped()
        return LambdaWrapper0<R?> {
            try {
                lambda()
            } catch (e: Exception) {
                println("Error in function from ${this::class.simpleName}.lambdifySilentFailure():\n${e.toString().prependIndent("\t")}")
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
