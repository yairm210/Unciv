package com.unciv.scripting.sync

import com.badlogic.gdx.Gdx
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

// Utility to run multiple actions related to scripting in separate threads, sequentially.
object ScriptingRunThreader {
    private val isDoingRuns = AtomicBoolean(false)
    @Suppress("NewApi") // FIXME: Bump API level up.
    private var runQueue = ConcurrentLinkedDeque<() -> Unit>()
    fun queueRun(toRun: () -> Unit) {
        runQueue.add(toRun)
    }
    fun queueRuns(runs: Iterable<() -> Unit>) {
        for (run in runs) runQueue.add(run)
    }
    fun queueRuns(runs: Sequence<() -> Unit>) {
        for (run in runs) runQueue.add(run)
    }
    @Synchronized fun doRuns(begin: Boolean = true) {
        if (begin) {
            if (!isDoingRuns.compareAndSet(false, true)) {
                throw IllegalStateException("${this::class.simpleName} is already consuming its queued runs!")
            }
        }
        val run: (() -> Unit)? = runQueue.poll()
        if (run != null) {
            thread {
                try {
                    run()
                } finally {
                    Gdx.app.postRunnable { doRuns(false) }
                }
            }
        } else {
            isDoingRuns.set(false)
        }
    }
}

