package com.unciv.logic

import com.unciv.utils.Concurrency
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class AlternatingStateManager<State>(
    private val name: String,
    private val state: State,
    private val originalState: (State) -> Unit,
    private val alternateState: (State) -> Unit
) {
    private var job: Job? = null

    @OptIn(ExperimentalTime::class)
    fun start(
        duration: Duration = 5.seconds, interval: Duration = 500.milliseconds
    ) {
        job?.cancel()
        job = Concurrency.run(name) {
            val startTime = Clock.System.now()

            var isAlternateState = true
            while (true) {
                if (isAlternateState) {
                    alternateState(state)
                    isAlternateState = false
                } else {
                    originalState(state)
                    isAlternateState = true
                }

                if (Clock.System.now() - startTime >= duration) {
                    originalState(state)
                    return@run
                }

                delay(interval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        originalState(state)
    }
}
