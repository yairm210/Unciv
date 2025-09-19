package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.unciv.utils.Concurrency
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class AlternatingStateManager(
    private val name: String,
    private val originalState: () -> Unit,
    private val alternateState: () -> Unit
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
                    Gdx.app.postRunnable(alternateState)
                    isAlternateState = false
                } else {
                    Gdx.app.postRunnable(originalState)
                    isAlternateState = true
                }

                if (Clock.System.now() - startTime >= duration) {
                    Gdx.app.postRunnable(originalState)
                    return@run
                }

                delay(interval)
            }
        }
    }

    fun stop() {
        job?.cancel()
        Gdx.app.postRunnable(originalState)
    }
}
