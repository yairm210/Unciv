package com.unciv.ui.screens.worldscreen.unit.actions

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.removeConditionals
import com.unciv.models.translations.tr
import com.unciv.ui.components.fonts.Fonts

object UnitActionModifiers {
    fun canUse(unit: MapUnit, actionUnique: Unique): Boolean {
        val usagesLeft = usagesLeft(unit, actionUnique)
        return usagesLeft == null || usagesLeft > 0
    }

    fun getUsableUnitActionUniques(unit: MapUnit, actionUniqueType: UniqueType) =
        unit.getMatchingUniques(actionUniqueType)
            .filter { unique -> unique.conditionals.none { it.type == UniqueType.UnitActionExtraLimitedTimes } }
            .filter { canUse(unit, it) }

    private fun getMovementPointsToUse(actionUnique: Unique): Int {
        val movementCost = actionUnique.conditionals
            .filter { it.type == UniqueType.UnitActionMovementCost }
            .minOfOrNull { it.params[0].toInt() }
        if (movementCost != null) return movementCost
        return 1
    }

    fun activateSideEffects(unit: MapUnit, actionUnique: Unique) {
        val movementCost = getMovementPointsToUse(actionUnique)
        unit.useMovementPoints(movementCost.toFloat())

        for (conditional in actionUnique.conditionals) {
            when (conditional.type) {
                UniqueType.UnitActionConsumeUnit -> unit.consume()
                UniqueType.UnitActionLimitedTimes, UniqueType.UnitActionOnce -> {
                    if (usagesLeft(unit, actionUnique) == 1
                        && actionUnique.conditionals.any { it.type== UniqueType.UnitActionAfterWhichConsumed }) {
                        unit.consume()
                        continue
                    }
                    val usagesSoFar = unit.abilityToTimesUsed[actionUnique.placeholderText] ?: 0
                    unit.abilityToTimesUsed[actionUnique.placeholderText] = usagesSoFar + 1
                }
                else -> continue
            }
        }
    }

    /** Returns 'null' if usages are not limited */
    private fun usagesLeft(unit: MapUnit, actionUnique: Unique): Int?{
        val usagesTotal = getMaxUsages(unit, actionUnique) ?: return null
        val usagesSoFar = unit.abilityToTimesUsed[actionUnique.placeholderText] ?: 0
        return usagesTotal - usagesSoFar
    }

    private fun getMaxUsages(unit: MapUnit, actionUnique: Unique): Int? {
        val extraTimes = unit.getMatchingUniques(actionUnique.type!!)
            .filter { it.text.removeConditionals() == actionUnique.text.removeConditionals() }
            .flatMap { unique -> unique.conditionals.filter { it.type == UniqueType.UnitActionExtraLimitedTimes } }
            .sumOf { it.params[0].toInt() }

        val times = actionUnique.conditionals
            .filter { it.type == UniqueType.UnitActionLimitedTimes }
            .maxOfOrNull { it.params[0].toInt() }
        if (times != null) return times + extraTimes
        if (actionUnique.conditionals.any { it.type == UniqueType.UnitActionOnce }) return 1 + extraTimes

        return null
    }

    fun actionTextWithSideEffects(originalText: String, actionUnique: Unique, unit: MapUnit): String {
        val sideEffectString = getSideEffectString(unit, actionUnique)
        if (sideEffectString == "") return originalText
        else return "{$originalText} $sideEffectString"
    }

    private fun getSideEffectString(unit: MapUnit, actionUnique: Unique): String {
        val effects = ArrayList<String>()

        val maxUsages = getMaxUsages(unit, actionUnique)
        if (maxUsages!=null) effects += "${usagesLeft(unit, actionUnique)}/$maxUsages"

        if (actionUnique.conditionals.any { it.type == UniqueType.UnitActionConsumeUnit }
            || actionUnique.conditionals.any { it.type == UniqueType.UnitActionAfterWhichConsumed } && usagesLeft(unit, actionUnique) == 1
        ) effects += Fonts.death.toString()
        else effects += getMovementPointsToUse(actionUnique).toString() + Fonts.movement


        return if (effects.isEmpty()) ""
        else "(${effects.joinToString { it.tr() }})"
    }
}
