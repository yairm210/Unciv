package com.unciv.ui.screens.worldscreen.unit

import com.unciv.models.metadata.GameSettings
import com.unciv.ui.screens.worldscreen.WorldScreen

class AutoPlay(val worldScreen: WorldScreen) {
    var turnsToAutoPlay: Int = 0
    var autoPlaying: Boolean = false
    var autoPlayTurnInProgress: Boolean = false
    fun startAutoPlay() {
        autoPlaying = true
        turnsToAutoPlay = worldScreen.game.settings.autoPlay.autoPlayMaxTurns
    }

    fun stopAutoPlay() {
        autoPlaying = false
        turnsToAutoPlay = 0
        autoPlayTurnInProgress = false
    }

    fun isAutoPlaying(): Boolean = autoPlaying

    fun fullAutoPlayAI(): Boolean = isAutoPlaying() && worldScreen.game.settings.autoPlay.fullAutoPlayAI
}