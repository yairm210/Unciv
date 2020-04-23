package com.unciv.models.metadata

import com.unciv.Constants
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.ruleset.tech.TechEra

class GameParameters { // Default values are the default new game
    var difficulty = "Prince"
    var gameSpeed = GameSpeed.Standard
    var players = mutableListOf<Player>().apply {
        add(Player().apply { playerType = PlayerType.Human })
        for (i in 1..3) add(Player())
    }
    var numberOfCityStates = 0

    var noBarbarians = false
    var oneCityChallenge = false
    var nuclearWeaponsEnabled = true

    var victoryTypes: ArrayList<VictoryType> = VictoryType.values().toCollection(ArrayList()) // By default, all victory types
    var startingEra = Constants.ancientEra

    var isOnlineMultiplayer = false
    var mods = LinkedHashSet<String>()

    fun clone(): GameParameters {
        val parameters = GameParameters()
        parameters.difficulty = difficulty
        parameters.gameSpeed = gameSpeed
        parameters.players = ArrayList(players)
        parameters.numberOfCityStates = numberOfCityStates
        parameters.noBarbarians = noBarbarians
        parameters.oneCityChallenge = oneCityChallenge
        parameters.victoryTypes = ArrayList(victoryTypes)
        parameters.startingEra = startingEra
        parameters.isOnlineMultiplayer = isOnlineMultiplayer
        parameters.mods = LinkedHashSet(mods)
        return parameters
    }
}