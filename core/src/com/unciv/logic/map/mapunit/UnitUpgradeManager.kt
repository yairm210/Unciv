package com.unciv.logic.map.mapunit

import com.unciv.logic.UncivShowableException
import com.unciv.models.ruleset.RejectionReasonType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.components.extensions.toPercent
import kotlin.math.pow

class UnitUpgradeManager(val unit:MapUnit) {

    /** Returns the cheapest FULL upgrade path starting from [upgradeUnit],
     * Does not check what we can or cannot build currently.
     * Does not contain current baseunit, so will be empty if no upgrades. */
    private fun getUpgradePath(upgradeUnit: BaseUnit?): Iterable<BaseUnit> {
        var currentUnit = unit.baseUnit
        val upgradeList = linkedSetOf<BaseUnit>()
        if (upgradeUnit == null ||
            upgradeUnit.name !in currentUnit.getUpgradeUnits(StateForConditionals(unit.civ, unit = unit)))
            return upgradeList
        var nextUpgrade = if (upgradeUnit != null ) unit.civ.getEquivalentUnit(upgradeUnit) else null
        while (nextUpgrade != null) {
            if (nextUpgrade in upgradeList)
                throw(UncivShowableException("Circular or self-referencing upgrade path for ${currentUnit.name}"))
            currentUnit = nextUpgrade
            upgradeList.add(currentUnit)
            nextUpgrade = currentUnit.getRulesetUpgradeUnits(StateForConditionals(unit.civ, unit = unit))
                .map { unit.civ.getEquivalentUnit(it) }
                .minByOrNull { it.cost }
        }
        return upgradeList
    }

    /** Get the base unit this map unit could upgrade to, respecting researched tech and nation uniques only.
     *  Note that if the unit can't upgrade, the current BaseUnit is returned.
     */
    // Used from UnitAutomation
    fun getUnitToUpgradeTo(): BaseUnit {
        val cheapestUnit = unit.baseUnit.getRulesetUpgradeUnits(StateForConditionals(unit.civ, unit = unit))
            .map { unit.civ.getEquivalentUnit(it) }.minByOrNull { it.cost }
        val upgradePath = getUpgradePath(cheapestUnit)

        fun isInvalidUpgradeDestination(baseUnit: BaseUnit): Boolean{
            if (!unit.civ.tech.isResearched(baseUnit))
                return true
            return baseUnit.getMatchingUniques(UniqueType.OnlyAvailableWhen, StateForConditionals.IgnoreConditionals).any {
                !it.conditionalsApply(StateForConditionals(unit.civ, unit = unit ))
            }
        }

        for (baseUnit in upgradePath.reversed()) {
            if (isInvalidUpgradeDestination(baseUnit)) continue
            return baseUnit
        }
        return unit.baseUnit
    }

    /** Check whether this unit can upgrade to [unitToUpgradeTo]. This does not check or follow the
     *  normal upgrade chain defined by [BaseUnit.getUpgradeUnits]
     *  @param ignoreRequirements Ignore possible tech/policy/building requirements (e.g. resource requirements still count).
     *          Used for upgrading units via ancient ruins.
     *  @param ignoreResources Ignore resource requirements (tech still counts)
     *          Used to display disabled Upgrade button
     */
    fun canUpgrade(
        unitToUpgradeTo: BaseUnit,
        ignoreRequirements: Boolean = false,
        ignoreResources: Boolean = false
    ): Boolean {
        if (unit.name == unitToUpgradeTo.name) return false

        val rejectionReasons = unitToUpgradeTo.getRejectionReasons(unit.civ, additionalResources = unit.getResourceRequirementsPerTurn())

        var relevantRejectionReasons = rejectionReasons.filterNot { it.isConstructionRejection() }
        if (ignoreRequirements)
            relevantRejectionReasons = relevantRejectionReasons.filterNot { it.techPolicyEraWonderRequirements() }
        if (ignoreResources)
            relevantRejectionReasons = relevantRejectionReasons.filterNot { it.type == RejectionReasonType.ConsumesResources }
        return relevantRejectionReasons.none()
    }

    /** Determine gold cost of a Unit Upgrade, potentially over several steps.
     *  @param unitToUpgradeTo the final BaseUnit. Must be reachable via normal upgrades or else
     *         the function will return the cost to upgrade to the last possible and researched normal upgrade.
     *  @return Gold cost in increments of 5, never negative. Will return 0 for invalid inputs (unit can't upgrade or is is already a [unitToUpgradeTo])
     *  @see   <a href="https://github.com/dmnd/CvGameCoreSource/blob/6501d2398113a5100ffa854c146fb6f113992898/CvGameCoreDLL_Expansion1/CvUnit.cpp#L7728">CvUnit::upgradePrice</a>
     */
    // Only one use from getUpgradeAction at the moment, so AI-specific rules omitted
    //todo Does the AI never buy upgrades???
    fun getCostOfUpgrade(unitToUpgradeTo: BaseUnit): Int {
        // Source rounds to int every step, we don't
        //TODO From the source, this should apply _Production_ modifiers (Temple of Artemis? GameSpeed! StartEra!), at the moment it doesn't

        var goldCostOfUpgrade = 0

        val ruleset = unit.civ.gameInfo.ruleset
        val constants = ruleset.modOptions.constants.unitUpgradeCost
        // apply modifiers: Wonders (Pentagon), Policies (Professional Army). Cached outside loop despite
        // the UniqueType being allowed on a BaseUnit - we don't have a MapUnit in the loop.
        // Actually instantiating every intermediate to support such mods: todo
        var civModifier = 1f
        val stateForConditionals = StateForConditionals(unit.civ, unit = unit)
        for (unique in unit.civ.getMatchingUniques(UniqueType.UnitUpgradeCost, stateForConditionals))
            civModifier *= unique.params[0].toPercent()

        val upgradePath = getUpgradePath(unitToUpgradeTo)
        var currentUnit = unit.baseUnit
        for (baseUnit in upgradePath) {
            // do clamping and rounding here so upgrading stepwise costs the same as upgrading far down the chain
            var stepCost = constants.base
            stepCost += (constants.perProduction * (baseUnit.cost - currentUnit.cost)).coerceAtLeast(0f)
            val era = baseUnit.era(ruleset)
            if (era != null)
                stepCost *= (1f + era.eraNumber * constants.eraMultiplier)
            stepCost = (stepCost * civModifier).pow(constants.exponent)
            stepCost *= unit.civ.gameInfo.speed.modifier
            goldCostOfUpgrade += (stepCost / constants.roundTo).toInt() * constants.roundTo
            if (baseUnit == unitToUpgradeTo)
                break  // stop at requested BaseUnit to upgrade to
            currentUnit = baseUnit
        }


        return goldCostOfUpgrade
    }


}
