package com.unciv.logic.automation.unit

import com.unciv.logic.battle.AttackableTile
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.city.City
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType

object BattleHelper {

    /** Returns true if the unit cannot further move this turn - NOT if an attack was successful! */
    fun tryAttackNearbyEnemy(unit: MapUnit, stayOnTile: Boolean = false): Boolean {
        if (unit.hasUnique(UniqueType.CannotAttack)) return false
        val distanceToTiles = unit.movement.getDistanceToTiles()
        val attackableEnemies = TargetHelper.getAttackableEnemies(unit, unit.movement.getDistanceToTiles(), stayOnTile=stayOnTile)
            // Only take enemies we can fight without dying or are made to die
            .filter {unit.hasUnique(UniqueType.SelfDestructs) ||
                (BattleDamage.calculateDamageToAttacker(
                    MapUnitCombatant(unit),
                    Battle.getMapCombatantOfTile(it.tileToAttack)!!) < unit.health
                    && unit.getDamageFromTerrain(it.tileToAttackFrom) <= 0)
                    // For mounted units it is fine to attack from these tiles, but with current AI movement logic it is not easy to determine if our unit can meaningfully move away after attacking
                    // Also, AI doesn't build tactical roads
            }

        val enemyTileToAttack = chooseAttackTarget(unit, attackableEnemies)

        if (enemyTileToAttack != null) {
            if (enemyTileToAttack.tileToAttack.militaryUnit == null && unit.baseUnit.isRanged()
                && unit.movement.canMoveTo(enemyTileToAttack.tileToAttack)
                && distanceToTiles.containsKey(enemyTileToAttack.tileToAttack)) { // Since the 'getAttackableEnemies' could return a tile we attack at range but cannot reach
                // Ranged units should move to caputre a civilian unit instead of attacking it
                unit.movement.moveToTile(enemyTileToAttack.tileToAttack)
            } else {
                Battle.moveAndAttack(MapUnitCombatant(unit), enemyTileToAttack)
            }
        }
        return !unit.hasMovement()
    }

    fun tryDisembarkUnitToAttackPosition(unit: MapUnit): Boolean {
        if (!unit.baseUnit.isMelee() || !unit.baseUnit.isLandUnit || !unit.isEmbarked()) return false
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
        // Get the highest valued attackableEnemy
        var highestAttackValue = 0
        var attackTile: AttackableTile? = null
        // We always have to calculate the attack value even if there is only one attackableEnemy
        for (attackableEnemy in attackableEnemies) {
            val tempAttackValue = if (attackableEnemy.tileToAttack.isCityCenter())
                getCityAttackValue(unit, attackableEnemy.tileToAttack.getCity()!!)
            else getUnitAttackValue(unit, attackableEnemy)
            if (tempAttackValue > highestAttackValue) {
                highestAttackValue = tempAttackValue
                attackTile = attackableEnemy
            }
        }
        // todo For air units, prefer to attack tiles with lower intercept chance
        // Only return that tile if it is actually a good tile to attack
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

        if (attacker.baseUnit.isMelee()) {
            val battleDamage = BattleDamage.calculateDamageToAttacker(attackerUnit, cityUnit)
            if (attacker.health - battleDamage * 2 <= 0 && !attacker.hasUnique(UniqueType.SelfDestructs)) {
                // The more fiendly units around the city, the more willing we should be to just attack the city
                val friendlyUnitsAroundCity = city.getCenterTile().getTilesInDistance(3).count { it.militaryUnit?.civ == attacker.civ }
                // If we have more than 4 other units around the city, go for it
                if (friendlyUnitsAroundCity < 5) {
                    val attackerHealthModifier = 1.0 + 1.0 / friendlyUnitsAroundCity
                    if (attacker.health - battleDamage * attackerHealthModifier <= 0)
                        return 0 // We'll probably die next turn if we attack the city
                }
            }
        }

        var attackValue = 100
        // Siege units should really only attack the city
        if (attacker.baseUnit.isProbablySiegeUnit()) attackValue += 100
        // Ranged units don't take damage from the city
        else if (attacker.baseUnit.isRanged()) attackValue += 10
        // Lower health cities have a higher priority to attack ranges from -20 to 30
        attackValue -= (city.health - 60) / 2

        // Add value based on number of units around the city
        val defendingCityCiv = city.civ
        city.getCenterTile().getTilesInDistance(2).forEach {
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
                BattleDamage.calculateDamageToDefender(MapUnitCombatant(attacker), MapUnitCombatant(militaryUnit)))
                .coerceAtLeast(1f).coerceAtMost(10f)
            // We can kill them in this turn
            if (attacksToKill <= 1) attackValue += 30
            // On average, this should take around 3 turns, so -15
            else attackValue -= (attacksToKill * 5).toInt()
        } else if (civilianUnit != null) {
            attackValue = 50
            // Only melee units should really attack/capture civilian units, ranged units may be able to capture by moving
            if (attacker.baseUnit.isMelee() || attacker.movement.canReachInCurrentTurn(attackTile.tileToAttack)) {
                if (civilianUnit.isGreatPerson()) {
                    attackValue += 150
                }
                if (civilianUnit.hasUnique(UniqueType.FoundCity, StateForConditionals.IgnoreConditionals)) attackValue += 60
            } else if (attacker.baseUnit.isRanged() && !civilianUnit.hasUnique(UniqueType.Uncapturable)) {
                return 10 // Don't shoot civilians that we can capture!
            }
        }
        // Prioritise closer units as they are generally more threatening to this unit
        // Moving around less means we are straying less into enemy territory
        // Average should be around 2.5-5 early game and up to 35 for tanks in late game
        attackValue += (attackTile.movementLeftAfterMovingToAttackTile * 5).toInt()

        return attackValue
    }
}
