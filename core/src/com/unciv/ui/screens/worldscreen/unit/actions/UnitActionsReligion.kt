package com.unciv.ui.screens.worldscreen.unit.actions

import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
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

    internal fun getFoundReligionActions(unit: MapUnit, tile: Tile): Sequence<UnitAction> {
        if (!unit.civ.religionManager.mayFoundReligionAtAll()) return emptySequence()

        val unique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.MayFoundReligion)
            .firstOrNull() ?: return emptySequence()

        val hasActionModifiers = unique.modifiers.any { it.type?.targetTypes?.contains(
            UniqueTarget.UnitActionModifier
        ) == true }

        return sequenceOf(UnitAction(
            UnitActionType.FoundReligion, 80f,

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
            }.takeIf { unit.civ.religionManager.mayFoundReligionHere(tile)
                && UnitActionModifiers.canActivateSideEffects(unit, unique)}
        ))
    }

    internal fun getEnhanceReligionActions(unit: MapUnit, tile: Tile): Sequence<UnitAction> {
        if (!unit.civ.religionManager.mayEnhanceReligionAtAll()) return emptySequence()

        val unique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.MayEnhanceReligion)
            .firstOrNull() ?: return emptySequence()

        val hasActionModifiers = unique.modifiers.any { it.type?.targetTypes?.contains(
            UniqueTarget.UnitActionModifier
        ) == true }

        val baseTitle = "Enhance [${unit.civ.religionManager.religion!!.getReligionDisplayName()}]"
        return sequenceOf(UnitAction(
            UnitActionType.EnhanceReligion, 79f,
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
            }.takeIf { unit.civ.religionManager.mayEnhanceReligionHere(tile)
                && UnitActionModifiers.canActivateSideEffects(unit, unique)}
        ))
    }

    @Readonly
    private fun getPressureAddedFromSpread(unit: MapUnit): Int {
        var pressureAdded = unit.baseUnit.religiousStrength.toFloat()

        for (unique in unit.getMatchingUniques(UniqueType.SpreadReligionStrength, checkCivInfoUniques = true))
            pressureAdded *= unique.params[0].toPercent()

        return pressureAdded.toInt()
    }

    internal fun getSpreadReligionActions(unit: MapUnit, tile: Tile): Sequence<UnitAction> {
        if (!unit.civ.religionManager.maySpreadReligionAtAll(unit)) return emptySequence()
        val city = tile.getCity() ?: return emptySequence()

        val newStyleUnique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.CanSpreadReligion)
            .firstOrNull() ?: return emptySequence()

        val title = UnitActionModifiers.actionTextWithSideEffects("Spread [${unit.getReligionDisplayName()!!}]",
            newStyleUnique, unit)

        return sequenceOf(UnitAction(
            UnitActionType.SpreadReligion, 68f,
            title = title,
            action = {
                val followersOfOtherReligions = city.religion.getFollowersOfOtherReligionsThan(unit.religion!!)
                for (unique in unit.getMatchingUniques(UniqueType.StatsWhenSpreading, checkCivInfoUniques = true)) {
                    unit.civ.addStat(Stat.valueOf(unique.params[1]), followersOfOtherReligions * unique.params[0].toInt())
                }
                val previousReligion = city.religion.getMajorityReligion()
                city.religion.addPressure(unit.religion!!, getPressureAddedFromSpread(unit))
                if (unit.hasUnique(UniqueType.RemoveOtherReligions))
                    city.religion.removeAllPressuresExceptFor(unit.religion!!)
                
                val newReligion = city.religion.getMajorityReligion()
                if (previousReligion != newReligion && newReligion != null && city.civ != unit.civ) {
                    city.civ.addNotification("[${unit.civ.civName}]'s [${unit.name}] has converted [${city.name}] to [${newReligion.name}]!", NotificationCategory.Religion)
                }

                UnitActionModifiers.activateSideEffects(unit, newStyleUnique)

                if (city.civ != unit.civ) city.civ.getDiplomacyManager(unit.civ)!!
                    .setFlag(DiplomacyFlags.SpreadReligionInOurCities, 30)

            }.takeIf { unit.civ.religionManager.maySpreadReligionNow(unit)
                && UnitActionModifiers.canActivateSideEffects(unit, newStyleUnique)}
        ))
    }

    internal fun getRemoveHeresyActions(unit: MapUnit, tile: Tile): Sequence<UnitAction> {
        if (!unit.civ.gameInfo.isReligionEnabled()) return emptySequence()
        val religion = unit.civ.gameInfo.religions[unit.religion] ?: return emptySequence()
        if (religion.isPantheon()) return emptySequence()

        val city = tile.getCity() ?: return emptySequence()
        if (city.civ != unit.civ) return emptySequence()
        // Only allow the action if the city actually has any foreign religion
        // This will almost be always due to pressure from cities close-by
        if (city.religion.getPressures().none { it.key != unit.religion!! }) return emptySequence()

        val newStyleUnique = UnitActionModifiers.getUsableUnitActionUniques(unit, UniqueType.CanRemoveHeresy).firstOrNull()
        val hasNewStyleAbility = newStyleUnique != null

        if (!hasNewStyleAbility) return emptySequence()

        val title =
            UnitActionModifiers.actionTextWithSideEffects("Remove Heresy", newStyleUnique!!, unit)

        return sequenceOf(UnitAction(
            UnitActionType.RemoveHeresy, 69f,
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
            }.takeIf { UnitActionModifiers.canActivateSideEffects(unit, newStyleUnique)}
        ))
    }
}
