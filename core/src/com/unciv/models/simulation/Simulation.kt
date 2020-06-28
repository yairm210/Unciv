package com.unciv.models.simulation

import com.unciv.models.ruleset.VictoryType
import java.lang.Integer.max
import java.time.Duration

class Simulation(var civilizations: List<String> = ArrayList<String>()) {
    var steps = ArrayList<SimulationStep>()
    var winRate = mutableMapOf<String, MutableInt>()
    var winRateByVictory = HashMap<String, MutableMap<VictoryType, MutableInt>>()
    var avgSpeed = 0f
    private var totalTurns = 0
    var totalDuration: Duration = Duration.ZERO

    init{
        for (civ in civilizations) {
            this.winRate[civ] = MutableInt(0)
            winRateByVictory[civ] = mutableMapOf<VictoryType,MutableInt>()
            for (victory in VictoryType.values())
                winRateByVictory[civ]!![victory] = MutableInt(0)
        }
    }

    fun getStats() {
        // win Rate
        steps.forEach {
            if (it.winner != null) {
                winRate[it.winner!!]!!.inc()
                winRateByVictory[it.winner!!]!![it.victoryType]!!.inc()
            }
        }
        totalTurns = steps.sumBy { it.turns }
        steps.forEach { totalDuration += it.duration }
        avgSpeed = totalTurns.toFloat() / totalDuration.seconds
    }

    override fun toString(): String {
        var outString = ""
        for (civ in civilizations) {
            outString += "\n$civ:\n"
            val wins = winRate[civ]!!.value!! * 100 / steps.size
            outString += "$wins% total win rate \n"
            for (victory in VictoryType.values()) {
                val winsVictory = winRateByVictory[civ]!![victory]!!.value * 100 / max(winRate[civ]!!.value, 1)
                outString += "$victory: $winsVictory%    "
            }
             outString += "\n"
        }
        outString += "\nAverage speed: %.1f turns/s \n".format(avgSpeed)
        val avgDuration = formatDuration(totalDuration.dividedBy(steps.size.toLong()))
        outString += "Average game duration: $avgDuration\n"
        outString += "Total time: " + formatDuration(totalDuration) +"\n"

        return outString
    }
}

