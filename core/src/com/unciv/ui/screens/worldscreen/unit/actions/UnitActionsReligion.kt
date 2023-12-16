package com.unciv.ui.screens.worldscreen.unit.actions

import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.components.extensions.toPercent

object UnitActionsReligion {


    internal fun getFoundReligionActions(unit: MapUnit, tile:Tile): List<UnitAction> {
        if (!unit.civ.religionManager.mayFoundReligionAtAll()) return listOf()

        val unique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.MayFoundReligion)
            .firstOrNull() ?: return listOf()

        val hasActionModifiers = unique.conditionals.any { it.type?.targetTypes?.contains(
            UniqueTarget.UnitActionModifier
        ) == true }

        return listOf(UnitAction(
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
            }.takeIf { unit.civ.religionManager.mayFoundReligionHere(tile) }
        ))
    }

    internal fun getEnhanceReligionActions(unit: MapUnit, tile: Tile): List<UnitAction> {
        if (!unit.civ.religionManager.mayEnhanceReligionAtAll()) return listOf()

        val unique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.MayEnhanceReligion)
            .firstOrNull() ?: return listOf()

        val hasActionModifiers = unique.conditionals.any { it.type?.targetTypes?.contains(
            UniqueTarget.UnitActionModifier
        ) == true }

        val baseTitle = "Enhance [${unit.civ.religionManager.religion!!.getReligionDisplayName()}]"
        return listOf(UnitAction(
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
            }.takeIf { unit.civ.religionManager.mayEnhanceReligionHere(tile) }
        ))
    }

    private fun getPressureAddedFromSpread(unit: MapUnit): Int {
        var pressureAdded = unit.baseUnit.religiousStrength.toFloat()

        for (unique in unit.getMatchingUniques(UniqueType.SpreadReligionStrength, checkCivInfoUniques = true))
            pressureAdded *= unique.params[0].toPercent()

        return pressureAdded.toInt()
    }

    fun addSpreadReligionActions(unit: MapUnit, tile: Tile): List<UnitAction> {
        if (!unit.civ.religionManager.maySpreadReligionAtAll(unit)) return listOf()
        val city = tile.getCity() ?: return listOf()

        val newStyleUnique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.CanSpreadReligion).firstOrNull() ?: return emptyList()

        val title = UnitActionModifiers.actionTextWithSideEffects("Spread [${unit.getReligionDisplayName()!!}]",
            newStyleUnique, unit)

        return listOf(UnitAction(
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

                UnitActionModifiers.activateSideEffects(unit, newStyleUnique)
            }.takeIf { unit.currentMovement > 0 && unit.civ.religionManager.maySpreadReligionNow(unit) }
        ))
    }

    internal fun getRemoveHeresyActions(unit: MapUnit, tile: Tile): List<UnitAction> {
        if (!unit.civ.gameInfo.isReligionEnabled()) return listOf()
        val religion = unit.civ.gameInfo.religions[unit.religion] ?: return listOf()
        if (religion.isPantheon()) return listOf()

        val city = tile.getCity() ?: return listOf()
        if (city.civ != unit.civ) return listOf()
        // Only allow the action if the city actually has any foreign religion
        // This will almost be always due to pressure from cities close-by
        if (city.religion.getPressures().none { it.key != unit.religion!! }) return listOf()

        val newStyleUnique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.CanRemoveHeresy).firstOrNull()
        val hasNewStyleAbility = newStyleUnique != null

        if (!hasNewStyleAbility) return listOf()

        val title =
            UnitActionModifiers.actionTextWithSideEffects("Remove Heresy", newStyleUnique!!, unit)

        return listOf(UnitAction(
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
                UnitActionModifiers.activateSideEffects(unit, newStyleUnique)
            }.takeIf { unit.currentMovement > 0f }
        ))
    }
}
