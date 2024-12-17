package com.unciv.logic.simulation

import com.unciv.Constants
import com.unciv.logic.GameInfo

class SimulationStep (gameInfo: GameInfo, private val statTurns: List<Int> = listOf()) {
    val civilizations = gameInfo.civilizations.filter { it.civName != Constants.spectator }.map { it.civName }
    var turns = gameInfo.turns
    var victoryType = gameInfo.getCurrentPlayerCivilization().victoryManager.getVictoryTypeAchieved()
    var winner: String? = null
    var currentPlayer = gameInfo.currentPlayer
    val turnStats = mutableMapOf<String, MutableMap<Int, MutableInt>>() // [civ][turn][stat]
    
    init {
        for (civ in civilizations) {
            for (turn in statTurns)
                this.turnStats.getOrPut(civ) { mutableMapOf() }[turn] = MutableInt(-1)
            this.turnStats.getOrPut(civ) { mutableMapOf() }[-1] = MutableInt(-1) // end of game
        }
        
    }
    
    fun saveTurnStats(gameInfo: GameInfo) {
        victoryType = gameInfo.getCurrentPlayerCivilization().victoryManager.getVictoryTypeAchieved()
        val turn = if (victoryType != null) -1 else gameInfo.turns
        for (civ in gameInfo.civilizations.filter { it.civName != Constants.spectator }) {
            val popsum = civ.cities.sumOf { it.population.population }
            //println("$civ $popsum")
            turnStats[civ.civName]!![turn]!!.set(popsum)
        }
    }
    
    fun update(gameInfo: GameInfo) {
        turns = gameInfo.turns
        victoryType = gameInfo.getCurrentPlayerCivilization().victoryManager.getVictoryTypeAchieved()
        currentPlayer = gameInfo.currentPlayer
    }
}


