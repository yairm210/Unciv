package com.unciv.logic.map.mapunit.actions

import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.components.extensions.toPercent
import yairm210.purity.annotations.Readonly

object UnitActionsReligion {

    internal fun getFoundReligionAction(unit: MapUnit, tile: Tile): UnitAction? {
        if (!unit.civ.religionManager.mayFoundReligionAtAll()) return null

        val unique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.MayFoundReligion)
            .firstOrNull() ?: return null

        val hasActionModifiers = unique.modifiers.any { it.type?.targetTypes?.contains(
            UniqueTarget.UnitActionModifier
        ) == true }

        return UnitAction(
            UnitActionType.FoundReligion,

            if (hasActionModifiers) UnitActionModifiers.actionTextWithSideEffects(
                UnitActionType.FoundReligion.value,
                unique,
                unit
            )
            else UnitActionType.FoundReligion.value,
            associatedUnique = unique,
            action = {
                unit.civ.religionManager.foundReligion(unit)

                if (hasActionModifiers) UnitActionModifiers.activateSideEffects(unit, unique)
                else unit.consume()
            }.takeIf { unit.civ.religionManager.mayFoundReligionHere(tile)
                && UnitActionModifiers.canActivateSideEffects(unit, unique)}
        )
    }

    internal fun getEnhanceReligionAction(unit: MapUnit, tile: Tile): UnitAction? {
        if (!unit.civ.religionManager.mayEnhanceReligionAtAll()) return null

        val unique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.MayEnhanceReligion)
            .firstOrNull() ?: return null

        val hasActionModifiers = unique.modifiers.any { it.type?.targetTypes?.contains(
            UniqueTarget.UnitActionModifier
        ) == true }

        val baseTitle = "Enhance [${unit.civ.religionManager.religion!!.getReligionDisplayName()}]"
        return UnitAction(
            UnitActionType.EnhanceReligion,
            title = if (hasActionModifiers) UnitActionModifiers.actionTextWithSideEffects(
                baseTitle,
                unique,
                unit
            )
            else baseTitle,
            associatedUnique = unique,
            action = {
                unit.civ.religionManager.useProphetForEnhancingReligion(unit)
                if (hasActionModifiers) UnitActionModifiers.activateSideEffects(unit, unique)
                else unit.consume()
            }.takeIf { unit.civ.religionManager.mayEnhanceReligionHere(tile)
                && UnitActionModifiers.canActivateSideEffects(unit, unique)}
        )
    }

    @Readonly
    private fun getPressureAddedFromSpread(unit: MapUnit): Int {
        var pressureAdded = unit.baseUnit.religiousStrength.toFloat()

        unit.forEachMatchingUnique(UniqueType.SpreadReligionStrength, checkCivInfoUniques = true) { unique ->
            pressureAdded *= unique.params[0].toPercent()
        }

        return pressureAdded.toInt()
    }

    internal fun getSpreadReligionAction(unit: MapUnit, tile: Tile): UnitAction? {
        if (!unit.civ.religionManager.maySpreadReligionAtAll(unit)) return null
        val city = tile.getCity() ?: return null

        val newStyleUnique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.CanSpreadReligion)
            .firstOrNull() ?: return null

        val title = UnitActionModifiers.actionTextWithSideEffects("Spread [${unit.getReligionDisplayName()!!}]",
            newStyleUnique, unit)

        return UnitAction(
            UnitActionType.SpreadReligion,
            title = title,
            associatedUnique = newStyleUnique,
            action = {
                val followersOfOtherReligions = city.religion.getFollowersOfOtherReligionsThan(unit.religion!!)
                unit.forEachMatchingUnique(UniqueType.StatsWhenSpreading, checkCivInfoUniques = true) { unique ->
                    unit.civ.addStat(Stat.valueOf(unique.params[1]), followersOfOtherReligions * unique.params[0].toInt())
                }
                val previousReligion = city.religion.getMajorityReligion()
                city.religion.addPressure(unit.religion!!, getPressureAddedFromSpread(unit))
                if (unit.hasUnique(UniqueType.RemoveOtherReligions))
                    city.religion.removeAllPressuresExceptFor(unit.religion!!)
                
                val newReligion = city.religion.getMajorityReligion()
                if (previousReligion != newReligion && newReligion != null && city.civ != unit.civ) {
                    city.civ.addNotification(
                        "[${unit.civ.civName}]'s [${unit.name}] has converted [${city.name}] to [${newReligion.getReligionDisplayName()}]!",
                        city.location, NotificationCategory.Religion, NotificationIcon.Faith
                    )
                }

                UnitActionModifiers.activateSideEffects(unit, newStyleUnique)

                if (city.civ != unit.civ // Set flag only if they don't want our religion, which is when they can get their own
                    && (city.civ.religionManager.remainingFoundableReligions() != 0 || city.civ.religionManager.religionState > ReligionState.Pantheon))
                    city.civ.getDiplomacyManager(unit.civ)!!.setFlag(DiplomacyFlags.SpreadReligionInOurCities, 30, true)

            }.takeIf { unit.civ.religionManager.maySpreadReligionNow(unit)
                && UnitActionModifiers.canActivateSideEffects(unit, newStyleUnique)}
        )
    }

    internal fun getRemoveHeresyAction(unit: MapUnit, tile: Tile): UnitAction? {
        if (!unit.civ.gameInfo.isReligionEnabled()) return null
        val religion = unit.civ.gameInfo.religions[unit.religion] ?: return null
        if (religion.isPantheon()) return null

        val city = tile.getCity() ?: return null
        if (city.civ != unit.civ) return null
        // Only allow the action if the city actually has any foreign religion
        // This will almost be always due to pressure from cities close-by
        if (city.religion.getPressures().none { it.key != unit.religion!! }) return null

        val newStyleUnique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.CanRemoveHeresy).firstOrNull()
        val hasNewStyleAbility = newStyleUnique != null

        if (!hasNewStyleAbility) return null

        val title =
            UnitActionModifiers.actionTextWithSideEffects("Remove Heresy", newStyleUnique, unit)

        return UnitAction(
            UnitActionType.RemoveHeresy,
            title = title,
            associatedUnique = newStyleUnique,
            action = {
                city.religion.removeAllPressuresExceptFor(unit.religion!!)
                if (city.religion.religionThisIsTheHolyCityOf != null) {
                    val holyCityReligion = unit.civ.gameInfo.religions[city.religion.religionThisIsTheHolyCityOf]!!
                    if (city.religion.religionThisIsTheHolyCityOf != unit.religion && !city.religion.isBlockedHolyCity) {
                        holyCityReligion.foundingCiv.addNotification("An [${unit.baseUnit.name}] has removed your religion [${holyCityReligion.getReligionDisplayName()}] from its Holy City [${city.name}]!", NotificationCategory.Religion)
                        city.religion.isBlockedHolyCity = true
                    } else if (city.religion.religionThisIsTheHolyCityOf == unit.religion && city.religion.isBlockedHolyCity) {
                        holyCityReligion.foundingCiv.addNotification("An [${unit.baseUnit.name}] has restored [${city.name}] as the Holy City of your religion [${holyCityReligion.getReligionDisplayName()}]!", NotificationCategory.Religion)
                        city.religion.isBlockedHolyCity = false
                    }
                }
                UnitActionModifiers.activateSideEffects(unit, newStyleUnique)
            }.takeIf { UnitActionModifiers.canActivateSideEffects(unit, newStyleUnique)}
        )
    }
}
