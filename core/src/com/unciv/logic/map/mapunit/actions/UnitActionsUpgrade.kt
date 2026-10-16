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

    /**  Common implementation for [getUpgradeActions] and [getUpgradeActionAnywhere] */
    private fun getUpgradeActions(unit: MapUnit, isAnywhere: Boolean) = sequence<UnitAction> {
        val unitTile = unit.getTile()
        val civInfo = unit.civ
        val upgradeUnits = unit.upgrade.getUpgradesOfKind(isSpecial = false)
        if (upgradeUnits.isEmpty()) return@sequence // can't upgrade to anything
        if (!isAnywhere && unitTile.getOwner() != civInfo) return@sequence

        for (upgrade in upgradeUnits) {
            val upgradedUnit = upgrade.upgradedUnit

            // Upgrades we lack only the resources for are shown, but disabled
            if (upgrade.availability != UpgradeAvailability.Available
                && upgrade.availability != UpgradeAvailability.AvailableExceptResources) continue

            val resourceRequirementsDelta = upgrade.newResourceRequirements
            val newResourceRequirementsString = resourceRequirementsDelta.entries
                .joinToString { "${it.value} {${it.key}}".tr() }

            val goldCostOfUpgrade = upgrade.goldCostOfUpgrade

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
                    unit.upgrade.performUpgrade(upgrade, isFree = false)
                }.takeIf { unit.upgrade.canPerformUpgrade(upgrade, isFree = false) },
            ))
        }
    }

    fun getUpgradeActions(unit: MapUnit) = getUpgradeActions(unit, isAnywhere = false)
    fun getUpgradeActionAnywhere(unit: MapUnit) = getUpgradeActions(unit, isAnywhere = true)
}
