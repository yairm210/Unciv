package com.unciv.logic.automation.unit

import com.unciv.view.AttackableTileView
import com.unciv.view.ForeignCityView
import com.unciv.view.MapUnitView
import com.unciv.view.hasMovement
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

object BattleHelper {

    /** Returns true if the unit cannot further move this turn - NOT if an attack was successful! */
    fun tryAttackNearbyEnemy(unitView: MapUnitView, stayOnTile: Boolean = false): Boolean {
        if (unitView.hasUnique(UniqueType.CannotAttack)) return false
        val distanceToTiles = unitView.getDistanceToTiles()
        val attackableEnemies = unitView.getAttackableEnemies(stayOnTile = stayOnTile)
            // Only take enemies we can fight without dying or are made to die
            .filter {
                val defender = it.getTileToAttack().getCombatant()
                unitView.hasUnique(UniqueType.SelfDestructs) || (defender != null &&
                (unitView.asCombatant().calculateDamageToAttacker(defender) < unitView.unitHealth
                    && unitView.getDamageFromTerrain(it.getTileToAttackFrom()) <= 0))
                    // For mounted units it is fine to attack from these tiles, but with current AI movement logic it is not easy to determine if our unit can meaningfully move away after attacking
                    // Also, AI doesn't build tactical roads
            }

        val enemyTileToAttack = chooseAttackTarget(unitView, attackableEnemies)

        if (enemyTileToAttack != null) {
            val tileToAttack = enemyTileToAttack.getTileToAttack()
            if (tileToAttack.militaryUnit == null && unitView.getBaseUnit().isRanged()
                && unitView.canMoveTo(tileToAttack)
                && distanceToTiles.containsKey(tileToAttack)) { // Since the 'getAttackableEnemies' could return a tile we attack at range but cannot reach
                // Ranged units should move to caputre a civilian unit instead of attacking it
                unitView.tryMoveToTile(tileToAttack)
            } else {
                if (unitView.tryMovePreparingAttack(enemyTileToAttack, true))
                    unitView.attackOrNuke(enemyTileToAttack)
            }
        }
        return !unitView.hasMovement()
    }

    fun tryDisembarkUnitToAttackPosition(unitView: MapUnitView): Boolean {
        if (!unitView.getBaseUnit().isMelee() || !unitView.getBaseUnit().isLandUnit || !unitView.isEmbarked()) return false

        val attackableEnemiesNextTurn = unitView.getAttackableEnemies()
                // Only take enemies we can fight without dying
                .filter {
                    unitView.asCombatant().calculateDamageToAttacker(it.getTileToAttack().getCombatant()!!) < unitView.unitHealth
                }
                .filter { it.getTileToAttackFrom().isLand }

        val enemyTileToAttackNextTurn = chooseAttackTarget(unitView, attackableEnemiesNextTurn)

        if (enemyTileToAttackNextTurn != null) {
            unitView.tryMoveToTile(enemyTileToAttackNextTurn.getTileToAttackFrom())
            return true
        }
        return false
    }

    /**
     * Choses the best target in attackableEnemies, this could be a city or a unit.
     */
    @Readonly
    private fun chooseAttackTarget(unit: MapUnitView, attackableEnemies: List<AttackableTileView>): AttackableTileView? {
        // Get the highest valued attackableEnemy
        var highestAttackValue = 0
        var attackTile: AttackableTileView? = null
        // We always have to calculate the attack value even if there is only one attackableEnemy
        for (attackableEnemy in attackableEnemies) {
            val tileToAttack = attackableEnemy.getTileToAttack()
            val tempAttackValue = if (tileToAttack.isCityCenter())
                getCityAttackValue(unit, tileToAttack.getCity()!!)
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
    @Readonly
    private fun getCityAttackValue(attacker: MapUnitView, city: ForeignCityView): Int {
        val attackerUnit = attacker.asCombatant()
        val cityUnit = city.asCombatant()
        
        val canCaptureCity = attacker.getBaseUnit().isMelee() && !attacker.hasUnique(UniqueType.CannotCaptureCities)
        if (city.getHealth() == 1)
            return if (canCaptureCity) 10000 // Capture the city immediately!
            else 0 // No reason to attack, we won't make any difference
        
        if (canCaptureCity && city.getHealth() <= attackerUnit.calculateDamageToDefender(cityUnit).coerceAtLeast(1))
            return 10000
            

        if (attacker.getBaseUnit().isMelee()) {
            val battleDamage = attackerUnit.calculateDamageToAttacker(cityUnit)
            if (attacker.unitHealth - battleDamage * 2 <= 0 && !attacker.hasUnique(UniqueType.SelfDestructs)) {
                // The more fiendly units around the city, the more willing we should be to just attack the city
                val friendlyUnitsAroundCity = city.getCenterTile().getExploredTilesInDistance(3)
                    .count { it.militaryUnit?.civ() == attacker.civ() }
                // If we have more than 4 other units around the city, go for it
                if (friendlyUnitsAroundCity < 5) {
                    val attackerHealthModifier = 1.0 + 1.0 / friendlyUnitsAroundCity
                    if (attacker.unitHealth - battleDamage * attackerHealthModifier <= 0)
                        return 0 // We'll probably die next turn if we attack the city
                }
            }
        }

        var attackValue = 100
        // Siege units should really only attack the city
        if (attacker.getBaseUnit().isProbablySiegeUnit()) attackValue += 100
        // Ranged units don't take damage from the city
        else if (attacker.getBaseUnit().isRanged()) attackValue += 10
        // Lower health cities have a higher priority to attack ranges from -20 to 30
        attackValue -= (city.getHealth() - 60) / 2

        // Add value based on number of units around the city
        val defendingCityCiv = city.owningCiv()
        for (tile in city.getCenterTile().getExploredTilesInDistance(2)) {
            val militaryUnit = tile.militaryUnit ?: continue
            if (militaryUnit.civ().isAtWarWith(attacker.civ()))
                attackValue -= 5
            if (militaryUnit.civ().isAtWarWith(defendingCityCiv))
                attackValue += 15
        }

        return attackValue
    }

    /**
     * Returns a value which represents the attacker's motivation to attack a unit.
     * Base value is 100 and standard deviation is around 80 to 130
     */
    @Readonly
    private fun getUnitAttackValue(attacker: MapUnitView, attackTile: AttackableTileView): Int {
        // Base attack value, there is nothing there...
        var attackValue = Int.MIN_VALUE
        // Prioritize attacking military
        val tileToAttack = attackTile.getTileToAttack()
        val militaryUnit = tileToAttack.militaryUnit
        val civilianUnit = tileToAttack.civilianUnit
        if (militaryUnit != null) {
            attackValue = 200 - militaryUnit.unitHealth + // continuously prioritise lower-health units
                attacker.asCombatant().calculateDamageToDefender(militaryUnit.asCombatant())
        } else if (civilianUnit != null) {
            attackValue = 50
            // Only melee units should really attack/capture civilian units, ranged units may be able to capture by moving
            if (attacker.getBaseUnit().isMelee() || attacker.getDistanceToTiles().containsKey(tileToAttack)) {
                if (civilianUnit.getBaseUnit().isGreatPerson) {
                    attackValue += 150
                }
                if (civilianUnit.hasUnique(UniqueType.FoundCity, ignoreConditionals = true)) attackValue += 60
            } else if (attacker.getBaseUnit().isRanged() && !civilianUnit.hasUnique(UniqueType.Uncapturable)) {
                return 10 // Don't shoot civilians that we can capture!
            }
        }
        // Prioritise closer units as they are generally more threatening to this unit
        // Moving around less means we are straying less into enemy territory
        // Average should be around 2.5-5 early game and up to 35 for tanks in late game
        attackValue += (attackTile.getMovementLeftAfterMovingToAttackTile() * 5).toInt()

        return attackValue
    }
}
