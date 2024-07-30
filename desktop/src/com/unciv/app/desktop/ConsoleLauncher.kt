package com.unciv.app.desktop

import com.unciv.Constants
import com.unciv.Constants.simulationCiv1
import com.unciv.Constants.simulationCiv2
import com.unciv.UncivGame
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapSize
import com.unciv.logic.simulation.Simulation
import com.unciv.models.metadata.*
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.Speed
import com.unciv.models.ruleset.nation.Nation
import com.unciv.models.skins.SkinCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.utils.Log
import kotlin.time.ExperimentalTime

internal object ConsoleLauncher {

    // To run,set working directory to android/assets in run configuration
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

        runSimulation()
    }

    @ExperimentalTime
    private fun runSimulation() {
        val ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!

        ruleset.nations[simulationCiv1] = Nation().apply { name = simulationCiv1 }
        ruleset.nations[simulationCiv2] = Nation().apply { name = simulationCiv2 }

        val gameParameters = getGameParameters(simulationCiv1, simulationCiv2)
        val mapParameters = getMapParameters()
        val gameSetupInfo = GameSetupInfo(gameParameters, mapParameters)
        val newGame = GameStarter.startNewGame(gameSetupInfo)
        newGame.gameParameters.victoryTypes = ArrayList(newGame.ruleset.victories.keys)
        UncivGame.Current.gameInfo = newGame


        val simulation = Simulation(newGame, 10, 4)

        simulation.start()
    }

    private fun getMapParameters(): MapParameters {
        return MapParameters().apply {
            mapSize = MapSize.Small
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
