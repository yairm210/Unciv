package com.unciv.models.metadata

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.Speed

enum class BaseRuleset(val fullName:String){
    Civ_V_Vanilla("Civ V - Vanilla"),
    Civ_V_GnK("Civ V - Gods & Kings"),
}

class GameParameters : IsPartOfGameInfoSerialization { // Default values are the default new game
    var difficulty = "Prince"
    var speed = Speed.DEFAULT

    @Deprecated("Since 4.1.11")
    var gameSpeed = ""
    var players = ArrayList<Player>().apply {
        add(Player().apply { playerType = PlayerType.Human })
        for (i in 1..3) add(Player())
    }
    var numberOfCityStates = 6

    var noBarbarians = false
    var ragingBarbarians = false
    var oneCityChallenge = false
    var godMode = false
    var nuclearWeaponsEnabled = true
    @Deprecated("As of 4.2.3")
    var religionEnabled = true
    var espionageEnabled = false
    var noStartBias = false

    var victoryTypes: ArrayList<String> = arrayListOf()
    var startingEra = "Ancient era"

    var isOnlineMultiplayer = false
    var anyoneCanSpectate = false
    var baseRuleset: String = BaseRuleset.Civ_V_GnK.fullName
    var mods = LinkedHashSet<String>()

    var maxTurns = 500

    fun clone(): GameParameters {
        val parameters = GameParameters()
        parameters.difficulty = difficulty
        parameters.speed = speed
        parameters.players = ArrayList(players)
        parameters.numberOfCityStates = numberOfCityStates
        parameters.noBarbarians = noBarbarians
        parameters.ragingBarbarians = ragingBarbarians
        parameters.oneCityChallenge = oneCityChallenge
        parameters.nuclearWeaponsEnabled = nuclearWeaponsEnabled
        parameters.religionEnabled = religionEnabled
        parameters.victoryTypes = ArrayList(victoryTypes)
        parameters.startingEra = startingEra
        parameters.isOnlineMultiplayer = isOnlineMultiplayer
        parameters.baseRuleset = baseRuleset
        parameters.mods = LinkedHashSet(mods)
        parameters.maxTurns = maxTurns
        return parameters
    }

    // For debugging and GameStarter console output
    override fun toString() = sequence {
            yield("$difficulty $speed $startingEra")
            yield("${players.count { it.playerType == PlayerType.Human }} ${PlayerType.Human}")
            yield("${players.count { it.playerType == PlayerType.AI }} ${PlayerType.AI}")
            yield("$numberOfCityStates CS")
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

    fun getModsAndBaseRuleset(): HashSet<String> {
        return mods.toHashSet().apply { add(baseRuleset) }
    }
}
