package com.unciv.models.ruleset.unique

import com.unciv.models.stats.Stat

object Countables {

    fun getCountableAmount(countable: String, stateForConditionals: StateForConditionals): Int? {
        if (countable.toIntOrNull() != null) return countable.toInt()

        val relevantStat = Stat.safeValueOf(countable)
        if (relevantStat != null) return stateForConditionals.getStatAmount(relevantStat)

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
