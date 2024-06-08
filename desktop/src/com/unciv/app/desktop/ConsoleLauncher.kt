package com.unciv.app.desktop

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.utils.Log
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapSize
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.RulesetCache
import com.unciv.logic.simulation.Simulation
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.Speed
import com.unciv.models.skins.SkinCache
import kotlin.time.ExperimentalTime

internal object ConsoleLauncher {
    @ExperimentalTime
    @JvmStatic
    fun main(arg: Array<String>) {
        Log.backend = DesktopLogBackend()

        val game = UncivGame(true)

        UncivGame.Current = game
        UncivGame.Current.settings = GameSettings().apply {
            showTutorials = false
            turnsBetweenAutosaves = 10000
        }

        RulesetCache.loadRulesets(true)
        TileSetCache.loadTileSetConfigs(true)
        SkinCache.loadSkinConfigs(true)

        val gameParameters = getGameParameters("China", "Greece")
        val mapParameters = getMapParameters()
        val gameSetupInfo = GameSetupInfo(gameParameters, mapParameters)
        val newGame = GameStarter.startNewGame(gameSetupInfo)
        UncivGame.Current.startSimulation(newGame)

        val simulation = Simulation(newGame,10,4)

        simulation.start()

        simulation.getStats()
        println(simulation)
    }

    private fun getMapParameters(): MapParameters {
        return MapParameters().apply {
            mapSize = MapSize.Tiny
            noRuins = true
            noNaturalWonders = true
        }
    }

    private fun getGameParameters(civilization1: String, civilization2: String): GameParameters {
        return GameParameters().apply {
            difficulty = "Chieftain"
            speed = Speed.DEFAULT
            noBarbarians = true
            players = ArrayList<Player>().apply {
                add(Player(civilization1))
                add(Player(civilization2))
                add(Player(Constants.spectator, PlayerType.Human))
            }
        }
    }

}
