package com.unciv.models.metadata

import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapType
import com.unciv.models.gamebasics.VictoryType

class GameParameters { // Default values are the default new game
    var difficulty = "Prince"
    var gameSpeed = GameSpeed.Standard
    var mapRadius = 20
    var players = ArrayList<Player>().apply {
        add(Player().apply { playerType = PlayerType.Human })
        for (i in 1..3) add(Player())
    }
    var numberOfCityStates = 0
    var mapType = MapType.Perlin
    var noBarbarians = false
    var mapFileName: String? = null
    var victoryTypes: ArrayList<VictoryType> = VictoryType.values().toCollection(ArrayList()) // By default, all victory types

    var isOnlineMultiplayer = false
}