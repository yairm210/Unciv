package com.unciv.logic.civilization

import com.unciv.models.Counter

class VictoryManager {
    @Transient lateinit var civInfo: CivilizationInfo

    var requiredSpaceshipParts = Counter<String>()
    var currentsSpaceshipParts = Counter<String>()

    init {
        requiredSpaceshipParts.add("SS Booster", 3)
        requiredSpaceshipParts.add("SS Cockpit", 1)
        requiredSpaceshipParts.add("SS Engine", 1)
        requiredSpaceshipParts.add("SS Stasis Chamber", 1)
    }

    fun clone(): VictoryManager {
        val toReturn = VictoryManager()
        toReturn.currentsSpaceshipParts.putAll(currentsSpaceshipParts)
        return toReturn
    }

    fun unconstructedSpaceshipParts(): Counter<String> {
        val counter = requiredSpaceshipParts.clone()
        counter.remove(currentsSpaceshipParts)
        return counter
    }

    fun hasWonScientificVictory() = requiredSpaceshipParts.equals(currentsSpaceshipParts)

    fun hasWonCulturalVictory() = civInfo.policies.adoptedPolicies.count{it.endsWith("Complete")} > 3

    fun hasWonConquestVictory() = civInfo.gameInfo.civilizations.all { it==civInfo || it.isDefeated() || it.isCityState() }

    fun hasWon() = hasWonConquestVictory() || hasWonCulturalVictory() || hasWonScientificVictory()
}
