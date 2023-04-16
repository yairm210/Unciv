package com.unciv.logic.automation.unit

import com.unciv.Constants
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.BattleDamage
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.PathsToTilesWithinTurn
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.UniqueType

object BattleHelper {

    fun tryAttackNearbyEnemy(unit: MapUnit, stayOnTile: Boolean = false): Boolean {
        if (unit.hasUnique(UniqueType.CannotAttack)) return false
        val attackableEnemies = getAttackableEnemies(unit, unit.movement.getDistanceToTiles(), stayOnTile=stayOnTile)
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

    fun getAttackableEnemies(
        unit: MapUnit,
        unitDistanceToTiles: PathsToTilesWithinTurn,
        tilesToCheck: List<Tile>? = null,
        stayOnTile: Boolean = false
    ): ArrayList<AttackableTile> {
        val rangeOfAttack = unit.getRange()
        val attackableTiles = ArrayList<AttackableTile>()

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
                // still got leftover movement points after all that, to attack
                .filter { it.second > Constants.minimumMovementEpsilon }
                .filter {
                    it.first == unit.getTile() || unit.movement.canMoveTo(it.first)
                }

        val tilesWithEnemies: HashSet<Tile> = HashSet()
        val tilesWithoutEnemies: HashSet<Tile> = HashSet()
        for ((reachableTile, movementLeft) in tilesToAttackFrom) {  // tiles we'll still have energy after we reach there
            val tilesInAttackRange =
                if (unit.hasUnique(UniqueType.IndirectFire) || unit.baseUnit.movesLikeAirUnits())
                    reachableTile.getTilesInDistance(rangeOfAttack)
                else reachableTile.tileMap.getViewableTiles(reachableTile.position, rangeOfAttack, true).asSequence()

            for (tile in tilesInAttackRange) {
                // Since military units can technically enter tiles with enemy civilians,
                // some try to move to to the tile and then attack the unit it contains, which is silly
                if (tile == reachableTile) continue
                if (tile in tilesWithEnemies) attackableTiles += AttackableTile(
                    reachableTile,
                    tile,
                    movementLeft,
                    Battle.getMapCombatantOfTile(tile)!!
                )
                else if (tile in tilesWithoutEnemies) continue // avoid checking the same empty tile multiple times
                else if (tileContainsAttackableEnemy(unit, tile, tilesToCheck)) {
                    tilesWithEnemies += tile
                    attackableTiles += AttackableTile(
                        reachableTile, tile, movementLeft,
                        Battle.getMapCombatantOfTile(tile)
                    )
                } else if (unit.isPreparingAirSweep()) {
                    tilesWithEnemies += tile
                    attackableTiles += AttackableTile(
                        reachableTile, tile, movementLeft,
                        Battle.getMapCombatantOfTile(tile)
                    )
                } else tilesWithoutEnemies += tile
            }
        }
        return attackableTiles
    }

    private fun tileContainsAttackableEnemy(unit: MapUnit, tile: Tile, tilesToCheck: List<Tile>?): Boolean {
        if (!containsAttackableEnemy(tile, MapUnitCombatant(unit))) return false
        if (tile !in (tilesToCheck ?: unit.civ.viewableTiles)) return false
        val mapCombatant = Battle.getMapCombatantOfTile(tile)


        return (!unit.baseUnit.isMelee() || mapCombatant !is MapUnitCombatant || !mapCombatant.unit.isCivilian() || unit.movement.canPassThrough(tile))
    }

    fun containsAttackableEnemy(tile: Tile, combatant: ICombatant): Boolean {
        if (combatant is MapUnitCombatant && combatant.unit.isEmbarked() && !combatant.hasUnique(UniqueType.AttackOnSea)) {
            // Can't attack water units while embarked, only land
            if (tile.isWater || combatant.isRanged())
                return false
        }

        val tileCombatant = Battle.getMapCombatantOfTile(tile) ?: return false
        if (tileCombatant.getCivInfo() == combatant.getCivInfo()) return false
        // If the user automates units, one may capture the city before the user had a chance to decide what to do with it,
        //  and then the next unit should not attack that city
        if (tileCombatant is CityCombatant && tileCombatant.city.hasJustBeenConquered) return false
        if (!combatant.getCivInfo().isAtWarWith(tileCombatant.getCivInfo())) return false

        if (combatant is MapUnitCombatant && combatant.isLandUnit() && combatant.isMelee() &&
            !combatant.hasUnique(UniqueType.LandUnitEmbarkation) && tile.isWater
        )
            return false

        if (combatant is MapUnitCombatant && combatant.hasUnique(UniqueType.CannotAttack))
            return false

        if (combatant is MapUnitCombatant &&
            combatant.unit.getMatchingUniques(UniqueType.CanOnlyAttackUnits).run {
                any() && none { tileCombatant.matchesCategory(it.params[0]) }
            }
        )
            return false

        if (combatant is MapUnitCombatant &&
            combatant.unit.getMatchingUniques(UniqueType.CanOnlyAttackTiles).run {
                any() && none { tile.matchesFilter(it.params[0]) }
            }
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
