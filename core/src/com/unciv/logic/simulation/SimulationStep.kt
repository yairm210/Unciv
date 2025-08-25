package com.unciv.logic.simulation

import com.unciv.Constants
import com.unciv.logic.GameInfo
import kotlin.math.roundToInt

class SimulationStep (gameInfo: GameInfo, statTurns: List<Int> = listOf()) {
    private val civilizationNames = gameInfo.civilizations.filter { it.civName != Constants.spectator }.filter { it.isMajorCiv() }.map { it.civName }
    var turns = gameInfo.turns
    var victoryType = gameInfo.getCurrentPlayerCivilization().victoryManager.getVictoryTypeAchieved()
    var winner: String? = null
    var currentPlayer = gameInfo.currentPlayer
    val turnStatsPop = mutableMapOf<String, MutableMap<Int, MutableInt>>() // [civ][turn][stat]
    val turnStatsProd = mutableMapOf<String, MutableMap<Int, MutableInt>>() // [civ][turn][stat]
    val turnStatsCities = mutableMapOf<String, MutableMap<Int, MutableInt>>() // [civ][turn][stat]
    
    init {
        for (civ in civilizationNames) {
            for (turn in statTurns) {
                this.turnStatsPop.getOrPut(civ) { mutableMapOf() }[turn] = MutableInt(-1)
                this.turnStatsProd.getOrPut(civ) { mutableMapOf() }[turn] = MutableInt(-1)
                this.turnStatsCities.getOrPut(civ) { mutableMapOf() }[turn] = MutableInt(-1)
            }
            this.turnStatsPop.getOrPut(civ) { mutableMapOf() }[-1] = MutableInt(-1) // end of game
            this.turnStatsProd.getOrPut(civ) { mutableMapOf() }[-1] = MutableInt(-1) // end of game
            this.turnStatsCities.getOrPut(civ) { mutableMapOf() }[-1] = MutableInt(-1) // end of game
        }
        
    }
    
    fun saveTurnStats(gameInfo: GameInfo) {
        victoryType = gameInfo.getCurrentPlayerCivilization().victoryManager.getVictoryTypeAchieved()
        val turn = if (victoryType != null) -1 else gameInfo.turns
        for (civ in gameInfo.civilizations.filter { it.civName != Constants.spectator }.filter { it.isMajorCiv() }) {
            val popsum = civ.cities.sumOf { it.population.population }
            //println("$civ $turn $popsum")
            turnStatsPop[civ.civName]!![turn]!!.set(popsum)
            val prodsum = civ.cities.sumOf { it.cityStats.currentCityStats.production.roundToInt() }
            //println("$civ $prodsum")
            turnStatsProd[civ.civName]!![turn]!!.set(prodsum)
            val cityCnt = civ.cities.count()
            //println("$civ $cityCnt")
            turnStatsCities[civ.civName]!![turn]!!.set(cityCnt)
        }
    }
    
    fun update(gameInfo: GameInfo) {
        turns = gameInfo.turns
        victoryType = gameInfo.getCurrentPlayerCivilization().victoryManager.getVictoryTypeAchieved()
        currentPlayer = gameInfo.currentPlayer
    }
}


