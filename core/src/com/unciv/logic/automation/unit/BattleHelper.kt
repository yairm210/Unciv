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
import kotlin.math.max

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
        val attackTile = attackableEnemies.maxByOrNull { attackableEnemy -> 
            val tempAttackValue = if (attackableEnemy.tileToAttack.isCityCenter()) 
                getCityAttackValue(unit, attackableEnemy.tileToAttack.getCity()!!)
            else getUnitAttackValue(unit, attackableEnemy)
            highestAttackValue = max(tempAttackValue, highestAttackValue)
            tempAttackValue
        }
        // todo For air units, prefer to attack tiles with lower intercept chance
        return if (highestAttackValue > 30) attackTile else null
    }

    /**
     * Returns a value which represents the attacker's motivation to attack a city.
     * Siege units will almost always attack cities.
     * Base value is 100(Mele) 110(Ranged) standard deviation is around 80 to 130
     */
    private fun getCityAttackValue(attacker: MapUnit, city: City): Int {
        val attackerUnit = MapUnitCombatant(attacker)
        val cityUnit = CityCombatant(city)
        val isCityCapturable = city.health == 1 
            || attacker.baseUnit.isMelee() && city.health <= BattleDamage.calculateDamageToDefender(attackerUnit, cityUnit).coerceAtLeast(1)
        if (isCityCapturable)
            return if (attacker.baseUnit.isMelee()) 10000 // Capture the city immediatly!
            else 0 // Don't attack the city anymore since we are a ranged unit
        
        if (attacker.baseUnit.isMelee() && attacker.health - BattleDamage.calculateDamageToAttacker(attackerUnit, cityUnit) * 2 <= 0)
            return 0 // We'll probably die next turn if we attack the city

        var attackValue = 100 
        // Siege units should really only attack the city
        if (attacker.baseUnit.isProbablySiegeUnit()) attackValue += 100
        // Ranged units don't take damage from the city
        else if (attacker.baseUnit.isRanged()) attackValue += 10
        // Lower health cities have a higher priority to attack ranges from -20 to 30
        attackValue -= (city.health - 60) / 2

        // Add value based on number of units around the city
        val defendingCityCiv = city.civ
        city.getCenterTile().neighbors.forEach {
            if (it.militaryUnit != null) {
                if (it.militaryUnit!!.civ.isAtWarWith(attacker.civ))
                    attackValue -= 5
                if (it.militaryUnit!!.civ.isAtWarWith(defendingCityCiv))
                    attackValue += 15
            }
        }
        
        return attackValue
    }

    /**
     * Returns a value which represents the attacker's motivation to attack a unit.
     * Base value is 100 and standard deviation is around 80 to 130
     */
    private fun getUnitAttackValue(attacker: MapUnit, attackTile: AttackableTile): Int {
        // Base attack value, there is nothing there...
        var attackValue = Int.MIN_VALUE
        // Prioritize attacking military
        val militaryUnit = attackTile.tileToAttack.militaryUnit
        val civilianUnit = attackTile.tileToAttack.civilianUnit
        if (militaryUnit != null) {
            attackValue = 100
            // Associate enemy units with number of hits from this unit to kill them
            val attacksToKill = (militaryUnit.health.toFloat() / 
                BattleDamage.calculateDamageToDefender(MapUnitCombatant(attacker), MapUnitCombatant(militaryUnit))).coerceAtLeast(1f)
            // We can kill them in this turn
            if (attacksToKill <= 1) attackValue += 30
            // On average, this should take around 3 turns, so -15
            else attackValue -= (attacksToKill * 5).toInt()
        } else if (civilianUnit != null) {
            attackValue = 50
            // Only melee units should really attack/capture civilian units, ranged units take more than one turn
            if (attacker.baseUnit.isMelee()) {
                if (civilianUnit.isGreatPerson()) {
                    attackValue += 150
                }
                if (civilianUnit.hasUnique(UniqueType.FoundCity)) attackValue += 60
            }
        }
        // Prioritise closer units as they are generally more threatening to this unit
        // Moving around less means we are straying less into enemy territory
        // Average should be around 2.5-5 early game and up to 35 for tanks in late game
        attackValue += (attackTile.movementLeftAfterMovingToAttackTile * 5).toInt()
        
        return attackValue
    }
}
