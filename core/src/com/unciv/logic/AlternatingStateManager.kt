package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.unciv.utils.Concurrency
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AlternatingStateManager(
    private val name: String,
    private val onOriginalState: () -> Unit,
    private val onAlternateState: () -> Unit
) {
    private var job: Job? = null

    fun start(
        duration: Duration = 5.seconds, interval: Duration = 500.milliseconds
    ) {
        job?.cancel()
        job = Concurrency.run(name) {
            var isAlternateState = true
            val times = (duration / interval).toInt()
            repeat(times) {
                if (isAlternateState) {
                    Gdx.app.postRunnable(onAlternateState)
                    isAlternateState = false
                } else {
                    Gdx.app.postRunnable(onOriginalState)
                    isAlternateState = true
                }
                delay(interval)
            }

            // revert to original state before exiting if it isn't currently
            if (!isAlternateState) {
                Gdx.app.postRunnable(onOriginalState)
            }
        }
    }

    fun stop() {
        job?.cancel()
        Gdx.app.postRunnable(onOriginalState)
    }
}
