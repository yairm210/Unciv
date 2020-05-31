package com.unciv.logic.automation

import com.unciv.Constants
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.PathsToTilesWithinTurn
import com.unciv.logic.map.TileInfo
import com.unciv.models.AttackableTile

object BattleHelper {

    fun tryAttackNearbyEnemy(unit: MapUnit): Boolean {
        val attackableEnemies = getAttackableEnemies(unit, unit.movement.getDistanceToTiles())
                // Only take enemies we can fight without dying
                .filter {
                    BattleDamage.calculateDamageToAttacker(MapUnitCombatant(unit),
                            it.tileToAttackFrom,
                            Battle.getMapCombatantOfTile(it.tileToAttack)!!) < unit.health
                }

        val enemyTileToAttack = chooseAttackTarget(unit, attackableEnemies)

        if (enemyTileToAttack != null) {
            Battle.moveAndAttack(MapUnitCombatant(unit), enemyTileToAttack)
            return true
        }
        return false
    }

    fun getAttackableEnemies(
            unit: MapUnit,
            unitDistanceToTiles: PathsToTilesWithinTurn,
            tilesToCheck: List<TileInfo>? = null
    ): ArrayList<AttackableTile> {
        val tilesWithEnemies = (tilesToCheck ?: unit.civInfo.viewableTiles)
                .filter { containsAttackableEnemy(it, MapUnitCombatant(unit)) }

        val rangeOfAttack = unit.getRange()

        val attackableTiles = ArrayList<AttackableTile>()
        // The >0.1 (instead of >0) solves a bug where you've moved 2/3 road tiles,
        // you come to move a third (distance is less that remaining movements),
        // and then later we round it off to a whole.
        // So the poor unit thought it could attack from the tile, but when it comes to do so it has no movement points!
        // Silly floats, basically

        val unitMustBeSetUp = unit.hasUnique("Must set up to ranged attack")
        val tilesToAttackFrom = if (unit.type.isAirUnit()) sequenceOf(unit.currentTile)
        else
            unitDistanceToTiles.asSequence()
                    .filter {
                        val movementPointsToExpendAfterMovement = if (unitMustBeSetUp) 1 else 0
                        val movementPointsToExpendHere = if (unitMustBeSetUp && unit.action != Constants.unitActionSetUp) 1 else 0
                        val movementPointsToExpendBeforeAttack = if (it.key == unit.currentTile) movementPointsToExpendHere else movementPointsToExpendAfterMovement
                        unit.currentMovement - it.value.totalDistance - movementPointsToExpendBeforeAttack > 0.1
                    } // still got leftover movement points after all that, to attack (0.1 is because of Float nonsense, see MapUnit.moveToTile(...)
                    .map { it.key }
                    .filter { unit.movement.canMoveTo(it) || it == unit.getTile() }

        for (reachableTile in tilesToAttackFrom) {  // tiles we'll still have energy after we reach there
            val tilesInAttackRange =
                    if (unit.hasUnique("Ranged attacks may be performed over obstacles") || unit.type.isAirUnit())
                        reachableTile.getTilesInDistance(rangeOfAttack)
                    else reachableTile.getViewableTilesList(rangeOfAttack)
                            .asSequence()

            attackableTiles += tilesInAttackRange.filter { it in tilesWithEnemies }
                    .map { AttackableTile(reachableTile, it) }
        }
        return attackableTiles
    }

    fun containsAttackableEnemy(tile: TileInfo, combatant: ICombatant): Boolean {
        if (combatant is MapUnitCombatant) {
            if (combatant.unit.isEmbarked()) {
                if (tile.isWater) return false // can't attack water units while embarked, only land
                if (combatant.isRanged()) return false
            }
            if (combatant.unit.hasUnique("Can only attack water")) {
                if (tile.isLand) return false

                // trying to attack lake-to-coast or vice versa
                if ((tile.baseTerrain == Constants.lakes) != (combatant.getTile().baseTerrain == Constants.lakes))
                    return false
            }
        }

        val tileCombatant = Battle.getMapCombatantOfTile(tile) ?: return false
        if (tileCombatant.getCivInfo() == combatant.getCivInfo()) return false
        if (!combatant.getCivInfo().isAtWarWith(tileCombatant.getCivInfo())) return false

        //only submarine and destroyer can attack submarine
        //garrisoned submarine can be attacked by anyone, or the city will be in invincible
        if (tileCombatant.isInvisible() && !tile.isCityCenter()) {
            if (combatant is MapUnitCombatant
                && combatant.unit.hasUnique("Can attack submarines")
                && combatant.getCivInfo().viewableInvisibleUnitsTiles.map { it.position }.contains(tile.position)) {
                return true
            }
            return false
        }
        return true
    }

    fun tryDisembarkUnitToAttackPosition(unit: MapUnit): Boolean {
        val unitDistanceToTiles = unit.movement.getDistanceToTiles()
        if (!unit.type.isMelee() || !unit.type.isLandUnit() || !unit.isEmbarked()) return false

        val attackableEnemiesNextTurn = getAttackableEnemies(unit, unitDistanceToTiles)
                // Only take enemies we can fight without dying
                .filter {
                    BattleDamage.calculateDamageToAttacker(MapUnitCombatant(unit),
                            it.tileToAttackFrom,
                            Battle.getMapCombatantOfTile(it.tileToAttack)!!) < unit.health
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

        var enemyTileToAttack: AttackableTile? = null
        val capturableCity = cityTilesToAttack.firstOrNull { it.tileToAttack.getCity()!!.health == 1 }
        val cityWithHealthLeft = cityTilesToAttack.filter { it.tileToAttack.getCity()!!.health != 1 } // don't want ranged units to attack defeated cities
                .minBy { it.tileToAttack.getCity()!!.health }

        if (unit.type.isMelee() && capturableCity != null)
            enemyTileToAttack = capturableCity // enter it quickly, top priority!

        else if (nonCityTilesToAttack.isNotEmpty()) // second priority, units
            enemyTileToAttack = nonCityTilesToAttack.minBy { Battle.getMapCombatantOfTile(it.tileToAttack)!!.getHealth() }
        else if (cityWithHealthLeft != null) enemyTileToAttack = cityWithHealthLeft // third priority, city

        return enemyTileToAttack
    }
}