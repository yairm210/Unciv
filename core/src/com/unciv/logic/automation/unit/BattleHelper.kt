package com.unciv.logic.automation.unit

import com.unciv.logic.battle.AttackableTile
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.City
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

    /**
     * Choses the best target in attackableEnemies, this could be a city or a unit.
     */
    private fun chooseAttackTarget(unit: MapUnit, attackableEnemies: List<AttackableTile>): AttackableTile? {
        var highestAttackValue = 0
        var attackTile: AttackableTile? = null
        for (attackableEnemy in attackableEnemies) {
            val tempValue = if (attackableEnemy.tileToAttack.isCityCenter()) getCityAttackValue(unit, attackableEnemy.tileToAttack.getCity()!!)
            else getUnitAttackValue(unit, attackableEnemy)
            if (tempValue > highestAttackValue) {
                highestAttackValue = tempValue
                attackTile = attackableEnemy
            }
        }
        // todo For air units, prefer to attack tiles with lower intercept chance
        return attackTile
    }

    /**
     * Returns a value which represents the attacker's motivation to attack a city.
     * Ranged and siege units are prefered.
     */
    private fun getCityAttackValue(attacker: MapUnit, city: City): Int {
        val attackerUnit = MapUnitCombatant(attacker)
        val cityUnit = CityCombatant(city)
        val isCityCapturable = city.health == 1 
            || attacker.baseUnit.isMelee() && city.health <= BattleDamage.calculateDamageToDefender(attackerUnit, cityUnit).coerceAtLeast(1)
        if (isCityCapturable) {
            return if (attacker.baseUnit.isMelee()) 10000 // Capture the city immediatly!
            else 0 // Don't attack the city anymore as we are a ranged unit
        }
        
        // We'll probably die next turn if we attack the city
        if (attacker.baseUnit.isMelee() && attacker.health - BattleDamage.calculateDamageToAttacker(attackerUnit, cityUnit) * 2 <= 0)
            return 0

        var attackValue = 120 
        // Siege units should really only attack the city
        if (attacker.baseUnit.isProbablySiegeUnit()) attackValue += 300
        // Ranged units don't take damage from the city
        else if (attacker.baseUnit.isRanged()) attackValue += 50
        // Lower health cities have a higher priority to attack
        attackValue -= (city.health - 50) / 2

        // Add value based on number of units around the city
        val defendingCityCiv = city.civ
        city.getCenterTile().neighbors.forEach {
            if (it.militaryUnit != null) {
                if (it.militaryUnit?.civ == defendingCityCiv)
                    attackValue -= 10
                if (it.militaryUnit?.civ == attacker.civ)
                    attackValue += 20
            }
        }
        
        return attackValue
    }

    /**
     * Returns a value which represents the attacker's motivation to attack a unit.
     */
    private fun getUnitAttackValue(attacker: MapUnit, attackTile: AttackableTile): Int {
        // Base attack value, there is nothing there...
        var attackValue = Int.MIN_VALUE
        // Prioritize attacking military
        val militaryUnit = attackTile.tileToAttack.militaryUnit
        if (militaryUnit != null) {
            attackValue = 0
            attackValue += 150
            // Associate enemy units with number of hits from this unit to kill them
            val attacksToKill = (militaryUnit.health.toFloat() / 
                BattleDamage.calculateDamageToDefender(MapUnitCombatant(attacker), MapUnitCombatant(militaryUnit))).coerceAtLeast(1f)
            // We can kill them in this turn
            if (attacksToKill <= 1) attackValue += 100
            // On average, this should take around 3 turns, so -30
            else attackValue -= (attacksToKill * 10).toInt()
        } else {
            val civilianUnit = attackTile.tileToAttack.civilianUnit
            if (civilianUnit != null) {
                attackValue = 0
                attackValue += 50
                // Only melee units should really attack/capture civilian units, ranged units take more than one turn
                if (attacker.baseUnit.isMelee()) {
                    if (civilianUnit.isGreatPerson()) {
                        attackValue += 50
                        // This is really good if we can kill a great general
                        if (civilianUnit.isGreatPersonOfType("Great General")) attackValue += 150
                    }
                    if (civilianUnit.hasUnique(UniqueType.FoundCity)) attackValue += 30
                }
            }
        }
        // Prioritise closer units as they are generally more threatening to this unit
        attackValue += (attackTile.movementLeftAfterMovingToAttackTile * 5).toInt()
        
        return attackValue
    }
}
