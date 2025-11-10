package com.unciv.ui.screens.worldscreen.unit

import com.unciv.models.metadata.GameSettings
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import kotlinx.coroutines.Job
import yairm210.purity.annotations.Readonly

class AutoPlay(private var autoPlaySettings: GameSettings.GameSettingsAutoPlay) {
    /**
     * How many turns we should multiturn AutoPlay for.
     * In the case that [autoPlaySettings].autoPlayUntilEnd is true, the value should not be decremented after each turn.
     */
    var turnsToAutoPlay: Int = 0

    /**
     * Determines whether or not we are currently processing the viewing player's turn.
     * This can be on the main thread or on a different thread.
     */
    var autoPlayTurnInProgress: Boolean = false
    var autoPlayJob: Job? = null

    fun startMultiturnAutoPlay() {
        autoPlayTurnInProgress = false
        turnsToAutoPlay = autoPlaySettings.autoPlayMaxTurns
    }

    /**
     * Processes the end of the user's turn being AutoPlayed.
     * Only decrements [turnsToAutoPlay] if [autoPlaySettings].autoPlayUntilEnd is false.
     */
    fun endTurnMultiturnAutoPlay() {
        if (!autoPlaySettings.autoPlayUntilEnd && turnsToAutoPlay > 0)
            turnsToAutoPlay--
    }

    /**
     * Stops multiturn AutoPlay and sets [autoPlayTurnInProgress] to false
     */
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

    @Readonly fun isAutoPlaying(): Boolean = turnsToAutoPlay > 0 || autoPlayTurnInProgress

    @Readonly fun isAutoPlayingAndFullAutoPlayAI(): Boolean = isAutoPlaying() && autoPlaySettings.fullAutoPlayAI

    /**
     * @return true if we should play at least 1 more turn and we are not currenlty processing any AutoPlay
     */
    @Readonly fun shouldContinueAutoPlaying(): Boolean = !autoPlayTurnInProgress && turnsToAutoPlay > 0
}

