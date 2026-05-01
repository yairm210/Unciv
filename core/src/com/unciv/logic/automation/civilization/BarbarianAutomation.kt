package com.unciv.logic.automation.civilization

import com.unciv.Constants
import com.unciv.logic.automation.unit.BattleHelper
import com.unciv.logic.automation.unit.UniqueActionQueue
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit

class BarbarianAutomation(val civInfo: Civilization) {

    fun automate() {
        val uniqueActionQueue: UniqueActionQueue
        // ranged go first, after melee and then everyone else
        civInfo.units.getCivUnits().filter { it.baseUnit.isRanged() }.forEach { automateUnit(it) }
        civInfo.units.getCivUnits().filter { it.baseUnit.isMelee() }.forEach { automateUnit(it) }
        civInfo.units.getCivUnits().filter { !it.baseUnit.isRanged() && !it.baseUnit.isMelee() }.forEach { automateUnit(it) }
        // fix buildup of alerts - to shrink saves and ease debugging
        civInfo.popupAlerts.clear()
    }

    private fun automateUnit(unit: MapUnit) {
        val uniqueActionQueue = UniqueActionQueue(unit)
        if (unit.isCivilian()) automateCapturedCivilian(unit, uniqueActionQueue)
        else if (unit.currentTile.improvement == Constants.barbarianEncampment) automateUnitOnEncampment(unit, uniqueActionQueue)
        else automateCombatUnit(unit, uniqueActionQueue)
        uniqueActionQueue.automateRemainingUniqueActions()
    }

    private fun automateCapturedCivilian(unit: MapUnit, uniqueActionQueue: UniqueActionQueue) {
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(50f)
        // 1 - Stay on current encampment
        if (unit.currentTile.improvement == Constants.barbarianEncampment) return

        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(25f)
        val campTiles = unit.civ.gameInfo.barbarians.encampments.map { unit.civ.gameInfo.tileMap[it.position] }
            .sortedBy { unit.currentTile.aerialDistanceTo(it) }
        val bestCamp = campTiles.firstOrNull { it.civilianUnit == null && unit.movement.canReach(it)}
        if (bestCamp != null)
            unit.movement.headTowards(bestCamp) // 2 - Head towards an encampment
        else {
            uniqueActionQueue.automateRemainingUniqueActions()
            UnitAutomation.wander(unit) // 3 - Can't find a reachable encampment, wander aimlessly
        }
    }

    private fun automateUnitOnEncampment(unit: MapUnit, uniqueActionQueue: UniqueActionQueue) {
        // UnitActionType.Upgrade is useFrequency 120f
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(120f)
        // 1 - trying to upgrade
        if (UnitAutomation.tryUpgradeUnit(unit)) return

        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(37f)
        // 2 - trying to attack somebody - but don't leave the encampment
        if (BattleHelper.tryAttackNearbyEnemy(unit, stayOnTile = true)) return

        // 3 - at least fortifying
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(10f)
        unit.fortifyIfCan()
    }

    private fun automateCombatUnit(unit: MapUnit, uniqueActionQueue: UniqueActionQueue) {
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(200f)
        // 1 - Try pillaging to restore health (barbs don't auto-heal)
        if (unit.health < 50 && UnitAutomation.tryPillageImprovement(unit, true) && !unit.hasMovement()) return

        // UnitActionType.Upgrade is useFrequency 120f
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(120f)
        // 2 - trying to upgrade
        if (UnitAutomation.tryUpgradeUnit(unit)) return

        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(37f)
        // 3 - trying to attack enemy
        // if a embarked melee unit can land and attack next turn, do not attack from water.
        if (BattleHelper.tryDisembarkUnitToAttackPosition(unit)) return
        if (!unit.isCivilian() && BattleHelper.tryAttackNearbyEnemy(unit)) return

        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(27f)
        // 4 - trying to pillage tile or route
        while (UnitAutomation.tryPillageImprovement(unit)) {
            if (!unit.hasMovement()) return
        }

        uniqueActionQueue.automateRemainingUniqueActions()
        // 6 - wander
        UnitAutomation.wander(unit)
    }

}
