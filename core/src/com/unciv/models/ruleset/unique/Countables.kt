package com.unciv.models.ruleset.unique

import com.unciv.models.stats.Stat

object Countables {

    fun getCountableAmount(countable: String, stateForConditionals: StateForConditionals): Int? {
        if (countable.toIntOrNull() != null) return countable.toInt()

        val relevantStat = Stat.safeValueOf(countable)

        if (relevantStat != null) {
            return if (stateForConditionals.relevantCity != null) {
                stateForConditionals.relevantCity!!.getStatReserve(relevantStat)
            } else if (relevantStat in Stat.statsWithCivWideField && stateForConditionals.relevantCiv != null) {
                stateForConditionals.relevantCiv!!.getStatReserve(relevantStat)
            } else {
                null
            }
        }

        if (stateForConditionals.gameInfo == null) return null

        if (countable == "year") return stateForConditionals.gameInfo!!.getYear(stateForConditionals.gameInfo!!.turns)
        if (stateForConditionals.gameInfo!!.ruleset.tileResources.containsKey(countable))
            return stateForConditionals.getResourceAmount(countable)

        return null
    }
}