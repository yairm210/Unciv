package com.unciv.logic

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Timer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AlternatingStateManager(
    private val name: String,
    private val onOriginalState: () -> Unit,
    private val onAlternateState: () -> Unit,
) {
    private var task: Timer.Task? = null

    fun start(
        duration: Duration = 5.seconds,
        interval: Duration = 500.milliseconds,
    ) {
        task?.cancel()

        val repeats = (duration / interval).toInt().coerceAtLeast(0)
        if (repeats <= 0) {
            Gdx.app.postRunnable(onOriginalState)
            return
        }

        var isAlternateState = true
        var remaining = repeats

        val newTask = object : Timer.Task() {
            override fun run() {
                if (remaining <= 0) {
                    if (!isAlternateState) Gdx.app.postRunnable(onOriginalState)
                    cancel()
                    return
                }

                if (isAlternateState) {
                    Gdx.app.postRunnable(onAlternateState)
                } else {
                    Gdx.app.postRunnable(onOriginalState)
                }
                isAlternateState = !isAlternateState
                remaining--

                if (remaining <= 0 && !isAlternateState) {
                    Gdx.app.postRunnable(onOriginalState)
                }
            }
        }

        task = newTask
        Timer.schedule(newTask, 0f, interval.inWholeMilliseconds / 1000f)
    }

    fun stop() {
        task?.cancel()
        task = null
        Gdx.app.postRunnable(onOriginalState)
    }
}
