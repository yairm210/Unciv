package com.unciv.logic.automation.unit

import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.UniqueType

object BattleHelper {

    fun tryAttackNearbyEnemy(unit: MapUnit, stayOnTile: Boolean = false): Boolean {
        if (unit.hasUnique(UniqueType.CannotAttack)) return false
        val attackableEnemies = TargetHelper.getAttackableEnemies(unit, unit.movement.getDistanceToTiles(), stayOnTile=stayOnTile)
            // Only take enemies we can fight without dying
            .filter {
                BattleDamage.calculateDamageToAttacker(
                    MapUnitCombatant(unit),
                    Battle.getMapCombatantOfTile(it.tileToAttack)!!
                ) + unit.getDamageFromTerrain(it.tileToAttackFrom) < unit.health
            }

        val enemyTileToAttack = chooseAttackTarget(unit, attackableEnemies)

        if (enemyTileToAttack != null) {
            Battle.moveAndAttack(MapUnitCombatant(unit), enemyTileToAttack)
        }
        return unit.currentMovement == 0f
    }

    fun tryDisembarkUnitToAttackPosition(unit: MapUnit): Boolean {
        if (!unit.baseUnit.isMelee() || !unit.baseUnit.isLandUnit() || !unit.isEmbarked()) return false
        val unitDistanceToTiles = unit.movement.getDistanceToTiles()

        val attackableEnemiesNextTurn = TargetHelper.getAttackableEnemies(unit, unitDistanceToTiles)
                // Only take enemies we can fight without dying
                .filter {
                    BattleDamage.calculateDamageToAttacker(
                        MapUnitCombatant(unit),
                        Battle.getMapCombatantOfTile(it.tileToAttack)!!
                    ) < unit.health
                }
                .filter { it.tileToAttackFrom.isLand }

        val enemyTileToAttackNextTurn = chooseAttackTarget(unit, attackableEnemiesNextTurn)

        if (enemyTileToAttackNextTurn != null) {
            unit.movement.moveToTile(enemyTileToAttackNextTurn.tileToAttackFrom)
            return true
        }
        return false
    }

    private fun chooseAttackTarget(unit: MapUnit, attackableEnemies: List<AttackableTile>): AttackableTile? {
        val cityTilesToAttack = attackableEnemies.filter { it.tileToAttack.isCityCenter() }
        val nonCityTilesToAttack = attackableEnemies.filter { !it.tileToAttack.isCityCenter() }

        // todo For air units, prefer to attack tiles with lower intercept chance

        val capturableCity = cityTilesToAttack.firstOrNull { it.tileToAttack.getCity()!!.health == 1 }
        val cityWithHealthLeft =
            cityTilesToAttack.filter { it.tileToAttack.getCity()!!.health != 1 } // don't want ranged units to attack defeated cities
                .minByOrNull { it.tileToAttack.getCity()!!.health }

        if (unit.baseUnit.isMelee() && capturableCity != null)
            return capturableCity // enter it quickly, top priority!

        if (nonCityTilesToAttack.isNotEmpty()) // second priority, units
            return chooseUnitToAttack(unit, nonCityTilesToAttack)

        if (cityWithHealthLeft != null) return cityWithHealthLeft // third priority, city

        return null
    }

    private fun chooseUnitToAttack(unit: MapUnit, attackableUnits: List<AttackableTile>): AttackableTile {
        val militaryUnits = attackableUnits.filter { it.tileToAttack.militaryUnit != null }

        // prioritize attacking military
        if (militaryUnits.isNotEmpty()) {
            // associate enemy units with number of hits from this unit to kill them
            val attacksToKill = militaryUnits
                .associateWith { it.tileToAttack.militaryUnit!!.health.toFloat() / BattleDamage.calculateDamageToDefender(
                        MapUnitCombatant(unit),
                        MapUnitCombatant(it.tileToAttack.militaryUnit!!)
                    ).toFloat().coerceAtLeast(1f) }

            // kill a unit if possible, prioritizing by attack strength
            val canKill = attacksToKill.filter { it.value <= 1 }.keys
                .sortedByDescending { it.movementLeftAfterMovingToAttackTile } // Among equal kills, prioritize the closest unit
                .maxByOrNull { MapUnitCombatant(it.tileToAttack.militaryUnit!!).getAttackingStrength() }
            if (canKill != null) return canKill

            // otherwise pick the unit we can kill the fastest
            return attacksToKill.minBy { it.value }.key
        }

        // only civilians in attacking range - GP most important, second settlers, then anything else

        val unitsToConsider = attackableUnits.filter { it.tileToAttack.civilianUnit!!.isGreatPerson() }
            .ifEmpty { attackableUnits.filter { it.tileToAttack.civilianUnit!!.hasUnique(UniqueType.FoundCity) } }
            .ifEmpty { attackableUnits }

        // Melee - prioritize by distance, so we have most movement left
        if (unit.baseUnit.isMelee()){
            return unitsToConsider.maxBy { it.movementLeftAfterMovingToAttackTile }
        }

        // We're ranged, prioritize that we can kill
        return unitsToConsider.minBy {
            Battle.getMapCombatantOfTile(it.tileToAttack)!!.getHealth()
        }
    }
}
