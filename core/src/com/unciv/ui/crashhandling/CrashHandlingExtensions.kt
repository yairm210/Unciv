package com.unciv.ui.crashhandling

import com.unciv.UncivGame
import com.unciv.utils.Concurrency


/**
 * Returns a wrapped version of a function that automatically handles an uncaught exception or error. In case of an uncaught exception or error, the return will be null.
 *
 * [com.unciv.ui.screens.basescreen.UncivStage], [UncivGame.render] and [Concurrency] already use this to wrap nearly everything that can happen during the lifespan of the Unciv application.
 * Therefore, it usually shouldn't be necessary to manually use this.
 */
fun <R> (() -> R).wrapCrashHandling(
): () -> R?
        = {
    try {
        this()
    } catch (e: Throwable) {
        UncivGame.handleUncaughtThrowable(e)
        null
    }
}

/**
 * Returns a wrapped version of a function that automatically handles an uncaught exception or error.
 *
 * [com.unciv.ui.screens.basescreen.UncivStage], [UncivGame.render] and [Concurrency] already use this to wrap nearly everything that can happen during the lifespan of the Unciv application.
 * Therefore, it usually shouldn't be necessary to manually use this.
 */
fun (() -> Unit).wrapCrashHandlingUnit(): () -> Unit {
    val wrappedReturning = this.wrapCrashHandling()
    // Don't instantiate a new lambda every time the return get called.
    return { wrappedReturning() ?: Unit }
}
