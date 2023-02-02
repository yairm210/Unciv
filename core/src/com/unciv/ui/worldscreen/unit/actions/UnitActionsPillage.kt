package com.unciv.ui.worldscreen.unit.actions

import com.unciv.UncivGame
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.popup.ConfirmPopup
import com.unciv.ui.popup.hasOpenPopups
import com.unciv.ui.worldscreen.WorldScreen
import kotlin.random.Random

object UnitActionsPillage {

    fun addPillageAction(unit: MapUnit, actionList: ArrayList<UnitAction>, worldScreen: WorldScreen) {
        val pillageAction = getPillageAction(unit)
            ?: return
        if (pillageAction.action == null)
            actionList += UnitAction(
                UnitActionType.Pillage,
                title = "${UnitActionType.Pillage} [${unit.currentTile.getImprovementToPillageName()!!}]",
                action = null)
        else actionList += UnitAction(type = UnitActionType.Pillage,
            title = "${UnitActionType.Pillage} [${unit.currentTile.getImprovementToPillageName()!!}]") {
            if (!worldScreen.hasOpenPopups()) {
                val pillageText = "Are you sure you want to pillage this [${unit.currentTile.getImprovementToPillageName()!!}]?"
                ConfirmPopup(
                    UncivGame.Current.worldScreen!!,
                    pillageText,
                    "Pillage",
                    true
                ) {
                    (pillageAction.action)()
                    worldScreen.shouldUpdate = true
                }.open()
            }
        }
    }

    fun getPillageAction(unit: MapUnit): UnitAction? {
        val tile = unit.currentTile
        if (unit.isCivilian() || !tile.canPillageTile() || tile.getOwner() == unit.civ) return null
        return UnitAction(
            UnitActionType.Pillage,
            action = {
                val pillagedImprovement = unit.currentTile.getImprovementToPillageName()!!
                val pillagingImprovement = unit.currentTile.canPillageTileImprovement()
                val pillageText = "An enemy [${unit.baseUnit.name}] has pillaged our [$pillagedImprovement]"
                val icon = "ImprovementIcons/$pillagedImprovement"
                tile.getOwner()?.addNotification(
                    pillageText,
                    tile.position,
                    NotificationCategory.War,
                    icon,
                    NotificationIcon.War,
                    unit.baseUnit.name
                )

                pillageLooting(tile, unit)
                tile.setPillaged()
                if (tile.resource != null) tile.getOwner()?.cache?.updateCivResources()    // this might take away a resource
                tile.getCity()?.updateCitizens = true

                val freePillage = unit.hasUnique(UniqueType.NoMovementToPillage, checkCivInfoUniques = true)
                if (!freePillage) unit.useMovementPoints(1f)

                if (pillagingImprovement)  // only Improvements heal HP
                    unit.healBy(25)
            }.takeIf { unit.currentMovement > 0 && UnitActions.canPillage(unit, tile) })
    }

    private fun pillageLooting(tile: Tile, unit: MapUnit) {
        // Stats objects for reporting pillage results in a notification
        val pillageYield = Stats()
        val globalPillageYield = Stats()
        val toCityPillageYield = Stats()
        val closestCity = unit.civ.cities.minByOrNull { it.getCenterTile().aerialDistanceTo(tile) }
        val improvement = tile.getImprovementToPillage()!!

        for (unique in improvement.getMatchingUniques(UniqueType.PillageYieldRandom)) {
            for (stat in unique.stats) {
                val looted = Random.nextInt((stat.value + 1).toInt()) + Random.nextInt((stat.value + 1).toInt())
                pillageYield.add(stat.key, looted.toFloat())
            }
        }
        for (unique in improvement.getMatchingUniques(UniqueType.PillageYieldFixed)) {
            for (stat in unique.stats) {
                pillageYield.add(stat.key, stat.value)
            }
        }

        for (stat in pillageYield) {
            if (stat.key in Stat.statsWithCivWideField) {
                unit.civ.addStat(stat.key, stat.value.toInt())
                globalPillageYield[stat.key] += stat.value
            }
            else if (closestCity != null) {
                closestCity.addStat(stat.key, stat.value.toInt())
                toCityPillageYield[stat.key] += stat.value
            }
        }

        val lootNotificationText = if (!toCityPillageYield.isEmpty() && closestCity != null)
            "We have looted [${toCityPillageYield.toStringWithoutIcons()}] from a [${improvement.name}] which has been sent to [${closestCity.name}]"
        else "We have looted [${globalPillageYield.toStringWithoutIcons()}] from a [${improvement.name}]"

        unit.civ.addNotification(lootNotificationText, tile.position, NotificationCategory.War, "ImprovementIcons/${improvement.name}", NotificationIcon.War)
    }
}
