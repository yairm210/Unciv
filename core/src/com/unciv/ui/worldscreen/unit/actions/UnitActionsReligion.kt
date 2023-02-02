package com.unciv.ui.worldscreen.unit.actions

import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.utils.extensions.toPercent

object UnitActionsReligion {


    internal fun addFoundReligionAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (!unit.hasUnique(UniqueType.MayFoundReligion)) return
        if (!unit.civInfo.religionManager.mayFoundReligionAtAll(unit)) return
        actionList += UnitAction(
            UnitActionType.FoundReligion,
            action = getFoundReligionAction(unit).takeIf { unit.civInfo.religionManager.mayFoundReligionNow(unit) }
        )
    }

    fun getFoundReligionAction(unit: MapUnit): () -> Unit {
        return {
            unit.civInfo.religionManager.foundReligion(unit)
            unit.consume()
        }
    }

    internal fun addEnhanceReligionAction(unit: MapUnit, actionList: ArrayList<UnitAction>) {
        if (!unit.hasUnique(UniqueType.MayEnhanceReligion)) return
        if (!unit.civInfo.religionManager.mayEnhanceReligionAtAll(unit)) return
        actionList += UnitAction(
            UnitActionType.EnhanceReligion,
            title = "Enhance [${unit.civInfo.religionManager.religion!!.getReligionDisplayName()}]",
            action = getEnhanceReligionAction(unit).takeIf { unit.civInfo.religionManager.mayEnhanceReligionNow(unit) }
        )
    }

    fun getEnhanceReligionAction(unit: MapUnit): () -> Unit {
        return {
            unit.civInfo.religionManager.useProphetForEnhancingReligion(unit)
            unit.consume()
        }
    }

    fun addActionsWithLimitedUses(unit: MapUnit, actionList: ArrayList<UnitAction>, tile: Tile) {

        val actionsToAdd = unit.religiousActionsUnitCanDo()
        if (actionsToAdd.none()) return
        if (unit.religion == null || unit.civInfo.gameInfo.religions[unit.religion]!!.isPantheon()) return
        val city = tile.getCity() ?: return
        for (action in actionsToAdd) {
            if (!unit.abilityUsesLeft.containsKey(action)) continue
            if (unit.abilityUsesLeft[action]!! <= 0) continue
            when (action) {
                Constants.spreadReligion -> addSpreadReligionActions(unit, actionList, city)
                Constants.removeHeresy -> addRemoveHeresyActions(unit, actionList, city)
            }
        }
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

    private fun addSpreadReligionActions(unit: MapUnit, actionList: ArrayList<UnitAction>, city: City) {
        if (!unit.civInfo.religionManager.maySpreadReligionAtAll(unit)) return
        actionList += UnitAction(
            UnitActionType.SpreadReligion,
            title = "Spread [${unit.getReligionDisplayName()!!}]",
            action = {
                val followersOfOtherReligions = city.religion.getFollowersOfOtherReligionsThan(unit.religion!!)
                for (unique in unit.getMatchingUniques(UniqueType.StatsWhenSpreading, checkCivInfoUniques = true)) {
                    unit.civInfo.addStat(Stat.valueOf(unique.params[1]), followersOfOtherReligions * unique.params[0].toInt())
                }
                city.religion.addPressure(unit.religion!!, getPressureAddedFromSpread(unit))
                if (unit.hasUnique(UniqueType.RemoveOtherReligions))
                    city.religion.removeAllPressuresExceptFor(unit.religion!!)
                unit.currentMovement = 0f
                useActionWithLimitedUses(unit, Constants.spreadReligion)
            }.takeIf { unit.currentMovement > 0 && unit.civInfo.religionManager.maySpreadReligionNow(unit) }
        )
    }

    private fun addRemoveHeresyActions(unit: MapUnit, actionList: ArrayList<UnitAction>, city: City) {
        if (!unit.civInfo.gameInfo.isReligionEnabled()) return
        if (city.civ != unit.civInfo) return
        // Only allow the action if the city actually has any foreign religion
        // This will almost be always due to pressure from cities close-by
        if (city.religion.getPressures().none { it.key != unit.religion!! }) return
        actionList += UnitAction(
            UnitActionType.RemoveHeresy,
            title = "Remove Heresy",
            action = {
                city.religion.removeAllPressuresExceptFor(unit.religion!!)
                if (city.religion.religionThisIsTheHolyCityOf != null) {
                    val religion = unit.civInfo.gameInfo.religions[city.religion.religionThisIsTheHolyCityOf]!!
                    if (city.religion.religionThisIsTheHolyCityOf != unit.religion && !city.religion.isBlockedHolyCity) {
                        religion.getFounder().addNotification("An [${unit.baseUnit.name}] has removed your religion [${religion.getReligionDisplayName()}] from its Holy City [${city.name}]!", NotificationCategory.Religion)
                        city.religion.isBlockedHolyCity = true
                    } else if (city.religion.religionThisIsTheHolyCityOf == unit.religion && city.religion.isBlockedHolyCity) {
                        religion.getFounder().addNotification("An [${unit.baseUnit.name}] has restored [${city.name}] as the Holy City of your religion [${religion.getReligionDisplayName()}]!", NotificationCategory.Religion)
                        city.religion.isBlockedHolyCity = false
                    }
                }
                unit.currentMovement = 0f
                useActionWithLimitedUses(unit, Constants.removeHeresy)
            }.takeIf { unit.currentMovement > 0f }
        )
    }
}
