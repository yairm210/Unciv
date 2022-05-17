package com.unciv.ui.crashhandling

import com.badlogic.gdx.Gdx
import com.unciv.ui.utils.wrapCrashHandlingUnit
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import kotlin.concurrent.thread

private val DAEMON_EXECUTOR = Executors.newCachedThreadPool(object : ThreadFactory {
    var n = 0
    override fun newThread(r: java.lang.Runnable): Thread =
        crashHandlingThread(name = "crash-handling-daemon-${n++}", start = false, isDaemon = true, block = r::run)
}).asCoroutineDispatcher()
/**
 * Coroutine Scope that runs coroutines in separate daemon threads.
 *
 * Brings the main game loop to a [com.unciv.CrashScreen] if an exception happens.
 */
val CRASH_HANDLING_DAEMON_SCOPE = CoroutineScope(DAEMON_EXECUTOR)

private val EXECUTOR = Executors.newCachedThreadPool(object : ThreadFactory {
    var n = 0
    override fun newThread(r: java.lang.Runnable): Thread =
        crashHandlingThread(name = "crash-handling-${n++}", start = false, isDaemon = false, block = r::run)
}).asCoroutineDispatcher()
/**
 * Coroutine Scope that runs coroutines in separate threads that are not started as daemons.
 *
 * Brings the main game loop to a [com.unciv.CrashScreen] if an exception happens.
 */
val CRASH_HANDLING_SCOPE = CoroutineScope(EXECUTOR)

/**
 * Must be called only in [com.unciv.UncivGame.dispose] to not have any threads running that prevent JVM shutdown.
 */
fun closeExecutors() {
    EXECUTOR.close()
    DAEMON_EXECUTOR.close()
}

/** Wrapped version of [kotlin.concurrent.thread], that brings the main game loop to a [com.unciv.CrashScreen] if an exception happens. */
private fun crashHandlingThread(
    start: Boolean = true,
    isDaemon: Boolean = false,
    contextClassLoader: ClassLoader? = null,
    name: String? = null,
    priority: Int = -1,
    block: () -> Unit
) = thread(
        start = start,
        isDaemon = isDaemon,
        contextClassLoader = contextClassLoader,
        name = name,
        priority = priority,
        block = block.wrapCrashHandlingUnit(true)
    )

/** Wrapped version of Gdx.app.postRunnable ([com.badlogic.gdx.Application.postRunnable]), that brings the game loop to a [com.unciv.CrashScreen] if an exception occurs. */
fun postCrashHandlingRunnable(runnable: () -> Unit) {
    Gdx.app.postRunnable(runnable.wrapCrashHandlingUnit())
}

/**
 * [launch]es a new coroutine that brings the game loop to a [com.unciv.CrashScreen] if an exception occurs.
 * @see crashHandlingThread
 */
fun launchCrashHandling(name: String, runAsDaemon: Boolean = true,
                        flowBlock: suspend CoroutineScope.() -> Unit): Job {
    return getCoroutineContext(runAsDaemon).launch(CoroutineName(name)) { flowBlock(this) }
}
/**
 * Uses [async] to return a result from a new coroutine that brings the game loop to a [com.unciv.CrashScreen] if an exception occurs.
 * @see crashHandlingThread
 */
fun <T> asyncCrashHandling(name: String, runAsDaemon: Boolean = true,
                           flowBlock: suspend CoroutineScope.() -> T): Deferred<T> {
    return getCoroutineContext(runAsDaemon).async(CoroutineName(name)) { flowBlock(this) }
}

private fun getCoroutineContext(runAsDaemon: Boolean): CoroutineScope {
    return if (runAsDaemon) CRASH_HANDLING_DAEMON_SCOPE else CRASH_HANDLING_SCOPE
}