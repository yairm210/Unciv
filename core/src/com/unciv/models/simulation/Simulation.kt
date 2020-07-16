package com.unciv.models.simulation

import com.unciv.logic.GameInfo
import com.unciv.logic.GameStarter
import com.unciv.models.ruleset.VictoryType
import com.unciv.ui.newgamescreen.GameSetupInfo
import java.lang.Integer.max
import java.time.Duration
import kotlin.concurrent.thread

class Simulation(val newGameInfo: GameInfo,
                 val simulationsPerThread: Int = 5,
                 val threadsNumber: Int = 1,
                 val maxTurns: Int = 1000
) {
    val maxSimulations = threadsNumber * simulationsPerThread
    val civilizations = newGameInfo.civilizations.filter { it.civName != "Spectator" }.map { it.civName }
    private var startTime: Long = 0
    private var endTime: Long = 0
    var steps = ArrayList<SimulationStep>()
    var winRate = mutableMapOf<String, MutableInt>()
    var winRateByVictory = HashMap<String, MutableMap<VictoryType, MutableInt>>()
    var avgSpeed = 0f
    var avgDuration: Duration = Duration.ZERO
    private var totalTurns = 0
    private var totalDuration: Duration = Duration.ZERO
    var stepCounter: Int = 0


    init{
        for (civ in civilizations) {
            this.winRate[civ] = MutableInt(0)
            winRateByVictory[civ] = mutableMapOf<VictoryType,MutableInt>()
            for (victory in VictoryType.values())
                winRateByVictory[civ]!![victory] = MutableInt(0)
        }
    }

    fun start() {

        startTime = System.currentTimeMillis()
        val threads: ArrayList<Thread> = ArrayList()
        for (threadId in 1..threadsNumber) {
            threads.add(thread {
                for (i in 1..simulationsPerThread) {
                    val gameInfo = GameStarter.startNewGame(GameSetupInfo(newGameInfo))
                    gameInfo.simulateMaxTurns = maxTurns
                    gameInfo.simulateUntilWin = true
                    gameInfo.nextTurn()

                    var step = SimulationStep(gameInfo)

                    if (step.victoryType != null) {
                        step.winner = step.currentPlayer
                        printWinner(step)
                    }
                    else
                        printDraw(step)

                    updateCounter(threadId)
                    add(step)
                }
            })
        }
        // wait for all threads to finish
        for (thread in threads) thread.join()
        endTime = System.currentTimeMillis()
    }

    @Synchronized fun add(step: SimulationStep, threadId: Int = 1) {
//        println("Thread $threadId: End simulation ($stepCounter/$maxSimulations)")
        steps.add(step)
    }

    @Synchronized fun updateCounter(threadId: Int = 1) {
        stepCounter++
//        println("Thread $threadId: Start simulation ($stepCounter/$maxSimulations)")
        println("Simulation step ($stepCounter/$maxSimulations)")
    }

    private fun printWinner(step: SimulationStep) {
        println("%s won %s victory on %d turn".format(
                step.winner,
                step.victoryType,
                step.turns
        ))
    }

    private fun printDraw(step: SimulationStep) {
        println("Max simulation %d turns reached : Draw".format(
                step.turns
        ))
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
        totalDuration = Duration.ofMillis(endTime - startTime)
        avgSpeed = totalTurns.toFloat() / totalDuration.seconds
        avgDuration = totalDuration.dividedBy(steps.size.toLong())
    }

    override fun toString(): String {
        var outString = ""
        for (civ in civilizations) {
            outString += "\n$civ:\n"
            val wins = winRate[civ]!!.value!! * 100 / max(steps.size, 1)
            outString += "$wins% total win rate \n"
            for (victory in VictoryType.values()) {
                val winsVictory = winRateByVictory[civ]!![victory]!!.value * 100 / max(winRate[civ]!!.value, 1)
                outString += "$victory: $winsVictory%    "
            }
             outString += "\n"
        }
        outString += "\nAverage speed: %.1f turns/s \n".format(avgSpeed)
        outString += "Average game duration: " + formatDuration(avgDuration) + "\n"
        outString += "Total time: " + formatDuration(totalDuration) + "\n"

        return outString
    }
}

