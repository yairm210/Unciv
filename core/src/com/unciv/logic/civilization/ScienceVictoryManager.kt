package com.unciv.logic.civilization

import com.unciv.models.Counter

class ScienceVictoryManager {
    var requiredParts = Counter<String>()
    var currentParts = Counter<String>()

    init {
        requiredParts.add("SS Booster", 3)
        requiredParts.add("SS Cockpit", 1)
        requiredParts.add("SS Engine", 1)
        requiredParts.add("SS Statis Chamber", 1)
    }

    fun unconstructedParts(): Counter<String> {
        val counter = requiredParts.clone()
        counter.remove(currentParts)
        return counter
    }

    fun hasWon() = requiredParts.equals(currentParts)
}
