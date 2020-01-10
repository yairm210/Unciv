package com.unciv.models.metadata

import com.unciv.logic.civilization.PlayerType
import com.unciv.models.VictoryType
import com.unciv.models.ruleset.tech.TechEra

class GameParameters { // Default values are the default new game
    var difficulty = "Prince"
    var gameSpeed = GameSpeed.Standard
    var players = ArrayList<Player>().apply {
        add(Player().apply { playerType = PlayerType.Human })
        for (i in 1..3) add(Player())
    }
    var numberOfCityStates = 0

    var noBarbarians = false
    var oneCityChallenge = false

    var victoryTypes: ArrayList<VictoryType> = VictoryType.values().toCollection(ArrayList()) // By default, all victory types
    var startingEra = TechEra.Ancient

    var isOnlineMultiplayer = false
    var mods = HashSet<String>()
}