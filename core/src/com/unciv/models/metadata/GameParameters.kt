package com.unciv.models.metadata

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.Speed

class GameParameters : IsPartOfGameInfoSerialization { // Default values are the default new game
    var difficulty = "Prince"
    var speed = Speed.DEFAULT

    var randomNumberOfPlayers = false
    var minNumberOfPlayers = 3
    var maxNumberOfPlayers = 3
    var players = ArrayList<Player>().apply {
        add(Player(playerType = PlayerType.Human))
        repeat(3) { add(Player()) }
    }
    var randomNumberOfCityStates = false
    var minNumberOfCityStates = 6
    var maxNumberOfCityStates = 6
    var numberOfCityStates = 6

    var enableRandomNationsPool = false
    var randomNationsPool = arrayListOf<String>()

    var noCityRazing = false
    var noBarbarians = false
    var ragingBarbarians = false
    var oneCityChallenge = false
    var godMode = false
    var nuclearWeaponsEnabled = true
    var espionageEnabled = false
    var noStartBias = false
    var shufflePlayerOrder = false

    var victoryTypes: ArrayList<String> = arrayListOf()
    var startingEra = "Ancient era"

    // Multiplayer parameters
    var isOnlineMultiplayer = false
    var multiplayerServerUrl: String? = null
    var anyoneCanSpectate = true
    /** After this amount of minutes, anyone can choose to 'skip turn' of the current player to keep the game going */
    var minutesUntilSkipTurn = 60 * 24
    var hoursUntilForceResign = 3 * 24

    var baseRuleset: String = BaseRuleset.Civ_V_GnK.fullName
    var mods = LinkedHashSet<String>()

    var maxTurns = 500

    var acceptedModCheckErrors = ""

    fun clone(): GameParameters {
        val parameters = GameParameters()
        parameters.difficulty = difficulty
        parameters.speed = speed
        parameters.randomNumberOfPlayers = randomNumberOfPlayers
        parameters.minNumberOfPlayers = minNumberOfPlayers
        parameters.maxNumberOfPlayers = maxNumberOfPlayers
        parameters.players = ArrayList(players)
        parameters.randomNumberOfCityStates = randomNumberOfCityStates
        parameters.minNumberOfCityStates = minNumberOfCityStates
        parameters.maxNumberOfCityStates = maxNumberOfCityStates
        parameters.numberOfCityStates = numberOfCityStates
        parameters.enableRandomNationsPool = enableRandomNationsPool
        parameters.randomNationsPool = ArrayList(randomNationsPool)
        parameters.noCityRazing = noCityRazing
        parameters.noBarbarians = noBarbarians
        parameters.ragingBarbarians = ragingBarbarians
        parameters.oneCityChallenge = oneCityChallenge
        // godMode intentionally reset on clone
        parameters.nuclearWeaponsEnabled = nuclearWeaponsEnabled
        parameters.espionageEnabled = espionageEnabled
        parameters.noStartBias = noStartBias
        parameters.shufflePlayerOrder = shufflePlayerOrder
        parameters.victoryTypes = ArrayList(victoryTypes)
        parameters.startingEra = startingEra
        parameters.isOnlineMultiplayer = isOnlineMultiplayer
        parameters.multiplayerServerUrl = multiplayerServerUrl
        parameters.anyoneCanSpectate = anyoneCanSpectate
        parameters.baseRuleset = baseRuleset
        parameters.mods = LinkedHashSet(mods)
        parameters.maxTurns = maxTurns
        parameters.acceptedModCheckErrors = acceptedModCheckErrors
        return parameters
    }

    // For debugging and GameStarter console output
    override fun toString() = sequence {
            yield("$difficulty $speed $startingEra")
            yield("${players.count { it.playerType == PlayerType.Human }} ${PlayerType.Human}")
            yield("${players.count { it.playerType == PlayerType.AI }} ${PlayerType.AI}")
            if (randomNumberOfPlayers) yield("Random number of Players: $minNumberOfPlayers..$maxNumberOfPlayers")
            if (randomNumberOfCityStates) yield("Random number of City-States: $minNumberOfCityStates..$maxNumberOfCityStates")
            else yield("$numberOfCityStates CS")
            if (isOnlineMultiplayer) yield("Online Multiplayer")
            if (noBarbarians) yield("No barbs")
            if (ragingBarbarians) yield("Raging barbs")
            if (oneCityChallenge) yield("OCC")
            if (!nuclearWeaponsEnabled) yield("No nukes")
            if (godMode) yield("God mode")
            yield("Enabled Victories: " + victoryTypes.joinToString())
            yield(baseRuleset)
            yield(if (mods.isEmpty()) "no mods" else mods.joinToString(",", "mods=(", ")", 6) )
        }.joinToString(prefix = "(", postfix = ")")

    /** Get all mods including base
     *
     *  The returned Set is ordered base first, then in the order they are stored in a save.
     *  This creates a fresh instance, and the caller is allowed to mutate it.
     */
    fun getModsAndBaseRuleset() =
        LinkedHashSet<String>(mods.size + 1).apply {
            add(baseRuleset)
            addAll(mods)
        }
}
