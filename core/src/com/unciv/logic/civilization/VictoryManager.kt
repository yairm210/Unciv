package com.unciv.logic.civilization

import com.unciv.models.Counter
import com.unciv.models.VictoryType

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

    fun spaceshipPartsRemaining() = requiredSpaceshipParts.values.sum() - currentsSpaceshipParts.values.sum()

    fun hasWonScientificVictory() = civInfo.gameInfo.gameParameters.victoryTypes.contains(VictoryType.Scientific)
            && spaceshipPartsRemaining()==0

    fun hasWonCulturalVictory() = civInfo.gameInfo.gameParameters.victoryTypes.contains(VictoryType.Cultural)
            && civInfo.policies.adoptedPolicies.count{it.endsWith("Complete")} > 4

    fun hasWonDominationVictory() = civInfo.gameInfo.gameParameters.victoryTypes.contains(VictoryType.Domination)
            && civInfo.gameInfo.civilizations.all { it==civInfo || it.isDefeated() || it.isCityState() }

    fun hasWonVictoryType(): VictoryType? {
        if(!civInfo.isMajorCiv()) return null
        if(hasWonDominationVictory()) return VictoryType.Domination
        if(hasWonScientificVictory()) return VictoryType.Scientific
        if(hasWonCulturalVictory()) return VictoryType.Cultural
        return null
    }

    fun hasWon() = hasWonVictoryType()!=null
}
