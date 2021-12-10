package com.unciv.scripting.api

import com.unciv.scripting.ScriptingState
import com.unciv.scripting.reflection.Reflection
import com.unciv.scripting.utils.ScriptingBackendException
import com.unciv.scripting.utils.ScriptingErrorHandling
import com.unciv.scripting.utils.ScriptingRunLock

// Wrapper for a function that takes no arguments.
//
// Presents as a reflection-friendly invokable instance
// Catches all thrown Exceptions and shows error dialogs instead.
//
// @param asName Textual identifier for this function to use in error messages.
// @param func The function to wrap.
class LambdaWrapper0<R>(asName: String? = null, func: () -> R?): () -> R? { // Making private messes with calling reflectively.
    val lambda: () -> R? = func.unwrapped()
    val suppressing: () -> R? = lambda.reportExceptionsAsScriptErrors(asName)
    override fun invoke() = suppressing()
}

// Extension function to strip a zero-argument function of a wrapping LambdaWrapper0 if present.
private fun <R> (() -> R?).unwrapped() = if (this is LambdaWrapper0) this.lambda else this

// Extension function to wrap a zero-argument function to suppress all exceptions, instead returning null and notifying the player.
private fun <R> (() -> R).reportExceptionsAsScriptErrors(asName: String? = null): () -> R? {
    return {
        try {
            this()
        } catch (e: Exception) {
            ScriptingErrorHandling.notifyPlayerScriptFailure(e, asName = asName)
            null
        }
    }
}

private val alphanumeric = ('A'..'Z') + ('a'..'z') + ('0'..'9')
private fun (String?).lambdaName() = "${this}+λ${(1..3).map { alphanumeric.random() }.joinToString("")}"

object ScriptingModApiHelpers {
    fun lambdifyExecScript(code: String): () -> Unit? {
        val backend = ScriptingScope.apiExecutionContext.scriptingBackend!!
        val name = ScriptingRunLock.runningName.lambdaName()
        return LambdaWrapper0(name) {
            val execResult = ScriptingState.exec(
                command = code,
                asName = name,
                withBackend = backend
            )
            if (execResult.isException) {
                throw ScriptingBackendException(execResult.resultPrint)
            } // Thrown exception will be caught by reportExceptionsAsScriptErrors, and show error dialog.
            Unit
        }
    }
    //setTimeout? // Probably don't implement this. But see ToastPopup.startTimer() if you do.
    fun lambdifyReadPathcode(instance: Any?, pathcode: String): () -> Any? {
        val path = Reflection.parseKotlinPath(pathcode)
        return LambdaWrapper0(ScriptingRunLock.runningName.lambdaName()) { Reflection.resolveInstancePath(instance ?: ScriptingScope, path) }
    }
    fun lambdifyAssignPathcode(instance: Any?, pathcode: String, value: Any?): () -> Unit? {
        val path = Reflection.parseKotlinPath(pathcode)
        return LambdaWrapper0(ScriptingRunLock.runningName.lambdaName()) { Reflection.setInstancePath(instance ?: ScriptingScope, path, value) }
    }
    fun lambdifyAssignPathcode(instance: Any?, pathcode: String, value: () -> Any?): () -> Unit? {
        val path = Reflection.parseKotlinPath(pathcode)
        val getter = value.unwrapped()
        return LambdaWrapper0(ScriptingRunLock.runningName.lambdaName()) { Reflection.setInstancePath(instance ?: ScriptingScope, path, getter()) }
    }
    fun <R> lambdifySuppressReturn(func: () -> R): () -> Unit? {
        val lambda = func.unwrapped()
        return LambdaWrapper0(ScriptingRunLock.runningName.lambdaName()) { lambda(); null }
    }
    fun lambdifyCombine(funcs: List<() -> Any?>): () -> Unit? {
        val lambdas = funcs.map { it.unwrapped() }
        return LambdaWrapper0(ScriptingRunLock.runningName.lambdaName()) { for (f in lambdas) { f() } }
    }
}
