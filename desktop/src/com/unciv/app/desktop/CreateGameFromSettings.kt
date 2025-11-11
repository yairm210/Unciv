package com.unciv.app.desktop

import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.json.fromJsonFile
import com.unciv.json.json
import com.unciv.logic.GameStarter
import com.unciv.logic.map.MapParameters
import com.unciv.logic.multiplayer.storage.AuthStatus
import com.unciv.logic.multiplayer.storage.MultiplayerServer
import com.unciv.logic.multiplayer.storage.UncivServerFileStorage
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSetupInfo

/**
 * Configuration schema for creating games from JSON files.
 * Represents the complete game setup including map parameters,
 * game parameters, and optional multiplayer server password.
 */
data class GameConfig(
    val gameParameters: GameParameters = GameParameters(),
    val mapParameters: MapParameters = MapParameters(),
    val serverPassword: String = ""
)

/**
 * Utility for creating and starting games from JSON configuration files.
 * Enables headless game creation without the UI client.
 */
object CreateGameFromSettings {

    /**
     * Creates and starts a new game from a JSON configuration file.
     *
     * @param settingPath Path to the JSON configuration file
     * @return The game ID of the created game
     * @throws Exception if authentication fails or game creation errors occur
     */
    suspend fun startGame(vararg settingPath: String): String {
        val configFile = FileHandle(settingPath[0])
        val config = json().fromJsonFile(GameConfig::class.java, configFile)

        if (config.gameParameters.isOnlineMultiplayer) {
            setupMultiplayer(config.gameParameters, config.serverPassword)
        }

        val gameSetupInfo = GameSetupInfo(config.gameParameters, config.mapParameters)
        val newGame = GameStarter.startNewGame(gameSetupInfo)
        newGame.gameParameters.victoryTypes = ArrayList(newGame.ruleset.victories.keys)
        UncivGame.Current.gameInfo = newGame

        if (config.gameParameters.isOnlineMultiplayer) {
            val multiplayerServer = MultiplayerServer()
            multiplayerServer.uploadGame(newGame, withPreview = true)
        }

        val gameid = newGame.gameId
        println("Game started successfully with game id: $gameid")
        return gameid
    }

    /**
     * Configures multiplayer settings and authenticates with the server.
     *
     * @throws Exception if authentication fails with invalid credentials
     */
    private fun setupMultiplayer(gameParameters: GameParameters, serverPassword: String) {
        val gameSettings = UncivGame.Current.settings
        val multiplayerServerUrl = gameParameters.multiplayerServerUrl.toString()
        val userId = gameParameters.players[0].playerId

        gameSettings.multiplayer.setServer(multiplayerServerUrl)
        gameSettings.multiplayer.setUserId(userId)
        gameSettings.multiplayer.setCurrentServerPassword(serverPassword)

        println("Multiplayer setup: server=$multiplayerServerUrl, userId=$userId")

        UncivServerFileStorage.serverUrl = multiplayerServerUrl
        UncivServerFileStorage.timeout = 30000

        val authStatus = UncivServerFileStorage.checkAuthStatus(userId, serverPassword)
        when (authStatus) {
            AuthStatus.UNREGISTERED -> {
                println("User not registered, authenticating...")
                UncivServerFileStorage.authenticate(userId, serverPassword)
                println("Authentication successful")
            }
            AuthStatus.VERIFIED -> {
                println("User already verified")
            }
            AuthStatus.UNAUTHORIZED -> {
                throw Exception("Authentication failed: Invalid credentials")
            }
            AuthStatus.UNKNOWN -> {
                println("Warning: Could not verify auth status, proceeding anyway")
            }
        }
    }
}
