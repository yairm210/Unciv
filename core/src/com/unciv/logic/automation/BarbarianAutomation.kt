package com.unciv.logic.automation

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.PathsToTilesWithinTurn
import com.unciv.logic.map.TileInfo
import com.unciv.models.AttackableTile
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.ui.worldscreen.unit.UnitActions

class BarbarianAutomation(val civInfo: CivilizationInfo) {

    private val battleHelper = BattleHelper()
    private val battleDamage = BattleDamage()

    fun automate() {
        // ranged go first, after melee and then everyone else
        civInfo.getCivUnits().filter { it.type.isRanged() }.forEach { automateUnit(it) }
        civInfo.getCivUnits().filter { it.type.isMelee() }.forEach { automateUnit(it) }
        civInfo.getCivUnits().filter { !it.type.isRanged() && !it.type.isMelee() }.forEach { automateUnit(it) }
    }

    private fun automateUnit(unit: MapUnit) {
        when {
            unit.currentTile.improvement == Constants.barbarianEncampment -> automateEncampment(unit)
            unit.type == UnitType.Scout -> automateScout(unit)
            else -> automateCombatUnit(unit)
        }
    }

    private fun automateEncampment(unit: MapUnit) {
        val unitActions = UnitActions().getUnitActions(unit, UncivGame.Current.worldScreen)

        // 1 - trying to upgrade
        if (tryUpgradeUnit(unit, unitActions)) return

        // 2 - trying to attack somebody
        if (battleHelper.tryAttackNearbyEnemy(unit)) return

        // 3 - at least fortifying
        unit.fortifyIfCan()
    }

    private fun automateCombatUnit(unit: MapUnit) {
        val unitActions = UnitActions().getUnitActions(unit, UncivGame.Current.worldScreen)
        val unitDistanceToTiles = unit.movement.getDistanceToTiles()
        val nearEnemyTiles = battleHelper.getAttackableEnemies(unit, unitDistanceToTiles)

        // 1 - heal or fortifying if death is near
        if (unit.health < 50) {
            val possibleDamage = nearEnemyTiles
                    .map {
                        battleDamage.calculateDamageToAttacker(MapUnitCombatant(unit),
                                Battle.getMapCombatantOfTile(it.tileToAttack)!!)
                    }
                    .sum()
            val possibleHeal = unit.rankTileForHealing(unit.currentTile)
            if (possibleDamage > possibleHeal) {
                // run
                val furthestTile = findFurthestTileCanMoveTo(unit, unitDistanceToTiles, nearEnemyTiles)
                if(furthestTile!=null) unit.movement.moveToTile(furthestTile)
            }
            unit.fortifyIfCan()
            return
        }

        // 2 - trying to upgrade
        if (tryUpgradeUnit(unit, unitActions)) return

        // 3 - trying to attack enemy
        // if a embarked melee unit can land and attack next turn, do not attack from water.
        if (battleHelper.tryDisembarkUnitToAttackPosition(unit, unitDistanceToTiles)) return
        if (battleHelper.tryAttackNearbyEnemy(unit)) return

        // 4 - trying to pillage tile or route
        if (UnitAutomation().tryPillageImprovement(unit, unitDistanceToTiles)) return

        // 5 - heal the unit if needed
        if (unit.health < 100 && UnitAutomation().tryHealUnit(unit, unitDistanceToTiles)) return

        // 6 - wander
        UnitAutomation().wander(unit, unitDistanceToTiles)
    }

    private fun automateScout(unit: MapUnit) {
        val unitDistanceToTiles = unit.movement.getDistanceToTiles()
        val nearEnemyTiles = battleHelper.getAttackableEnemies(unit, unitDistanceToTiles)

        // 1 - heal or run if death is near
        if (unit.health < 50) {
            if (nearEnemyTiles.isNotEmpty()) {
                val furthestTile = findFurthestTileCanMoveTo(unit, unitDistanceToTiles, nearEnemyTiles)
                if(furthestTile!=null) unit.movement.moveToTile(furthestTile)
            }
            unit.fortifyIfCan()

            return
        }

        // 2 - trying to capture someone
        // TODO

        // 3 - trying to pillage tile or trade route
        if (UnitAutomation().tryPillageImprovement(unit, unitDistanceToTiles)) return

        // 4 - heal the unit if needed
        if (unit.health < 100 && UnitAutomation().tryHealUnit(unit, unitDistanceToTiles)) return

        // 5 - wander
        UnitAutomation().wander(unit, unitDistanceToTiles)
    }

    private fun findFurthestTileCanMoveTo(
            unit: MapUnit,
            unitDistanceToTiles: PathsToTilesWithinTurn,
            nearEnemyTiles: List<AttackableTile>
    ): TileInfo? {
        val possibleTiles = unitDistanceToTiles.keys.filter { unit.movement.canMoveTo(it) }
        if(possibleTiles.isEmpty()) return null
        val enemies = nearEnemyTiles.mapNotNull { it.tileToAttack.militaryUnit }
        var furthestTile: Pair<TileInfo, Float> = possibleTiles.random() to 0f
        for (enemy in enemies) {
            for (tile in possibleTiles) {
                val distance = enemy.movement.getMovementCostBetweenAdjacentTiles(enemy.currentTile, tile, enemy.civInfo)
                if (distance > furthestTile.second) {
                    furthestTile = tile to distance
                }
            }
        }
        return furthestTile.first
    }


    private fun tryUpgradeUnit(unit: MapUnit, unitActions: List<UnitAction>): Boolean {
        if (unit.baseUnit().upgradesTo != null) {
            val upgradedUnit = unit.civInfo.gameInfo.ruleSet.units[unit.baseUnit().upgradesTo!!]!!
            if (upgradedUnit.isBuildable(unit.civInfo)) {
                val upgradeAction = unitActions.firstOrNull { it.type == UnitActionType.Upgrade }
                if (upgradeAction != null && upgradeAction.canAct) {
                    upgradeAction.action?.invoke()
                    return true
                }
            }
        }
        return false
    }

}