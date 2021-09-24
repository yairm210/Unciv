package com.unciv.models.simulation

import com.unciv.logic.GameInfo

class SimulationStep (gameInfo: GameInfo) {
    var turns = gameInfo.turns
    var victoryType = gameInfo.currentPlayerCiv.victoryManager.hasWonVictoryType()
    var winner: String? = null
    val currentPlayer = gameInfo.currentPlayer
//    val durationString: String = formatDuration(Duration.ofMillis(System.currentTimeMillis() - startTime))
}


