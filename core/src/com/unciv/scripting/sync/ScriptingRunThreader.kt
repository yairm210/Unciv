package com.unciv.scripting.sync

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.scripting.utils.ScriptingDebugParameters
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

// Utility to run multiple actions related to scripting in separate threads, but still sequentially.
object ScriptingRunThreader {
    private val runLock = Semaphore(1, true)
    //// Should only ever be called from main thread actually, so fairness shouldn't matter. Nevermind. // Switched from AtomicBoolean to ReentrantLock, and now going to switch to a semaphore of some kind.
    private val runQueue = ConcurrentLinkedDeque<() -> Unit>()
    @Synchronized fun queueRun(toRun: () -> Unit) {
        if (ScriptingDebugParameters.printThreadingStatus) {
            println("Add $toRun to script thread queue.")
        }
        runQueue.add(toRun)
    }
    @Synchronized fun queueRuns(runs: Iterable<() -> Unit>) {
        for (run in runs) queueRun(run)
    }
    @Synchronized fun queueRuns(runs: Sequence<() -> Unit>) {
        for (run in runs) queueRun(run)
    }
    private fun doNextRunRecursive() {
        val run: (() -> Unit)? = runQueue.poll()
        if (run != null) {
            if (ScriptingDebugParameters.printThreadingStatus) {
                println("Continuing to consume script thread queue.")
            }
            thread {
                try {
                    run()
                } finally {
                    Gdx.app.postRunnable(::doNextRunRecursive)
                }
            }
        } else {
            if (ScriptingDebugParameters.printThreadingStatus) {
                println("Finished consuming script thread queue.")
            }
            runLock.release()
        }
    }
    @Synchronized fun doRuns() {
        runLock.acquire()
        if (Thread.currentThread() == UncivGame.MainThread) {
            println("WARNING: Calling ${this::class.simpleName}.doRuns() from the main thread. I think using a short-lived worker thread for this may be more durable in terms of avoiding thread-locking bugs.")
        }
        if (ScriptingDebugParameters.printThreadingStatus) {
            println("Starting to consume script thread queue.")
        }
        doNextRunRecursive()
    }
}

// When run the given function toRun, using the given concurrentRunner to run it, and block until it's done.

// @throws IllegalStateException If already on the main thread.
fun blockingConcurrentRun(concurrentRunner: (Runnable) -> Unit, toRun: () -> Unit) {
    if (concurrentRunner == Gdx.app::postRunnable
        && Thread.currentThread() == UncivGame.MainThread) {
        throw IllegalStateException("Tried to use lockingConcurrentRun to target the main thread with Gdx.app.postRunnable, while already on the main thread.")
    }
    val lock = Semaphore(1)
    lock.acquire()
    concurrentRunner {
        toRun()
        lock.release()
    }
    lock.acquire()
}


// Callable that takes a function toRun, a concurrentRunner that will execute it outside of the current thread, and, when called, returns the result of its execution in the foreign thread.

// @property concurrentRunner First-order function that runs its argument concurrently (but with access to the same memory).
// @property toRun Function with a return result.
// @returns Result of the function toRun after it's run concurrently by the concurrentRunner.
class BlockingConcurrentRunReturn<R>(val concurrentRunner: (Runnable) -> Unit, val toRun: () -> R) {
    var result: R? = null
    fun invoke(): R {
        blockingConcurrentRun(concurrentRunner) {
            result = toRun()
        }
        return result as R
    }
}


