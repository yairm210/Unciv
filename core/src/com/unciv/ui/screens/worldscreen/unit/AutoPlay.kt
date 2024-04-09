package com.unciv.ui.screens.worldscreen.unit

import com.unciv.models.metadata.GameSettings

class AutoPlay(private var autoPlaySettings: GameSettings.GameSettingsAutoPlay) {
    var turnsToAutoPlay: Int = 0
    var autoPlayTurnInProgress: Boolean = false

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

    fun isAutoPlaying(): Boolean = turnsToAutoPlay > 0 || autoPlayTurnInProgress

    fun fullAutoPlayAI(): Boolean = isAutoPlaying() && autoPlaySettings.fullAutoPlayAI

    fun shouldContinueAutoPlaying(): Boolean = isAutoPlaying() && !autoPlayTurnInProgress && turnsToAutoPlay > 0
}

