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
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@ExperimentalTime
class Simulation(
    private val newGameInfo: GameInfo,
    val simulationsPerThread: Int = 1,
    private val threadsNumber: Int = 1,
    private val maxTurns: Int = 500,
    private val statTurns: List<Int> = listOf()
) {
    private val maxSimulations = threadsNumber * simulationsPerThread
    val civilizations = newGameInfo.civilizations.filter { it.civName != Constants.spectator }.map { it.civName }
    private val majorCivs = newGameInfo.civilizations.filter { it.civName != Constants.spectator }.filter { it.isMajorCiv() }.size
    private var startTime: Long = 0
    var steps = ArrayList<SimulationStep>()
    var numWins = mutableMapOf<String, MutableInt>()
    private var summaryStats = HashMap<String, HashMap<Int, HashMap<Stat, MutableInt>>>() // [civ][turn][stat]=value
    private var winRateByVictory = HashMap<String, MutableMap<String, MutableInt>>()
    private var winTurnByVictory = HashMap<String, MutableMap<String, MutableInt>>()
    private var avgSpeed = 0f
    private var avgDuration: Duration = Duration.ZERO
    private var totalTurns = 0
    private var totalDuration: Duration = Duration.ZERO
    private var stepCounter: Int = 0
    enum class Stat {
        SUM,
        NUM
    }


    init{
        for (civ in civilizations) {
            this.numWins[civ] = MutableInt(0)
            for (turn in statTurns) {
                this.summaryStats.getOrPut(civ) { hashMapOf() }.getOrPut(turn){hashMapOf()}[Stat.SUM] = MutableInt(0)
                this.summaryStats.getOrPut(civ) { hashMapOf() }.getOrPut(turn){hashMapOf()}[Stat.NUM] = MutableInt(0)
            }
            val turn = -1 // end of game
            this.summaryStats.getOrPut(civ) { hashMapOf() }.getOrPut(turn){hashMapOf()}[Stat.SUM] = MutableInt(0)
            this.summaryStats.getOrPut(civ) { hashMapOf() }.getOrPut(turn){hashMapOf()}[Stat.NUM] = MutableInt(0)
            winRateByVictory[civ] = mutableMapOf()
            for (victory in UncivGame.Current.gameInfo!!.ruleset.victories.keys)
                winRateByVictory[civ]!![victory] = MutableInt(0)
            winTurnByVictory[civ] = mutableMapOf()
            for (victory in UncivGame.Current.gameInfo!!.ruleset.victories.keys)
                winTurnByVictory[civ]!![victory] = MutableInt(0)
        }
    }

    fun start() = runBlocking {

        startTime = System.currentTimeMillis()
        val jobs: ArrayList<Job> = ArrayList()
        println("Starting new game with major civs: "+newGameInfo.civilizations.filter { it.isMajorCiv() }.joinToString { it.civName }
        + " and minor civs: "+newGameInfo.civilizations.filter { it.isCityState }.joinToString { it.civName })
        newGameInfo.gameParameters.shufflePlayerOrder = true
        for (threadId in 1..threadsNumber) {
            jobs.add(launch(CoroutineName("simulation-${threadId}")) {
                repeat(simulationsPerThread) {
                    val step = SimulationStep(newGameInfo, statTurns)
                    val gameInfo = GameStarter.startNewGame(GameSetupInfo(newGameInfo))
                    gameInfo.simulateUntilWin = true
                    for (turn in statTurns) {
                        gameInfo.simulateMaxTurns = turn
                        gameInfo.nextTurn()
                        step.update(gameInfo)
                        if (step.victoryType != null)
                            break
                        step.saveTurnStats(gameInfo)
                    }
                    // check if Victory
                    step.update(gameInfo)
                    if (step.victoryType == null) {
                        gameInfo.simulateMaxTurns = maxTurns
                        gameInfo.nextTurn()
                    }

                    step.update(gameInfo)  // final game state

                    if (step.victoryType != null) {
                        step.saveTurnStats(gameInfo)
                        step.winner = step.currentPlayer
                        println("${step.winner} won ${step.victoryType} victory on turn ${step.turns}")
                    }
                    else println("Max simulation ${step.turns} turns reached: Draw")

                    updateCounter(threadId)
                    add(step)
                    print()
                }
            })
        }
        // wait for all to finish
        for (job in jobs) job.join()
    }

    @Suppress("UNUSED_PARAMETER")   // used when activating debug output
    @Synchronized fun add(step: SimulationStep, threadId: Int = 1) {
        steps.add(step)
    }

    @Suppress("UNUSED_PARAMETER")   // used when activating debug output
    @Synchronized fun updateCounter(threadId: Int = 1) {
        stepCounter++
        println("Simulation step ($stepCounter/$maxSimulations)")
    }

    @Synchronized
    fun print(){
        getStats()
        println(text())
    }

    fun getStats() {
        // win Rate
        numWins.values.forEach { it.value = 0 }
        winRateByVictory.flatMap { it.value.values }.forEach { it.value = 0 }
        winTurnByVictory.flatMap { it.value.values }.forEach { it.value = 0 }
        summaryStats.flatMap { it.value.values }.forEach {
            it.values.forEach { it.value = 0 }
        }
        steps.forEach {
            if (it.winner != null) {
                numWins[it.winner!!]!!.inc()
                winRateByVictory[it.winner!!]!![it.victoryType]!!.inc()
                winTurnByVictory[it.winner!!]!![it.victoryType]!!.add(it.turns)
            }
            for (civ in civilizations) {
                for (turn in statTurns) {
                    if (it.turnStats[civ]!![turn]!!.value != -1) {
                        summaryStats[civ]!![turn]!![Stat.SUM]!!.add(it.turnStats[civ]!![turn]!!.value)
                        summaryStats[civ]!![turn]!![Stat.NUM]!!.inc()
                        //println("civ ${civ} @ ${turn} value ${it.turnStats[civ]!![turn]!!.value} avg ${summaryStats[civ]!![turn]!!["avg"]!!.value} numAvg ${summaryStats[civ]!![turn]!!["numAvg"]!!.value}")
                    }
                }
                val turn = -1 // end of game
                summaryStats[civ]!![turn]!![Stat.SUM]!!.add(it.turnStats[civ]!![turn]!!.value)
                summaryStats[civ]!![turn]!![Stat.NUM]!!.inc()
            }
        }
        totalTurns = steps.sumOf { it.turns }
        totalDuration = (System.currentTimeMillis() - startTime).milliseconds
        avgSpeed = totalTurns.toFloat() / totalDuration.inWholeSeconds
        avgDuration = totalDuration / steps.size
    }

    fun text(): String {
        var outString = ""
        for (civ in civilizations) {

            val numSteps = max(steps.size, 1)
            val expWinRate = 1f / majorCivs
            if (numWins[civ]!!.value == 0) continue
            val winRate = String.format("%.1f", numWins[civ]!!.value * 100f / numSteps)

            outString += "\n$civ:\n"
            outString += "$winRate% total win rate \n"
            if (numSteps * expWinRate >= 10 && numSteps * (1 - expWinRate) >= 10) {
                // large enough sample size, binomial distribution approximates the Normal Curve
                val pval = binomialTest(
                    numWins[civ]!!.value.toDouble(),
                    numSteps.toDouble(),
                    expWinRate.toDouble(),
                    "greater"
                )
                outString += "one-tail binomial pval = $pval\n"
            }
            for (victory in UncivGame.Current.gameInfo!!.ruleset.victories.keys) {
                val winsVictory =
                    winRateByVictory[civ]!![victory]!!.value * 100 / max(numWins[civ]!!.value, 1)
                outString += "$victory: $winsVictory%    "
            }
            outString += "\n"
            for (victory in UncivGame.Current.gameInfo!!.ruleset.victories.keys) {
                val winsTurns =
                    winTurnByVictory[civ]!![victory]!!.value / max(winRateByVictory[civ]!![victory]!!.value, 1)
                outString += "$victory: $winsTurns    "
            }
            outString += "avg turns\n"
            for (turn in statTurns) {
                val turnStats = summaryStats[civ]!![turn]!!
                outString += "@$turn: popsum avg=${turnStats[Stat.SUM]!!.value.toFloat() / turnStats[Stat.NUM]!!.value.toFloat()} cnt=${turnStats[Stat.NUM]!!.value}\n"
            }
            val turn = -1 // end of match
            val turnStats = summaryStats[civ]!![turn]!!
            outString += "@END: popsum avg=${turnStats[Stat.SUM]!!.value.toFloat()/turnStats[Stat.NUM]!!.value.toFloat()} cnt=${turnStats[Stat.NUM]!!.value}\n"
        }
        outString += "\nAverage speed: %.1f turns/s \n".format(avgSpeed)
        outString += "Average game duration: $avgDuration\n"
        outString += "Total time: $totalDuration\n"

        return outString
    }

    private fun binomialTest(successes: Double, trials: Double, p: Double, alternative: String): Double {
        val q = 1 - p
        val mean = trials * p
        val variance = trials * p * q
        val stdDev = sqrt(variance)
        val z = (successes - mean) / stdDev
        val pValue = 1 - normalCdf(z)
        return when (alternative) {
            "greater" -> pValue
            "less" -> 1 - pValue
            else -> throw IllegalArgumentException("Alternative must be 'greater' or 'less'")
        }
    }

    private fun normalCdf(z: Double): Double {
        return 0.5 * (1 + erf(z / sqrt(2.0)))
    }

    // Approximation of the error function
    private fun erf(x: Double): Double {
        val t = 1.0 / (1.0 + 0.5 * kotlin.math.abs(x))
        val tau =
            t * kotlin.math.exp(
                -x * x - 1.26551223 +
                    t * (1.00002368 + t * (0.37409196 + t * (0.09678418
                    + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 +
                    t * (1.48851587 + t * (-0.82215223 + t * 0.17087277))))))))
            )
        return if (x >= 0) 1 - tau else tau - 1
    }
}

