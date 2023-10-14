package com.unciv.ui.screens.worldscreen.unit.actions

import com.unciv.Constants
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.components.extensions.toPercent

object UnitActionsReligion {


    internal fun addFoundReligionAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (!unit.civ.religionManager.mayFoundReligionAtAll()) return

        val unique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.MayFoundReligion)
            .firstOrNull() ?: return

        val hasActionModifiers = unique.conditionals.any { it.type?.targetTypes?.contains(
            UniqueTarget.UnitActionModifier
        ) == true }

        actionList += UnitAction(
            UnitActionType.FoundReligion,

            if (hasActionModifiers) UnitActionModifiers.actionTextWithSideEffects(
                UnitActionType.FoundReligion.value,
                unique,
                unit
            )
            else UnitActionType.FoundReligion.value,
            action = {
                unit.civ.religionManager.foundReligion(unit)

                if (hasActionModifiers) UnitActionModifiers.activateSideEffects(unit, unique)
                else unit.consume()
            }.takeIf { unit.civ.religionManager.mayFoundReligionNow(unit) }
        )
    }

    internal fun addEnhanceReligionAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (!unit.civ.religionManager.mayEnhanceReligionAtAll(unit)) return

        val unique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.MayEnhanceReligion)
            .firstOrNull() ?: return

        val hasActionModifiers = unique.conditionals.any { it.type?.targetTypes?.contains(
            UniqueTarget.UnitActionModifier
        ) == true }

        val baseTitle = "Enhance [${unit.civ.religionManager.religion!!.getReligionDisplayName()}]"
        actionList += UnitAction(
            UnitActionType.EnhanceReligion,
            title = if (hasActionModifiers) UnitActionModifiers.actionTextWithSideEffects(
                baseTitle,
                unique,
                unit
            )
            else baseTitle,
            action = {
                unit.civ.religionManager.useProphetForEnhancingReligion(unit)
                if (hasActionModifiers) UnitActionModifiers.activateSideEffects(unit, unique)
                else unit.consume()
            }.takeIf { unit.civ.religionManager.mayEnhanceReligionNow(unit) }
        )
    }

    private fun useActionWithLimitedUses(unit: MapUnit, action: String) {
        unit.abilityUsesLeft[action] = unit.abilityUsesLeft[action]!! - 1
        if (unit.abilityUsesLeft[action]!! <= 0) {
            unit.consume()
        }
    }

    private fun getPressureAddedFromSpread(unit: MapUnit): Int {
        var pressureAdded = unit.baseUnit.religiousStrength.toFloat()

        for (unique in unit.getMatchingUniques(UniqueType.SpreadReligionStrength, checkCivInfoUniques = true))
            pressureAdded *= unique.params[0].toPercent()

        return pressureAdded.toInt()
    }

    fun addSpreadReligionActions(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (!unit.civ.religionManager.maySpreadReligionAtAll(unit)) return
        val city = unit.currentTile.getCity() ?: return

        val newStyleUnique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.CanSpreadReligion).firstOrNull()

        val title = if (newStyleUnique != null)
                UnitActionModifiers.actionTextWithSideEffects("Spread [${unit.getReligionDisplayName()!!}]", newStyleUnique, unit)
        else "Spread [${unit.getReligionDisplayName()!!}]"

        actionList += UnitAction(
            UnitActionType.SpreadReligion,
            title = title,
            action = {
                val followersOfOtherReligions = city.religion.getFollowersOfOtherReligionsThan(unit.religion!!)
                for (unique in unit.getMatchingUniques(UniqueType.StatsWhenSpreading, checkCivInfoUniques = true)) {
                    unit.civ.addStat(Stat.valueOf(unique.params[1]), followersOfOtherReligions * unique.params[0].toInt())
                }
                city.religion.addPressure(unit.religion!!, getPressureAddedFromSpread(unit))
                if (unit.hasUnique(UniqueType.RemoveOtherReligions))
                    city.religion.removeAllPressuresExceptFor(unit.religion!!)

                if (newStyleUnique != null) UnitActionModifiers.activateSideEffects(unit, newStyleUnique)
                else {
                    useActionWithLimitedUses(unit, Constants.spreadReligion)
                    unit.currentMovement = 0f
                }
            }.takeIf { unit.currentMovement > 0 && unit.civ.religionManager.maySpreadReligionNow(unit) }
        )
    }

    internal fun addRemoveHeresyActions(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (!unit.civ.gameInfo.isReligionEnabled()) return
        val religion = unit.civ.gameInfo.religions[unit.religion] ?: return
        if (religion.isPantheon()) return

        val city = unit.currentTile.getCity() ?: return
        if (city.civ != unit.civ) return
        // Only allow the action if the city actually has any foreign religion
        // This will almost be always due to pressure from cities close-by
        if (city.religion.getPressures().none { it.key != unit.religion!! }) return

        val hasOldStyleAbility = unit.abilityUsesLeft.containsKey(Constants.removeHeresy)
            && unit.abilityUsesLeft[Constants.removeHeresy]!! > 0

        val newStyleUnique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.CanRemoveHeresy).firstOrNull()
        val hasNewStyleAbility = newStyleUnique != null

        if (!hasOldStyleAbility && !hasNewStyleAbility) return

        val title = if (hasNewStyleAbility)
            UnitActionModifiers.actionTextWithSideEffects("Remove Heresy", newStyleUnique!!, unit)
        else "Remove Heresy"

        actionList += UnitAction(
            UnitActionType.RemoveHeresy,
            title = title,
            action = {
                city.religion.removeAllPressuresExceptFor(unit.religion!!)
                if (city.religion.religionThisIsTheHolyCityOf != null) {
                    val holyCityReligion = unit.civ.gameInfo.religions[city.religion.religionThisIsTheHolyCityOf]!!
                    if (city.religion.religionThisIsTheHolyCityOf != unit.religion && !city.religion.isBlockedHolyCity) {
                        holyCityReligion.getFounder().addNotification("An [${unit.baseUnit.name}] has removed your religion [${holyCityReligion.getReligionDisplayName()}] from its Holy City [${city.name}]!", NotificationCategory.Religion)
                        city.religion.isBlockedHolyCity = true
                    } else if (city.religion.religionThisIsTheHolyCityOf == unit.religion && city.religion.isBlockedHolyCity) {
                        holyCityReligion.getFounder().addNotification("An [${unit.baseUnit.name}] has restored [${city.name}] as the Holy City of your religion [${holyCityReligion.getReligionDisplayName()}]!", NotificationCategory.Religion)
                        city.religion.isBlockedHolyCity = false
                    }
                }

                if (hasNewStyleAbility) UnitActionModifiers.activateSideEffects(unit, newStyleUnique!!)
                else {
                    useActionWithLimitedUses(unit, Constants.removeHeresy)
                    unit.currentMovement = 0f
                }
            }.takeIf { unit.currentMovement > 0f }
        )
    }
}
