package com.unciv.ui.screens.worldscreen.unit.actions

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.removeConditionals
import com.unciv.models.translations.tr
import com.unciv.ui.components.fonts.Fonts
import kotlin.math.ceil

object UnitActionModifiers {
    fun canUse(unit: MapUnit, actionUnique: Unique): Boolean {
        val usagesLeft = usagesLeft(unit, actionUnique)
        return usagesLeft == null || usagesLeft > 0
    }

    fun getUsableUnitActionUniques(unit: MapUnit, actionUniqueType: UniqueType) =
        unit.getMatchingUniques(actionUniqueType)
            .filter { unique -> unique.conditionals.none { it.type == UniqueType.UnitActionExtraLimitedTimes } }
            .filter { canUse(unit, it) }

    private fun getMovementPointsToUse(unit: MapUnit, actionUnique: Unique, defaultAllMovement: Boolean = false): Int {
        if (actionUnique.conditionals.any { it.type == UniqueType.UnitActionMovementCostAll })
            return unit.getMaxMovement()
        val movementCost = actionUnique.conditionals
            .filter { it.type == UniqueType.UnitActionMovementCost }
            .minOfOrNull { it.params[0].toInt() }
        val movementCostRequired = actionUnique.conditionals
            .filter { it.type == UniqueType.UnitActionMovementCostRequired }
            .minOfOrNull { it.params[0].toInt() }
        if (movementCost != null && movementCostRequired != null) {
            return maxOf(movementCost, movementCostRequired)
        } else {
            if (movementCost != null)
                return movementCost
            if (movementCostRequired != null)
                return movementCostRequired
        }
        return if (defaultAllMovement) unit.getMaxMovement() else 1
    }

    private fun getMovementPointsRequired(actionUnique: Unique): Int {
        if (actionUnique.conditionals.any { it.type == UniqueType.UnitActionMovementCostAll })
            return 1
        val movementCostRequired = actionUnique.conditionals
            .filter { it.type == UniqueType.UnitActionMovementCostRequired }
            .minOfOrNull { it.params[0].toInt() }
        return movementCostRequired ?: 1
    }

    /**Check if the stat costs in this Action Modifier can be spent by the Civ/Closest City without
     * going into the negatives
     * @return Boolean
     */
    private fun canSpendStatsCost(unit: MapUnit, actionUnique: Unique): Boolean {
        for (conditional in actionUnique.conditionals.filter { it.type == UniqueType.UnitActionStatsCost }) {
            for ((stat, value) in conditional.stats) {
                if (stat in Stat.statsWithCivWideField) {
                    if (!unit.civ.hasStatToBuy(stat, value.toInt()))
                        return false
                } else {
                    if (unit.getClosestCity() != null) {
                        if (!unit.getClosestCity()!!.hasStatToBuy(stat, value.toInt())) {
                            return false
                        }
                    } else return false
                }
            }
        }

        return true
    }

    /**Checks if this Action Unique can be executed, based on action modifiers
     * @return Boolean
     */
    fun canActivateSideEffects(unit: MapUnit, actionUnique: Unique): Boolean {
        return canUse(unit, actionUnique)
            && getMovementPointsRequired(actionUnique) <= ceil(unit.currentMovement).toInt()
            && canSpendStatsCost(unit, actionUnique)
    }

    fun activateSideEffects(unit: MapUnit, actionUnique: Unique, defaultAllMovement: Boolean = false) {
        val movementCost = getMovementPointsToUse(unit, actionUnique, defaultAllMovement)
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
                UniqueType.UnitActionStatsCost -> {
                    // do Stat costs, either Civ-wide or local city
                    // should have validated this doesn't send us negative
                    for ((stat, value) in conditional.stats) {
                        if (stat in Stat.statsWithCivWideField) {
                            unit.civ.addStat(stat, -value.toInt())
                        } else unit.getClosestCity()?.addStat(stat, -value.toInt())
                    }
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

    fun getSideEffectString(unit: MapUnit, actionUnique: Unique, defaultAllMovement: Boolean = false): String {
        val effects = ArrayList<String>()

        val maxUsages = getMaxUsages(unit, actionUnique)
        if (maxUsages!=null) effects += "${usagesLeft(unit, actionUnique)}/$maxUsages"

        if (actionUnique.conditionals.any { it.type == UniqueType.UnitActionStatsCost}) {
            val statCost = Stats()
            for (conditional in actionUnique.conditionals.filter { it.type == UniqueType.UnitActionStatsCost })
                statCost.add(conditional.stats)
            effects += (statCost * -1).toStringOnlyIcons()
        }

        if (actionUnique.conditionals.any { it.type == UniqueType.UnitActionConsumeUnit }
            || actionUnique.conditionals.any { it.type == UniqueType.UnitActionAfterWhichConsumed } && usagesLeft(unit, actionUnique) == 1
        ) effects += Fonts.death.toString()
        else effects += getMovementPointsToUse(unit, actionUnique, defaultAllMovement).toString() + Fonts.movement

        return if (effects.isEmpty()) ""
        else "(${effects.joinToString { it.tr() }})"
    }
}
