package com.unciv.app.desktop

import com.unciv.UncivGame
import com.unciv.UncivGameParameters
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapSize
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSpeed
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.simulation.Simulation
import com.unciv.models.simulation.SimulationStep
import com.unciv.models.simulation.formatDuration
import com.unciv.ui.newgamescreen.GameSetupInfo
import java.time.Duration
import kotlin.concurrent.thread
import kotlin.system.exitProcess

internal object ConsoleLauncher {
    @JvmStatic
    fun main(arg: Array<String>) {

        val version = "0.1"
        val consoleParameters = UncivGameParameters(
                version,
                null,
                { exitProcess(0) },
                null,
                null,
                true
        )
        val game = UncivGame(consoleParameters)

        UncivGame.Current = game
        UncivGame.Current.settings = GameSettings().apply {
            showTutorials = false
            turnsBetweenAutosaves = 10000
        }

        RulesetCache.loadRulesets(true)

        val gameParameters = getGameParameters("China", "Greece")
        val mapParameters = getMapParameters()
        val gameSetupInfo = GameSetupInfo(gameParameters, mapParameters)
        val newGame = GameStarter.startNewGame(gameSetupInfo)
        UncivGame.Current.gameInfo = newGame

        var simulation = Simulation(newGame,10,4)

        simulation.start()

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

    private fun getGameParameters(civilization1: String, civilization2: String): GameParameters {
        return GameParameters().apply {
            difficulty = "Chieftain"
            gameSpeed = GameSpeed.Quick
            noBarbarians = true
            players = ArrayList<Player>().apply {
                add(Player().apply {
                    playerType = PlayerType.AI
                    chosenCiv = civilization1
                })
                add(Player().apply {
                    playerType = PlayerType.AI
                    chosenCiv = civilization2
                })
                add(Player().apply {
                    playerType = PlayerType.Human
                    chosenCiv = "Spectator"
                })
            }
        }
    }

}