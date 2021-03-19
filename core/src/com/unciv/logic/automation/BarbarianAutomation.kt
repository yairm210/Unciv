package com.unciv.logic.automation

import com.unciv.Constants
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit

class BarbarianAutomation(val civInfo: CivilizationInfo) {

    fun automate() {
        // ranged go first, after melee and then everyone else
        civInfo.getCivUnits().filter { it.type.isRanged() }.forEach { automateUnit(it) }
        civInfo.getCivUnits().filter { it.type.isMelee() }.forEach { automateUnit(it) }
        civInfo.getCivUnits().filter { !it.type.isRanged() && !it.type.isMelee() }.forEach { automateUnit(it) }
    }

    private fun automateUnit(unit: MapUnit) {
        if (unit.currentTile.improvement == Constants.barbarianEncampment) automateUnitOnEncampment(unit)
        else automateCombatUnit(unit)
    }

    private fun automateUnitOnEncampment(unit: MapUnit) {
        // 1 - trying to upgrade
        if (UnitAutomation.tryUpgradeUnit(unit)) return

        // 2 - trying to attack somebody
        if (BattleHelper.tryAttackNearbyEnemy(unit)) return

        // 3 - at least fortifying
        unit.fortifyIfCan()
    }

    private fun automateCombatUnit(unit: MapUnit) {
        // 1 - Try pillaging to restore health (barbs don't auto-heal)
        if (unit.health < 50 && UnitAutomation.tryPillageImprovement(unit)) return

        // 2 - trying to upgrade
        if (UnitAutomation.tryUpgradeUnit(unit)) return

        // 3 - trying to attack enemy
        // if a embarked melee unit can land and attack next turn, do not attack from water.
        if (BattleHelper.tryDisembarkUnitToAttackPosition(unit)) return
        if (!unit.type.isCivilian() && BattleHelper.tryAttackNearbyEnemy(unit)) return

        // 4 - trying to pillage tile or route
        if (UnitAutomation.tryPillageImprovement(unit)) return

        // 6 - wander
        UnitAutomation.wander(unit)
    }

}