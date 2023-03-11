package com.unciv.logic.civilization.managers

import com.unciv.Constants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.toPercent
import kotlin.collections.HashMap

interface RepeatableTechManager {
    companion object {
        private const val defaultScorePerTech = 10
    }

    data class RepeatableTechCounter(
        val count: Int,
        val cost: Int,
        val score: Int
    ) {
        @Suppress("unused")  // for serialization
        constructor() : this(1, 0, 0)
    }
    var repeatableTechs: HashMap<String, RepeatableTechCounter>

    private data class RepeatableTechFactors(
        val baseCost: Int,
        val costIncrement: Int,
        val costMultiplier: Float,
        val scorePerTech: Int
    )

    private fun getTechFactors(tech: Technology): RepeatableTechFactors {
        val costIncrement = tech.getMatchingUniques(UniqueType.ResearchableMultipleTimesCost)
            .map { it.params[0].toInt() }
            .sum()
        val costMultiplier = tech.getMatchingUniques(UniqueType.ResearchableMultipleTimesCostPercent)
            .map { it.params[0].toFloat() }
            .sum().toPercent()
        val scorePerTech = tech.getMatchingUniques(UniqueType.ResearchableMultipleTimesScore)
            .map { it.params[0].toInt() }
            // Allow fallback for older mods that lack the Unique
            // At some point when all mods use the Unique - replace that fold() with sum()
            .fold(null) { acc: Int?, value: Int -> (acc ?: 0) + value }
            ?: defaultScorePerTech
        return RepeatableTechFactors(tech.cost, costIncrement, costMultiplier, scorePerTech)
    }

    private fun calculateCounter(tech: Technology, count: Int): RepeatableTechCounter {
        val factors = getTechFactors(tech)
        val cost = (1..count).fold(factors.baseCost) { acc, _ ->
            acc * factors.costMultiplier.toInt() + factors.costIncrement
        }
        return RepeatableTechCounter(count, cost, count * factors.scorePerTech)
    }

    fun incrementRepeatableTech(tech: Technology) {
        val oldCount = repeatableTechs[tech.name]?.count ?: 0
        repeatableTechs[tech.name] = calculateCounter(tech, 1 + oldCount)
    }

    fun getRepeatableTechScore() = repeatableTechs.values.sumOf { it.score }

    fun costOfRepeatableTech(techName: String): Int? {
        return repeatableTechs[techName]?.cost
    }

    @Suppress("DEPRECATION")
    fun migrateRepeatableTechsResearched(ruleset: Ruleset) {
        this as TechManager
        if (repeatingTechsResearched == 0) return
        val tech = ruleset.technologies[Constants.futureTech] ?: return
        repeatableTechs[Constants.futureTech] = calculateCounter(tech, repeatingTechsResearched)
        repeatingTechsResearched = 0
    }
}
