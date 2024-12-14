package com.unciv.ui.screens.worldscreen.unit.actions

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.removeConditionals
import com.unciv.models.translations.tr
import com.unciv.ui.components.fonts.FontRulesetIcons
import com.unciv.ui.components.fonts.Fonts
import kotlin.math.ceil

object UnitActionModifiers {
    fun canUse(unit: MapUnit, actionUnique: Unique): Boolean {
        val usagesLeft = usagesLeft(unit, actionUnique)
        return usagesLeft == null || usagesLeft > 0
    }

    fun getUsableUnitActionUniques(unit: MapUnit, actionUniqueType: UniqueType) =
        unit.getMatchingUniques(actionUniqueType)
            .filter { unique -> !unique.hasModifier(UniqueType.UnitActionExtraLimitedTimes) }
            .filter { canUse(unit, it) }

    private fun getMovementPointsToUse(unit: MapUnit, actionUnique: Unique, defaultAllMovement: Boolean = false): Int {
        if (actionUnique.hasModifier(UniqueType.UnitActionMovementCostAll))
            return unit.getMaxMovement()
        val movementCost = actionUnique.modifiers
            .filter { it.type == UniqueType.UnitActionMovementCost || it.type == UniqueType.UnitActionMovementCostRequired }
            .maxOfOrNull { it.params[0].toInt() }

        if (movementCost != null)
            return movementCost
        return if (defaultAllMovement) unit.getMaxMovement() else 1
    }

    private fun getMovementPointsRequired(actionUnique: Unique): Int {
        if (actionUnique.hasModifier(UniqueType.UnitActionMovementCostAll))
            return 1
        val movementCostRequired = actionUnique.getModifiers(UniqueType.UnitActionMovementCostRequired)
            .minOfOrNull { it.params[0].toInt() }
        return movementCostRequired ?: 1
    }

    /**Check if the stat costs in this Action Modifier can be spent by the Civ/Closest City without
     * going into the negatives
     * @return Boolean
     */
    private fun canSpendStatsCost(unit: MapUnit, actionUnique: Unique): Boolean {
        for (conditional in actionUnique.getModifiers(UniqueType.UnitActionStatsCost)) {
            for ((stat, value) in conditional.stats) {
                if (unit.getClosestCity() != null) {
                    if (!unit.getClosestCity()!!.hasStatToBuy(stat, value.toInt())) {
                        return false
                    }
                } else if (stat in Stat.statsWithCivWideField) {
                    if (!unit.civ.hasStatToBuy(stat, value.toInt()))
                        return false
                } else return false // no city to spend the Stat
            }
        }

        return true
    }

    private fun canSpendStockpileCost(unit: MapUnit, actionUnique: Unique): Boolean {
        for (conditional in actionUnique.getModifiers(UniqueType.UnitActionStockpileCost)) {
            val amount = conditional.params[0].toInt()
            val resourceName = conditional.params[1]
            if (unit.civ.getResourceAmount(resourceName) < amount) {
                return false
            }
        }
        return true
    }

    /** Checks if this Action Unique can be executed, based on action modifiers
     * @param unit: The specific unit executing the Action
     * @param actionUnique: Unique that defines the Action
     * @return Boolean
     */
    fun canActivateSideEffects(unit: MapUnit, actionUnique: Unique): Boolean {
        if (!canUse(unit, actionUnique)) return false
        if (getMovementPointsRequired(actionUnique) > ceil(unit.currentMovement).toInt()) return false
        if (!canSpendStatsCost(unit, actionUnique)) return false
        if (!canSpendStockpileCost(unit, actionUnique)) return false
        return true
    }

    fun activateSideEffects(unit: MapUnit, actionUnique: Unique, defaultAllMovement: Boolean = false) {
        val movementCost = getMovementPointsToUse(unit, actionUnique, defaultAllMovement)
        unit.useMovementPoints(movementCost.toFloat())

        for (conditional in actionUnique.modifiers) {
            when (conditional.type) {
                UniqueType.UnitActionConsumeUnit -> unit.consume()
                UniqueType.UnitActionLimitedTimes, UniqueType.UnitActionOnce -> {
                    if (usagesLeft(unit, actionUnique) == 1
                        && actionUnique.hasModifier(UniqueType.UnitActionAfterWhichConsumed)) {
                        unit.consume()
                        continue
                    }
                    val usagesSoFar = unit.abilityToTimesUsed[actionUnique.text.removeConditionals()] ?: 0
                    unit.abilityToTimesUsed[actionUnique.text.removeConditionals()] = usagesSoFar + 1
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
                UniqueType.UnitActionStockpileCost -> {
                    val amount = conditional.params[0].toInt()
                    val resourceName = conditional.params[1]
                    if(unit.civ.getCivResourcesByName()[resourceName] != null)
                        unit.civ.resourceStockpiles.add(resourceName, -amount)
                }
                UniqueType.UnitActionRemovingPromotion -> {
                    val promotionName = conditional.params[0]
                    // if has a status, remove that instead - the promotion is 'safe'
                    if (unit.hasStatus(promotionName)) {
                        unit.removeStatus(promotionName)
                    } else { // check for real promotion
                        unit.promotions.removePromotion(promotionName)
                    }
                }
                else -> continue
            }
        }
    }

    /** Returns 'null' if usages are not limited */
    private fun usagesLeft(unit: MapUnit, actionUnique: Unique): Int?{
        val usagesTotal = getMaxUsages(unit, actionUnique) ?: return null
        val usagesSoFar = unit.abilityToTimesUsed[actionUnique.text.removeConditionals()] ?: 0
        return usagesTotal - usagesSoFar
    }

    private fun getMaxUsages(unit: MapUnit, actionUnique: Unique): Int? {
        val extraTimes = unit.getMatchingUniques(actionUnique.type!!)
            .filter { it.text.removeConditionals() == actionUnique.text.removeConditionals() }
            .flatMap { unique -> unique.getModifiers(UniqueType.UnitActionExtraLimitedTimes) }
            .sumOf { it.params[0].toInt() }

        val times = actionUnique.getModifiers(UniqueType.UnitActionLimitedTimes)
            .maxOfOrNull { it.params[0].toInt() }
        if (times != null) return times + extraTimes
        if (actionUnique.hasModifier(UniqueType.UnitActionOnce)) return 1 + extraTimes

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

        if (actionUnique.hasModifier(UniqueType.UnitActionStatsCost)) {
            val statCost = Stats()
            for (conditional in actionUnique.getModifiers(UniqueType.UnitActionStatsCost))
                statCost.add(conditional.stats)
            effects += statCost.toStringOnlyIcons(false)
        }

        if (actionUnique.hasModifier(UniqueType.UnitActionStockpileCost)) {
            var stockpileString = ""
            for (conditionals in actionUnique.getModifiers(UniqueType.UnitActionStockpileCost))
                stockpileString += " ${conditionals.params[0].toInt()} {${conditionals.params[1]}}"
            effects += stockpileString.removePrefix(" ") // drop leading space
        }

        if (actionUnique.hasModifier(UniqueType.UnitActionConsumeUnit)
            || actionUnique.hasModifier(UniqueType.UnitActionAfterWhichConsumed) && usagesLeft(unit, actionUnique) == 1
        ) effects += Fonts.death.toString()
        else effects += getMovementPointsToUse(
            unit,
            actionUnique,
            defaultAllMovement
        ).tr() + Fonts.movement
        
        for (removes in actionUnique.getModifiers(UniqueType.UnitActionRemovingPromotion)) {
            val promotionName = removes.params[0]
            val promotionChar = FontRulesetIcons.rulesetObjectNameToChar[promotionName]
            if (promotionChar != null) effects += "-$promotionChar"
        }

        return if (effects.isEmpty()) ""
        else "(${effects.joinToString { it.tr() }})"
    }
}
