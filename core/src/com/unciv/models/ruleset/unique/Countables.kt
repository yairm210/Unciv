package com.unciv.models.ruleset.unique

import com.unciv.models.stats.Stat
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters

object Countables {

    fun getCountableAmount(countable: String, stateForConditionals: StateForConditionals): Int? {
        if (countable.toIntOrNull() != null) return countable.toInt()

        val relevantStat = Stat.safeValueOf(countable)
        if (relevantStat != null) return stateForConditionals.getStatAmount(relevantStat)

        val gameInfo = stateForConditionals.gameInfo ?: return null

        if (countable == "turns") return gameInfo.turns
        if (countable == "year") return gameInfo.getYear(gameInfo.turns)

        val civInfo = stateForConditionals.relevantCiv ?: return null

        if (countable == "Cities") return civInfo.cities.size

        val placeholderParameters = countable.getPlaceholderParameters()
        if (countable.equalsPlaceholderText("[] Cities"))
            return civInfo.cities.count { it.matchesFilter(placeholderParameters[0]) }

        if (countable == "Units") return civInfo.units.getCivUnitsSize()
        if (countable.equalsPlaceholderText("[] Units"))
            return civInfo.units.getCivUnits().count { it.matchesFilter(placeholderParameters[0]) }

        if (countable.equalsPlaceholderText("[] Buildings"))
            return civInfo.cities.sumOf { it.cityConstructions.getBuiltBuildings()
                .count { it.matchesFilter(placeholderParameters[0]) } }
        
        if (countable.equalsPlaceholderText("Remaining [] Civilizations"))
            return gameInfo.civilizations.filter { !it.isDefeated() }
                .count { it.matchesFilter(placeholderParameters[0]) }
        
        if (countable.equalsPlaceholderText("Completed Policy branches"))
            return civInfo.getCompletedPolicyBranchesCount()
        
        if (countable.equalsPlaceholderText("Owned [] Tiles")) 
            return civInfo.cities.sumOf { it.getTiles().count { it.matchesFilter(placeholderParameters[0]) } }

        if (gameInfo.ruleset.tileResources.containsKey(countable))
            return stateForConditionals.getResourceAmount(countable)

        return null
    }
}
