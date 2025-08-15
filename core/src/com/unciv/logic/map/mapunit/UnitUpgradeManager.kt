package com.unciv.logic.map.mapunit

import com.unciv.models.ruleset.RejectionReasonType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.components.extensions.toPercent
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsUpgrade
import yairm210.purity.annotations.Readonly
import kotlin.math.pow

class UnitUpgradeManager(val unit: MapUnit) {

    /** Check whether this unit can upgrade to [unitToUpgradeTo]. This does not check or follow the
     *  normal upgrade chain defined by [BaseUnit.getUpgradeUnits]
     *  @param ignoreRequirements Ignore possible tech/policy/building requirements (e.g. resource requirements still count).
     *          Used for upgrading units via ancient ruins.
     *  @param ignoreResources Ignore resource requirements (tech still counts)
     *          Used to display disabled Upgrade button
     */
    @Readonly
    fun canUpgrade(
        unitToUpgradeTo: BaseUnit,
        ignoreRequirements: Boolean = false,
        ignoreResources: Boolean = false
    ): Boolean {
        if (unit.name == unitToUpgradeTo.name) return false

        val rejectionReasons = unitToUpgradeTo.getRejectionReasons(unit.civ, additionalResources = unit.getResourceRequirementsPerTurn())

        var relevantRejectionReasons = rejectionReasons.filterNot {
            it.isConstructionRejection() || it.type == RejectionReasonType.Obsoleted
        }
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
        val stateForConditionals = unit.cache.state
        for (unique in unit.civ.getMatchingUniques(UniqueType.UnitUpgradeCost, stateForConditionals))
            civModifier *= unique.params[0].toPercent()

        var cost = constants.base
        cost += (constants.perProduction * (unitToUpgradeTo.cost - unit.baseUnit.cost)).coerceAtLeast(0f)
        val era = unitToUpgradeTo.era(ruleset)
        if (era != null)
            cost *= (1f + era.eraNumber * constants.eraMultiplier)
        cost = (cost * civModifier).pow(constants.exponent)
        cost *= unit.civ.gameInfo.speed.modifier
        goldCostOfUpgrade += (cost / constants.roundTo).toInt() * constants.roundTo

        return goldCostOfUpgrade
    }

    /** _Perform_ an upgrade, assuming validity checks were already passed.
     *
     *  Continuing to use a reference to this manager or its unit after this call is invalid!
     *
     *  Please use [UnitActionsUpgrade.getUpgradeActions] instead if at all possible.
     *
     *  Note - the upgraded unit is a new instance, and it's possible this method will need to place it on a different tile.
     *  It is also possible the placement fails and the original is resurrected - in which case it is a **new instance** as well.
     *  It might be desirable to return `newUnit` (or `resurrectedUnit`) if needed -
     *  but then the lambda in UnitActionsUpgrade will complain and need to be forced back to Unit type.
     */
    fun performUpgrade(upgradedUnit: BaseUnit, isFree: Boolean, goldCostOfUpgrade: Int? = null) {
        // When mashing the upgrade button, you can 'queue' 2 upgrade actions
        //  If both are performed, what you get is the unit is doubled
        //  This prevents this, since we lack another way to do so -_-'  
        if (unit.isDestroyed) return  
            
        unit.destroy(destroyTransportedUnit = false)
        val civ = unit.civ
        val position = unit.currentTile.position
        val newUnit = civ.units.placeUnitNearTile(position, upgradedUnit, unit.id)

        /** We were UNABLE to place the new unit, which means that the unit failed to upgrade!
         * The only known cause of this currently is "land units upgrading to water units" which fail to be placed.
         */
        if (newUnit == null) {
            val resurrectedUnit = civ.units.placeUnitNearTile(position, unit.baseUnit)!!
            unit.copyStatisticsTo(resurrectedUnit)
            return
        }

        // Managed to upgrade
        if (!isFree) civ.addGold(-(goldCostOfUpgrade ?: getCostOfUpgrade(upgradedUnit)))
        unit.copyStatisticsTo(newUnit)
        newUnit.currentMovement = 0f
        // wake up if lost ability to fortify
        if (newUnit.isFortified() && !newUnit.canFortify(ignoreAlreadyFortified = true))
            newUnit.action = null
        // wake up from Guarding if can't Withdraw
        if (newUnit.isGuarding() && !newUnit.hasUnique(UniqueType.WithdrawsBeforeMeleeCombat))
            newUnit.action = null
    }
}
