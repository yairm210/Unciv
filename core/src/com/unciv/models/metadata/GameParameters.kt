package com.unciv.models.metadata

import com.unciv.Constants
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.VictoryType

enum class BaseRuleset(val fullName:String){
    Civ_V_Vanilla("Civ V - Vanilla"),
}

class GameParameters { // Default values are the default new game
    var difficulty = "Prince"
    var gameSpeed = GameSpeed.Standard
    var players = ArrayList<Player>().apply {
        add(Player().apply { playerType = PlayerType.Human })
        for (i in 1..3) add(Player())
    }
    var numberOfCityStates = 6

    var noBarbarians = false
    var oneCityChallenge = false
    var godMode = false
    var nuclearWeaponsEnabled = true
    var religionEnabled = false
    var permanentWarEnabled = false

    // By default, all victory types except Diplomacy as it is quite new
    var victoryTypes: ArrayList<VictoryType> = arrayListOf(VictoryType.Cultural, VictoryType.Domination, VictoryType.Scientific)  
    var startingEra = "Ancient era"

    var isOnlineMultiplayer = false
    var baseRuleset: BaseRuleset = BaseRuleset.Civ_V_Vanilla
    var mods = LinkedHashSet<String>()

    fun clone(): GameParameters {
        val parameters = GameParameters()
        parameters.difficulty = difficulty
        parameters.gameSpeed = gameSpeed
        parameters.players = ArrayList(players)
        parameters.numberOfCityStates = numberOfCityStates
        parameters.noBarbarians = noBarbarians
        parameters.oneCityChallenge = oneCityChallenge
        parameters.nuclearWeaponsEnabled = nuclearWeaponsEnabled
        parameters.religionEnabled = religionEnabled
        parameters.permanentWarEnabled = permanentWarEnabled
        parameters.victoryTypes = ArrayList(victoryTypes)
        parameters.startingEra = startingEra
        parameters.isOnlineMultiplayer = isOnlineMultiplayer
        parameters.baseRuleset = baseRuleset
        parameters.mods = LinkedHashSet(mods)
        return parameters
    }

    // For debugging and GameStarter console output
    override fun toString() = sequence<String> {
            yield("$difficulty $gameSpeed $startingEra")
            yield("${players.count { it.playerType == PlayerType.Human }} ${PlayerType.Human}")
            yield("${players.count { it.playerType == PlayerType.AI }} ${PlayerType.AI}")
            yield("$numberOfCityStates CS")
            if (isOnlineMultiplayer) yield("Online Multiplayer")
            if (noBarbarians) yield("No barbs")
            if (oneCityChallenge) yield("OCC")
            if (!nuclearWeaponsEnabled) yield("No nukes")
            if (religionEnabled) yield("Religion")
            if (!permanentWarEnabled) yield("No Permanent War")
            if (godMode) yield("God mode")
            for (victoryType in VictoryType.values()) {
                if (victoryType !in victoryTypes) yield("No $victoryType Victory")
            }
            yield(if (mods.isEmpty()) "no mods" else mods.joinToString(",", "mods=(", ")", 6) )
        }.joinToString(prefix = "(", postfix = ")")
}