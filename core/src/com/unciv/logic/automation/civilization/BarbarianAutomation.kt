package com.unciv.logic.automation.civilization

import com.unciv.view.hasMovement
import com.unciv.view.isCivilian
import com.unciv.logic.automation.unit.BattleHelper
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.civilization.Civilization
import com.unciv.view.GameView
import com.unciv.view.MapUnitView

class BarbarianAutomation(val civInfo: Civilization) {

    fun automate() {
        val units = GameView(civInfo.gameInfo, civInfo).civView.getUnits()
        // ranged go first, after melee and then everyone else
        units.filter { it.getBaseUnit().isRanged() }.forEach { automateUnit(it) }
        units.filter { it.getBaseUnit().isMelee() }.forEach { automateUnit(it) }
        units.filter { !it.getBaseUnit().isRanged() && !it.getBaseUnit().isMelee() }.forEach { automateUnit(it) }
        // fix buildup of alerts - to shrink saves and ease debugging
        civInfo.popupAlerts.clear()
    }

    private fun automateUnit(unit: MapUnitView) {
        if (unit.isCivilian()) automateCapturedCivilian(unit)
        else if (unit.getUnit().currentTile.isBarbarianEncampment()) automateUnitOnEncampment(unit)
        else automateCombatUnit(unit)
    }

    private fun automateCapturedCivilian(unitView: MapUnitView) {
        val unit = unitView.getUnit()
        // 1 - Stay on current encampment
        if (unit.currentTile.isBarbarianEncampment()) return

        val campTiles = unit.civ.gameInfo.barbarians.getEncampmentTiles()
        campTiles.sortBy { unit.currentTile.aerialDistanceTo(it) }
        val bestCamp = campTiles.firstOrNull { it.civilianUnit == null && unit.movement.canReach(it)}
        if (bestCamp != null)
            unit.movement.headTowards(bestCamp) // 2 - Head towards an encampment
        else
            UnitAutomation.wander(unitView) // 3 - Can't find a reachable encampment, wander aimlessly
    }

    private fun automateUnitOnEncampment(unitView: MapUnitView) {
        // 1 - trying to upgrade
        if (UnitAutomation.tryUpgradeUnit(unitView)) return

        // 2 - trying to attack somebody - but don't leave the encampment
        if (BattleHelper.tryAttackNearbyEnemy(unitView, stayOnTile = true)) return

        // 3 - at least fortifying
        unitView.getUnit().fortifyIfCan()
    }

    private fun automateCombatUnit(unit: MapUnitView) {
        // 1 - Try pillaging to restore health (barbs don't auto-heal)
        if (unit.unitHealth < 50 && UnitAutomation.tryPillageImprovement(unit, true) && !unit.hasMovement()) return

        // 2 - trying to upgrade
        if (UnitAutomation.tryUpgradeUnit(unit)) return

        // 3 - trying to attack enemy
        // if a embarked melee unit can land and attack next turn, do not attack from water.
        if (BattleHelper.tryDisembarkUnitToAttackPosition(unit.getUnit())) return
        if (!unit.isCivilian() && BattleHelper.tryAttackNearbyEnemy(unit)) return

        // 4 - trying to pillage tile or route
        while (UnitAutomation.tryPillageImprovement(unit)) {
            if (!unit.hasMovement()) return
        }

        // 6 - wander
        UnitAutomation.wander(unit)
    }

}
