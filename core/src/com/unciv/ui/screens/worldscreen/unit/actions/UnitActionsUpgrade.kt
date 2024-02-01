package com.unciv.ui.screens.worldscreen.unit.actions

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.Counter
import com.unciv.models.UnitAction
import com.unciv.models.UpgradeUnitAction
import com.unciv.models.ruleset.unique.StateForConditionals
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
        val specialUpgradesTo = if (isSpecial)
            unit.baseUnit().getMatchingUniques(UniqueType.RuinsUpgrade, StateForConditionals(civInfo, unit = unit))
                .map { it.params[0] }.firstOrNull()
        else null
        val upgradeUnits = if (specialUpgradesTo != null) sequenceOf(specialUpgradesTo)
            else unit.baseUnit.getUpgradeUnits(StateForConditionals(civInfo, unit = unit))
        if (upgradeUnits.none()) return@sequence // can't upgrade to anything
        if (!isAnywhere && unitTile.getOwner() != civInfo) return@sequence

        for (upgradesTo in upgradeUnits){
            val upgradedUnit = civInfo.getEquivalentUnit(upgradesTo)

            if (!unit.upgrade.canUpgrade(unitToUpgradeTo = upgradedUnit, ignoreRequirements = isFree, ignoreResources = true))
                continue

            // Check _new_ resource requirements (display only - yes even for free or special upgrades)
            // Using Counter to aggregate is a bit exaggerated, but - respect the mad modder.
            val resourceRequirementsDelta = Counter<String>()
            for ((resource, amount) in unit.getResourceRequirementsPerTurn())
                resourceRequirementsDelta.add(resource, -amount)
            for ((resource, amount) in upgradedUnit.getResourceRequirementsPerTurn(StateForConditionals(unit.civ, unit = unit)))
                resourceRequirementsDelta.add(resource, amount)
            for ((resource, _) in resourceRequirementsDelta.filter { it.value < 0 })  // filter copies, so no CCM
                resourceRequirementsDelta[resource] = 0
            val newResourceRequirementsString = resourceRequirementsDelta.entries
                .joinToString { "${it.value} {${it.key}}".tr() }

            val goldCostOfUpgrade = if (isFree) 0 else unit.upgrade.getCostOfUpgrade(upgradedUnit)

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
                action = {
                    unit.destroy(destroyTransportedUnit = false)
                    val newUnit = civInfo.units.placeUnitNearTile(unitTile.position, upgradedUnit)

                    /** We were UNABLE to place the new unit, which means that the unit failed to upgrade!
                     * The only known cause of this currently is "land units upgrading to water units" which fail to be placed.
                     */
                    if (newUnit == null) {
                        val resurrectedUnit = civInfo.units.placeUnitNearTile(unitTile.position, unit.baseUnit)!!
                        unit.copyStatisticsTo(resurrectedUnit)
                    } else { // Managed to upgrade
                        if (!isFree) civInfo.addGold(-goldCostOfUpgrade)
                        unit.copyStatisticsTo(newUnit)
                        newUnit.currentMovement = 0f
                    }
                }.takeIf {
                    isFree || (
                        unit.civ.gold >= goldCostOfUpgrade
                            && unit.currentMovement > 0
                            && unitTile.getOwner() == civInfo
                            && !unit.isEmbarked()
                            && unit.upgrade.canUpgrade(unitToUpgradeTo = upgradedUnit)
                        )
                }
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
