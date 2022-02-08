package com.unciv.logic.automation

import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.PathsToTilesWithinTurn
import com.unciv.logic.map.TileInfo
import com.unciv.models.AttackableTile
import com.unciv.models.ruleset.unique.UniqueType

object BattleHelper {

    fun tryAttackNearbyEnemy(unit: MapUnit, stayOnTile: Boolean = false): Boolean {
        if (unit.hasUnique(UniqueType.CannotAttack)) return false
        val attackableEnemies = getAttackableEnemies(unit, unit.movement.getDistanceToTiles(), stayOnTile=stayOnTile)
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
            tilesToCheck: List<TileInfo>? = null,
            stayOnTile: Boolean = false
    ): ArrayList<AttackableTile> {
        val tilesWithEnemies = (tilesToCheck ?: unit.civInfo.viewableTiles)
            .filter { containsAttackableEnemy(it, MapUnitCombatant(unit)) }
            .filterNot {
                val mapCombatant = Battle.getMapCombatantOfTile(it)
                // IF all of these are true, THEN the action we'll be taking is in fact CAPTURING the civilian.
                unit.baseUnit.isMelee() && mapCombatant is MapUnitCombatant && mapCombatant.unit.isCivilian()
                        // If we can't pass though that tile, we can't capture the civilian "remotely"
                        // DO NOT use "!unit.movement.canPassThrough(it)" since then we won't be able to
                        // capture enemy units since we can't move through them!
                        && !it.canCivPassThrough(unit.civInfo)
                        // Land Unit can't capture Naval and vice versa
                        && (unit.type.isLandUnit() && it.isWater || unit.type.isWaterUnit() && it.isLand)
            }

        val rangeOfAttack = unit.getRange()

        val attackableTiles = ArrayList<AttackableTile>()
        // The >0.1 (instead of >0) solves a bug where you've moved 2/3 road tiles,
        // you come to move a third (distance is less that remaining movements),
        // and then later we round it off to a whole.
        // So the poor unit thought it could attack from the tile, but when it comes to do so it has no movement points!
        // Silly floats, basically

        val unitMustBeSetUp = unit.hasUnique(UniqueType.MustSetUp)
        val tilesToAttackFrom = if (stayOnTile || unit.baseUnit.movesLikeAirUnits())
            sequenceOf(Pair(unit.currentTile, unit.currentMovement))
        else
            unitDistanceToTiles.asSequence()
                .map { (tile, distance) ->
                    val movementPointsToExpendAfterMovement = if (unitMustBeSetUp) 1 else 0
                    val movementPointsToExpendHere =
                        if (unitMustBeSetUp && !unit.isSetUpForSiege()) 1 else 0
                    val movementPointsToExpendBeforeAttack =
                        if (tile == unit.currentTile) movementPointsToExpendHere else movementPointsToExpendAfterMovement
                    val movementLeft =
                        unit.currentMovement - distance.totalDistance - movementPointsToExpendBeforeAttack
                    Pair(tile, movementLeft)
                }
                // still got leftover movement points after all that, to attack (0.1 is because of Float nonsense, see MapUnit.moveToTile(...)
                .filter { it.second > 0.1f }
                .filter {
                    it.first == unit.getTile() || unit.movement.canMoveTo(it.first)
                }

        for ((reachableTile, movementLeft) in tilesToAttackFrom) {  // tiles we'll still have energy after we reach there
            val tilesInAttackRange =
                if (unit.hasUnique("Ranged attacks may be performed over obstacles") || unit.baseUnit.movesLikeAirUnits())
                    reachableTile.getTilesInDistance(rangeOfAttack)
                else reachableTile.getViewableTilesList(rangeOfAttack)
                    .asSequence()

            attackableTiles += tilesInAttackRange.filter { it in tilesWithEnemies }
                .map { AttackableTile(reachableTile, it, movementLeft) }
        }
        return attackableTiles
    }

    fun containsAttackableEnemy(tile: TileInfo, combatant: ICombatant): Boolean {
        if (combatant is MapUnitCombatant && combatant.unit.isEmbarked()) {
            if (tile.isWater) return false // can't attack water units while embarked, only land
            if (combatant.isRanged()) return false
        }

        val tileCombatant = Battle.getMapCombatantOfTile(tile) ?: return false
        if (tileCombatant.getCivInfo() == combatant.getCivInfo()) return false
        if (!combatant.getCivInfo().isAtWarWith(tileCombatant.getCivInfo())) return false

        if (combatant is MapUnitCombatant && 
            combatant.unit.hasUnique(UniqueType.CanOnlyAttackUnits) &&
            combatant.unit.getMatchingUniques(UniqueType.CanOnlyAttackUnits).none { tileCombatant.matchesCategory(it.params[0]) }
        )
            return false

        if (combatant is MapUnitCombatant &&
            combatant.unit.getMatchingUniques(UniqueType.CanOnlyAttackTiles)
                .let { unique -> unique.any() && unique.none { tile.matchesFilter(it.params[0]) } }
        )
            return false

        // Only units with the right unique can view submarines (or other invisible units) from more then one tile away.
        // Garrisoned invisible units can be attacked by anyone, as else the city will be in invincible.
        if (tileCombatant.isInvisible(combatant.getCivInfo()) && !tile.isCityCenter()) {
            return combatant is MapUnitCombatant 
                && combatant.getCivInfo().viewableInvisibleUnitsTiles.map { it.position }.contains(tile.position)
        }
        return true
    }

    fun tryDisembarkUnitToAttackPosition(unit: MapUnit): Boolean {
        if (!unit.baseUnit.isMelee() || !unit.baseUnit.isLandUnit() || !unit.isEmbarked()) return false
        val unitDistanceToTiles = unit.movement.getDistanceToTiles()

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
        val cityWithHealthLeft =
            cityTilesToAttack.filter { it.tileToAttack.getCity()!!.health != 1 } // don't want ranged units to attack defeated cities
                .minByOrNull { it.tileToAttack.getCity()!!.health }

        if (unit.baseUnit.isMelee() && capturableCity != null)
            enemyTileToAttack = capturableCity // enter it quickly, top priority!

        else if (nonCityTilesToAttack.isNotEmpty()) // second priority, units
            enemyTileToAttack = nonCityTilesToAttack.minByOrNull {
                Battle.getMapCombatantOfTile(it.tileToAttack)!!.getHealth()
            }
        else if (cityWithHealthLeft != null) enemyTileToAttack = cityWithHealthLeft // third priority, city

        return enemyTileToAttack
    }
}