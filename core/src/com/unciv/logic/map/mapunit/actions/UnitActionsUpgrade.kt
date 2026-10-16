package com.unciv.logic.map.mapunit.actions

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.PossibleUpgrade
import com.unciv.models.UnitAction
import com.unciv.models.UpgradeUnitAction
import com.unciv.models.translations.tr

object UnitActionsUpgrade {

    fun getUpgradeActions(unit: MapUnit) = sequence<UnitAction> {
        if (unit.getTile().getOwner() != unit.civ) return@sequence

        for (upgrade in unit.upgrade.getUpgradesOfKind(isSpecial = false)) {
            // Upgrades we lack only the resources for are shown, but disabled
            if (!upgrade.isShownToPlayer) continue

            yield(UpgradeUnitAction(
                title = getUpgradeTitle(upgrade),
                upgrade = upgrade,
                action = {
                    unit.upgrade.performUpgrade(upgrade, isFree = false)
                }.takeIf { unit.upgrade.canPerformUpgrade(upgrade, isFree = false) },
            ))
        }
    }

    fun getUpgradeTitle(upgrade: PossibleUpgrade): String {
        val newResourceRequirementsString = upgrade.newResourceRequirements.entries
            .joinToString { "${it.value} {${it.key}}".tr() }
        val name = upgrade.upgradedUnit.name
        val gold = upgrade.goldCostOfUpgrade
        return if (newResourceRequirementsString.isEmpty()) "Upgrade to [$name] ([$gold] gold)"
            else "Upgrade to [$name]\n([$gold] gold, [$newResourceRequirementsString])"
    }
}
