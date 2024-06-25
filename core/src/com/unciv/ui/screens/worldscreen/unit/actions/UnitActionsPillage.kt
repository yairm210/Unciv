package com.unciv.ui.screens.worldscreen.unit.actions

import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.popups.ConfirmPopup
import kotlin.random.Random

object UnitActionsPillage {

    internal fun getPillageActions(unit: MapUnit, tile: Tile): Sequence<UnitAction> {
        val pillageAction = getPillageAction(unit, tile)
            ?: return emptySequence()
        if (pillageAction.action == null || unit.civ.isAIOrAutoPlaying())
            return sequenceOf(pillageAction)
        else return sequenceOf(UnitAction(UnitActionType.Pillage, 65f, pillageAction.title) {
            val pillageText = "Are you sure you want to pillage this [${tile.getImprovementToPillageName()!!}]?"
            ConfirmPopup(
                GUI.getWorldScreen(),
                pillageText,
                "Pillage",
                true
            ) {
                (pillageAction.action)()
                GUI.setUpdateWorldOnNextRender()
            }.open()
        })
    }

    internal fun getPillageAction(unit: MapUnit, tile: Tile): UnitAction? {
        val improvementName = unit.currentTile.getImprovementToPillageName()
        if (unit.isCivilian() || improvementName == null || tile.getOwner() == unit.civ) return null
        return UnitAction(
            UnitActionType.Pillage, 65f,
            title = "${UnitActionType.Pillage} [$improvementName]",
            action = {
                val pillagedImprovement = unit.currentTile.getImprovementToPillageName()!!  // can this differ from improvementName due to later execution???
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
                tile.setPillaged()  // Also triggers reassignPopulation
                if (tile.resource != null) tile.getOwner()?.cache?.updateCivResources()    // this might take away a resource

                val freePillage = unit.hasUnique(UniqueType.NoMovementToPillage, checkCivInfoUniques = true)
                if (!freePillage) unit.useMovementPoints(1f)

                if (pillagingImprovement)  // only Improvements heal HP
                    unit.healBy(25)
            }.takeIf { unit.currentMovement > 0 && canPillage(unit, tile) }
        )
    }

    private fun pillageLooting(tile: Tile, unit: MapUnit) {
        val closestCity = unit.civ.cities.minByOrNull { it.getCenterTile().aerialDistanceTo(tile) }
        val improvement = tile.getImprovementToPillage()!!

        // Accumulate the loot
        val pillageYield = Stats()
        val stateForConditionals = StateForConditionals(unit=unit)
        for (unique in improvement.getMatchingUniques(UniqueType.PillageYieldRandom, stateForConditionals)) {
            for ((stat, value) in unique.stats) {
                // Unique text says "approximately [X]", so we add 0..X twice - think an RPG's 2d12
                val looted = Random.nextInt((value + 1).toInt()) + Random.nextInt((value + 1).toInt())
                pillageYield.add(stat, looted.toFloat())
            }
        }
        for (unique in improvement.getMatchingUniques(UniqueType.PillageYieldFixed, stateForConditionals)) {
            pillageYield.add(unique.stats)
        }

        // Please no notification when there's no loot
        if (pillageYield.isEmpty()) return

        // Distribute the loot and keep record what went to civ/city for the notification(s)
        val globalPillageYield = Stats()
        val toCityPillageYield = Stats()
        for ((stat, value) in pillageYield) {
            if (stat in Stat.statsWithCivWideField) {
                unit.civ.addStat(stat, value.toInt())
                globalPillageYield[stat] += value
            }
            else if (closestCity != null) {
                closestCity.addStat(stat, value.toInt())
                toCityPillageYield[stat] += value
            }
        }

        // Now tell the user about the swag
        fun Stats.notify(suffix: String) {
            if (isEmpty()) return
            val text = "We have looted [${toStringWithoutIcons()}] from a [${improvement.name}]" + suffix
            unit.civ.addNotification(text, tile.position, NotificationCategory.War, "ImprovementIcons/${improvement.name}", NotificationIcon.War)
        }
        toCityPillageYield.notify(" which has been sent to [${closestCity?.name}]")
        globalPillageYield.notify("")
    }

    // Public - used in UnitAutomation
    fun canPillage(unit: MapUnit, tile: Tile): Boolean {
        if (unit.isTransported) return false
        if (!tile.canPillageTile()) return false
        if (unit.hasUnique(UniqueType.CannotPillage)) return false
        val tileOwner = tile.getOwner()
        // Can't pillage friendly tiles, just like you can't attack them - it's an 'act of war' thing
        return tileOwner == null || unit.civ.isAtWarWith(tileOwner)
    }
}
