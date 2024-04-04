package com.unciv.ui.screens.worldscreen.unit

import com.unciv.models.metadata.GameSettings

class AutoPlay(private var autoPlaySettings: GameSettings.GameSettingsAutoPlay) {
    var turnsToAutoPlay: Int = 0
    var autoPlaying: Boolean = false
    var autoPlayTurnInProgress: Boolean = false

    fun startAutoPlay() {
        autoPlaying = true
        turnsToAutoPlay = autoPlaySettings.autoPlayMaxTurns
    }

    fun stopAutoPlay() {
        autoPlaying = false
        turnsToAutoPlay = 0
        autoPlayTurnInProgress = false
    }

    fun isAutoPlaying(): Boolean = autoPlaying

    fun fullAutoPlayAI(): Boolean = isAutoPlaying() && autoPlaySettings.fullAutoPlayAI

    fun shouldContinueAutoPlaying(): Boolean = isAutoPlaying() && !autoPlayTurnInProgress && (turnsToAutoPlay > 0 || autoPlaySettings.autoPlayUntilEnd)
}

