package com.unciv.utils

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.ui.crashhandling.wrapCrashHandlingUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Runnable
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.time.Duration

object Concurrency {
    fun <T> runBlocking(
        name: String? = null,
        context: CoroutineContext = Dispatcher.NON_DAEMON,
        block: suspend CoroutineScope.() -> T,
    ): T? {
        var result: Result<T>? = null
        val scope = SimpleScope(addName(EmptyCoroutineContext, name))
        block.startCoroutine(scope, object : Continuation<T> {
            override val context: CoroutineContext = EmptyCoroutineContext
            override fun resumeWith(resumeResult: Result<T>) {
                result = resumeResult
            }
        })
        return result?.getOrElse {
            UncivGame.handleUncaughtThrowable(it)
            null
        }
    }

    fun parallelize(
        functions: List<() -> Unit>,
        parallelize: Boolean = true,
    ) {
        if (parallelize) {
            functions.forEach { function -> run { function() } }
        } else {
            functions.forEach { function -> function() }
        }
    }

    fun run(
        name: String? = null,
        scope: CoroutineScope = CoroutineScope(Dispatcher.DAEMON),
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        return scope.launchCrashHandling(scope.coroutineContext, name, block)
    }

    fun runOnNonDaemonThreadPool(name: String? = null, block: suspend CoroutineScope.() -> Unit) =
        run(name, CoroutineScope(Dispatcher.NON_DAEMON), block)

    fun runOnGLThread(name: String? = null, block: suspend CoroutineScope.() -> Unit) =
        run(name, CoroutineScope(Dispatcher.GL), block)

    fun stopThreadPools() {
        // No-op on web.
    }
}

fun CoroutineScope.launchCrashHandling(
    context: CoroutineContext,
    name: String? = null,
    block: suspend CoroutineScope.() -> Unit,
): Job {
    val scope = SimpleScope(addName(EmptyCoroutineContext, name))
    block.startCoroutine(scope, object : Continuation<Unit> {
        override val context: CoroutineContext = EmptyCoroutineContext
        override fun resumeWith(result: Result<Unit>) {
            val failure = result.exceptionOrNull()
            if (failure != null) UncivGame.handleUncaughtThrowable(failure)
        }
    })
    return NonCancellable
}

private fun addName(context: CoroutineContext, name: String?) =
    if (name != null) context + CoroutineName(name) else context

fun CoroutineScope.launchOnThreadPool(name: String? = null, block: suspend CoroutineScope.() -> Unit) =
    launchCrashHandling(Dispatcher.DAEMON, name, block)

fun CoroutineScope.launchOnNonDaemonThreadPool(name: String? = null, block: suspend CoroutineScope.() -> Unit) =
    launchCrashHandling(Dispatcher.NON_DAEMON, name, block)

fun CoroutineScope.launchOnGLThread(name: String? = null, block: suspend CoroutineScope.() -> Unit) =
    launchCrashHandling(Dispatcher.GL, name, block)

suspend fun <T> withThreadPoolContext(block: suspend CoroutineScope.() -> T): T =
    block(SimpleScope(Dispatcher.DAEMON))

suspend fun <T> withNonDaemonThreadPoolContext(block: suspend CoroutineScope.() -> T): T =
    block(SimpleScope(Dispatcher.NON_DAEMON))

suspend fun <T> withGLContext(block: suspend CoroutineScope.() -> T): T =
    block(SimpleScope(Dispatcher.GL))

suspend fun delayMillis(millis: Long) {
    // No-op on web phase 1.
}

suspend fun delayDuration(duration: Duration) {
    // No-op on web phase 1.
}

object Dispatcher {
    val DAEMON: CoroutineDispatcher = DirectDispatcher
    val NON_DAEMON: CoroutineDispatcher = DirectDispatcher
    val GL: CoroutineDispatcher = GLDispatcher

    fun stopThreadPools() {
        // No-op on web.
    }
}

private object DirectDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        block::run.wrapCrashHandlingUnit()()
    }
}

private object GLDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val app = Gdx.app
        if (app == null) {
            block::run.wrapCrashHandlingUnit()()
            return
        }
        app.postRunnable(block::run.wrapCrashHandlingUnit())
    }
}

private class SimpleScope(
    override val coroutineContext: CoroutineContext,
) : CoroutineScope
