package com.unciv.logic.civilization

import com.unciv.models.Counter

@Deprecated("As of 2.11.3")
class ScienceVictoryManager {
    var requiredParts = Counter<String>()
    var currentParts = Counter<String>()

    init {
        requiredParts.add("SS Booster", 3)
        requiredParts.add("SS Cockpit", 1)
        requiredParts.add("SS Engine", 1)
        requiredParts.add("SS Stasis Chamber", 1)
    }

    fun clone(): ScienceVictoryManager {
        val toReturn = ScienceVictoryManager()
        toReturn.currentParts.putAll(currentParts)
        return toReturn
    }

    fun unconstructedParts(): Counter<String> {
        val counter = requiredParts.clone()
        counter.remove(currentParts)
        return counter
    }

    fun hasWon() = requiredParts.equals(currentParts)
}
