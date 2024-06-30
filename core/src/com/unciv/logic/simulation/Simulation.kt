package com.unciv.logic.simulation

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameStarter
import com.unciv.models.metadata.GameSetupInfo
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@ExperimentalTime
class Simulation(
    private val newGameInfo: GameInfo,
    val simulationsPerThread: Int = 1
    ,
    private val threadsNumber: Int = 1,
    private val maxTurns: Int = 500
) {
    private val maxSimulations = threadsNumber * simulationsPerThread
    val civilizations = newGameInfo.civilizations.filter { it.civName != Constants.spectator }.map { it.civName }
    private var startTime: Long = 0
    private var endTime: Long = 0
    var steps = ArrayList<SimulationStep>()
    var winRate = mutableMapOf<String, MutableInt>()
    private var winRateByVictory = HashMap<String, MutableMap<String, MutableInt>>()
    private var avgSpeed = 0f
    private var avgDuration: Duration = Duration.ZERO
    private var totalTurns = 0
    private var totalDuration: Duration = Duration.ZERO
    private var stepCounter: Int = 0


    init{
        for (civ in civilizations) {
            this.winRate[civ] = MutableInt(0)
            winRateByVictory[civ] = mutableMapOf()
            for (victory in UncivGame.Current.gameInfo!!.ruleset.victories.keys)
                winRateByVictory[civ]!![victory] = MutableInt(0)
        }
    }

    fun start() = runBlocking {

        startTime = System.currentTimeMillis()
        val jobs: ArrayList<Job> = ArrayList()
        println("Starting new game with major civs: "+newGameInfo.civilizations.filter { it.isMajorCiv() }.joinToString { it.civName }
        + " and minor civs: "+newGameInfo.civilizations.filter { it.isCityState }.joinToString { it.civName })
        for (threadId in 1..threadsNumber) {
            jobs.add(launch(CoroutineName("simulation-${threadId}")) {
                repeat(simulationsPerThread) {
                    val gameInfo = GameStarter.startNewGame(GameSetupInfo(newGameInfo))
                    gameInfo.simulateMaxTurns = maxTurns
                    gameInfo.simulateUntilWin = true
                    gameInfo.nextTurn()

                    val step = SimulationStep(gameInfo)

                    if (step.victoryType != null) {
                        step.winner = step.currentPlayer
                        println("${step.winner} won ${step.victoryType} victory on turn ${step.turns}")
                    }
                    else println("Max simulation ${step.turns} turns reached: Draw")

                    print(gameInfo)
                    updateCounter(threadId)
                    add(step)
                }
            })
        }
        // wait for all to finish
        for (job in jobs) job.join()
        endTime = System.currentTimeMillis()
    }

    @Suppress("UNUSED_PARAMETER")   // used when activating debug output
    @Synchronized fun add(step: SimulationStep, threadId: Int = 1) {
//        println("Thread $threadId: End simulation ($stepCounter/$maxSimulations)")
        steps.add(step)
    }

    @Suppress("UNUSED_PARAMETER")   // used when activating debug output
    @Synchronized fun updateCounter(threadId: Int = 1) {
        stepCounter++
//        println("Thread $threadId: Start simulation ($stepCounter/$maxSimulations)")
        println("Simulation step ($stepCounter/$maxSimulations)")
    }

    fun getStats() {
        // win Rate
        steps.forEach {
            if (it.winner != null) {
                winRate[it.winner!!]!!.inc()
                winRateByVictory[it.winner!!]!![it.victoryType]!!.inc()
            }
        }
        totalTurns = steps.sumOf { it.turns }
        totalDuration = (endTime - startTime).milliseconds
        avgSpeed = totalTurns.toFloat() / totalDuration.inWholeSeconds
        avgDuration = totalDuration / steps.size
    }

    fun text(): String {
        var outString = ""
        for (civ in civilizations) {
            outString += "\n$civ:\n"
            val wins = winRate[civ]!!.value * 100 / max(steps.size, 1)
            outString += "$wins% total win rate \n"
            for (victory in UncivGame.Current.gameInfo!!.ruleset.victories.keys) {
                val winsVictory = winRateByVictory[civ]!![victory]!!.value * 100 / max(winRate[civ]!!.value, 1)
                outString += "$victory: $winsVictory%    "
            }
             outString += "\n"
        }
        outString += "\nAverage speed: %.1f turns/s \n".format(avgSpeed)
        outString += "Average game duration: $avgDuration\n"
        outString += "Total time: $totalDuration\n"

        return outString
    }
}

