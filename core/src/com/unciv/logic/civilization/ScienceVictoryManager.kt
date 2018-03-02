package com.unciv.logic.civilization

import com.unciv.models.linq.LinqCounter

class ScienceVictoryManager {

    var requiredParts = LinqCounter<String>()
    var currentParts = LinqCounter<String>()

    fun unconstructedParts(): LinqCounter<String> {
        val counter = requiredParts.clone()
        counter.remove(currentParts)
        return counter
    }

    init {
        requiredParts.add("SS Booster", 3)
        requiredParts.add("SS Cockpit", 1)
        requiredParts.add("SS Engine", 1)
        requiredParts.add("SS Statis Chamber", 1)
    }
}
