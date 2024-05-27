package com.unciv.models.ruleset.unique

import com.unciv.models.stats.Stat

object Countables {

    fun getCountableAmount(countable: String, stateForConditionals: StateForConditionals): Int? {
        if (countable.toIntOrNull() != null) return countable.toInt()

        val relevantStat = Stat.safeValueOf(countable)

        if (relevantStat != null) {
            return when {
                stateForConditionals.relevantCity != null ->
                    stateForConditionals.relevantCity!!.getStatReserve(relevantStat)
                relevantStat in Stat.statsWithCivWideField && stateForConditionals.relevantCiv != null ->
                    stateForConditionals.relevantCiv!!.getStatReserve(relevantStat)
                else -> null
            }
        }

        val gameInfo = stateForConditionals.gameInfo ?: return null

        if (countable == "year") return stateForConditionals.gameInfo!!.getYear(gameInfo.turns)

        val civInfo = stateForConditionals.relevantCiv ?: return null

        if (gameInfo.ruleset.tileResources.containsKey(countable))
            return stateForConditionals.getResourceAmount(countable)

        if (countable in gameInfo.ruleset.units){
            return civInfo.units.getCivUnits().count { it.name == countable }
        }

        if (countable in gameInfo.ruleset.buildings){
            return civInfo.cities.count { it.cityConstructions.containsBuildingOrEquivalent(countable) }
        }

        return null
    }
}
