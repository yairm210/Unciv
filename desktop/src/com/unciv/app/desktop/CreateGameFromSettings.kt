package com.unciv.app.desktop

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.unciv.UncivGame
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapSize
import com.unciv.logic.multiplayer.storage.AuthStatus
import com.unciv.logic.multiplayer.storage.MultiplayerServer
import com.unciv.logic.multiplayer.storage.UncivServerFileStorage
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import java.io.File


object CreateGameFromSettings {
    suspend fun startGame(vararg settingPath: String): String {
        val configFile = File(settingPath[0])
        val jsonString = configFile.readText(Charsets.UTF_8)

        val jsonReader = JsonReader()
        val configJson = jsonReader.parse(jsonString)

        val gameParamsJson = configJson.get("gameParameters")
        val mapParamsJson = configJson.get("mapParameters")

        val mapParameters = getMapParameters(mapParamsJson)
        val gameParameters = getGameParameters(gameParamsJson)

        // Setup multiplayer before creating the game
        if (gameParameters.isOnlineMultiplayer) {
            setupMultiplayer(gameParameters, configJson)
        }

        val gameSetupInfo = GameSetupInfo(gameParameters, mapParameters)
        val newGame = GameStarter.startNewGame(gameSetupInfo)
        newGame.gameParameters.victoryTypes = ArrayList(newGame.ruleset.victories.keys)
        UncivGame.Current.gameInfo = newGame

        // Upload game to server
        if (gameParameters.isOnlineMultiplayer) {
            val multiplayerServer = MultiplayerServer()
            multiplayerServer.uploadGame(newGame, withPreview = true)
        }

        val gameid = newGame.gameId
        println("Game started successfully with game id: $gameid")
        return gameid
    }

    private fun setupMultiplayer(gameParameters: GameParameters, configJson: JsonValue) {
        val gameSettings = UncivGame.Current.settings
        val multiplayerServerUrl = gameParameters.multiplayerServerUrl.toString()
        val userId = gameParameters.players[0].playerId
        val serverPassword = configJson.getString("serverPassword", "")

        // Set multiplayer settings
        gameSettings.multiplayer.setServer(multiplayerServerUrl)
        gameSettings.multiplayer.setUserId(userId)
        gameSettings.multiplayer.setCurrentServerPassword(serverPassword)

        println("Multiplayer setup: server=$multiplayerServerUrl, userId=$userId")

        // Check authentication status and register if needed
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

    private fun getMapParameters(mapParamsJson: Any?): MapParameters {
        return MapParameters().apply {
            if (mapParamsJson is JsonValue) {
                name = mapParamsJson.getString("name", name)
                type = mapParamsJson.getString("type", type)
                shape = mapParamsJson.getString("shape", shape)
                mirroring = mapParamsJson.getString("mirroring", mirroring)

                if (mapParamsJson.has("mapSize")) {
                    val mapSizeString = mapParamsJson.getString("mapSize")
                    mapSize = MapSize(mapSizeString) // Uses MapSize constructor
                }

                mapResources = mapParamsJson.getString("mapResources", mapResources)
                noRuins = mapParamsJson.getBoolean("noRuins", noRuins)
                noNaturalWonders = mapParamsJson.getBoolean("noNaturalWonders", noNaturalWonders)
                worldWrap = mapParamsJson.getBoolean("worldWrap", worldWrap)
                strategicBalance = mapParamsJson.getBoolean("strategicBalance", strategicBalance)
                legendaryStart = mapParamsJson.getBoolean("legendaryStart", legendaryStart)
                baseRuleset = mapParamsJson.getString("baseRuleset", baseRuleset)
                createdWithVersion = mapParamsJson.getString("createdWithVersion", createdWithVersion)
                seed = mapParamsJson.getLong("seed", seed)
                tilesPerBiomeArea = mapParamsJson.getInt("tilesPerBiomeArea", tilesPerBiomeArea)
                maxCoastExtension = mapParamsJson.getInt("maxCoastExtension", maxCoastExtension)
                elevationExponent = mapParamsJson.getFloat("elevationExponent", elevationExponent)
                temperatureintensity = mapParamsJson.getFloat("temperatureintensity", temperatureintensity)
                vegetationRichness = mapParamsJson.getFloat("vegetationRichness", vegetationRichness)
                rareFeaturesRichness = mapParamsJson.getFloat("rareFeaturesRichness", rareFeaturesRichness)
                resourceRichness = mapParamsJson.getFloat("resourceRichness", resourceRichness)
                waterThreshold = mapParamsJson.getFloat("waterThreshold", waterThreshold)
                temperatureShift = mapParamsJson.getFloat("temperatureShift", temperatureShift)

                if (mapParamsJson.has("mods")) {
                    val modsArray = mapParamsJson.get("mods")
                    mods.clear()
                    for (mod in modsArray) {
                        mods.add(mod.asString())
                    }
                    println("Loaded map mods: $mods")
                }
            }
        }
    }

    private fun getGameParameters(gameParamsJson: Any?): GameParameters {
        return GameParameters().apply {
            if (gameParamsJson is JsonValue) {
                // Basic settings
                difficulty = gameParamsJson.getString("difficulty", difficulty)
                speed = gameParamsJson.getString("speed", speed)
                startingEra = gameParamsJson.getString("startingEra", startingEra)
                baseRuleset = gameParamsJson.getString("baseRuleset", baseRuleset)
                maxTurns = gameParamsJson.getInt("maxTurns", maxTurns)
                acceptedModCheckErrors = gameParamsJson.getString("acceptedModCheckErrors", acceptedModCheckErrors)

                // Player settings
                randomNumberOfPlayers = gameParamsJson.getBoolean("randomNumberOfPlayers", randomNumberOfPlayers)
                minNumberOfPlayers = gameParamsJson.getInt("minNumberOfPlayers", minNumberOfPlayers)
                maxNumberOfPlayers = gameParamsJson.getInt("maxNumberOfPlayers", maxNumberOfPlayers)

                // City states
                randomNumberOfCityStates = gameParamsJson.getBoolean("randomNumberOfCityStates", randomNumberOfCityStates)
                minNumberOfCityStates = gameParamsJson.getInt("minNumberOfCityStates", minNumberOfCityStates)
                maxNumberOfCityStates = gameParamsJson.getInt("maxNumberOfCityStates", maxNumberOfCityStates)
                numberOfCityStates = gameParamsJson.getInt("numberOfCityStates", numberOfCityStates)

                // Game options
                enableRandomNationsPool = gameParamsJson.getBoolean("enableRandomNationsPool", enableRandomNationsPool)
                noCityRazing = gameParamsJson.getBoolean("noCityRazing", noCityRazing)
                noBarbarians = gameParamsJson.getBoolean("noBarbarians", noBarbarians)
                ragingBarbarians = gameParamsJson.getBoolean("ragingBarbarians", ragingBarbarians)
                oneCityChallenge = gameParamsJson.getBoolean("oneCityChallenge", oneCityChallenge)
                godMode = gameParamsJson.getBoolean("godMode", godMode)
                nuclearWeaponsEnabled = gameParamsJson.getBoolean("nuclearWeaponsEnabled", nuclearWeaponsEnabled)
                espionageEnabled = gameParamsJson.getBoolean("espionageEnabled", espionageEnabled)
                noStartBias = gameParamsJson.getBoolean("noStartBias", noStartBias)
                shufflePlayerOrder = gameParamsJson.getBoolean("shufflePlayerOrder", shufflePlayerOrder)

                // Multiplayer settings
                isOnlineMultiplayer = gameParamsJson.getBoolean("isOnlineMultiplayer", isOnlineMultiplayer)
                multiplayerServerUrl = gameParamsJson.getString("multiplayerServerUrl", multiplayerServerUrl)
                anyoneCanSpectate = gameParamsJson.getBoolean("anyoneCanSpectate", anyoneCanSpectate)
                minutesUntilSkipTurn = gameParamsJson.getInt("minutesUntilSkipTurn", minutesUntilSkipTurn)
                hoursUntilForceResign = gameParamsJson.getInt("hoursUntilForceResign", hoursUntilForceResign)

                // Mods array
                if (gameParamsJson.has("mods")) {
                    val modsArray = gameParamsJson.get("mods")
                    mods.clear()
                    for (mod in modsArray) {
                        mods.add(mod.asString())
                    }
                    println("Loaded mods: $mods")
                }

                // Victory types array
                if (gameParamsJson.has("victoryTypes")) {
                    val victoryTypesArray = gameParamsJson.get("victoryTypes")
                    victoryTypes.clear()
                    for (victoryType in victoryTypesArray) {
                        victoryTypes.add(victoryType.asString())
                    }
                    println("Loaded victory types: $victoryTypes")
                }

                // Players array
                if (gameParamsJson.has("players")) {
                    val playersArray = gameParamsJson.get("players")
                    players.clear()
                    for (playerJson in playersArray) {
                        val player = Player().apply {
                            chosenCiv = playerJson.getString("chosenCiv", chosenCiv)
                            // Parse PlayerType enum
                            val playerTypeString = playerJson.getString("playerType", "AI")
                            playerType = PlayerType.valueOf(playerTypeString)
                            playerId = playerJson.getString("playerId", playerId)
                        }
                        players.add(player)
                    }
                    println("Loaded ${players.size} players")
                }

                println("GameParameters loaded successfully")
            }
        }
    }
}
