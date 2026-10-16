package com.unciv.logic.map.mapunit.actions

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.UpgradeAvailability
import com.unciv.models.Counter
import com.unciv.models.UnitAction
import com.unciv.models.UpgradeUnitAction
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr

object UnitActionsUpgrade {

    /**  Common implementation for `getUpgradeActions(unit)`, [getFreeUpgradeAction], [getAncientRuinsUpgradeAction] and [getUpgradeActionAnywhere] */
    private fun getUpgradeActions(
        unit: MapUnit,
        isFree: Boolean,
        isSpecial: Boolean,
        isAnywhere: Boolean
    ) = sequence<UnitAction> {
        val unitTile = unit.getTile()
        val civInfo = unit.civ
        val allUpgrades = unit.upgrade.getUpgrades()
        // Special (ruins) upgrades replace the normal ones when requested and present
        val upgradeUnits = allUpgrades.filter { it.isSpecial }.take(1).takeIf { isSpecial && it.isNotEmpty() }
            ?: allUpgrades.filter { !it.isSpecial }
        if (upgradeUnits.isEmpty()) return@sequence // can't upgrade to anything
        if (!isAnywhere && unitTile.getOwner() != civInfo) return@sequence

        for (upgrade in upgradeUnits) {
            val upgradedUnit = upgrade.upgradedUnit

            val isShown = if (isFree) upgrade.availability != UpgradeAvailability.Unavailable
                else upgrade.availability == UpgradeAvailability.Available
                    || upgrade.availability == UpgradeAvailability.AvailableExceptResources
            if (!isShown) continue

            val resourceRequirementsDelta = upgrade.newResourceRequirements
            val newResourceRequirementsString = resourceRequirementsDelta.entries
                .joinToString { "${it.value} {${it.key}}".tr() }

            val goldCostOfUpgrade = if (isFree) 0 else upgrade.goldCostOfUpgrade

            // No string for "FREE" variants, these are never shown to the user.
            // The free actions are only triggered via OneTimeUnitUpgrade or OneTimeUnitSpecialUpgrade in UniqueTriggerActivation.
            val title = if (newResourceRequirementsString.isEmpty())
                "Upgrade to [${upgradedUnit.name}] ([$goldCostOfUpgrade] gold)"
            else "Upgrade to [${upgradedUnit.name}]\n([$goldCostOfUpgrade] gold, [$newResourceRequirementsString])"

            yield(UpgradeUnitAction(
                title = title,
                unitToUpgradeTo = upgradedUnit,
                goldCostOfUpgrade = goldCostOfUpgrade,
                newResourceRequirements = resourceRequirementsDelta,
                associatedUnique = upgrade.unique,
                action = {
                    unit.upgrade.performUpgrade(upgrade, isFree)
                }.takeIf { unit.upgrade.canPerformUpgrade(upgrade, isFree) },
            ))
        }
    }

    fun getUpgradeActions(unit: MapUnit) =
        getUpgradeActions(unit, isSpecial = false, isFree = false, isAnywhere = false)
    fun getFreeUpgradeAction(unit: MapUnit) =
        getUpgradeActions(unit, isSpecial = false, isFree = true, isAnywhere = true)
    fun getAncientRuinsUpgradeAction(unit: MapUnit) =
        getUpgradeActions(unit, isSpecial = true, isFree = true, isAnywhere = true)
    fun getUpgradeActionAnywhere(unit: MapUnit) =
        getUpgradeActions(unit, isSpecial = false, isFree = false, isAnywhere = true)
}
