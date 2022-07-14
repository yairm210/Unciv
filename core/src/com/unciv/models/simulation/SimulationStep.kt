package com.unciv.models.simulation

import com.unciv.logic.GameInfo

class SimulationStep (gameInfo: GameInfo) {
    var turns = gameInfo.turns
    var victoryType = gameInfo.currentCiv.victoryManager.getVictoryTypeAchieved()
    var winner: String? = null
    val currentCivName = gameInfo.currentCivName
//    val durationString: String = formatDuration(Duration.ofMillis(System.currentTimeMillis() - startTime))
}


