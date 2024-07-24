package com.unciv.logic.automation.civilization

import com.unciv.Constants
import com.unciv.logic.automation.unit.BattleHelper
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit

class BarbarianAutomation(val civInfo: Civilization) {

    fun automate() {
        // ranged go first, after melee and then everyone else
        civInfo.units.getCivUnits().filter { it.baseUnit.isRanged() }.forEach { automateUnit(it) }
        civInfo.units.getCivUnits().filter { it.baseUnit.isMelee() }.forEach { automateUnit(it) }
        civInfo.units.getCivUnits().filter { !it.baseUnit.isRanged() && !it.baseUnit.isMelee() }.forEach { automateUnit(it) }
        // fix buildup of alerts - to shrink saves and ease debugging
        civInfo.popupAlerts.clear()
    }

    private fun automateUnit(unit: MapUnit) {
        if (unit.isCivilian()) automateCapturedCivilian(unit)
        else if (unit.currentTile.improvement == Constants.barbarianEncampment) automateUnitOnEncampment(unit)
        else automateCombatUnit(unit)
    }

    private fun automateCapturedCivilian(unit: MapUnit) {
        // 1 - Stay on current encampment
        if (unit.currentTile.improvement == Constants.barbarianEncampment) return

        val campTiles = unit.civ.gameInfo.barbarians.encampments.map { unit.civ.gameInfo.tileMap[it.position] }
            .sortedBy { unit.currentTile.aerialDistanceTo(it) }
        val bestCamp = campTiles.firstOrNull { it.civilianUnit == null && unit.movement.canReach(it)}
        if (bestCamp != null)
            unit.movement.headTowards(bestCamp) // 2 - Head towards an encampment
        else
            UnitAutomation.wander(unit) // 3 - Can't find a reachable encampment, wander aimlessly
    }

    private fun automateUnitOnEncampment(unit: MapUnit) {
        // 1 - trying to upgrade
        if (UnitAutomation.tryUpgradeUnit(unit)) return

        // 2 - trying to attack somebody - but don't leave the encampment
        if (BattleHelper.tryAttackNearbyEnemy(unit, stayOnTile = true)) return

        // 3 - at least fortifying
        unit.fortifyIfCan()
    }

    private fun automateCombatUnit(unit: MapUnit) {
        // 1 - Try pillaging to restore health (barbs don't auto-heal)
        if (unit.health < 50 && UnitAutomation.tryPillageImprovement(unit, true) && !unit.hasMovement()) return

        // 2 - trying to upgrade
        if (UnitAutomation.tryUpgradeUnit(unit)) return

        // 3 - trying to attack enemy
        // if a embarked melee unit can land and attack next turn, do not attack from water.
        if (BattleHelper.tryDisembarkUnitToAttackPosition(unit)) return
        if (!unit.isCivilian() && BattleHelper.tryAttackNearbyEnemy(unit)) return

        // 4 - trying to pillage tile or route
        while (UnitAutomation.tryPillageImprovement(unit)) {
            if (!unit.hasMovement()) return
        }

        // 6 - wander
        UnitAutomation.wander(unit)
    }

}
