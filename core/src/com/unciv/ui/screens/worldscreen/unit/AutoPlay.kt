package com.unciv.ui.screens.worldscreen.unit

import com.unciv.models.metadata.GameSettings
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import kotlinx.coroutines.Job

class AutoPlay(private var autoPlaySettings: GameSettings.GameSettingsAutoPlay) {
    var turnsToAutoPlay: Int = 0
    var autoPlayTurnInProgress: Boolean = false
    var autoPlayJob: Job? = null

    fun startMultiturnAutoPlay() {
        autoPlayTurnInProgress = false
        turnsToAutoPlay = autoPlaySettings.autoPlayMaxTurns
    }

    /**
     * Processes the end of the user's turn being AutoPlayed
     */
    fun endTurnMultiturnAutoPlay() {
        if (!autoPlaySettings.autoPlayUntilEnd)
            turnsToAutoPlay--
    }

    fun stopAutoPlay() {
        turnsToAutoPlay = 0
        autoPlayTurnInProgress = false
    }

    /**
     * Does the provided job on a new thread if there isn't already an AutoPlay thread running.
     * Will set autoPlayTurnInProgress to true for the duration of the job.
     *
     * @param setPlayerTurnAfterEnd keep this as the default (true) if it will still be the viewing player's turn after the job is finished.
     * Set it to false if the turn will end.
     * @throws IllegalStateException if an AutoPlay job is currently running as this is called.
     */
    fun runAutoPlayJobInNewThread(jobName: String, worldScreen: WorldScreen, setPlayerTurnAfterEnd: Boolean = true, job: () -> Unit) {
        if (autoPlayTurnInProgress) throw IllegalStateException("Trying to start an AutoPlay job while a job is currently running")
        autoPlayTurnInProgress = true
        worldScreen.isPlayersTurn = false
        autoPlayJob = Concurrency.runOnNonDaemonThreadPool(jobName) {
            job()
            autoPlayTurnInProgress = false
            if (setPlayerTurnAfterEnd)
                 worldScreen.isPlayersTurn = true
        }
    }

    fun isAutoPlaying(): Boolean = turnsToAutoPlay > 0 || autoPlayTurnInProgress

    fun fullAutoPlayAI(): Boolean = isAutoPlaying() && autoPlaySettings.fullAutoPlayAI

    fun shouldContinueAutoPlaying(): Boolean =
        isAutoPlaying() && !autoPlayTurnInProgress && turnsToAutoPlay > 0
}

