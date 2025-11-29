package com.unciv.logic.battle

import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.movement.ParentTileAndTotalMovement
import com.unciv.logic.map.mapunit.movement.PathsToTilesWithinTurn
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

object TargetHelper {
    @Readonly
    fun getAttackableEnemies(
        unit: MapUnit,
        unitDistanceToTiles: PathsToTilesWithinTurn,
        tilesToCheck: List<Tile>? = null,
        stayOnTile: Boolean = false
    ): ArrayList<AttackableTile> {
        val rangeOfAttack = unit.getRange()
        val attackableTiles = ArrayList<AttackableTile>()

        val unitMustBeSetUp = unit.hasUnique(UniqueType.MustSetUp)
        val tilesToAttackFrom = 
            if (stayOnTile || unit.baseUnit.movesLikeAirUnits)
                PathsToTilesWithinTurn.of(unit.currentTile,
                    ParentTileAndTotalMovement(unit.currentTile, unit.currentMovement)
                )
            else unitDistanceToTiles

        val tilesWithEnemies: HashSet<Tile> = HashSet()
        val tilesWithoutEnemies: HashSet<Tile> = HashSet()
        tilesToAttackFrom.forEachTile { reachableTile, path ->
            // tiles we'll still have energy after we reach there
            val movementLeft = getMovementAfterUnitMoves(reachableTile, path, unitMustBeSetUp, unit)
            if (movementLeft <= Constants.minimumMovementEpsilon) return@forEachTile
            // If we are a melee unit that is escorting, we only want to be able to attack from this
            // tile if the escorted unit can also move into the tile we are attacking if we kill the enemy unit.
            if (unit.baseUnit.isMelee() && unit.isEscorting()) {
                val escortingUnit = unit.getOtherEscortUnit()!!
                if (!escortingUnit.movement.canReachInCurrentTurn(reachableTile)
                    || escortingUnit.currentMovement - escortingUnit.movement.getDistanceToTiles().getValue(reachableTile).totalMovement <= 0f) 
                    return@forEachTile
            }

            val tilesInAttackRange =
                if (unit.baseUnit.isMelee()) reachableTile.neighbors
                else if (unit.baseUnit.movesLikeAirUnits || unit.hasUnique(UniqueType.IndirectFire, checkCivInfoUniques = true))
                    reachableTile.getTilesInDistance(rangeOfAttack)
                else reachableTile.tileMap.getViewableTiles(reachableTile.position, rangeOfAttack, true).asSequence()

            for (tile in tilesInAttackRange) {
                when {
                    // Since military units can technically enter tiles with enemy civilians,
                    // some try to move to to the tile and then attack the unit it contains, which is silly
                    tile == reachableTile -> continue

                    tile in tilesWithEnemies -> attackableTiles += AttackableTile(
                        reachableTile,
                        tile,
                        movementLeft,
                        Battle.getMapCombatantOfTile(tile)
                    )
                    tile in tilesWithoutEnemies -> continue // avoid checking the same empty tile multiple times
                    tileContainsAttackableEnemy(unit, tile, tilesToCheck) || unit.isPreparingAirSweep() -> {
                        tilesWithEnemies += tile
                        attackableTiles += AttackableTile(
                            reachableTile, tile, movementLeft,
                            Battle.getMapCombatantOfTile(tile)
                        )
                    }
                    else -> tilesWithoutEnemies += tile
                }
            }
        }       

        return attackableTiles
    }
    
    @Readonly
    private fun getMovementAfterUnitMoves(tile: Tile, distance: ParentTileAndTotalMovement, unitMustBeSetUp: Boolean, unit: MapUnit): Float {
        val movementPointsToExpendAfterMovement = if (unitMustBeSetUp) 1 else 0
        val movementPointsToExpendHere =
            if (unitMustBeSetUp && !unit.isSetUpForSiege()) 1 else 0
        val movementPointsToExpendBeforeAttack =
            if (tile == unit.currentTile) movementPointsToExpendHere else movementPointsToExpendAfterMovement
        val movementLeft =
            unit.currentMovement - distance.totalMovement - movementPointsToExpendBeforeAttack
        if (movementLeft <= Constants.minimumMovementEpsilon)
            return 0f
        // still got leftover movement points after all that, to attack
        if (tile == unit.getTile() || unit.movement.canMoveTo(tile))
            return movementLeft
        return 0f
    }

    @Readonly
    private fun tileContainsAttackableEnemy(unit: MapUnit, tile: Tile, tilesToCheck: List<Tile>?): Boolean {
        if (tile !in (tilesToCheck ?: unit.civ.viewableTiles) || !containsAttackableEnemy(tile, MapUnitCombatant(unit)) )
            return false
        val mapCombatant = Battle.getMapCombatantOfTile(tile)

        return (!unit.baseUnit.isMelee() || mapCombatant !is MapUnitCombatant || !mapCombatant.unit.isCivilian() || unit.movement.canPassThrough(tile))
    }

    @Readonly
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

        if (combatant is MapUnitCombatant && combatant.isLandUnit() && combatant.isMelee() && tile.isWater &&
            !combatant.getCivInfo().tech.unitsCanEmbark && !combatant.unit.cache.canMoveOnWater
        )
            return false

        
        if (combatant is MapUnitCombatant) {
            val gameContext = GameContext(
                unit = (combatant as? MapUnitCombatant)?.unit, tile = tile, 
                ourCombatant = combatant, theirCombatant = tileCombatant, combatAction = CombatAction.Attack)

            if (combatant.hasUnique(UniqueType.CannotAttack, gameContext))
                return false

            if (combatant.unit.getMatchingUniques(UniqueType.CanOnlyAttackUnits, gameContext).run {
                    any() && none { tileCombatant.matchesFilter(it.params[0]) }
                }
            )
                return false

            if (combatant.unit.getMatchingUniques(UniqueType.CanOnlyAttackTiles, gameContext).run {
                    any() && none { tile.matchesFilter(it.params[0]) }
                }
            )
                return false
        }

        // Only units with the right unique can view submarines (or other invisible units) from more then one tile away.
        // Garrisoned invisible units can be attacked by anyone, as else the city will be in invincible.
        if (tileCombatant.isInvisible(combatant.getCivInfo()) && !tile.isCityCenter()) {
            return combatant is MapUnitCombatant
                && combatant.getCivInfo().viewableInvisibleUnitsTiles.map { it.position }.contains(tile.position)
        }
        
        return true
    }

    /** Get a list of visible tiles which have something attackable */
    @Readonly
    fun getBombardableTiles(city: City): Sequence<Tile> =
            city.getCenterTile().getTilesInDistance(city.getBombardRange())
                    .filter { it.isVisible(city.civ) && containsAttackableEnemy(it, CityCombatant(city)) }

}
