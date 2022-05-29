package com.unciv.ui.crashhandling

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
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

private fun getCoroutineContext(runAsDaemon: Boolean): CoroutineScope {
    return if (runAsDaemon) CRASH_HANDLING_DAEMON_SCOPE else CRASH_HANDLING_SCOPE
}
/**
 * Returns a wrapped version of a function that safely crashes the game to [CrashScreen] if an exception or error is thrown.
 *
 * In case an exception or error is thrown, the return will be null. Therefore the return type is always nullable.
 *
 * The game loop, threading, and event systems already use this to wrap nearly everything that can happen during the lifespan of the Unciv application.
 *
 * Therefore, it usually shouldn't be necessary to manually use this. See the note at the top of [CrashScreen].kt for details.
 *
 * @param postToMainThread Whether the [CrashScreen] should be opened by posting a runnable to the main thread, instead of directly. Set this to true if the function is going to run on any thread other than the main loop.
 * @return Result from the function, or null if an exception is thrown.
 * */
fun <R> (() -> R).wrapCrashHandling(
    postToMainThread: Boolean = false
): () -> R?
    = {
        try {
            this()
        } catch (e: Throwable) {
            if (postToMainThread) {
                Gdx.app.postRunnable {
                    UncivGame.Current.setScreen(CrashScreen(e))
                }
            } else UncivGame.Current.setScreen(CrashScreen(e))
            null
        }
    }
/**
 * Returns a wrapped a version of a Unit-returning function which safely crashes the game to [CrashScreen] if an exception or error is thrown.
 *
 * The game loop, threading, and event systems already use this to wrap nearly everything that can happen during the lifespan of the Unciv application.
 *
 * Therefore, it usually shouldn't be necessary to manually use this. See the note at the top of [CrashScreen].kt for details.
 *
 * @param postToMainThread Whether the [CrashScreen] should be opened by posting a runnable to the main thread, instead of directly. Set this to true if the function is going to run on any thread other than the main loop.
 * */
fun (() -> Unit).wrapCrashHandlingUnit(
    postToMainThread: Boolean = false
): () -> Unit {
    val wrappedReturning = this.wrapCrashHandling(postToMainThread)
    // Don't instantiate a new lambda every time the return get called.
    return { wrappedReturning() ?: Unit }
}
