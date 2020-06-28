package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Files
import com.badlogic.gdx.backends.lwjgl.LwjglApplication
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapSize
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSpeed
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.simulation.Simulation
import com.unciv.models.simulation.SimulationStep
import com.unciv.models.simulation.formatDuration
import com.unciv.ui.newgamescreen.GameSetupInfo
import java.time.Duration
import kotlin.random.Random
import kotlin.system.exitProcess

internal object ConsoleLauncher {
    @JvmStatic
    fun main(arg: Array<String>) {

        val version = "0.1"
        val game = UncivGame ( version, null, { exitProcess(0) }, null, NativeFontDesktop(45), true )

        UncivGame.Current = game
        UncivGame.Current.settings = GameSettings().apply { showTutorials = false }
        UncivGame.Current.simulateMaxTurns = 1000
        UncivGame.Current.simulateUntilWin = true

        RulesetCache.loadRulesets()

        val gameParameters = getGameParameters()
        val mapParameters = getMapParameters()
        val gameSetupInfo = GameSetupInfo(gameParameters, mapParameters)
        val newGame = GameStarter.startNewGame(gameSetupInfo)
        UncivGame.Current.gameInfo = newGame

        var simulation = Simulation(newGame.civilizations.filter { it.civName != "Spectator" }
                .map { it.civName })

        val maxSimulations = 50

        for (i in 1..maxSimulations) {
            println("Simulation step ($i/$maxSimulations)")

            /** Map generation timing ~50ms for tiny maps */
//            val mapGenStart = System.currentTimeMillis()
            val gameInfo = GameStarter.startNewGame(GameSetupInfo(newGame))
//            val mapGenDuration = Duration.ofMillis(System.currentTimeMillis() - mapGenStart)
//            val mapGenTime = formatDuration(mapGenDuration)
//            println("Map generating - $mapGenTime")

            val startTime = System.currentTimeMillis()
            gameInfo.nextTurn()

            val thisPlayer = gameInfo.currentPlayer
            val turns = gameInfo.turns
            val victoryType = gameInfo.currentPlayerCiv.victoryManager.hasWonVictoryType()

            /** Debug */
//                    val thisPlayer = listOf<String>("China", "Greece").shuffled().first()
//                    val turns = Random.nextInt(100, 1000)
//                    val victoryType = VictoryType.values().toList().shuffled().first()

            val duration = Duration.ofMillis(System.currentTimeMillis() - startTime)
            val timeString = formatDuration(duration)

            var simulationStep = SimulationStep(turns, duration = duration)

            // somebody won
            if (victoryType != null) {
                println("$thisPlayer won $victoryType victory on $turns turn - $timeString")
                simulationStep.winner = thisPlayer
                simulationStep.victoryType = victoryType
            } else {
                // nobody won
                println("Max simulation $turns turns reached : Draw - $timeString")
            }

            simulation.steps.add(simulationStep)
        }

        simulation.getStats()
        println(simulation)

    }

    private fun getMapParameters(): MapParameters {
        return MapParameters().apply {
            size = MapSize.Tiny
            noRuins = true
            noNaturalWonders = true
        }
    }

    private fun getGameParameters(): GameParameters {
        return GameParameters().apply {
            difficulty = "Chieftain"
            gameSpeed = GameSpeed.Quick
            noBarbarians = true
            players = ArrayList<Player>().apply {
                add(Player().apply {
                    playerType = PlayerType.AI
                    chosenCiv = "China"
                })

                add(Player().apply {
                    playerType = PlayerType.AI
                    chosenCiv = "Greece"
                })

                add(Player().apply {
                    playerType = PlayerType.Human
                    chosenCiv = "Spectator"
                })
            }
        }
    }

}