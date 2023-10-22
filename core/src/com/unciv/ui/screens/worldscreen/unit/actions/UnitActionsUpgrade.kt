package com.unciv.ui.screens.worldscreen.unit.actions

import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.Counter
import com.unciv.models.UnitAction
import com.unciv.models.UpgradeUnitAction
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr

object UnitActionsUpgrade {

    internal fun addUnitUpgradeAction(
        unit: MapUnit,
        actionList: ArrayList<UnitAction>
    ) {
        val upgradeAction = getUpgradeAction(unit)
        if (upgradeAction != null) actionList += upgradeAction
    }

    /**  Common implementation for [getUpgradeAction], [getFreeUpgradeAction] and [getAncientRuinsUpgradeAction] */
    private fun getUpgradeAction(
        unit: MapUnit,
        isFree: Boolean,
        isSpecial: Boolean,
        isAnywhere: Boolean
    ): UnitAction? {
        val specialUpgradesTo = unit.baseUnit().getMatchingUniques(UniqueType.RuinsUpgrade).map { it.params[0] }.firstOrNull()
        if (unit.baseUnit().upgradesTo == null && specialUpgradesTo == null) return null // can't upgrade to anything
        val unitTile = unit.getTile()
        val civInfo = unit.civ
        if (!isAnywhere && unitTile.getOwner() != civInfo) return null

        val upgradesTo = unit.baseUnit().upgradesTo
        val upgradedUnit = when {
            isSpecial && specialUpgradesTo != null -> civInfo.getEquivalentUnit(specialUpgradesTo)
            (isFree || isSpecial) && upgradesTo != null -> civInfo.getEquivalentUnit(upgradesTo) // Only get DIRECT upgrade
            else -> unit.upgrade.getUnitToUpgradeTo() // Get EVENTUAL upgrade, all the way up the chain
        }

        if (!unit.upgrade.canUpgrade(unitToUpgradeTo = upgradedUnit, ignoreRequirements = isFree, ignoreResources = true))
            return null

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

        return UpgradeUnitAction(
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
        )
    }

    fun getUpgradeAction(unit: MapUnit) =
            getUpgradeAction(unit, isFree = false, isSpecial = false, isAnywhere = false)
    fun getFreeUpgradeAction(unit: MapUnit) =
            getUpgradeAction(unit, isFree = true, isSpecial = false, isAnywhere = true)
    fun getAncientRuinsUpgradeAction(unit: MapUnit) =
            getUpgradeAction(unit, isFree = true, isSpecial = true, isAnywhere = true)
    fun getUpgradeActionAnywhere(unit: MapUnit) =
            getUpgradeAction(unit, isFree = false, isSpecial = false, isAnywhere = true)
}
